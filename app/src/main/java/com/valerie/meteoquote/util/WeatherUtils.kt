package com.valerie.meteoquote.util

import android.content.Context
import androidx.core.content.ContextCompat
import com.valerie.meteoquote.R

object WeatherUtils {
    
    fun wmoToLabel(code: Int): String = when (code) {
        0 -> "Ciel clair"
        1, 2, 3 -> "Plutôt nuageux"
        45, 48 -> "Brouillard"
        51, 53, 55 -> "Bruine"
        56, 57 -> "Bruine verglaçante"
        61, 63, 65, 80, 81, 82 -> "Pluie"
        66, 67 -> "Pluie verglaçante"
        71, 73, 75, 85, 86 -> "Neige"
        95, 96, 99 -> "Orage"
        else -> "Conditions variées"
    }
    
    fun wmoToIconRes(code: Int): Int = when (code) {
        0 -> R.drawable.ic_weather_sunny_color
        1, 2, 3 -> R.drawable.ic_weather_cloudy_color
        45, 48 -> R.drawable.ic_weather_fog_color
        51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82, 66, 67 -> R.drawable.ic_weather_rain_color
        71, 73, 75, 85, 86 -> R.drawable.ic_weather_snow_color
        95, 96, 99 -> R.drawable.ic_weather_thunder_color
        else -> R.drawable.ic_weather_cloudy_color
    }
    
    fun themeDrawableRes(code: Int): Int = when (code) {
        0 -> R.drawable.bg_weather_clear
        1, 2, 3 -> R.drawable.bg_weather_clouds
        45, 48 -> R.drawable.bg_weather_fog
        51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82, 66, 67 -> R.drawable.bg_weather_rain
        71, 73, 75, 85, 86 -> R.drawable.bg_weather_snow
        95, 96, 99 -> R.drawable.bg_weather_thunder
        else -> R.drawable.bg_weather_clouds
    }
    
    fun statusColorRes(code: Int): Int = when (code) {
        0 -> R.color.status_clear
        1, 2, 3 -> R.color.status_clouds
        45, 48 -> R.color.status_fog
        51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82, 66, 67 -> R.color.status_rain
        71, 73, 75, 85, 86 -> R.color.status_snow
        95, 96, 99 -> R.color.status_thunder
        else -> R.color.status_clouds
    }
    
    fun isBgLight(code: Int): Boolean = when (code) {
        0, 1, 2, 3, 45, 48, 71, 73, 75, 85, 86 -> true
        else -> false
    }
    
    fun uvCategory(context: Context, v: Double): Pair<String, Int> {
        val resId = when {
            v < 3 -> R.color.uv_low
            v < 6 -> R.color.uv_mod
            v < 8 -> R.color.uv_high
            v < 11 -> R.color.uv_veryhigh
            else -> R.color.uv_extreme
        }
        val label = when {
            v < 3 -> "Faible"
            v < 6 -> "Modéré"
            v < 8 -> "Élevé"
            v < 11 -> "Très élevé"
            else -> "Extrême"
        }
        return label to ContextCompat.getColor(context, resId)
    }
    
    fun aqiCategoryEU(context: Context, v: Int): Pair<String, Int> {
        val res = when {
            v <= 20 -> R.color.aqi_good
            v <= 40 -> R.color.aqi_fair
            v <= 60 -> R.color.aqi_mod
            v <= 80 -> R.color.aqi_poor
            v <= 100 -> R.color.aqi_vpoor
            else -> R.color.aqi_epoor
        }
        val label = when {
            v <= 20 -> "Bon"
            v <= 40 -> "Moyen"
            v <= 60 -> "Médiocre"
            v <= 80 -> "Mauvais"
            v <= 100 -> "Très mauvais"
            else -> "Extrêmement mauvais"
        }
        return label to ContextCompat.getColor(context, res)
    }
}
