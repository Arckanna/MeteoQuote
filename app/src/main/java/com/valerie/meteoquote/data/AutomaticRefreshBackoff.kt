package com.valerie.meteoquote.data

import java.time.Instant

/** Process-local cooldown per place and service. Forced requests bypass the gate. */
internal class AutomaticRefreshBackoff {
    private data class Failure(val count: Int, val retryAt: Instant, val error: Exception)
    private val failures = mutableMapOf<String, Failure>()

    @Synchronized fun deferredFailure(key: String, now: Instant): Exception? =
        failures[key]?.takeIf { now.isBefore(it.retryAt) }?.error

    @Synchronized fun failed(key: String, error: Exception, now: Instant) {
        val count = ((failures[key]?.count ?: 0) + 1).coerceAtMost(5)
        val seconds = minOf(60L shl (count - 1), 15 * 60L)
        failures[key] = Failure(count, now.plusSeconds(seconds), error)
    }

    @Synchronized fun succeeded(key: String) { failures.remove(key) }
}
