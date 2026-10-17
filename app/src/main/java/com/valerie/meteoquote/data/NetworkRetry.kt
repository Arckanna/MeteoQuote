package com.valerie.meteoquote.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.net.ProtocolException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

internal const val REQUEST_BUDGET_MILLIS = 30_000L

/** HTTP is kept distinct from connection failures, while remaining compatible with optional AQI. */
class HttpStatusException(val statusCode: Int, val retryAfterMillis: Long? = null) :
    IOException("HTTP $statusCode")

class InvalidResponseException : IOException("Invalid response body")

internal suspend fun <T> retryNetwork(block: suspend () -> T): T {
    repeat(3) { attempt ->
        currentCoroutineContext().ensureActive()
        try {
            return block()
        } catch (e: IOException) {
            val retryable = when (e) {
                is HttpStatusException -> e.statusCode in setOf(408, 429, 500, 502, 503, 504)
                is InvalidResponseException, is SSLHandshakeException,
                is SSLPeerUnverifiedException, is ProtocolException -> false
                else -> true
            }
            val serverDelay = (e as? HttpStatusException)?.retryAfterMillis ?: 0L
            // Do not retry earlier than the server allows, or hold a request beyond its budget.
            if (!retryable || attempt == 2 || serverDelay >= REQUEST_BUDGET_MILLIS) throw e
            delay(maxOf(500L shl attempt, serverDelay))
        }
    }
    error("Unreachable")
}
