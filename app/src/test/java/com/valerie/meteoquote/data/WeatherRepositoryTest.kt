package com.valerie.meteoquote.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.toList

class WeatherRepositoryTest {
    @Test fun repeatedAutumnHourUsesTheCorrectInstant() {
        val root = org.json.JSONObject(body)
        val hour = root.getJSONObject("hourly")
        hour.put("time", org.json.JSONArray(listOf(
            Instant.parse("2026-10-25T00:00:00Z").epochSecond,
            Instant.parse("2026-10-25T01:00:00Z").epochSecond)))
        val result = OpenMeteoWeatherRepository(clock = Clock.fixed(
            Instant.parse("2026-10-25T01:30:00Z"), ZoneOffset.UTC)).parseWeather(root.toString())
        assertEquals(1, result.hourly.size)
        assertEquals(19.0, result.hourly.first().temp, 0.001)
        assertEquals(4.0, result.uvNow, 0.001)
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun weatherIsEmittedBeforeSlowAirQualityAndAirWaitIsBounded() = kotlinx.coroutines.test.runTest {
        val transport = io.mockk.mockk<HttpTransport>()
        io.mockk.coEvery { transport.get(match { it.contains("api.open-meteo.com") }) } returns body
        var airCancelled = false
        io.mockk.coEvery { transport.get(match { it.contains("air-quality-api") }) } coAnswers {
            try { kotlinx.coroutines.delay(30_000); "{}" } finally { airCancelled = true }
        }
        val results = mutableListOf<com.valerie.meteoquote.data.model.WeatherResult>()
        val repo = OpenMeteoWeatherRepository(transport, Clock.fixed(Instant.parse("2026-10-17T10:30:00Z"), ZoneOffset.UTC))
        repo.weatherUpdates(48.0, 2.0).collect {
            results.add(it)
            if (results.size == 1) {
                assertEquals(0L, testScheduler.currentTime)
                assertEquals(18.5, it.current.first, 0.001)
                assertTrue(it.isAirQualityLoading)
                assertEquals(-1, it.aqiNow)
            }
        }
        assertEquals(5_000L, testScheduler.currentTime)
        assertTrue(airCancelled)
        assertEquals(2, results.size)
        assertFalse(results.last().isAirQualityLoading)
        assertEquals(results.first().current, results.last().current)
    }

    @Test fun airQualityEnrichesWeatherAfterFirstEmission() = kotlinx.coroutines.runBlocking {
        val transport = io.mockk.mockk<HttpTransport>()
        io.mockk.coEvery { transport.get(match { it.contains("api.open-meteo.com") }) } returns body
        io.mockk.coEvery { transport.get(match { it.contains("air-quality-api") }) } returns
            """{"hourly":{"time":["2026-10-17T12:00"],"european_aqi":[42]}}"""
        val results = OpenMeteoWeatherRepository(transport,
            Clock.fixed(Instant.parse("2026-10-17T10:30:00Z"), ZoneOffset.UTC))
            .weatherUpdates(48.0, 2.0).toList()
        assertEquals(listOf(-1, 42), results.map { it.aqiNow })
        assertEquals(listOf(true, false), results.map { it.isAirQualityLoading })
        assertEquals(results.first().hourly, results.last().hourly)
    }

    @Test fun widgetFetchNeverRequestsAirQuality() = kotlinx.coroutines.runBlocking {
        val transport = io.mockk.mockk<HttpTransport>()
        io.mockk.coEvery { transport.get(any()) } returns body
        OpenMeteoWeatherRepository(transport).fetchWeather(48.0, 2.0)
        io.mockk.coVerify(exactly = 1) { transport.get(any()) }
        io.mockk.coVerify(exactly = 0) { transport.get(match { it.contains("air-quality-api") }) }
    }

    private fun eightDayBody(): String {
        val root = org.json.JSONObject(body)
        val daily = root.getJSONObject("daily")
        val start = java.time.LocalDate.of(2026, 10, 17)
        daily.put("time", org.json.JSONArray((0..7).map { start.plusDays(it.toLong()).toString() }))
        for (field in listOf("temperature_2m_min", "temperature_2m_max", "uv_index_max")) {
            daily.put(field, org.json.JSONArray((0..7).map { it.toDouble() }))
        }
        daily.put("weather_code", org.json.JSONArray((0..7).map { 3 }))
        return root.toString()
    }

    @Test fun sevenForecastDaysStartTomorrowAndEndAtDaySeven() {
        val days = repository.parseWeather(eightDayBody()).daily
        assertEquals(7, days.size)
        assertEquals(java.time.LocalDate.of(2026, 10, 18), days.first().date)
        assertEquals(java.time.LocalDate.of(2026, 10, 24), days.last().date)
        assertEquals(1.0, days.first().tmin, 0.001)
        assertEquals(7.0, days.last().tmax, 0.001)
    }

    @Test fun forecastTomorrowIsBasedOnCityDateAtInternationalDateBoundary() {
        val root = org.json.JSONObject(eightDayBody()).put("timezone", "Pacific/Kiritimati")
        val clock = Clock.fixed(Instant.parse("2026-10-17T10:30:00Z"), ZoneOffset.UTC)
        val days = OpenMeteoWeatherRepository(clock = clock).parseWeather(root.toString()).daily
        assertEquals(java.time.LocalDate.of(2026, 10, 19), days.first().date)
        assertTrue(days.all { it.date.isAfter(java.time.LocalDate.of(2026, 10, 18)) })
    }

    @Test fun todayUvFallbackIsNotTakenFromTomorrow() {
        val root = org.json.JSONObject(eightDayBody())
        val hourly = root.getJSONObject("hourly")
        for (field in listOf("time", "temperature_2m", "weather_code", "uv_index")) {
            hourly.put(field, org.json.JSONArray())
        }
        assertEquals(0.0, repository.parseWeather(root.toString()).uvMaxToday, 0.001)
        assertEquals(java.time.LocalDate.of(2026, 10, 18), repository.parseWeather(root.toString()).daily.first().date)
    }

    @Test fun requestIncludesTodayAndSevenFutureDays() = kotlinx.coroutines.runBlocking {
        val transport = io.mockk.mockk<HttpTransport>()
        io.mockk.coEvery { transport.get(any()) } returns eightDayBody()
        val result = OpenMeteoWeatherRepository(transport,
            Clock.fixed(Instant.parse("2026-10-17T10:30:00Z"), ZoneOffset.UTC)).fetchWeather(48.0, 2.0)
        assertEquals(7, result.daily.size)
        io.mockk.coVerify { transport.get(match { it.contains("forecast_days=8") && it.contains("timezone=auto") }) }
    }

    private val repository = OpenMeteoWeatherRepository(clock = Clock.fixed(Instant.parse("2026-10-17T10:30:00Z"), ZoneOffset.UTC))
    private val body = """{
        "timezone":"Europe/Paris",
        "current":{"temperature_2m":18.5,"weather_code":77},
        "hourly":{"time":["2026-10-17T12:00","2026-10-17T13:00"],"temperature_2m":[18.0,19.0],"weather_code":[77,3],"uv_index":[2.0,4.0]},
        "daily":{"time":["2026-10-17","2026-10-18"],"temperature_2m_max":[20.0,21.0],"temperature_2m_min":[10.0,11.0],"weather_code":[77,3],"uv_index_max":[4.0,5.0]}
    }"""

    @Test fun parsesForecastInCityTimezoneAndKeepsCurrentHour() {
        val result = repository.parseWeather(body)
        assertEquals(18.5, result.current.first, 0.001)
        assertEquals(77, result.current.second)
        assertEquals(12, result.hourly.first().time.hour)
        assertEquals(2.0, result.uvNow, 0.001)
        assertEquals(13, result.uvPeakTime!!.hour)
        assertEquals(11.0, result.daily.first().tmin, 0.001)
        assertEquals(java.time.LocalDate.of(2026, 10, 18), result.daily.first().date)
        assertEquals(-1, result.aqiNow)
    }

    @Test fun optionalAirFailureDoesNotLoseWeather() {
        assertEquals(-1, repository.parseWeather(body, "invalid").aqiNow)
        val air = """{"hourly":{"time":["2026-10-17T12:00"],"european_aqi":[null]}}"""
        assertEquals(-1, repository.parseWeather(body, air).aqiNow)
    }

    @Test fun optionalHttpFailureStillReturnsWeather() = kotlinx.coroutines.runBlocking {
        val transport = io.mockk.mockk<HttpTransport>()
        io.mockk.coEvery { transport.get(match { it.contains("api.open-meteo.com") }) } returns body
        io.mockk.coEvery { transport.get(match { it.contains("air-quality-api") }) } throws HttpStatusException(503)
        val result = OpenMeteoWeatherRepository(transport, Clock.fixed(Instant.parse("2026-10-17T10:30:00Z"), ZoneOffset.UTC))
            .weatherUpdates(48.0, 2.0).toList().last()
        assertEquals(18.5, result.current.first, 0.001)
        assertEquals(-1, result.aqiNow)
    }

    @Test fun optionalAirCancellationIsNotSwallowed() = kotlinx.coroutines.runBlocking {
        val transport = io.mockk.mockk<HttpTransport>()
        io.mockk.coEvery { transport.get(any()) } returns body
        io.mockk.coEvery { transport.get(match { it.contains("air-quality-api") }) } throws kotlinx.coroutines.CancellationException()
        try { OpenMeteoWeatherRepository(transport).weatherUpdates(48.0, 2.0).toList(); fail("Expected cancellation") }
        catch (_: kotlinx.coroutines.CancellationException) { }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMisalignedArrays() {
        repository.parseWeather(body.replace("[18.0,19.0]", "[18.0]"))
    }

    @Test(expected = org.json.JSONException::class)
    fun rejectsMalformedResponse() { repository.parseWeather("<html>error</html>") }
}
