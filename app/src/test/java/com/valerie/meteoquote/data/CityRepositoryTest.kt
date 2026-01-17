package com.valerie.meteoquote.data

import android.content.Context
import android.content.SharedPreferences
import com.valerie.meteoquote.data.model.City
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CityRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var repository: CityRepository

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit

        repository = CityRepository(mockContext)
    }

    @Test
    fun `defaultCities should contain Toulouse`() {
        val defaultCities = repository.defaultCities

        assertEquals(1, defaultCities.size)
        assertEquals("Toulouse", defaultCities[0].label)
        assertEquals(43.6047, defaultCities[0].lat, 0.0001)
        assertEquals(1.4442, defaultCities[0].lon, 0.0001)
    }

    @Test
    fun `loadCities should return empty list when no saved cities`() {
        every { mockPrefs.getString(any(), any()) } returns null

        val cities = repository.loadCities()

        assertTrue(cities.isEmpty())
    }

    @Test
    fun `loadCities should return saved cities`() {
        val jsonArray = JSONArray()
        val city1 = JSONObject().apply {
            put("label", "Paris")
            put("lat", 48.8566)
            put("lon", 2.3522)
        }
        val city2 = JSONObject().apply {
            put("label", "Lyon")
            put("lat", 45.7640)
            put("lon", 4.8357)
        }
        jsonArray.put(city1)
        jsonArray.put(city2)

        every { mockPrefs.getString(any(), any()) } returns jsonArray.toString()

        val cities = repository.loadCities()

        assertEquals(2, cities.size)
        assertEquals("Paris", cities[0].label)
        assertEquals(48.8566, cities[0].lat, 0.0001)
        assertEquals("Lyon", cities[1].label)
        assertEquals(45.7640, cities[1].lat, 0.0001)
    }

    @Test
    fun `saveCities should persist cities to SharedPreferences`() {
        val cities = listOf(
            City("Paris", 48.8566, 2.3522),
            City("Lyon", 45.7640, 4.8357)
        )

        repository.saveCities(cities)

        verify { mockEditor.putString("cities_json", any()) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `saveCities should create valid JSON`() {
        val cities = listOf(
            City("Paris", 48.8566, 2.3522)
        )

        val savedJson = io.mockk.slot<String>()
        every { mockEditor.putString("cities_json", capture(savedJson)) } returns mockEditor

        repository.saveCities(cities)

        assertTrue(savedJson.isCaptured)
        val jsonArray = JSONArray(savedJson.captured)
        assertEquals(1, jsonArray.length())
        val cityObj = jsonArray.getJSONObject(0)
        assertEquals("Paris", cityObj.getString("label"))
        assertEquals(48.8566, cityObj.getDouble("lat"), 0.0001)
    }
}
