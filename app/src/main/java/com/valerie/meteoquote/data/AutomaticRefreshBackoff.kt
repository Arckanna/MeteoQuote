package com.valerie.meteoquote.data

import java.time.Instant

/**
 * An automatic refresh skipped because the previous attempt failed recently.
 * No request was sent: callers must not retry before [retryAt]. [failure] is the last real error.
 */
class RefreshDeferredException(val retryAt: Instant, val failure: Exception) :
    Exception("Actualisation automatique reportée", failure)

/** Process-local cooldown per place and service. Forced requests bypass the gate. */
internal class AutomaticRefreshBackoff {
    private data class Failure(val count: Int, val retryAt: Instant, val error: Exception)
    private val failures = mutableMapOf<String, Failure>()

    /** A new exception each time, so concurrent callers never share (and mutate) the same instance. */
    @Synchronized fun deferredFailure(key: String, now: Instant): RefreshDeferredException? =
        failures[key]?.takeIf { now.isBefore(it.retryAt) }?.let { RefreshDeferredException(it.retryAt, it.error) }

    @Synchronized fun failed(key: String, error: Exception, now: Instant) {
        val count = ((failures[key]?.count ?: 0) + 1).coerceAtMost(5)
        val seconds = minOf(60L shl (count - 1), 15 * 60L)
        failures[key] = Failure(count, now.plusSeconds(seconds), error)
    }

    @Synchronized fun succeeded(key: String) { failures.remove(key) }
}
