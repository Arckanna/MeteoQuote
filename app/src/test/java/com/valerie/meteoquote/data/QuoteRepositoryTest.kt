package com.valerie.meteoquote.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuoteRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("meteoquote_prefs", Context.MODE_PRIVATE)
    private val clock = MutableClock(Instant.parse("2026-10-20T10:00:00Z"))
    private var zone = ZoneId.of("Europe/Paris")
    private fun repository() = LocalQuoteRepository(context, clock) { zone }

    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun readingBeforeAttributionDoesNotChooseNeutralAndRestoresTodaysAssignment() {
        assertNull(repository().assignedQuote())
        val first = repository().getQuote(0)
        assertEquals(first, repository().assignedQuote())
        clock.value = clock.value.plus(Duration.ofDays(1))
        assertNull(repository().assignedQuote())
    }

    @Test fun sameDateSurvivesWeatherCityAndRepositoryChanges() {
        val first = repository().getQuote(0)
        for (code in listOf(null, 3, 45, 61, 77, 95)) assertEquals(first, repository().getQuote(code))
    }

    @Test fun consecutiveDaysNeverRepeatWithinSameAtmosphere() {
        var previous: String? = null
        repeat(30) {
            val current = repository().getQuote(3)
            assertNotEquals(previous, current)
            previous = current
            clock.value = clock.value.plus(Duration.ofDays(1))
        }
    }

    @Test fun midnightUsesPhoneDateRatherThanUtc() {
        clock.value = Instant.parse("2026-10-20T21:59:59Z")
        val before = repository().getQuote(0)
        clock.value = clock.value.plusSeconds(1) // midnight in Paris
        assertNotEquals(before, repository().getQuote(0))
        assertTrue(prefs.contains("daily_quote_v1_2026-10-21"))
    }

    @Test fun changingTimezoneAndReturningToKnownDateReusesAssignment() {
        clock.value = Instant.parse("2026-10-20T23:30:00Z")
        zone = ZoneId.of("America/New_York")
        val twentieth = repository().getQuote(61)
        zone = ZoneId.of("Asia/Tokyo")
        val twentyFirst = repository().getQuote(77)
        assertNotEquals(twentieth, twentyFirst)
        zone = ZoneId.of("America/New_York")
        assertEquals(twentieth, repository().getQuote(0))
    }

    @Test fun productionZoneProviderReadsSystemTimezoneEachTime() {
        val previousZone = TimeZone.getDefault()
        try {
            clock.value = Instant.parse("2026-10-20T23:30:00Z")
            val repo = LocalQuoteRepository(context, clock)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val first = repo.getQuote(0)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertNotEquals(first, repo.getQuote(0))
        } finally { TimeZone.setDefault(previousZone) }
    }

    @Test fun daylightSavingJumpDoesNotChangeQuoteWithinTheDay() {
        for (start in listOf("2026-03-29T00:30:00Z", "2026-10-25T00:30:00Z")) {
            clock.value = Instant.parse(start)
            val first = repository().getQuote(0)
            clock.value = clock.value.plusSeconds(3600)
            assertEquals(first, repository().getQuote(61))
        }
    }

    @Test fun leapDayAndNewYearGetTheirOwnAssignments() {
        for (start in listOf("2024-02-28T12:00:00Z", "2026-12-31T12:00:00Z")) {
            clock.value = Instant.parse(start)
            val first = repository().getQuote(0)
            clock.value = clock.value.plus(Duration.ofDays(1))
            assertNotEquals(first, repository().getQuote(0))
        }
    }

    @Test fun pastDateAssignmentIsNotOverwrittenByLaterWeather() {
        val first = repository().getQuote(0)
        clock.value = clock.value.plus(Duration.ofDays(5))
        repository().getQuote(61)
        clock.value = clock.value.minus(Duration.ofDays(5))
        assertEquals(first, repository().getQuote(77))
    }

    @Test fun offlineAssignmentIsNeutralAndDoesNotRerollWhenWeatherReturns() {
        val first = repository().getQuote(null)
        assertTrue(QuoteCatalog.entries(null).any { it.formatted == first })
        assertEquals(first, repository().getQuote(95))
    }

    @Test fun migratesLegacyIndexOnceAndPreservesCities() {
        prefs.edit().putInt("quote_idx_rain", 1).putString("cities_json", "cities").apply()
        assertEquals(QuoteCatalog.entries(61)[1].formatted, repository().getQuote(61))
        clock.value = clock.value.plus(Duration.ofDays(1))
        assertNotEquals(QuoteCatalog.entries(61)[1].formatted, repository().getQuote(61))
        assertEquals("cities", prefs.getString("cities_json", null))
    }

    @Test fun corruptedLegacyIndexAndDailyIdAreRecovered() {
        prefs.edit().putInt("quote_idx_snow", Int.MIN_VALUE)
            .putString("daily_quote_v1_2026-10-20", "unknown-id").apply()
        val quote = repository().getQuote(77)
        assertTrue(QuoteCatalog.entries(77).any { it.formatted == quote })
        assertEquals(quote, repository().getQuote(0))
    }

    @Test fun wronglyTypedPreferencesAreRecovered() {
        prefs.edit().putString("quote_idx_clear", "invalid")
            .putBoolean("daily_quote_v1_2026-10-20", true)
            .putString("daily_quote_migrated_v1", "invalid").apply()
        assertTrue(QuoteCatalog.entries(0).any { it.formatted == repository().getQuote(0) })
    }

    @Test fun simultaneousAppAndWidgetRequestsProduceOneAssignment() {
        val executor = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        try {
            val results = (0..23).map { index -> executor.submit<String> {
                start.await()
                repository().getQuote(if (index % 2 == 0) 0 else 61)
            } }
            start.countDown()
            assertEquals(1, results.map { it.get(5, TimeUnit.SECONDS) }.toSet().size)
        } finally { executor.shutdownNow() }
    }

    @Test fun catalogHasUniquePersistentIds() {
        val entries = listOf(null, 0, 3, 45, 61, 71, 95).flatMap(QuoteCatalog::entries)
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        entries.forEach { assertEquals(it, QuoteCatalog.find(it.id)) }
    }

    private class MutableClock(var value: Instant) : Clock() {
        override fun instant(): Instant = value
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(value, zone)
    }
}
