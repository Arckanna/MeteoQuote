package com.valerie.meteoquote.ui

import com.valerie.meteoquote.data.model.City
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class WidgetRenderKeyTest {
    private val now = Instant.parse("2026-11-01T10:00:00Z")
    private val state = WeatherUiState(cities = listOf(City("Paris", 48.0, 2.0)),
        currentTemp = 15.0, weatherCode = 0, observedAt = now, updatedAt = now, quote = "Citation")

    @Test fun loadingAndAqiChangesDoNotRedrawTheWidget() {
        val initial = WidgetRenderKey.from(state, now)
        assertEquals(initial, WidgetRenderKey.from(state.copy(isRefreshing = true,
            isAirQualityLoading = true, aqiNow = 42, airFetchedAt = now.plusSeconds(30)), now.plusSeconds(30)))
    }

    @Test fun crossingFreshnessBoundaryRequiresNewRenderWithoutNewDownload() {
        val initial = WidgetRenderKey.from(state, now)
        assertEquals(initial, WidgetRenderKey.from(state, now.plusSeconds(3599)))
        assertNotEquals(initial, WidgetRenderKey.from(state, now.plusSeconds(3600)))
    }

    @Test fun cityQuoteWeatherAndDownloadTimeChangesRequireRender() {
        val initial = WidgetRenderKey.from(state, now)
        for (changed in listOf(state.copy(quote = "Autre citation"),
            state.copy(currentTemp = 16.0), state.copy(weatherCode = 3),
            state.copy(updatedAt = now.plusSeconds(1)),
            state.copy(cities = listOf(City("Lyon", 45.0, 4.0))))) {
            assertNotEquals(initial, WidgetRenderKey.from(changed, now))
        }
    }
}
