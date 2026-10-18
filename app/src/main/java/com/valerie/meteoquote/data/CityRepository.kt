package com.valerie.meteoquote.data

import com.valerie.meteoquote.data.model.City

interface CityRepository {
    val defaultCities: List<City>
    fun loadCities(): List<City>
    fun saveCities(cities: List<City>)
    fun selectedIndex(size: Int): Int
    fun saveSelectedIndex(index: Int)
    suspend fun geocodeCity(name: String): City?
    suspend fun reverseGeocode(lat: Double, lon: Double): City?
}
