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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28])
class WeatherViewModelTest {

    private lateinit var application: Application
    private lateinit var mockWeatherRepository: WeatherRepository
    private lateinit var mockQuoteRepository: QuoteRepository
    private lateinit var mockCityRepository: CityRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Utiliser un Application réel pour les tests
        application = ApplicationProvider.getApplicationContext<Application>()
        mockWeatherRepository = mockk(relaxed = true)
        every { mockWeatherRepository.weatherUpdates(any(), any()) } answers {
            val lat = firstArg<Double>()
            val lon = secondArg<Double>()
            kotlinx.coroutines.flow.flow { emit(mockWeatherRepository.fetchWeather(lat, lon)) }
        }
        mockQuoteRepository = mockk(relaxed = true)
        every { mockQuoteRepository.assignedQuote() } returns null
        mockCityRepository = mockk(relaxed = true)

        // Setup default cities - retourner une liste vide pour éviter le chargement automatique
        every { mockCityRepository.defaultCities } returns listOf(
            City("Toulouse", 43.6047, 1.4442)
        )
        every { mockCityRepository.loadCities() } returns emptyList()
        every { mockCityRepository.saveCities(any()) } returns Unit
    }
    
    @org.junit.After
    fun tearDown() { Dispatchers.resetMain() }

    private fun createViewModel(): WeatherViewModel {
        return WeatherViewModel(
            application,
            mockWeatherRepository,
            mockQuoteRepository,
            mockCityRepository
        )
    }

    @Test fun manualChoiceRejectsPositionReceivedAfterAcquisitionStarted() = runTest(testDispatcher) {
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        val vm = createViewModel()
        val request = vm.beginLocationRequest()
        vm.addCity(City("Choix manuel", 45.0, 4.0))
        vm.detectLocation(48.0, 2.0, request)
        advanceUntilIdle()
        assertEquals("Choix manuel", vm.uiState.value.cities[vm.uiState.value.selectedCityIndex].label)
        coVerify(exactly = 0) { mockCityRepository.reverseGeocode(any(), any()) }
    }

    @Test fun cancelledOrSupersededAcquisitionCannotStartGeocoding() = runTest(testDispatcher) {
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        coEvery { mockCityRepository.reverseGeocode(46.0, 3.0) } returns City("Position récente", 46.0, 3.0)
        val vm = createViewModel()
        val oldRequest = vm.beginLocationRequest()
        val newRequest = vm.beginLocationRequest()
        vm.detectLocation(48.0, 2.0, oldRequest)
        vm.detectLocation(46.0, 3.0, newRequest)
        advanceUntilIdle()
        assertEquals("Position récente", vm.uiState.value.cities[vm.uiState.value.selectedCityIndex].label)
        coVerify(exactly = 0) { mockCityRepository.reverseGeocode(48.0, 2.0) }
        val stopped = vm.beginLocationRequest()
        vm.cancelLocationRequest()
        vm.detectLocation(49.0, 1.0, stopped)
        advanceUntilIdle()
        coVerify(exactly = 0) { mockCityRepository.reverseGeocode(49.0, 1.0) }
    }

    @Test fun weatherRemainsUsableWhileAirQualityLoadsWithoutChangingTimestamp() = runTest(testDispatcher) {
        val result = createMockWeatherResult()
        every { mockWeatherRepository.weatherUpdates(any(), any()) } returns kotlinx.coroutines.flow.flow {
            emit(result.copy(aqiNow = -1, isAirQualityLoading = true))
            kotlinx.coroutines.delay(4_000)
            emit(result)
        }
        val vm = createViewModel()
        runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.isAirQualityLoading)
        assertEquals(20.5, vm.uiState.value.currentTemp!!, 0.001)
        val timestamp = vm.uiState.value.updatedAt
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isAirQualityLoading)
        assertEquals(30, vm.uiState.value.aqiNow)
        assertEquals(timestamp, vm.uiState.value.updatedAt)
    }

    @Test fun switchingCityCancelsOldAirQualityAndClearsItsValues() = runTest(testDispatcher) {
        val cities = listOf(City("Paris", 48.0, 2.0), City("Lyon", 45.0, 4.0))
        every { mockCityRepository.loadCities() } returns cities
        var oldCancelled = false
        every { mockWeatherRepository.weatherUpdates(48.0, 2.0) } returns kotlinx.coroutines.flow.flow {
            emit(createMockWeatherResult().copy(aqiNow = -1, isAirQualityLoading = true))
            try {
                kotlinx.coroutines.delay(4_000)
                emit(createMockWeatherResult().copy(aqiNow = 99))
            } finally { oldCancelled = true }
        }
        every { mockWeatherRepository.weatherUpdates(45.0, 4.0) } returns kotlinx.coroutines.flow.flowOf(
            createMockWeatherResult().copy(current = 10.0 to 3, aqiNow = -1))
        val vm = createViewModel()
        runCurrent()
        vm.selectCity(1)
        advanceUntilIdle()
        assertTrue(oldCancelled)
        assertEquals(10.0, vm.uiState.value.currentTemp!!, 0.001)
        assertEquals(-1, vm.uiState.value.aqiNow)
        assertFalse(vm.uiState.value.isAirQualityLoading)
    }

    @Test fun optionalEnrichmentFailureKeepsVisibleWeather() = runTest(testDispatcher) {
        every { mockWeatherRepository.weatherUpdates(any(), any()) } returns kotlinx.coroutines.flow.flow {
            emit(createMockWeatherResult().copy(aqiNow = -1, isAirQualityLoading = true))
            throw java.io.IOException("Air service unavailable")
        }
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(20.5, vm.uiState.value.currentTemp!!, 0.001)
        assertEquals(null, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isAirQualityLoading)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `initial state should load cities from repository`() = runTest(testDispatcher) {
        val cities = listOf(
            City("Paris", 48.8566, 2.3522),
            City("Lyon", 45.7640, 4.8357)
        )
        every { mockCityRepository.loadCities() } returns cities
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        every { mockQuoteRepository.getQuote(any()) } returns "\"Test\" — Author"

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
        every { mockQuoteRepository.getQuote(any()) } returns quote

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
        every { mockQuoteRepository.getQuote(any()) } returns "\"Test\" — Author"

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
        assertEquals("Météo indisponible. Réessayez dans un instant.", state.error)
        assertEquals(WeatherErrorKind.UNKNOWN, state.errorKind)
        assertFalse(state.isLoading)
    }

    @Test
    fun `retry clears typed failure and publishes fresh weather`() = runTest(testDispatcher) {
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } throws com.valerie.meteoquote.data.HttpStatusException(503)
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(WeatherErrorKind.HTTP, vm.uiState.value.errorKind)
        assertTrue(vm.uiState.value.error!!.contains("503"))
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        every { mockQuoteRepository.getQuote(any()) } returns "Recovered quote"
        vm.refreshWeather()
        assertTrue(vm.uiState.value.isLoading)
        assertEquals(null, vm.uiState.value.errorKind)
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.error)
        assertEquals(null, vm.uiState.value.errorKind)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("Recovered quote", vm.uiState.value.quote)
    }

    @Test
    fun `selectCity should update selected city index`() = runTest(testDispatcher) {
        val cities = listOf(
            City("Paris", 48.8566, 2.3522),
            City("Lyon", 45.7640, 4.8357)
        )
        every { mockCityRepository.loadCities() } returns cities
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        every { mockQuoteRepository.getQuote(any()) } returns "\"Test\" — Author"

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
        every { mockQuoteRepository.getQuote(any()) } returns "\"Test\" — Author"

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
        every { mockQuoteRepository.getQuote(any()) } returns "\"Test\" — Author"

        val newViewModel = createViewModel()
        advanceUntilIdle()

        newViewModel.addCity(city)
        advanceUntilIdle()

        val state = newViewModel.uiState.value
        assertEquals(1, state.cities.size) // Ne devrait pas avoir de doublon
    }

    @Test
    fun `visible screen updates daily quote without a weather request`() = runTest(testDispatcher) {
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        every { mockQuoteRepository.getQuote(any()) } returns "Yesterday"
        val model = createViewModel()
        advanceUntilIdle()
        every { mockQuoteRepository.getQuote(any()) } returns "Today"
        model.refreshDailyQuote()
        assertEquals("Today", model.uiState.value.quote)
        coVerify(exactly = 1) { mockWeatherRepository.fetchWeather(any(), any()) }
    }

    @Test
    fun `previously assigned quote is available before the weather load finishes`() = runTest(testDispatcher) {
        every { mockQuoteRepository.assignedQuote() } returns "Today's persisted quote"
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } throws java.io.IOException()
        every { mockQuoteRepository.getQuote(null) } returns "Today's persisted quote"
        val model = createViewModel()
        assertTrue(model.uiState.value.isLoading)
        assertEquals("Today's persisted quote", model.uiState.value.quote)
        advanceUntilIdle()
        assertEquals("Today's persisted quote", model.uiState.value.quote)
    }

    @Test
    fun `offline first load still assigns a daily quote`() = runTest(testDispatcher) {
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } throws java.io.IOException()
        every { mockQuoteRepository.getQuote(null) } returns "Neutral daily thought"
        val model = createViewModel()
        advanceUntilIdle()
        assertEquals("Neutral daily thought", model.uiState.value.quote)
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.uiState.value.error != null)
    }

    @Test
    fun `detectLocation should add detected city`() = runTest(testDispatcher) {
        val lat = 48.8566
        val lon = 2.3522
        val detectedCity = City("Paris", lat, lon)

        every { mockCityRepository.loadCities() } returns emptyList()
        coEvery { mockCityRepository.reverseGeocode(lat, lon) } returns detectedCity
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        every { mockQuoteRepository.getQuote(any()) } returns "\"Test\" — Author"

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

    @Test fun latestCityWinsWhenEarlierRequestIsSlow() = runTest(testDispatcher) {
        val cities = listOf(City("Paris", 48.0, 2.0), City("Lyon", 45.0, 4.0))
        every { mockCityRepository.loadCities() } returns cities
        coEvery { mockWeatherRepository.fetchWeather(48.0, 2.0) } coAnswers {
            kotlinx.coroutines.delay(10_000)
            createMockWeatherResult().copy(current = 1.0 to 0)
        }
        coEvery { mockWeatherRepository.fetchWeather(45.0, 4.0) } returns createMockWeatherResult().copy(current = 22.0 to 3)
        val vm = createViewModel()
        runCurrent()
        vm.selectCity(1)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.selectedCityIndex)
        assertEquals(22.0, vm.uiState.value.currentTemp!!, 0.001)
        assertEquals(null, vm.uiState.value.error)
    }

    @Test fun manualCityNeedsNoLocation() = runTest(testDispatcher) {
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        val vm = createViewModel()
        vm.addCity(City("Paris", 48.8566, 2.3522))
        advanceUntilIdle()
        assertEquals("Paris", vm.uiState.value.cities[vm.uiState.value.selectedCityIndex].label)
        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 0) { mockCityRepository.reverseGeocode(any(), any()) }
    }

    @Test fun missingGeocoderStillUsesCoordinates() = runTest(testDispatcher) {
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        coEvery { mockCityRepository.reverseGeocode(any(), any()) } returns null
        val vm = createViewModel()
        vm.detectLocation(48.0, 2.0)
        advanceUntilIdle()
        assertEquals(City("Ma position", 48.0, 2.0), vm.uiState.value.cities.first())
    }

    @Test fun manualChoiceCancelsSlowReverseGeocoding() = runTest(testDispatcher) {
        coEvery { mockWeatherRepository.fetchWeather(any(), any()) } returns createMockWeatherResult()
        coEvery { mockCityRepository.reverseGeocode(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(10_000)
            City("Position tardive", 48.0, 2.0)
        }
        val vm = createViewModel()
        vm.detectLocation(48.0, 2.0)
        runCurrent()
        vm.addCity(City("Choix manuel", 45.0, 4.0))
        advanceUntilIdle()
        assertEquals("Choix manuel", vm.uiState.value.cities[vm.uiState.value.selectedCityIndex].label)
        assertFalse(vm.uiState.value.cities.any { it.label == "Position tardive" })
    }
}
