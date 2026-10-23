package com.valerie.meteoquote.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CachePolicyTest {
    @Test fun boundariesAndClockRollback() {
        val stored = 1_000_000L
        fun at(age: Long) = Instant.ofEpochMilli(stored + age)
        assertTrue(CachePolicy.fresh(stored, at(0)))
        assertFalse(CachePolicy.fresh(stored, at(-1)))
        assertTrue(CachePolicy.old(stored, at(-1)))
        assertTrue(CachePolicy.fresh(stored, at(CachePolicy.FRESH_MILLIS - 1)))
        assertFalse(CachePolicy.fresh(stored, at(CachePolicy.FRESH_MILLIS)))
        assertFalse(CachePolicy.old(stored, at(CachePolicy.OLD_MILLIS - 1)))
        assertTrue(CachePolicy.old(stored, at(CachePolicy.OLD_MILLIS)))
        assertTrue(CachePolicy.usable(stored, at(CachePolicy.EXPIRY_MILLIS - 1)))
        assertFalse(CachePolicy.usable(stored, at(CachePolicy.EXPIRY_MILLIS)))
    }
    @Test fun identityUsesCoordinatesWithoutRoundingAndNormalizesNegativeZero() {
        assertEquals(CachePolicy.key(-0.0, 0.0), CachePolicy.key(0.0, -0.0))
        assertNotEquals(CachePolicy.key(48.0, 2.0), CachePolicy.key(48.000001, 2.0))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidCoordinatesRejected() {
        CachePolicy.key(Double.NaN, 2.0)
    }
}
