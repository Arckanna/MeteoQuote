package com.valerie.meteoquote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valerie.meteoquote.data.model.*
import com.valerie.meteoquote.ui.WeatherUiState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Deterministic UI states, rendered by the real Android Views on the emulator.
 * Reflection is test-only: no fixture flag or alternative data source in production. */
@RunWith(AndroidJUnit4::class)
class UiLayoutTest {
    private val date = LocalDate.of(2026, 10, 17)
    private fun state(code: Int = 3) = WeatherUiState(
        currentTemp = -12.5, weatherCode = code, condition = "Plutôt nuageux",
        cities = listOf(City("Saint-Remy-en-Bouzemont-Saint-Genest-et-Isson, Grand Est (FR)", 48.0, 4.0)),
        uvNow = 1.3, uvMaxToday = 3.2, uvLabel = "Modéré", uvPeakTime = "14:00", uvColor = 0xFFF0C400.toInt(),
        aqiNow = 18, aqiMaxToday = 22, aqiLabel = "Bon", aqiPeakTime = "06:00", aqiColor = 0xFF24B877.toInt(),
        updatedAt = java.time.Instant.parse("2026-10-17T12:00:00Z"),
        hourlyForecasts = (0..23).map { HourlyForecast(LocalDateTime.of(2026, 10, 17, 12, 0).plusHours(it.toLong()), 18.0 - it, code) },
        dailyForecasts = (0..6).map { DailyForecast(date.plusDays(it.toLong()), -12.0 + it, 8.0 + it, code) },
        quote = "\"La lumière revient toujours entre les nuages. Prenons le temps de regarder le ciel, de respirer et de trouver, dans les petites choses du quotidien, une raison de sourire.\" — Auteur de test"
    )

