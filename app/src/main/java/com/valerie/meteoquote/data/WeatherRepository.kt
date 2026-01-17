package com.valerie.meteoquote.data

import com.valerie.meteoquote.data.model.WeatherResult
import com.valerie.meteoquote.data.model.HourlyForecast
import com.valerie.meteoquote.data.model.DailyForecast
import org.json.JSONObject
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime

class WeatherRepository {
    
    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResult {
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&hourly=temperature_2m,weather_code,uv_index" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,uv_index_max" +
                "&timezone=auto"

        val body = URL(url).openStream().bufferedReader().use { it.readText() }
        val root = JSONObject(body)

        // Current
        val cur = root.getJSONObject("current")
        val currentTemp = cur.getDouble("temperature_2m")
        val currentCode = cur.getInt("weather_code")

        // Hourly
        val hourly = root.getJSONObject("hourly")
        val hTimes = hourly.getJSONArray("time")
        val hTemps = hourly.getJSONArray("temperature_2m")
        val hCodes = hourly.getJSONArray("weather_code")
        val hUv = hourly.getJSONArray("uv_index")
        val now = LocalDateTime.now()

        val hList = mutableListOf<HourlyForecast>()
        var uvNow = 0.0
        var grabbedUv = false
        for (i in 0 until hTimes.length()) {
            val t = LocalDateTime.parse(hTimes.getString(i))
            if (!grabbedUv && !t.isBefore(now)) {
                uvNow = hUv.optDouble(i, 0.0)
                grabbedUv = true
            }
            if (!t.isBefore(now)) {
                hList.add(HourlyForecast(t, hTemps.getDouble(i), hCodes.getInt(i)))
            }
            if (hList.size >= 24) break
        }

        // Daily (7 jours)
        val daily = root.getJSONObject("daily")
        val dTimes = daily.getJSONArray("time")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dCodes = daily.getJSONArray("weather_code")
        val dUvMax = daily.getJSONArray("uv_index_max")
        val dList = mutableListOf<DailyForecast>()
        val daysToTake = minOf(7, dTimes.length())
        for (i in 0 until daysToTake) {
            val d = LocalDate.parse(dTimes.getString(i))
            dList.add(DailyForecast(d, dMin.getDouble(i), dMax.getDouble(i), dCodes.getInt(i)))
        }

        val today = LocalDate.now()
        var peakVal = -1.0
        var peakTime: LocalDateTime? = null
        for (i in 0 until hTimes.length()) {
            val t = LocalDateTime.parse(hTimes.getString(i))
            if (t.toLocalDate() == today) {
                val v = hUv.optDouble(i, 0.0)
                if (v > peakVal) {
                    peakVal = v
                    peakTime = t
                }
            }
        }
        val uvMaxToday = if (peakVal >= 0) peakVal else if (dUvMax.length() > 0) dUvMax.getDouble(0) else 0.0

        // AQI (européen)
        val urlAq = "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=$lat&longitude=$lon" +
                "&hourly=european_aqi" +
                "&timezone=auto"
        val aq = URL(urlAq).openStream().bufferedReader().use { it.readText() }
        val aqr = JSONObject(aq).getJSONObject("hourly")
        val aTimes = aqr.getJSONArray("time")
        val aVals = aqr.getJSONArray("european_aqi")

        var aqiNow = 0
        var aqiMaxToday = 0
        var tookNow = false
        var aqiPeakTime: LocalDateTime? = null
        for (i in 0 until aTimes.length()) {
            val t = LocalDateTime.parse(aTimes.getString(i))
            val v = aVals.optInt(i, 0)
            if (!t.isBefore(now) && !tookNow) {
                aqiNow = v
                tookNow = true
            }
            if (t.toLocalDate() == today && v > aqiMaxToday) {
                aqiMaxToday = v
                aqiPeakTime = t
            }
        }
        if (!tookNow && aVals.length() > 0) aqiNow = aVals.optInt(0, 0)
        if (aqiMaxToday < 0) aqiMaxToday = aVals.optInt(0, 0)

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
