package com.valerie.meteoquote.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.valerie.meteoquote.data.CityRepository
import com.valerie.meteoquote.data.QuoteRepository
import com.valerie.meteoquote.data.WeatherRepository

class WeatherViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            val weatherRepository = WeatherRepository()
            val quoteRepository = QuoteRepository(application)
            val cityRepository = CityRepository(application)
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(
                application,
                weatherRepository,
                quoteRepository,
                cityRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
