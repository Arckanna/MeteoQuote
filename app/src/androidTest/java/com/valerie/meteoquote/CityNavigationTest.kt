package com.valerie.meteoquote

import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.valerie.meteoquote.data.model.City
import com.valerie.meteoquote.ui.WeatherViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CityNavigationTest {
    @Test fun keyboardRotationAndIdentityBasedConfirmation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.etCitySearch)).perform(scrollTo(), click(), replaceText(""))
            onView(withId(R.id.etCitySearch)).perform(object : androidx.test.espresso.ViewAction {
                override fun getConstraints() = isAssignableFrom(EditText::class.java)
                override fun getDescription() = "Wait for the IME input connection before injecting key events"
                override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                    val deadline = android.os.SystemClock.uptimeMillis() + 5_000
                    while (androidx.core.view.ViewCompat.getRootWindowInsets(view)
                            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) != true &&
                        android.os.SystemClock.uptimeMillis() < deadline) controller.loopMainThreadForAtLeast(100)
                    controller.loopMainThreadForAtLeast(500)
                }
            }, typeTextIntoFocusedView("Paris"), closeSoftKeyboard())
            onView(withId(R.id.etCitySearch)).check(matches(withText("Paris")))
            // A multiword draft tests recreation independently of the emulator IME's
            // English auto-correction of synthetic key events at word boundaries.
            onView(withId(R.id.etCitySearch)).perform(replaceText("Saint Martin"), closeSoftKeyboard())
            scenario.recreate()
            onView(withId(R.id.etCitySearch)).check(matches(withText("Saint Martin")))
            val target = City("Ville de recette à supprimer", 12.345, 23.456)
            val retained = City("Ville de recette à conserver", 34.567, 45.678)
            fun model(activity: MainActivity): WeatherViewModel = MainActivity::class.java
                .getDeclaredField("viewModel").apply { isAccessible = true }.get(activity) as WeatherViewModel
            scenario.onActivity { model(it).apply { addCity(target); addCity(retained) } }
            onView(withId(R.id.btnManageCities)).perform(scrollTo(), click())
            onView(withContentDescription("Supprimer ${target.label}")).perform(scrollTo(), click())
            onView(withText("Annuler")).perform(click())
            scenario.onActivity { assertTrue(model(it).uiState.value.cities.contains(target)) }
            onView(withId(R.id.btnManageCities)).perform(scrollTo(), click())
            onView(withContentDescription("Supprimer ${target.label}")).perform(scrollTo(), click())
            // Change selection while the confirmation is open; its target must stay fixed.
            scenario.onActivity { model(it).selectSavedCity(retained) }
            onView(withText("Supprimer")).perform(click())
            scenario.onActivity {
                val state = model(it).uiState.value
                assertFalse(state.cities.contains(target))
                assertEquals(retained, state.cities[state.selectedCityIndex])
                model(it).removeCity(retained)
            }
        }
    }
}
