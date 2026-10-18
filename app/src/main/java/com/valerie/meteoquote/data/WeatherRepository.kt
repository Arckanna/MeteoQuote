package com.valerie.meteoquote.data

import com.valerie.meteoquote.data.model.WeatherResult

interface WeatherRepository {
    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResult
}
