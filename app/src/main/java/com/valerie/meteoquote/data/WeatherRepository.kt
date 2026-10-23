package com.valerie.meteoquote.data

import com.valerie.meteoquote.data.model.WeatherResult

interface WeatherRepository {
    /** Core weather only, without waiting for the optional air-quality service. */
    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResult
    suspend fun cachedWeather(lat: Double, lon: Double): WeatherResult? = null
    fun refreshUpdates(lat: Double, lon: Double, force: Boolean): kotlinx.coroutines.flow.Flow<WeatherResult> =
        weatherUpdates(lat, lon)

    /** First usable weather, then optional air-quality enrichment. */
    fun weatherUpdates(lat: Double, lon: Double): kotlinx.coroutines.flow.Flow<WeatherResult> =
        kotlinx.coroutines.flow.flow { emit(fetchWeather(lat, lon)) }
}
