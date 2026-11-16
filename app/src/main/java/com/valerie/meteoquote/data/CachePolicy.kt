package com.valerie.meteoquote.data

import java.time.Duration
import java.time.Instant

object CachePolicy {
    const val FRESH_MILLIS = 30 * 60 * 1000L
    const val OLD_MILLIS = 24 * 60 * 60 * 1000L
    const val EXPIRY_MILLIS = 7 * OLD_MILLIS
    /**
     * Place identity for the cache, rounded to 3 decimals (about 100 m, finer than the forecast grid).
     * Repeated GPS fixes a few metres apart therefore share one cache entry and one cooldown.
     * Requests still use the exact coordinates; only the cache identity is rounded.
     */
    fun key(lat: Double, lon: Double): String {
        require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0)
        return "${rounded(lat)}:${rounded(lon)}"
    }

    private fun rounded(value: Double): Double {
        val result = Math.round(value * KEY_SCALE) / KEY_SCALE
        return if (result == 0.0) 0.0 else result // −0.0 and 0.0 must give the same key
    }

    private const val KEY_SCALE = 1_000.0
    fun age(fetchedAt: Long, now: Instant) = now.toEpochMilli() - fetchedAt
    fun fresh(fetchedAt: Long, now: Instant) = age(fetchedAt, now) in 0 until FRESH_MILLIS
    fun usable(fetchedAt: Long, now: Instant) = age(fetchedAt, now) < EXPIRY_MILLIS
    fun old(fetchedAt: Long, now: Instant) = age(fetchedAt, now).let { it < 0 || it >= OLD_MILLIS }
    fun label(fetchedAt: Instant, now: Instant): String {
        val age = Duration.between(fetchedAt, now)
        return when {
            age.isNegative -> "Date de récupération à vérifier"
            age.toDays() >= 1 -> "Dernières données connues · il y a ${age.toDays()} j"
            age.toHours() >= 1 -> "Données enregistrées il y a ${age.toHours()} h"
            else -> "Actualisé il y a ${age.toMinutes()} min"
        }
    }
}

class WeatherStorageException(cause: Throwable) : Exception("Stockage météo indisponible", cause)
class NoCachedWeatherException(val failure: Exception) : Exception("Aucune météo enregistrée pour cette ville", failure)
