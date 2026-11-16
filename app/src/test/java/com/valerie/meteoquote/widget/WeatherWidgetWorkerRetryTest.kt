package com.valerie.meteoquote.widget

import com.valerie.meteoquote.data.HttpStatusException
import com.valerie.meteoquote.data.InvalidResponseException
import com.valerie.meteoquote.data.RefreshDeferredException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class WeatherWidgetWorkerRetryTest {
    @Test fun transientNetworkFailuresAreRetriedTwiceAtMost() {
        assertTrue(WeatherWidgetWorker.shouldRetry(IOException("offline"), networkRun = true, attempt = 0))
        assertTrue(WeatherWidgetWorker.shouldRetry(HttpStatusException(503), networkRun = true, attempt = 1))
        assertFalse(WeatherWidgetWorker.shouldRetry(IOException("offline"), networkRun = true, attempt = 2))
    }

    @Test fun cooldownDeferralIsNeverRetriedBecauseNoRequestWasSent() {
        val deferred = RefreshDeferredException(Instant.EPOCH, IOException("offline"))
        assertFalse(WeatherWidgetWorker.shouldRetry(deferred, networkRun = true, attempt = 0))
    }

    @Test fun permanentFailuresAndCacheOnlyRunsAreNotRetried() {
        assertFalse(WeatherWidgetWorker.shouldRetry(HttpStatusException(404), networkRun = true, attempt = 0))
        assertFalse(WeatherWidgetWorker.shouldRetry(InvalidResponseException(), networkRun = true, attempt = 0))
        assertFalse(WeatherWidgetWorker.shouldRetry(IOException("offline"), networkRun = false, attempt = 0))
    }
}
