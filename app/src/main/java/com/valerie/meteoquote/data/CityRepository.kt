package com.valerie.meteoquote.data

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.valerie.meteoquote.data.model.City
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class CityRepository(private val context: Context) {
    
    private fun prefs() = context.getSharedPreferences("meteoquote_prefs", Context.MODE_PRIVATE)
    
    val defaultCities = listOf(
        City("Toulouse", 43.6047, 1.4442)
    )
    
    fun loadCities(): List<City> {
        val json = prefs().getString("cities_json", null) ?: return emptyList()
        val arr = JSONArray(json)
        val list = mutableListOf<City>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(City(o.getString("label"), o.getDouble("lat"), o.getDouble("lon")))
        }
        return list
    }
    
    fun saveCities(cities: List<City>) {
        val arr = JSONArray()
        cities.forEach { c ->
            val o = JSONObject()
            o.put("label", c.label)
            o.put("lat", c.lat)
            o.put("lon", c.lon)
            arr.put(o)
        }
        prefs().edit()
            .putString("cities_json", arr.toString())
            .apply()
    }
    
    suspend fun geocodeCity(name: String): City? {
        val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=fr&format=json"
        val body = URL(url).openStream().bufferedReader().use { it.readText() }
        val results = JSONObject(body).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val first = results.getJSONObject(0)

        val nm = first.optString("name", name)
        val admin1 = first.optString("admin1", "")
        val country = first.optString("country_code", "")
        val label = when {
            admin1.isNotEmpty() && country.isNotEmpty() -> "$nm, $admin1 ($country)"
            country.isNotEmpty() -> "$nm ($country)"
            else -> nm
        }

        val lat = first.getDouble("latitude")
        val lon = first.getDouble("longitude")
        return City(label, lat, lon)
    }
    
    suspend fun reverseGeocode(lat: Double, lon: Double): City? {
        runCatching { reverseOpenMeteo(lat, lon) }.getOrNull()?.let { if (it != null) return it }
        runCatching { reverseNominatim(lat, lon) }.getOrNull()?.let { if (it != null) return it }
        return runCatching { reverseAndroidGeocoder(lat, lon) }.getOrNull()
    }
    
    private fun reverseOpenMeteo(lat: Double, lon: Double): City? {
        val url = URL(
            "https://geocoding-api.open-meteo.com/v1/reverse" +
                    "?latitude=$lat&longitude=$lon&language=fr&format=json&count=1"
        )
        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }

            if (code !in 200..299) {
                Log.w("MeteoQuote", "OpenMeteo reverse HTTP $code: $body")
                null
            } else {
                val results = JSONObject(body).optJSONArray("results") ?: return null
                if (results.length() == 0) return null
                val first = results.getJSONObject(0)

                val nm = first.optString("name", "")
                val admin1 = first.optString("admin1", "")
                val country = first.optString("country_code", "")
                val label = buildLabel(nm, town = null, village = null, municipality = null, county = null, state = admin1, countryCode = country)
                val la = first.optDouble("latitude", lat)
                val lo = first.optDouble("longitude", lon)
                if (label.isBlank()) null else City(label, la, lo)
            }
        } catch (e: Exception) {
            Log.e("MeteoQuote", "reverseOpenMeteo error", e)
            null
        } finally {
            conn?.disconnect()
        }
    }
    
    private fun reverseNominatim(lat: Double, lon: Double): City? {
        val url = URL(
            "https://nominatim.openstreetmap.org/reverse" +
                    "?lat=$lat&lon=$lon&format=jsonv2&accept-language=fr&zoom=10"
        )
        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MeteoQuote/6.2 (ivray3dlabs@gmail.com)")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }

            if (code !in 200..299) {
                Log.w("MeteoQuote", "Nominatim reverse HTTP $code: $body")
                null
            } else {
                val jo = JSONObject(body)
                val addr = jo.optJSONObject("address") ?: return null

                val city = addr.optString("city", "")
                val town = addr.optString("town", "")
                val village = addr.optString("village", "")
                val municipality = addr.optString("municipality", "")
                val county = addr.optString("county", "")
                val state = addr.optString("state", "")
                val cc = addr.optString("country_code", "").uppercase(Locale.ROOT)

                val label = buildLabel(city, town, village, municipality, county, state, cc)
                if (label.isBlank()) null else City(label, lat, lon)
            }
        } catch (e: Exception) {
            Log.e("MeteoQuote", "reverseNominatim error", e)
            null
        } finally {
            conn?.disconnect()
        }
    }
    
    private fun reverseAndroidGeocoder(lat: Double, lon: Double): City? {
        if (!Geocoder.isPresent()) return null
        return try {
            val geo = Geocoder(context, Locale.getDefault())
            val list = geo.getFromLocation(lat, lon, 1)
            val a = list?.firstOrNull() ?: return null
            val city = a.locality ?: a.subAdminArea ?: a.subLocality ?: ""
            val state = a.adminArea ?: ""
            val cc = a.countryCode ?: ""
            val label = buildLabel(city, town = null, village = null, municipality = null, county = a.subAdminArea, state = state, countryCode = cc)
            if (label.isBlank()) null else City(label, lat, lon)
        } catch (e: IOException) {
            Log.e("MeteoQuote", "reverseAndroidGeocoder IO", e)
            null
        } catch (e: Exception) {
            Log.e("MeteoQuote", "reverseAndroidGeocoder error", e)
            null
        }
    }
    
    private fun buildLabel(
        city: String?,
        town: String?,
        village: String?,
        municipality: String?,
        county: String?,
        state: String?,
        countryCode: String?
    ): String {
        val place = when {
            !city.isNullOrBlank() -> city
            !town.isNullOrBlank() -> town
            !village.isNullOrBlank() -> village
            !municipality.isNullOrBlank() -> municipality
            !county.isNullOrBlank() -> county
            else -> ""
        }
        val admin1 = state?.takeIf { it.isNotBlank() } ?: ""
        val cc = countryCode?.takeIf { it.isNotBlank() } ?: ""
        return when {
            place.isBlank() -> ""
            admin1.isNotEmpty() && cc.isNotEmpty() -> "$place, $admin1 ($cc)"
            cc.isNotEmpty() -> "$place ($cc)"
            admin1.isNotEmpty() -> "$place, $admin1"
            else -> place
        }
    }
}
