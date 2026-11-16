package com.valerie.meteoquote.ui

import com.valerie.meteoquote.data.HttpStatusException
import com.valerie.meteoquote.data.InvalidResponseException
import java.io.IOException
import java.io.InterruptedIOException
import javax.net.ssl.SSLException

enum class WeatherErrorKind { CONNECTION, TIMEOUT, RATE_LIMITED, HTTP, INVALID_DATA, UNKNOWN }

data class WeatherFailure(val kind: WeatherErrorKind, val message: String) {
    companion object {
        fun from(error: Exception): WeatherFailure = when (error) {
            is com.valerie.meteoquote.data.NoCachedWeatherException -> from(error.failure).let {
                it.copy(message = "Aucune météo enregistrée pour cette ville. " + it.message)
            }
            // No request was sent; describe the failure that started the cooldown.
            is com.valerie.meteoquote.data.RefreshDeferredException -> from(error.failure)
            is com.valerie.meteoquote.data.WeatherStorageException -> WeatherFailure(WeatherErrorKind.UNKNOWN,
                "Stockage météo indisponible. Réessayez.")
            is HttpStatusException -> when (error.statusCode) {
                429 -> WeatherFailure(WeatherErrorKind.RATE_LIMITED, "Trop de demandes. Réessayez un peu plus tard.")
                404 -> WeatherFailure(WeatherErrorKind.HTTP, "Données météo introuvables (HTTP 404).")
                408 -> WeatherFailure(WeatherErrorKind.TIMEOUT, "Le service met trop de temps à répondre. Réessayez.")
                in 500..599 -> WeatherFailure(WeatherErrorKind.HTTP, "Le service météo est temporairement indisponible (HTTP ${error.statusCode}).")
                else -> WeatherFailure(WeatherErrorKind.HTTP, "Le service météo a refusé la demande (HTTP ${error.statusCode}).")
            }
            is InvalidResponseException, is org.json.JSONException, is IllegalArgumentException,
            is java.time.DateTimeException -> WeatherFailure(WeatherErrorKind.INVALID_DATA, "Les données météo reçues sont invalides. Réessayez plus tard.")
            is InterruptedIOException -> WeatherFailure(WeatherErrorKind.TIMEOUT, "La connexion a pris trop de temps. Réessayez.")
            is SSLException -> WeatherFailure(WeatherErrorKind.CONNECTION, "Impossible d’établir une connexion sécurisée au service météo.")
            is IOException -> WeatherFailure(WeatherErrorKind.CONNECTION, "Impossible de joindre le service météo. Vérifiez votre connexion Internet.")
            else -> WeatherFailure(WeatherErrorKind.UNKNOWN, "Météo indisponible. Réessayez dans un instant.")
        }
    }
}

/** User-facing text for a failed city search; never shows raw technical messages such as "HTTP 503". */
fun cityLookupErrorMessage(error: Exception): String = when (WeatherFailure.from(error).kind) {
    WeatherErrorKind.CONNECTION -> "Impossible de joindre le service de recherche. Vérifiez votre connexion Internet."
    WeatherErrorKind.TIMEOUT -> "La recherche a pris trop de temps. Réessayez."
    WeatherErrorKind.RATE_LIMITED -> "Trop de demandes. Réessayez un peu plus tard."
    else -> "Recherche de ville indisponible. Réessayez dans un instant."
}
