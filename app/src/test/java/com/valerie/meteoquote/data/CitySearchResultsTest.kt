package com.valerie.meteoquote.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CitySearchResultsTest {
    private val repository = AndroidCityRepository(ApplicationProvider.getApplicationContext<Context>())

    @Test fun homonymsKeepRegionCountryAndDistinctCoordinates() {
        val results = repository.parseSearchResults("""{"results":[
            {"name":"Paris","admin1":"Île-de-France","country":"France","latitude":48.85,"longitude":2.35},
            {"name":"Paris","admin1":"Texas","country":"États-Unis","latitude":33.66,"longitude":-95.55}
        ]}""")
        assertEquals(listOf("Paris, Île-de-France (France)", "Paris, Texas (États-Unis)"), results.map { it.label })
        assertNotEquals(results[0].lat, results[1].lat)
    }

    @Test fun limitsResultsAndRejectsUnusableCoordinates() {
        val entries = (1..7).map { """{"name":"Ville $it","latitude":$it,"longitude":0}""" }
        assertEquals(5, repository.parseSearchResults("{\"results\":[${entries.joinToString()}]}").size)
        assertTrue(repository.parseSearchResults("""{"results":[{"name":"Invalide","latitude":91,"longitude":0}]}""").isEmpty())
        assertTrue(repository.parseSearchResults("{}").isEmpty())
    }

    @Test fun duplicatePlaceAndMissingRegionHaveSafeFallbacks() {
        val entry = """{"name":"Paris","country_code":"FR","latitude":48,"longitude":2}"""
        val results = repository.parseSearchResults("{\"results\":[$entry,$entry]}")
        assertEquals("Paris (FR)", results.single().label)
    }
}
