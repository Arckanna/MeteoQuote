package com.valerie.meteoquote

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.valerie.meteoquote.data.*
import com.valerie.meteoquote.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

/** Real Android SQLite and disk; deliberately no network or production database. */
@RunWith(AndroidJUnit4::class)
class OfflineCacheTest {
    @Test fun offlineReadAfterReopeningDatabasePreservesDateAndIndependentAir() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "instrumented-offline-cache.db"
        val now = Instant.parse("2026-10-22T12:30:00Z")
        val key = CachePolicy.key(48.0, 2.0)
        val payload = """{"timezone":"UTC","current":{"temperature_2m":19,"weather_code":3},
          "hourly":{"time":["2026-10-22T12:00"],"temperature_2m":[19],"weather_code":[3],"uv_index":[2]},
          "daily":{"time":["2026-10-23"],"temperature_2m_min":[10],"temperature_2m_max":[20],"weather_code":[3],"uv_index_max":[3]}}"""
        context.deleteDatabase(name)
        try {
            val first = Room.databaseBuilder(context, WeatherDatabase::class.java, name).build()
            try {
                first.weather().putWeather(WeatherRecord(key, 48.0, 2.0, "UTC", now.toEpochMilli(), payload))
                first.weather().putAir(AirRecord(key, now.minusSeconds(3600).toEpochMilli(),
                    """{"hourly":{"time":["2026-10-22T12:00"],"european_aqi":[42]}}"""))
            } finally { first.close() }
            val second = Room.databaseBuilder(context, WeatherDatabase::class.java, name).build()
            try {
                val clock = Clock.fixed(now, ZoneOffset.UTC)
                val repository = OfflineWeatherRepository(OpenMeteoWeatherRepository(clock = clock), second.weather(), clock)
                val cached = repository.cachedWeather(48.0, 2.0)!!
                assertEquals(19.0, cached.current.first, 0.001)
                assertEquals(now, cached.fetchedAt)
                assertEquals(now.minusSeconds(3600), cached.airFetchedAt)
                assertEquals(42, cached.aqiNow)
                assertNull(repository.cachedWeather(45.0, 4.0))
            } finally { second.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun transactionRollbackKeepsPreviousSnapshot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, WeatherDatabase::class.java).build()
        try {
            val old = WeatherRecord("place", 48.0, 2.0, "UTC", 100, "old")
            database.weather().putWeather(old)
            try {
                database.withTransaction {
                    database.weather().putWeather(old.copy(payload = "new"))
                    throw IllegalStateException("Interrupted replacement")
                }
            } catch (_: IllegalStateException) { }
            assertEquals(old, database.weather().weather("place"))
        } finally { database.close() }
    }
}
