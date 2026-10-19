package com.valerie.meteoquote

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.valerie.meteoquote.ui.WeatherUiState
import com.valerie.meteoquote.ui.compose.QuoteRoute
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class QuoteComposeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun quoteReactsToFlowWithoutChangeAction() {
        val states = MutableStateFlow(WeatherUiState(weatherCode = 0,
            quote = "\"Le ciel s’éclaircit.\" — Auteur"))
        compose.runOnUiThread {
            compose.activity.findViewById<ComposeView>(R.id.quoteBlock).setContent {
                QuoteRoute(states)
            }
        }
        compose.onNodeWithText("Quelques mots sous un ciel clair").assertExists()
        compose.onNodeWithTag("quote-author").assertTextEquals("— Auteur")
        compose.onNodeWithTag("new-quote").assertDoesNotExist()
        compose.onNodeWithText("Nouvelle citation").assertDoesNotExist()
        capture("compose-short")

        val longQuote = "La lumière revient toujours entre les nuages. " +
            "Prenons le temps de regarder le ciel, de respirer et de trouver, " +
            "dans les petites choses du quotidien, une raison de sourire."
        compose.runOnIdle { states.value = states.value.copy(weatherCode = 63, quote = longQuote) }
        compose.onNodeWithText("Quelques mots au rythme de la pluie").assertExists()
        compose.onNodeWithTag("quote-text").assertTextEquals(longQuote)
        compose.onNodeWithTag("quote-author").assertDoesNotExist()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("quote-text").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertFalse("Long quote must not be clipped", layouts.single().hasVisualOverflow)
        capture("compose-long-rain")

        compose.runOnIdle { states.value = states.value.copy(isLoading = true) }
        compose.onNodeWithTag("new-quote").assertDoesNotExist()
        compose.onNodeWithTag("quote-text").assertTextEquals("—")
        compose.runOnIdle { states.value = states.value.copy(isLoading = false, error = "Réseau indisponible") }
        compose.onNodeWithTag("new-quote").assertDoesNotExist()
        compose.runOnIdle { states.value = states.value.copy(error = null, weatherCode = 73) }
        compose.onNodeWithTag("new-quote").assertDoesNotExist()
        compose.onNodeWithText("Quelques mots dans le calme de la neige").assertExists()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnUiThread {
            val host = compose.activity.findViewById<ComposeView>(R.id.quoteBlock)
            val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
            host.draw(Canvas(bitmap))
            val profile = InstrumentationRegistry.getArguments().getString("profile") ?: "default"
            val dir = File(compose.activity.getExternalFilesDir(null), "ui-review/$profile").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
