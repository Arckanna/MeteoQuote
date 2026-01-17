package com.valerie.meteoquote.data.model

import java.time.LocalDate
import java.time.LocalDateTime

data class HourlyForecast(
    val time: LocalDateTime,
    val temp: Double,
    val code: Int
)

data class DailyForecast(
    val date: LocalDate,
    val tmin: Double,
    val tmax: Double,
    val code: Int
)

data class WeatherResult(
    val current: Pair<Double, Int>, // temp, code
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val uvNow: Double,
    val uvMaxToday: Double,
    val uvPeakTime: LocalDateTime?,
    val aqiNow: Int,
    val aqiMaxToday: Int,
    val aqiPeakTime: LocalDateTime?
)
