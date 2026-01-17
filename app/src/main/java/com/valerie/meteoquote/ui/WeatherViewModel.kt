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
import java.time.LocalDate

data class WeatherUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
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

class WeatherViewModel(
    application: Application,
    private val weatherRepository: WeatherRepository,
    private val quoteRepository: QuoteRepository,
    private val cityRepository: CityRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        loadCities()
    }

    private fun loadCities() {
        val saved = cityRepository.loadCities()
        val cities = if (saved.isNotEmpty()) saved else cityRepository.defaultCities
        _uiState.value = _uiState.value.copy(cities = cities)
        if (cities.isNotEmpty()) {
            refreshWeather(cities[0])
        }
    }

    fun refreshWeather(city: City? = null) {
        val targetCity = city ?: _uiState.value.cities.getOrNull(_uiState.value.selectedCityIndex)
        if (targetCity == null) {
            _uiState.value = _uiState.value.copy(
                error = "Aucune ville sélectionnée",
                isLoading = false
            )
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val result = weatherRepository.fetchWeather(targetCity.lat, targetCity.lon)
                val (temp, code) = result.current
                val condition = WeatherUtils.wmoToLabel(code)
                val quote = quoteRepository.getQuote(LocalDate.now(), code, advance = false)

                val (uvLabel, uvColor) = WeatherUtils.uvCategory(getApplication(), result.uvMaxToday)
                val uvPeakTime = result.uvPeakTime?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

                val (aqiLabel, aqiColor) = WeatherUtils.aqiCategoryEU(getApplication(), result.aqiNow)
                val aqiPeakTime = result.aqiPeakTime?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
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
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Erreur réseau"
                )
            }
        }
    }

    fun selectCity(index: Int) {
        if (index in _uiState.value.cities.indices) {
            _uiState.value = _uiState.value.copy(selectedCityIndex = index)
            refreshWeather(_uiState.value.cities[index])
        }
    }

    fun addCity(city: City) {
        val currentCities = _uiState.value.cities.toMutableList()
        val alreadyExists = currentCities.any { it.label.equals(city.label, ignoreCase = true) }
        if (!alreadyExists) {
            currentCities.add(city)
            cityRepository.saveCities(currentCities)
            _uiState.value = _uiState.value.copy(cities = currentCities)
            selectCity(currentCities.size - 1)
        }
    }

    fun nextQuote() {
        val code = _uiState.value.weatherCode
        if (code != null) {
            val quote = quoteRepository.getQuote(LocalDate.now(), code, advance = true)
            _uiState.value = _uiState.value.copy(quote = quote)
        }
    }

    fun detectLocation(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val detected = cityRepository.reverseGeocode(lat, lon)
                    ?: City("Ma position", lat, lon)
                
                val currentCities = _uiState.value.cities.toMutableList()
                val existingIndex = currentCities.indexOfFirst { 
                    it.label.equals(detected.label, ignoreCase = true) 
                }
                
                if (existingIndex >= 0) {
                    selectCity(existingIndex)
                } else {
                    currentCities.add(0, detected)
                    cityRepository.saveCities(currentCities)
                    _uiState.value = _uiState.value.copy(cities = currentCities)
                    selectCity(0)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Erreur lors de la détection de la position: ${e.message}"
                )
            }
        }
    }

}
