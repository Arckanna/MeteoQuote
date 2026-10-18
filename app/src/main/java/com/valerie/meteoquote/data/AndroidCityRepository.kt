package com.valerie.meteoquote.data

import android.content.Context
import android.location.Geocoder
import com.valerie.meteoquote.data.model.City
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class AndroidCityRepository(private val context: Context, private val transport: HttpTransport = HttpTransport()) : CityRepository {
    
    private fun prefs() = context.getSharedPreferences("meteoquote_prefs", Context.MODE_PRIVATE)
    
    override val defaultCities = listOf(
        City("Toulouse", 43.6047, 1.4442)
    )
    
    override fun loadCities(): List<City> {
        val json = prefs().getString("cities_json", null) ?: return emptyList()
        val arr = try { JSONArray(json) } catch (_: org.json.JSONException) { return emptyList() }
        val list = mutableListOf<City>()
        for (i in 0 until arr.length()) {
            runCatching {
                val o = arr.getJSONObject(i)
                City(o.getString("label"), o.getDouble("lat"), o.getDouble("lon"))
            }.getOrNull()?.takeIf { it.label.isNotBlank() && it.lat in -90.0..90.0 && it.lon in -180.0..180.0 }?.let(list::add)
        }
        return list
    }
    
    override fun saveCities(cities: List<City>) {
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
    
    override suspend fun geocodeCity(name: String): City? {
        val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=fr&format=json"
        val body = transport.get(url)
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
    
    override suspend fun reverseGeocode(lat: Double, lon: Double): City? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            val geo = Geocoder(context, Locale.FRENCH)
            val list = if (android.os.Build.VERSION.SDK_INT >= 33) {
                kotlinx.coroutines.withTimeoutOrNull(5_000) {
                    kotlinx.coroutines.suspendCancellableCoroutine<List<android.location.Address>> { continuation ->
                        geo.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                if (continuation.isActive) continuation.resumeWith(Result.success(addresses))
                            }
                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resumeWith(Result.success(emptyList()))
                            }
                        })
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geo.getFromLocation(lat, lon, 1)
            }
            val a = list?.firstOrNull() ?: return@withContext null
            val label = buildLabel(a.locality, null, null, null, a.subAdminArea, a.adminArea, a.countryCode)
            if (label.isBlank()) null else City(label, lat, lon)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: IOException) {
            null
        }
    }

    override fun selectedIndex(size: Int): Int = prefs().getInt("selected_city", 0).coerceIn(0, (size - 1).coerceAtLeast(0))
    override fun saveSelectedIndex(index: Int) { prefs().edit().putInt("selected_city", index).apply() }
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
