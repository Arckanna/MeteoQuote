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

    @Test fun deferredRefreshIsDescribedByItsOriginalFailure() {
        val deferred = com.valerie.meteoquote.data.RefreshDeferredException(
            java.time.Instant.EPOCH, HttpStatusException(503))
        assertEquals(WeatherFailure.from(HttpStatusException(503)), WeatherFailure.from(deferred))
    }

    @Test fun cityLookupMessagesHideTechnicalDetails() {
        val message = cityLookupErrorMessage(java.net.SocketTimeoutException("Request time budget exceeded"))
        assertFalse(message.contains("budget"))
        assertTrue(message.contains("trop de temps"))
        assertFalse(cityLookupErrorMessage(HttpStatusException(503)).contains("HTTP"))
        assertTrue(cityLookupErrorMessage(java.net.UnknownHostException()).contains("connexion"))
    }

    @Test fun neverExposesTechnicalDetailsOrClaimsOfflineForEveryIOException() {
        val failure = WeatherFailure.from(java.io.IOException("https://private.example?latitude=42"))
        assertFalse(failure.message.contains("private"))
        assertFalse(failure.message.contains("42"))
        assertFalse(failure.message.contains("Pas de connexion"))
        assertEquals(WeatherErrorKind.UNKNOWN, WeatherFailure.from(Exception("raw internals")).kind)
    }
}
