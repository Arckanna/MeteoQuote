package com.valerie.meteoquote.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WeatherRepositoryTest {
    private val repository = WeatherRepository(clock = Clock.fixed(Instant.parse("2026-10-17T10:30:00Z"), ZoneOffset.UTC))
    private val body = """{
        "timezone":"Europe/Paris",
        "current":{"temperature_2m":18.5,"weather_code":77},
        "hourly":{"time":["2026-10-17T12:00","2026-10-17T13:00"],"temperature_2m":[18.0,19.0],"weather_code":[77,3],"uv_index":[2.0,4.0]},
        "daily":{"time":["2026-10-17"],"temperature_2m_max":[20.0],"temperature_2m_min":[10.0],"weather_code":[77],"uv_index_max":[4.0]}
    }"""

    @Test fun parsesForecastInCityTimezoneAndKeepsCurrentHour() {
        val result = repository.parseWeather(body)
        assertEquals(18.5, result.current.first, 0.001)
        assertEquals(77, result.current.second)
        assertEquals(12, result.hourly.first().time.hour)
        assertEquals(2.0, result.uvNow, 0.001)
        assertEquals(13, result.uvPeakTime!!.hour)
        assertEquals(10.0, result.daily.first().tmin, 0.001)
        assertEquals(-1, result.aqiNow)
    }

    @Test fun optionalAirFailureDoesNotLoseWeather() {
        assertEquals(-1, repository.parseWeather(body, "invalid").aqiNow)
        val air = """{"hourly":{"time":["2026-10-17T12:00"],"european_aqi":[null]}}"""
        assertEquals(-1, repository.parseWeather(body, air).aqiNow)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMisalignedArrays() {
        repository.parseWeather(body.replace("[18.0,19.0]", "[18.0]"))
    }

    @Test(expected = org.json.JSONException::class)
    fun rejectsMalformedResponse() { repository.parseWeather("<html>error</html>") }
}
