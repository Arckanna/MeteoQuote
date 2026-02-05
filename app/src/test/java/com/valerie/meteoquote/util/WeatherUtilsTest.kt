package com.valerie.meteoquote.util

import com.valerie.meteoquote.R
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherUtilsTest {

    @Test
    fun `wmoToLabel should return correct label for clear sky`() {
        assertEquals("Ciel clair", WeatherUtils.wmoToLabel(0))
    }

    @Test
    fun `wmoToLabel should return correct label for cloudy`() {
        assertEquals("Plutôt nuageux", WeatherUtils.wmoToLabel(1))
        assertEquals("Plutôt nuageux", WeatherUtils.wmoToLabel(2))
        assertEquals("Plutôt nuageux", WeatherUtils.wmoToLabel(3))
    }

    @Test
    fun `wmoToLabel should return correct label for rain`() {
        assertEquals("Pluie", WeatherUtils.wmoToLabel(61))
        assertEquals("Pluie", WeatherUtils.wmoToLabel(65))
    }

    @Test
    fun `wmoToLabel should return correct label for snow`() {
        assertEquals("Neige", WeatherUtils.wmoToLabel(71))
        assertEquals("Neige", WeatherUtils.wmoToLabel(75))
    }

    @Test
    fun `wmoToLabel should return correct label for thunder`() {
        assertEquals("Orage", WeatherUtils.wmoToLabel(95))
        assertEquals("Orage", WeatherUtils.wmoToLabel(99))
    }

    @Test
    fun `wmoToIconRes should return correct icon for clear sky`() {
        assertEquals(R.drawable.ic_weather_sunny_color, WeatherUtils.wmoToIconRes(0))
    }

    @Test
    fun `wmoToIconRes should return correct icon for cloudy`() {
        assertEquals(R.drawable.ic_weather_cloudy_color, WeatherUtils.wmoToIconRes(1))
        assertEquals(R.drawable.ic_weather_cloudy_color, WeatherUtils.wmoToIconRes(3))
    }

    @Test
    fun `wmoToIconRes should return correct icon for rain`() {
        assertEquals(R.drawable.ic_weather_rain_color, WeatherUtils.wmoToIconRes(61))
        assertEquals(R.drawable.ic_weather_rain_color, WeatherUtils.wmoToIconRes(65))
    }

    @Test
    fun `isBgLight should return true for light backgrounds`() {
        assertEquals(true, WeatherUtils.isBgLight(0)) // clear
        assertEquals(true, WeatherUtils.isBgLight(1)) // cloudy
        assertEquals(true, WeatherUtils.isBgLight(45)) // fog
        assertEquals(true, WeatherUtils.isBgLight(71)) // snow
    }

    @Test
    fun `isBgLight should return false for dark backgrounds`() {
        assertEquals(false, WeatherUtils.isBgLight(61)) // rain
        assertEquals(false, WeatherUtils.isBgLight(95)) // thunder
    }
}
