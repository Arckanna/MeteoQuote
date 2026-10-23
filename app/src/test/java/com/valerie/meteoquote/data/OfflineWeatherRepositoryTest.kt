package com.valerie.meteoquote.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.valerie.meteoquote.data.local.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OfflineWeatherRepositoryTest {
    private lateinit var database: WeatherDatabase
    private lateinit var transport: HttpTransport
    private lateinit var repo: OfflineWeatherRepository
    private lateinit var scope: CoroutineScope
    private val key = CachePolicy.key(48.0, 2.0)
    private val now = Instant.parse("2026-10-22T12:30:00Z")
    private val clock = object : Clock() {
        var current = now
        override fun instant() = current
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
    }
    private val body = """{
      "timezone":"UTC","current":{"time":1792670400,"temperature_2m":18.5,"weather_code":3},
      "hourly":{"time":["2026-10-22T12:00","2026-10-22T13:00","2026-10-23T12:00"],
        "temperature_2m":[18.5,19,20],"weather_code":[3,3,0],"uv_index":[2,3,4]},
      "daily":{"time":["2026-10-22","2026-10-23","2026-10-24"],
        "temperature_2m_min":[10,11,12],"temperature_2m_max":[20,21,22],"weather_code":[3,0,0],"uv_index_max":[3,4,5]}
    }"""
    private val air = """{"hourly":{"time":["2026-10-22T12:00"],"european_aqi":[42]}}"""

    @Before fun setup() {
        clock.current = now
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeatherDatabase::class.java).build()
        transport = mockk()
        coEvery { transport.get(match { it.contains("api.open-meteo.com") }) } returns body
        coEvery { transport.get(match { it.contains("air-quality-api") }) } returns air
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repo = OfflineWeatherRepository(OpenMeteoWeatherRepository(transport, clock), database.weather(), clock, scope,
            retainedKeys = { listOf(key) })
    }
    @After fun teardown() { scope.cancel(); database.close() }
    private suspend fun finished(force: Boolean = true) = withTimeout(15_000) {
        repo.refreshUpdates(48.0, 2.0, force).first { !it.isRefreshing && !it.isAirQualityLoading }
    }

    @Test fun successfulRequestIsPersistedAndCanBeReadOffline() = runBlocking {
        val fresh = finished()
        assertEquals(42, fresh.aqiNow)
        coEvery { transport.get(any()) } throws java.io.IOException("offline")
        val offline = finished()
        assertEquals(18.5, offline.current.first, 0.001)
        assertEquals(now, offline.fetchedAt)
        assertNotNull(offline.refreshError)
        assertEquals(42, offline.aqiNow)
    }

    @Test fun firstCacheEmissionDoesNotWaitForNetwork() = runBlocking {
        finished()
        coEvery { transport.get(any()) } coAnswers { awaitCancellation() }
        val cached = withTimeout(2_000) { repo.refreshUpdates(48.0, 2.0, true).first() }
        assertEquals(18.5, cached.current.first, 0.001)
        assertTrue(cached.isRefreshing)
    }

    @Test fun freshCacheSkipsBothRequests() = runBlocking {
        finished()
        clearMocks(transport, answers = false)
        finished(force = false)
        coVerify(exactly = 0) { transport.get(any()) }
    }

    @Test fun brokenAirKeepsPreviousAirTimestampAndNewWeather() = runBlocking {
        finished()
        clock.current = now.plusSeconds(1800)
        coEvery { transport.get(match { it.contains("air-quality-api") }) } returns "{}"
        val updated = finished()
        assertEquals(clock.instant(), updated.fetchedAt)
        assertEquals(now, updated.airFetchedAt)
        // The stored air series has no value for 13:00: never relabel 12:00 as current.
        assertEquals(-1, updated.aqiNow)
    }

    @Test fun storageErrorsAreNotReportedAsNetworkFailures() = runBlocking {
        val broken = mockk<WeatherDao>()
        coEvery { broken.snapshot(any()) } throws IllegalStateException("disk failure")
        val failing = OfflineWeatherRepository(OpenMeteoWeatherRepository(transport, clock), broken, clock, scope)
        try { failing.cachedWeather(48.0, 2.0); fail("Expected storage error") }
        catch (e: WeatherStorageException) { assertTrue(e.cause is IllegalStateException) }
        coVerify(exactly = 0) { transport.get(any()) }
    }

    @Test fun failureWithoutCacheDoesNotUseAnotherCity() = runBlocking {
        finished()
        coEvery { transport.get(any()) } throws java.io.IOException("offline")
        assertNull(repo.cachedWeather(45.0, 4.0))
        try {
            withTimeout(5_000) { repo.refreshUpdates(45.0, 4.0, true).first() }
            fail("Expected offline failure")
        } catch (e: NoCachedWeatherException) { assertTrue(e.failure is java.io.IOException) }
        assertEquals(18.5, repo.cachedWeather(48.0, 2.0)!!.current.first, 0.001)
    }

    @Test fun invalidNetworkResponseDoesNotReplaceLastGoodSnapshot() = runBlocking {
        finished()
        val saved = database.weather().weather(key)
        coEvery { transport.get(any()) } returns "not-json"
        assertNotNull(finished().refreshError)
        assertEquals(saved, database.weather().weather(key))
    }

    @Test fun widgetRefreshDoesNotEraseOrRejuvenateAir() = runBlocking {
        finished()
        val previous = database.weather().air(key)
        clock.current = now.plusSeconds(3600)
        repo.fetchWeather(48.0, 2.0)
        assertEquals(previous, database.weather().air(key))
        assertEquals(clock.instant(), repo.cachedWeather(48.0, 2.0)!!.fetchedAt)
    }

    @Test fun nextDayUsesStoredFullSeriesAndDoesNotShowYesterdayPeakOrAir() = runBlocking {
        finished()
        clock.current = now.plusSeconds(86400)
        val cached = repo.cachedWeather(48.0, 2.0)!!
        assertTrue(cached.isOld)
        assertEquals(LocalDate.of(2026, 10, 24), cached.daily.first().date)
        assertEquals(4.0, cached.uvMaxToday, 0.001)
        assertEquals(-1, cached.aqiNow)
        assertEquals(now, cached.fetchedAt)
        clock.current = now.plusSeconds(7 * 86400)
        assertNull(repo.cachedWeather(48.0, 2.0))
    }

    @Test fun simultaneousConsumersShareWeatherAndCancellingOneKeepsTheOther() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { transport.get(match { it.contains("api.open-meteo.com") }) } coAnswers {
            started.complete(Unit)
            release.await()
            body
        }
        val screen = async { finished() }
        started.await()
        val widget = async { repo.fetchWeather(48.0, 2.0) }
        // Give the second consumer an opportunity to join the in-flight operation.
        delay(100)
        screen.cancelAndJoin()
        release.complete(Unit)
        assertEquals(18.5, widget.await().current.first, 0.001)
        coVerify(exactly = 1) { transport.get(match { it.contains("api.open-meteo.com") }) }
    }

    @Test fun databaseSurvivesClosingAndReopeningWithoutChangingPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "lot-c-reopen-test.db"
        context.deleteDatabase(name)
        val prefs = context.getSharedPreferences("lot-c-test", Context.MODE_PRIVATE)
        prefs.edit().putString("cities_json", "kept").commit()
        try {
            Room.databaseBuilder(context, WeatherDatabase::class.java, name).build().let { db ->
                try { db.weather().putWeather(WeatherRecord(key, 48.0, 2.0, "UTC", now.toEpochMilli(), body)) }
                finally { db.close() }
            }
            Room.databaseBuilder(context, WeatherDatabase::class.java, name).build().let { db ->
                try {
                    val reopened = OfflineWeatherRepository(OpenMeteoWeatherRepository(transport, clock), db.weather(), clock, scope)
                    assertEquals(18.5, reopened.cachedWeather(48.0, 2.0)!!.current.first, 0.001)
                    assertEquals(now, reopened.cachedWeather(48.0, 2.0)!!.fetchedAt)
                } finally { db.close() }
            }
            assertEquals("kept", prefs.getString("cities_json", null))
        } finally { context.deleteDatabase(name); prefs.edit().clear().commit() }
    }
}
