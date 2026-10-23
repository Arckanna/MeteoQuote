package com.valerie.meteoquote.data

import java.time.Duration
import java.time.Instant

object CachePolicy {
    const val FRESH_MILLIS = 30 * 60 * 1000L
    const val OLD_MILLIS = 24 * 60 * 60 * 1000L
    const val EXPIRY_MILLIS = 7 * OLD_MILLIS
    fun key(lat: Double, lon: Double): String {
        require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0)
        return "${if (lat == 0.0) 0.0 else lat}:${if (lon == 0.0) 0.0 else lon}"
    }
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
