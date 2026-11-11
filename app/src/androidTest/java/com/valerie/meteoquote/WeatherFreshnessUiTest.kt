package com.valerie.meteoquote

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.action.ViewActions.click
import com.valerie.meteoquote.ui.WeatherUiState
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherFreshnessUiTest {
    @Test fun oldObservationRemainsVisibleAndDatesOpenOnTouch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val now = Instant.now()
                val render = MainActivity::class.java.getDeclaredMethod(
                    "renderFreshness", WeatherUiState::class.java, Instant::class.java
                ).apply { isAccessible = true }
                val state = WeatherUiState(currentTemp = 15.0, observedAt = now.minusSeconds(7200),
                    updatedAt = now.minusSeconds(600), zoneId = "Europe/Paris", isRefreshing = true)
                render.invoke(activity, state, now)
                assertEquals("Dernières conditions connues", activity.findViewById<TextView>(R.id.tvCurrentWeatherTitle).text)
                val summary = activity.findViewById<TextView>(R.id.tvUpdated).text.toString()
                assertTrue(summary.contains("Données anciennes"))
                assertTrue(summary.contains("Conditions relevées il y a 2 h"))
                assertTrue(summary.contains("Actualisation en cours"))
                val details = activity.findViewById<Button>(R.id.btnWeatherDetails)
                assertEquals(View.VISIBLE, details.visibility)
                assertTrue(details.contentDescription.contains("conditions et du téléchargement"))
                details.performClick()
            }
            onView(withText("Dates des données")).check(matches(isDisplayed()))
            onView(withId(android.R.id.message)).check(matches(withText(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("Conditions du"),
                org.hamcrest.Matchers.containsString("Téléchargées le")))))
            onView(withText("Fermer")).perform(click())
        }
    }
}
