package com.valerie.meteoquote.ui

import com.valerie.meteoquote.data.WeatherFreshness
import com.valerie.meteoquote.data.model.City
import java.time.Instant

/** Only fields shown by the widget. Loading and AQI changes do not require a new render. */
internal data class WidgetRenderKey(
    val city: City?,
    val temperature: Double?,
    val code: Int?,
    val fetchedAt: Instant?,
    val observedAt: Instant?,
    val zoneId: String,
    val current: Boolean,
    val quote: String
) {
    companion object {
        fun from(state: WeatherUiState, now: Instant) = WidgetRenderKey(
            state.cities.getOrNull(state.selectedCityIndex), state.currentTemp, state.weatherCode,
            state.updatedAt, state.observedAt, state.zoneId,
            WeatherFreshness.isCurrent(state.observedAt, state.updatedAt, now), state.quote)
    }
}
