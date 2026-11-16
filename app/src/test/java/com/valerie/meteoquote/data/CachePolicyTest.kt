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
    @Test fun identityIsRoundedToAboutOneHundredMetresAndNormalizesNegativeZero() {
        assertEquals(CachePolicy.key(-0.0, 0.0), CachePolicy.key(0.0, -0.0))
        assertEquals("0.0:0.0", CachePolicy.key(-0.0001, -0.0001))
        // Two GPS fixes a few metres apart share the same cache entry.
        assertEquals(CachePolicy.key(43.60471, 1.44421), CachePolicy.key(43.60468, 1.44418))
        assertEquals("43.605:1.444", CachePolicy.key(43.6047, 1.4442))
        // Places a few hundred metres apart stay distinct.
        assertNotEquals(CachePolicy.key(48.0, 2.0), CachePolicy.key(48.003, 2.0))
        assertEquals("-90.0:180.0", CachePolicy.key(-90.0, 180.0))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidCoordinatesRejected() {
        CachePolicy.key(Double.NaN, 2.0)
    }
}
