package com.valerie.meteoquote.ui

import com.valerie.meteoquote.data.HttpStatusException
import org.junit.Assert.*
import org.junit.Test

class WeatherFailureTest {
    @Test fun distinguishesHttpConnectivityTimeoutAndInvalidData() {
        assertEquals(WeatherErrorKind.HTTP, WeatherFailure.from(HttpStatusException(500)).kind)
        assertTrue(WeatherFailure.from(HttpStatusException(404)).message.contains("404"))
        assertEquals(WeatherErrorKind.RATE_LIMITED, WeatherFailure.from(HttpStatusException(429)).kind)
        assertEquals(WeatherErrorKind.CONNECTION, WeatherFailure.from(java.net.UnknownHostException()).kind)
        assertEquals(WeatherErrorKind.TIMEOUT, WeatherFailure.from(java.net.SocketTimeoutException()).kind)
        assertEquals(WeatherErrorKind.INVALID_DATA, WeatherFailure.from(org.json.JSONException("raw body")).kind)
    }

    @Test fun neverExposesTechnicalDetailsOrClaimsOfflineForEveryIOException() {
        val failure = WeatherFailure.from(java.io.IOException("https://private.example?latitude=42"))
        assertFalse(failure.message.contains("private"))
        assertFalse(failure.message.contains("42"))
        assertFalse(failure.message.contains("Pas de connexion"))
        assertEquals(WeatherErrorKind.UNKNOWN, WeatherFailure.from(Exception("raw internals")).kind)
    }
}
