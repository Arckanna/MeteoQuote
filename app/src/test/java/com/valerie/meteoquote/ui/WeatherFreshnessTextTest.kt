package com.valerie.meteoquote.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WeatherFreshnessTextTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-11-11T12:00:00Z")

    @Test fun recentDownloadDoesNotHideOldObservation() {
        assertEquals("Données anciennes\nConditions relevées il y a 2 h",
            WeatherFreshnessText.summary(context, now.minusSeconds(7200), now.minusSeconds(600), now))
    }

    @Test fun currentConditionsShowShortDownloadAge() {
        assertEquals("Actualisé il y a 10 min",
            WeatherFreshnessText.summary(context, now.minusSeconds(900), now.minusSeconds(600), now))
        assertEquals("Actualisé à l’instant", WeatherFreshnessText.summary(context, now, now, now))
    }

    @Test fun warningAppearsAtOneHourWithoutNewDownload() {
        val observation = now.minusSeconds(3599)
        assertFalse(WeatherFreshnessText.summary(context, observation, now, now).contains("Données anciennes"))
        assertEquals("Données anciennes\nConditions relevées il y a 1 h",
            WeatherFreshnessText.summary(context, observation, now, now.plusSeconds(1)))
    }

    @Test fun missingAndFutureDatesAreNotCalledCurrent() {
        assertEquals("Fraîcheur inconnue", WeatherFreshnessText.summary(context, null, now, now))
        assertEquals("Fraîcheur inconnue", WeatherFreshnessText.summary(context, now.plusSeconds(1), now, now))
        assertEquals("Fraîcheur inconnue", WeatherFreshnessText.summary(context, now, now.plusSeconds(1), now))
        assertEquals("Heure du téléchargement indisponible", WeatherFreshnessText.summary(context, now, null, now))
    }
}
