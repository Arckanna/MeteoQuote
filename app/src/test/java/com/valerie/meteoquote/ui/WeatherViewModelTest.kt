package com.valerie.meteoquote.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.valerie.meteoquote.data.CityRepository
import com.valerie.meteoquote.data.QuoteRepository
import com.valerie.meteoquote.data.WeatherRepository
import com.valerie.meteoquote.data.model.City
import com.valerie.meteoquote.data.model.DailyForecast
import com.valerie.meteoquote.data.model.HourlyForecast
import com.valerie.meteoquote.data.model.WeatherResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    private lateinit var application: Application
    private lateinit var mockWeatherRepository: WeatherRepository
    private lateinit var mockQuoteRepository: QuoteRepository
    private lateinit var mockCityRepository: CityRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        // Utiliser un Application réel pour les tests
        application = ApplicationProvider.getApplicationContext<Application>()
        mockWeatherRepository = mockk(relaxed = true)
        mockQuoteRepository = mockk(relaxed = true)
        mockCityRepository = mockk(relaxed = true)

        // Setup default cities - retourner une liste vide pour éviter le chargement automatique
        every { mockCityRepository.defaultCities } returns listOf(
            City("Toulouse", 43.6047, 1.4442)
        )
        every { mockCityRepository.loadCities() } returns emptyList()
        every { mockCityRepository.saveCities(any()) } returns Unit
    }
    
    private fun createViewModel(): WeatherViewModel {
        return WeatherViewModel(
            application,
            mockWeatherRepository,
            mockQuoteRepository,
            mockCityRepository
        )
    }

    @Test
    fun `initial state should load cities from repository`() = runTest(testDispatcher) {
        val cities = listOf(
            City("Paris", 48.8566, 2.3522),
            City("Lyon", 45.7640, 4.8357)
        )
        every { mockCityRepository.loadCities() } returns cities
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        every { mockQuoteRepository.getQuote(any(), any(), any()) } returns "\"Test\" — Author"

        val newViewModel = createViewModel()

        advanceUntilIdle()

        assertEquals(cities.size, newViewModel.uiState.value.cities.size)
    }

    @Test
    fun `refreshWeather should update state with weather data`() = runTest(testDispatcher) {
        val city = City("Paris", 48.8566, 2.3522)
        val weatherResult = createMockWeatherResult()
        val quote = "\"Test quote\" — Author"

        coEvery { mockWeatherRepository.fetchWeather(city.lat, city.lon) } returns weatherResult
        every { mockQuoteRepository.getQuote(any(), any(), any()) } returns quote

        val testViewModel = createViewModel()
        advanceUntilIdle() // Attendre que l'init se termine
        
        testViewModel.refreshWeather(city)
        advanceUntilIdle()

        val state = testViewModel.uiState.value
        assertEquals(20.5, state.currentTemp!!, 0.1)
        assertEquals(0, state.weatherCode)
        assertEquals("Ciel clair", state.condition)
        assertEquals(quote, state.quote)
        assertFalse(state.isLoading)
        assertTrue(state.hourlyForecasts.isNotEmpty())
        assertTrue(state.dailyForecasts.isNotEmpty())
    }

    @Test
    fun `refreshWeather should set loading state during fetch`() = runTest(testDispatcher) {
        val city = City("Paris", 48.8566, 2.3522)
        
        coEvery { mockWeatherRepository.fetchWeather(city.lat, city.lon) } coAnswers {
            kotlinx.coroutines.delay(100)
            createMockWeatherResult()
        }
        every { mockQuoteRepository.getQuote(any(), any(), any()) } returns "\"Test\" — Author"

        val testViewModel = createViewModel()
        advanceUntilIdle()
        
        testViewModel.refreshWeather(city)
        
        // Pendant le chargement
        assertTrue(testViewModel.uiState.value.isLoading)
        
        advanceUntilIdle()
        
        // Après le chargement
        assertFalse(testViewModel.uiState.value.isLoading)
    }

    @Test
    fun `refreshWeather should handle errors correctly`() = runTest(testDispatcher) {
        val city = City("Paris", 48.8566, 2.3522)
        val errorMessage = "Network error"

        coEvery { mockWeatherRepository.fetchWeather(city.lat, city.lon) } throws Exception(errorMessage)

        val testViewModel = createViewModel()
        advanceUntilIdle()
        
        testViewModel.refreshWeather(city)
        advanceUntilIdle()

        val state = testViewModel.uiState.value
        assertEquals(errorMessage, state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `selectCity should update selected city index`() = runTest(testDispatcher) {
        val cities = listOf(
            City("Paris", 48.8566, 2.3522),
            City("Lyon", 45.7640, 4.8357)
        )
        every { mockCityRepository.loadCities() } returns cities
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        every { mockQuoteRepository.getQuote(any(), any(), any()) } returns "\"Test\" — Author"

        val newViewModel = createViewModel()
        advanceUntilIdle()

        newViewModel.selectCity(1)
        advanceUntilIdle()

        assertEquals(1, newViewModel.uiState.value.selectedCityIndex)
    }

    @Test
    fun `addCity should add city to list and save`() = runTest(testDispatcher) {
        val newCity = City("Marseille", 43.2965, 5.3698)
        val initialCities = listOf(City("Paris", 48.8566, 2.3522))
        
        every { mockCityRepository.loadCities() } returns initialCities
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        every { mockQuoteRepository.getQuote(any(), any(), any()) } returns "\"Test\" — Author"

        val newViewModel = createViewModel()
        advanceUntilIdle()

        newViewModel.addCity(newCity)
        advanceUntilIdle()

        val state = newViewModel.uiState.value
        assertTrue(state.cities.contains(newCity))
        coVerify { mockCityRepository.saveCities(any()) }
    }

    @Test
    fun `addCity should not add duplicate cities`() = runTest(testDispatcher) {
        val city = City("Paris", 48.8566, 2.3522)
        val cities = listOf(city)
        
        every { mockCityRepository.loadCities() } returns cities
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        every { mockQuoteRepository.getQuote(any(), any(), any()) } returns "\"Test\" — Author"

        val newViewModel = createViewModel()
        advanceUntilIdle()

        newViewModel.addCity(city)
        advanceUntilIdle()

        val state = newViewModel.uiState.value
        assertEquals(1, state.cities.size) // Ne devrait pas avoir de doublon
    }

    @Test
    fun `nextQuote should advance to next quote`() = runTest(testDispatcher) {
        val city = City("Paris", 48.8566, 2.3522)
        val weatherResult = createMockWeatherResult()
        val quote1 = "\"Quote 1\" — Author"
        val quote2 = "\"Quote 2\" — Author"

        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns weatherResult
        every { mockQuoteRepository.getQuote(any(), any(), false) } returns quote1
        every { mockQuoteRepository.getQuote(any(), any(), true) } returns quote2

        val testViewModel = createViewModel()
        advanceUntilIdle()
        
        testViewModel.refreshWeather(city)
        advanceUntilIdle()

        testViewModel.nextQuote()
        advanceUntilIdle()

        val state = testViewModel.uiState.value
        assertEquals(quote2, state.quote)
        coVerify { mockQuoteRepository.getQuote(any(), any(), true) }
    }

    @Test
    fun `detectLocation should add detected city`() = runTest(testDispatcher) {
        val lat = 48.8566
        val lon = 2.3522
        val detectedCity = City("Paris", lat, lon)

        every { mockCityRepository.loadCities() } returns emptyList()
        coEvery { mockCityRepository.reverseGeocode(lat, lon) } returns detectedCity
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        every { mockQuoteRepository.getQuote(any(), any(), any()) } returns "\"Test\" — Author"

        val newViewModel = createViewModel()
        advanceUntilIdle()

        newViewModel.detectLocation(lat, lon)
        advanceUntilIdle()

        val state = newViewModel.uiState.value
        assertTrue(state.cities.any { it.label == detectedCity.label })
    }

    private fun createMockWeatherResult(): WeatherResult {
        val now = LocalDateTime.now()
        val hourly = listOf(
            HourlyForecast(now.plusHours(1), 20.0, 0),
            HourlyForecast(now.plusHours(2), 21.0, 0)
        )
        val daily = listOf(
            DailyForecast(LocalDate.now(), 15.0, 25.0, 0),
            DailyForecast(LocalDate.now().plusDays(1), 16.0, 26.0, 1)
        )
        return WeatherResult(
            current = 20.5 to 0,
            hourly = hourly,
            daily = daily,
            uvNow = 5.0,
            uvMaxToday = 6.0,
            uvPeakTime = now.plusHours(3),
            aqiNow = 30,
            aqiMaxToday = 35,
            aqiPeakTime = now.plusHours(4)
        )
    }
}
