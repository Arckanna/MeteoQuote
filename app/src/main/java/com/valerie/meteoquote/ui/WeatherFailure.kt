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