    @Test fun layoutAndStateMatrix() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val render = MainActivity::class.java.getDeclaredMethod("updateUI", WeatherUiState::class.java).apply { isAccessible = true }
                val root = activity.findViewById<ViewGroup>(R.id.rootContainer)
                activity.findViewById<android.widget.Spinner>(R.id.spinnerCities).onItemSelectedListener = null
                fun show(state: WeatherUiState) {
                    render.invoke(activity, state)
                    val available = activity.resources.displayMetrics.widthPixels
                    val width = minOf(available, (720 * activity.resources.displayMetrics.density).toInt())
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                }
                show(state())
                for (background in listOf(0xFFFFF0D7, 0xFFE7EFF2, 0xFFEBEEEB, 0xFFE7F2F4, 0xFFEEEBF4, 0xFFE6EEF7, 0xFFF6F8F7)) {
                    assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(0xFF52646B.toInt(), background.toInt()) >= 4.5)
                    assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(0xFF203A43.toInt(), background.toInt()) >= 4.5)
                }
                val uv = activity.findViewById<ViewGroup>(R.id.uvCard)
                val aqi = activity.findViewById<ViewGroup>(R.id.aqiCard)
                assertEquals(uv.height, aqi.height)
                assertEquals(uv.width, aqi.width)
                if (uv.top == aqi.top) {
                    for (id in listOf(R.id.metricTitle, R.id.metricLevel, R.id.metricPeak)) {
                        assertEquals(uv.findViewById<View>(id).top, aqi.findViewById<View>(id).top)
                    }
                } else assertTrue(aqi.top >= uv.bottom)
                val daily = activity.findViewById<LinearLayout>(R.id.containerDaily)
                val iconLeft = daily.getChildAt(0).findViewById<View>(R.id.dayIcon).left
                for (index in 0 until daily.childCount) assertEquals(iconLeft, daily.getChildAt(index).findViewById<View>(R.id.dayIcon).left)
                assertEquals(24, activity.findViewById<LinearLayout>(R.id.containerHourly).childCount)
                assertEquals("— Auteur de test", activity.findViewById<TextView>(R.id.tvQuoteAuthor).text.toString())
                for (id in listOf(R.id.btnAddCity, R.id.btnLocate, R.id.btnDeleteCity, R.id.btnRefresh)) {
                    val button = activity.findViewById<View>(id)
                    assertTrue(button.width >= 48 * activity.resources.displayMetrics.density - 1)
                    assertFalse(button.contentDescription.isNullOrBlank())
                }
                capture(activity, root, "full")
                capture(activity, activity.findViewById(R.id.quoteBlock), "quote")
                capture(activity, daily, "daily")
                assertTextFits(root)
                val search = activity.findViewById<android.widget.EditText>(R.id.etCitySearch)
                assertTrue("Search hint must wrap instead of scrolling out of view",
                    search.inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
                for (code in listOf(0, 63, 73, 95)) {
                    show(state(code))
                    capture(activity, activity.findViewById(R.id.quoteBlock), "quote-$code")
                }
                show(state().copy(aqiNow = -1, aqiMaxToday = -1))
                assertEquals("Indisponible", aqi.findViewById<TextView>(R.id.metricLevel).text.toString())
                capture(activity, activity.findViewById(R.id.containerChips), "missing-aqi")
                show(state().copy(error = "Pas de connexion internet"))
                assertTrue(activity.findViewById<TextView>(R.id.tvCondition).text.contains("connexion"))
                assertEquals(View.GONE, uv.visibility)
                assertEquals(0, daily.childCount)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.tvQuoteAuthor).visibility)
                assertTrue(activity.findViewById<View>(R.id.btnRefresh).isEnabled)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnRetry).visibility)
                assertTrue(activity.findViewById<View>(R.id.btnRetry).isEnabled)
                show(state().copy(error = "Service indisponible (HTTP 503).", errorKind = com.valerie.meteoquote.ui.WeatherErrorKind.HTTP))
                assertEquals("Service indisponible (HTTP 503).", activity.findViewById<TextView>(R.id.tvCondition).text.toString())
                assertTextFits(root)
                capture(activity, activity.findViewById(R.id.weatherBlock), "network-error")
                activity.findViewById<View>(R.id.btnRetry).performClick()
                val modelField = MainActivity::class.java.getDeclaredField("viewModel").apply { isAccessible = true }
                val model = modelField.get(activity) as com.valerie.meteoquote.ui.WeatherViewModel
                assertTrue("Retry must start a new load", model.uiState.value.isLoading)
                assertNull(model.uiState.value.error)
                show(state().copy(error = "unexpected end of stream on https://api.example.invalid"))
                assertEquals("Météo indisponible. Réessayez dans un instant.",
                    activity.findViewById<TextView>(R.id.tvCondition).text.toString())
                show(state().copy(isLoading = true))
                assertFalse(activity.findViewById<View>(R.id.btnRefresh).isEnabled)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.btnRetry).visibility)
                assertFalse(activity.findViewById<View>(R.id.btnNewQuote).isEnabled)
                show(state())
                assertTrue(activity.findViewById<View>(R.id.btnRefresh).isEnabled)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.btnRetry).visibility)
            }
        }
    }

    private fun assertTextFits(view: View) {
        if (view.visibility != View.VISIBLE) return
        if (view is TextView && view.text.isNotEmpty()) {
            val layout = view.layout ?: return
            assertTrue("Text clipped vertically: ${view.text}", layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
            for (line in 0 until layout.lineCount) {
                assertEquals("Ellipsized: ${view.text}", 0, layout.getEllipsisCount(line))
                assertTrue("Text clipped horizontally: ${view.text} (line $line: ${layout.getLineMax(line)} > ${layout.width})", layout.getLineMax(line) <= layout.width + 1)
            }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) assertTextFits(view.getChildAt(i))
    }

    private fun capture(activity: MainActivity, view: View, name: String) {
        val profile = InstrumentationRegistry.getArguments().getString("profile") ?: "default"
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFF6F8F7.toInt())
        view.draw(canvas)
        val dir = File(activity.getExternalFilesDir(null), "ui-review/$profile").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
