package com.valerie.meteoquote.data

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class WeatherFreshnessTest {
    private val now = Instant.parse("2026-10-31T09:30:00Z")

    @Test fun downloadingOldConditionsDoesNotMakeThemCurrent() {
        val observed = now.minusSeconds(2 * 3600)
        assertFalse(WeatherFreshness.isCurrent(observed, now, now))
        assertEquals("Dernières conditions connues", WeatherFreshness.title(observed, now, now))
        val label = WeatherFreshness.label(observed, now, "Europe/Paris", now)
        assertTrue(label.contains("Conditions du 31/10/2026 à 08:30"))
        assertTrue(label.contains("Téléchargées le 31/10/2026 à 10:30"))
    }

    @Test fun cachedConditionsAgeEvenWithoutAnotherDownload() {
        val observed = now.minusSeconds(15 * 60)
        assertTrue(WeatherFreshness.isCurrent(observed, now, now))
        assertTrue(WeatherFreshness.isCurrent(observed, now, observed.plusSeconds(3599)))
        assertFalse(WeatherFreshness.isCurrent(observed, now, observed.plusSeconds(3600)))
    }

    @Test fun missingOrFutureTimestampIsNeverPresentedAsCurrent() {
        assertFalse(WeatherFreshness.isCurrent(null, now, now))
        assertFalse(WeatherFreshness.isCurrent(now.plusSeconds(1), now, now))
        assertFalse(WeatherFreshness.isCurrent(now, now.plusSeconds(1), now))
        assertTrue(WeatherFreshness.label(null, now, "UTC", now).contains("Heure des conditions indisponible"))
        assertTrue(WeatherFreshness.label(now.plusSeconds(1), now, "UTC", now).contains("Date météo à vérifier"))
    }

    @Test fun legacyCacheDoesNotUseDownloadTimeAsProviderTime() {
        assertEquals("Dernières conditions connues", WeatherFreshness.title(null, now, now))
        assertTrue(WeatherFreshness.label(null, now, "UTC", now).contains("Téléchargées le"))
    }

    @Test fun datesUseTheCityZoneAcrossMidnight() {
        val instant = Instant.parse("2026-10-31T23:30:00Z")
        val label = WeatherFreshness.label(instant, instant, "Europe/Paris", instant)
        assertTrue(label.contains("01/11/2026 à 00:30"))
        assertEquals("Météo actuelle", WeatherFreshness.title(instant, instant, instant))
    }

    @Test fun daylightSavingOverlapShowsDistinctOffsets() {
        val first = Instant.parse("2026-10-25T00:30:00Z")
        val second = Instant.parse("2026-10-25T01:30:00Z")
        val label = WeatherFreshness.label(first, second, "Europe/Paris", second)
        assertTrue(label.contains("(+02:00)"))
        assertTrue(label.contains("(+01:00)"))
        assertFalse(WeatherFreshness.isCurrent(first, second, second))
    }

    @Test fun offlineConditionsFromPreviousDaysStayExplicitlyHistorical() {
        val previous = now.minusSeconds(3 * 24 * 3600)
        assertEquals("Dernières conditions connues", WeatherFreshness.title(previous, previous, now))
        assertTrue(WeatherFreshness.label(previous, previous, "Europe/Paris", now)
            .contains("28/10/2026"))
    }

    @Test fun invalidZoneFallsBackToUtcWithoutCrashing() {
        assertTrue(WeatherFreshness.label(now, null, "invalid", now).contains("09:30 UTC"))
        assertTrue(WeatherFreshness.label(now, null, "invalid", now).contains("Heure du téléchargement indisponible"))
    }
}
