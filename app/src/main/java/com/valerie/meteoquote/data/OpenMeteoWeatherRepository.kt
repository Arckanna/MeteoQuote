package com.valerie.meteoquote.data

import com.valerie.meteoquote.data.model.WeatherResult
import com.valerie.meteoquote.data.model.HourlyForecast
import com.valerie.meteoquote.data.model.DailyForecast
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

class OpenMeteoWeatherRepository(private val transport: HttpTransport = HttpTransport(), private val clock: java.time.Clock = java.time.Clock.systemUTC()) : WeatherRepository {

    override suspend fun fetchWeather(lat: Double, lon: Double): WeatherResult {
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&hourly=temperature_2m,weather_code,uv_index" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,uv_index_max" +
                "&timezone=auto&forecast_days=8"

        val body = transport.get(url)
        val aqBody = try {
            transport.get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&hourly=european_aqi&timezone=auto")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: java.io.IOException) {
            null
        }
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { parseWeather(body, aqBody) }
    }

    internal fun parseWeather(body: String, aqBody: String? = null): WeatherResult {
        val root = JSONObject(body)
        val now = java.time.ZonedDateTime.ofInstant(clock.instant(), java.time.ZoneId.of(root.getString("timezone"))).toLocalDateTime()
        // Current
        val cur = root.getJSONObject("current")
        val currentTemp = cur.getDouble("temperature_2m").also { require(it.isFinite()) }
        val currentCode = cur.getInt("weather_code")

        // Hourly
        val hourly = root.getJSONObject("hourly")
        val hTimes = hourly.getJSONArray("time")
        val hTemps = hourly.getJSONArray("temperature_2m")
        val hCodes = hourly.getJSONArray("weather_code")
        val hUv = hourly.getJSONArray("uv_index")
        require(listOf(hTemps, hCodes, hUv).all { it.length() == hTimes.length() }) { "Séries horaires incohérentes" }


        val hList = mutableListOf<HourlyForecast>()
        var uvNow = -1.0
        var grabbedUv = false
        for (i in 0 until hTimes.length()) {
            val t = LocalDateTime.parse(hTimes.getString(i))
            if (!grabbedUv && t == now.truncatedTo(java.time.temporal.ChronoUnit.HOURS)) {
                uvNow = hUv.optDouble(i, -1.0)
                grabbedUv = true
            }
            if (!t.isBefore(now.truncatedTo(java.time.temporal.ChronoUnit.HOURS))) {
                hList.add(HourlyForecast(t, hTemps.getDouble(i), hCodes.getInt(i)))
            }
            if (hList.size >= 24) break
        }

        // Seven future days, relative to the selected city's date (not the phone's date).
        val daily = root.getJSONObject("daily")
        val dTimes = daily.getJSONArray("time")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dCodes = daily.getJSONArray("weather_code")
        val dUvMax = daily.getJSONArray("uv_index_max")
        require(listOf(dMax, dMin, dCodes, dUvMax).all { it.length() == dTimes.length() }) { "Séries quotidiennes incohérentes" }
        val dList = mutableListOf<DailyForecast>()
        val today = now.toLocalDate()
        for (i in 0 until dTimes.length()) {
            val d = LocalDate.parse(dTimes.getString(i))
            if (d.isAfter(today) && !d.isAfter(today.plusDays(7))) {
                dList.add(DailyForecast(d, dMin.getDouble(i), dMax.getDouble(i), dCodes.getInt(i)))
            }
        }

        var peakVal = -1.0
        var peakTime: LocalDateTime? = null
        for (i in 0 until hTimes.length()) {
            val t = LocalDateTime.parse(hTimes.getString(i))
            if (t.toLocalDate() == today) {
                val v = hUv.optDouble(i, -1.0)
                if (v > peakVal) {
                    peakVal = v
                    peakTime = t
                }
            }
        }
        val todayIndex = (0 until dTimes.length()).firstOrNull { dTimes.getString(it) == today.toString() }
        val uvMaxToday = if (peakVal >= 0) peakVal else todayIndex?.let { dUvMax.optDouble(it, -1.0) } ?: -1.0

        // AQI (européen) - avec gestion d'erreur séparée car optionnel
        var aqiNow = -1
        var aqiMaxToday = -1
        var aqiPeakTime: LocalDateTime? = null

        try {
            val aq = aqBody ?: throw java.io.IOException("AQI indisponible")
            val aqr = JSONObject(aq).getJSONObject("hourly")
            val aTimes = aqr.getJSONArray("time")
            val aVals = aqr.getJSONArray("european_aqi")

            var tookNow = false
            for (i in 0 until aTimes.length()) {
                val t = LocalDateTime.parse(aTimes.getString(i))
                if (aVals.isNull(i)) continue
                val v = aVals.getInt(i)
                if (t == now.truncatedTo(java.time.temporal.ChronoUnit.HOURS) && !tookNow) {
                    aqiNow = v
                    tookNow = true
                }
                if (t.toLocalDate() == today && v > aqiMaxToday) {
                    aqiMaxToday = v
                    aqiPeakTime = t
                }
            }


        } catch (e: Exception) {
            aqiNow = -1
            aqiMaxToday = -1
            aqiPeakTime = null
            // AQI est optionnel
            // -1 représente une mesure indisponible, jamais une bonne qualité.
        }

        return WeatherResult(
            currentTemp to currentCode,
            hList,
            dList,
            uvNow,
            uvMaxToday,
            peakTime,
            aqiNow,
            aqiMaxToday,
            aqiPeakTime
        )
    }
}
