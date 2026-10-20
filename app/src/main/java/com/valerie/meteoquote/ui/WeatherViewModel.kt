package com.valerie.meteoquote.ui

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.valerie.meteoquote.data.CityRepository
import com.valerie.meteoquote.data.QuoteRepository
import com.valerie.meteoquote.data.WeatherRepository
import com.valerie.meteoquote.data.model.City
import com.valerie.meteoquote.util.WeatherUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WeatherUiState(
    val updatedAt: java.time.Instant? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val errorKind: WeatherErrorKind? = null,
    val currentTemp: Double? = null,
    val weatherCode: Int? = null,
    val condition: String = "—",
    val hourlyForecasts: List<com.valerie.meteoquote.data.model.HourlyForecast> = emptyList(),
    val dailyForecasts: List<com.valerie.meteoquote.data.model.DailyForecast> = emptyList(),
    val uvNow: Double = 0.0,
    val uvMaxToday: Double = 0.0,
    val uvPeakTime: String? = null,
    val uvLabel: String = "",
    val uvColor: Int = 0,
    val aqiNow: Int = 0,
    val aqiMaxToday: Int = 0,
    val aqiPeakTime: String? = null,
    val aqiLabel: String = "",
    val aqiColor: Int = 0,
    val quote: String = "—",
    val cities: List<City> = emptyList(),
    val selectedCityIndex: Int = 0
)

@dagger.hilt.android.lifecycle.HiltViewModel
class WeatherViewModel @javax.inject.Inject constructor(
    application: Application,
    private val weatherRepository: WeatherRepository,
    private val quoteRepository: QuoteRepository,
    private val cityRepository: CityRepository
) : AndroidViewModel(application) {

    private var weatherJob: kotlinx.coroutines.Job? = null
    private var locationJob: kotlinx.coroutines.Job? = null
    private val _uiState = MutableStateFlow(WeatherUiState(quote = quoteRepository.assignedQuote() ?: "—"))
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        loadCities()
    }

    private fun loadCities() {
        val saved = cityRepository.loadCities()
        val cities = if (saved.isNotEmpty()) saved else cityRepository.defaultCities
        val index = cityRepository.selectedIndex(cities.size)
        _uiState.value = _uiState.value.copy(cities = cities, selectedCityIndex = index)
        if (cities.isNotEmpty()) {
            refreshWeather(cities[index])
        }
    }

    fun refreshWeather(city: City? = null) {
        val targetCity = city ?: _uiState.value.cities.getOrNull(_uiState.value.selectedCityIndex)
        if (targetCity == null) {
            _uiState.value = _uiState.value.copy(
                error = "Aucune ville sélectionnée",
                errorKind = null,
                isLoading = false,
                quote = quoteRepository.getQuote(null)
            )
            return
        }

        weatherJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, errorKind = null)

        weatherJob = viewModelScope.launch {
            try {
                val result = weatherRepository.fetchWeather(targetCity.lat, targetCity.lon)
                val (temp, code) = result.current
                val condition = WeatherUtils.wmoToLabel(code)
                val quote = quoteRepository.getQuote(code)

                val (uvLabel, uvColor) = WeatherUtils.uvCategory(getApplication(), result.uvMaxToday)
                val uvPeakTime = result.uvPeakTime?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

                val (aqiLabel, aqiColor) = WeatherUtils.aqiCategoryEU(getApplication(), result.aqiNow)
                val aqiPeakTime = result.aqiPeakTime?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = null,
                    errorKind = null,
                    updatedAt = java.time.Instant.now(),
                    currentTemp = temp,
                    weatherCode = code,
                    condition = condition,
                    hourlyForecasts = result.hourly,
                    dailyForecasts = result.daily,
                    uvNow = result.uvNow,
                    uvMaxToday = result.uvMaxToday,
                    uvPeakTime = uvPeakTime,
                    uvLabel = uvLabel,
                    uvColor = uvColor,
                    aqiNow = result.aqiNow,
                    aqiMaxToday = result.aqiMaxToday,
                    aqiPeakTime = aqiPeakTime,
                    aqiLabel = aqiLabel,
                    aqiColor = aqiColor,
                    quote = quote
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = WeatherFailure.from(e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = failure.message,
                    errorKind = failure.kind,
                    quote = quoteRepository.getQuote(null)
                )
            }
        }
    }

    fun selectCity(index: Int, cancelDetection: Boolean = true) {
        if (index in _uiState.value.cities.indices) {
            if (cancelDetection) locationJob?.cancel()
            cityRepository.saveSelectedIndex(index)
            _uiState.value = _uiState.value.copy(selectedCityIndex = index)
            refreshWeather(_uiState.value.cities[index])
        }
    }

    fun addCity(city: City) {
        val currentCities = _uiState.value.cities.toMutableList()
        val alreadyExists = currentCities.any { it.label.equals(city.label, ignoreCase = true) }
        if (alreadyExists) {
            selectCity(currentCities.indexOfFirst { it.label.equals(city.label, ignoreCase = true) })
        } else {
            currentCities.add(city)
            cityRepository.saveCities(currentCities)
            _uiState.value = _uiState.value.copy(cities = currentCities)
            selectCity(currentCities.size - 1)
        }
    }

    fun removeCity(index: Int) {
        locationJob?.cancel()
        val currentCities = _uiState.value.cities.toMutableList()
        if (index in currentCities.indices && currentCities.size > 1) {
            currentCities.removeAt(index)
            cityRepository.saveCities(currentCities)
            val newIndex = if (index >= currentCities.size) currentCities.size - 1 else index.coerceAtLeast(0)
            _uiState.value = _uiState.value.copy(
                cities = currentCities,
                selectedCityIndex = newIndex
            )
            cityRepository.saveSelectedIndex(newIndex)
            if (currentCities.isNotEmpty()) {
                refreshWeather(currentCities[newIndex])
            }
        }
    }

    /** Called only while the screen is visible, also after returning from the background. */
    fun refreshDailyQuote() {
        val state = _uiState.value
        // Let the first weather response choose the atmosphere before assigning today's quote.
        if (state.isLoading) return
        val code = state.weatherCode.takeIf { state.error == null && state.currentTemp != null }
        _uiState.value = state.copy(quote = quoteRepository.getQuote(code))
    }

    fun detectLocation(lat: Double, lon: Double) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            try {
                val detected = cityRepository.reverseGeocode(lat, lon)
                    ?: City("Ma position", lat, lon)
                
                val currentCities = _uiState.value.cities.toMutableList()
                val existingIndex = currentCities.indexOfFirst { 
                    it.label.equals(detected.label, ignoreCase = true) 
                }
                
                if (existingIndex >= 0) {
                    currentCities[existingIndex] = detected
                    cityRepository.saveCities(currentCities)
                    _uiState.value = _uiState.value.copy(cities = currentCities)
                    selectCity(existingIndex, cancelDetection = false)
                } else {
                    currentCities.add(0, detected)
                    cityRepository.saveCities(currentCities)
                    _uiState.value = _uiState.value.copy(cities = currentCities)
                    selectCity(0, cancelDetection = false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Localisation indisponible. Recherchez une ville.",
                    errorKind = null
                )
            }
        }
    }

}
