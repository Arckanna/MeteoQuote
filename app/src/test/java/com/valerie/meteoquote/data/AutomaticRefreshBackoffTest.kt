package com.valerie.meteoquote.data

import java.io.IOException
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class AutomaticRefreshBackoffTest {
    @Test fun exponentialDelaysAreCappedAndBoundaryAllowsRetry() {
        val policy = AutomaticRefreshBackoff()
        var now = Instant.parse("2026-11-01T10:00:00Z")
        for (delay in listOf(60L, 120L, 240L, 480L, 900L, 900L)) {
            val error = IOException("offline")
            policy.failed("weather:city", error, now)
            assertSame(error, policy.deferredFailure("weather:city", now.plusSeconds(delay - 1)))
            assertNull(policy.deferredFailure("weather:city", now.plusSeconds(delay)))
            now = now.plusSeconds(delay)
        }
    }

    @Test fun readingDuringCooldownDoesNotExtendIt() {
        val policy = AutomaticRefreshBackoff()
        val now = Instant.parse("2026-11-01T10:00:00Z")
        policy.failed("weather:city", IOException("offline"), now)
        repeat(60) { assertNotNull(policy.deferredFailure("weather:city", now.plusSeconds(it.toLong()))) }
        assertNull(policy.deferredFailure("weather:city", now.plusSeconds(60)))
    }

    @Test fun successResetsDelayAndServicesAndCitiesAreIndependent() {
        val policy = AutomaticRefreshBackoff()
        val now = Instant.parse("2026-11-01T10:00:00Z")
        repeat(4) { policy.failed("weather:paris", IOException("offline"), now) }
        assertNull(policy.deferredFailure("air:paris", now))
        assertNull(policy.deferredFailure("weather:lyon", now))
        policy.succeeded("weather:paris")
        assertNull(policy.deferredFailure("weather:paris", now))
        policy.failed("weather:paris", IOException("offline again"), now)
        assertNull(policy.deferredFailure("weather:paris", now.plusSeconds(60)))
    }
}
