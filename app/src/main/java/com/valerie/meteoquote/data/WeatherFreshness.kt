package com.valerie.meteoquote.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Provider conditions and download freshness are independent. Never infer one from the other. */
object WeatherFreshness {
    const val CURRENT_MAX_AGE_SECONDS = 60 * 60L

    fun isCurrent(observedAt: Instant?, fetchedAt: Instant?, now: Instant): Boolean {
        if (observedAt == null) return false
        val age = Duration.between(observedAt, now)
        if (age.isNegative || age >= Duration.ofSeconds(CURRENT_MAX_AGE_SECONDS)) return false
        // Missing download metadata does not invent an observation time; future metadata is suspect.
        return fetchedAt == null || !fetchedAt.isAfter(now)
    }

    fun title(observedAt: Instant?, fetchedAt: Instant?, now: Instant): String =
        if (isCurrent(observedAt, fetchedAt, now)) "Météo actuelle" else "Dernières conditions connues"

    fun label(observedAt: Instant?, fetchedAt: Instant?, zoneId: String, now: Instant): String {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("UTC"))
        val format = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm z (XXX)", Locale.FRANCE)
        fun stamp(value: Instant) = format.format(value.atZone(zone))
        val conditions = when {
            observedAt == null -> "Heure des conditions indisponible"
            observedAt.isAfter(now) -> "Date météo à vérifier : ${stamp(observedAt)}"
            else -> "Conditions du ${stamp(observedAt)}"
        }
        val download = when {
            fetchedAt == null -> "Heure du téléchargement indisponible"
            fetchedAt.isAfter(now) -> "Date du téléchargement à vérifier : ${stamp(fetchedAt)}"
            else -> "Téléchargées le ${stamp(fetchedAt)}"
        }
        return "$conditions · $download"
    }
}
