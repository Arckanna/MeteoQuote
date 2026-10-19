package com.valerie.meteoquote.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valerie.meteoquote.R
import com.valerie.meteoquote.ui.QuotePresentation
import com.valerie.meteoquote.ui.WeatherMood
import com.valerie.meteoquote.ui.WeatherUiState
import kotlinx.coroutines.flow.StateFlow

private val skyColors = lightColorScheme(
    primary = Color(0xFF326575),
    background = Color(0xFFF6F8F7),
    surface = Color(0xFFF6F8F7),
    onSurface = Color(0xFF203A43),
    onSurfaceVariant = Color(0xFF52646B)
)

/** Uses the existing light palette so the migrated section matches its Views neighbours. */
@Composable
fun MeteoQuoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = skyColors, content = content)
}

@Composable
fun QuoteRoute(states: StateFlow<WeatherUiState>) {
    val state by states.collectAsStateWithLifecycle()
    MeteoQuoteTheme { QuoteSection(state) }
}

/** Stateless presentation: quote selection and loading remain owned by the ViewModel. */
@Composable
fun QuoteSection(state: WeatherUiState) {
    val available = !state.isLoading && state.error == null
    val quote = QuotePresentation.from(if (available) state.quote else "—")
    val (tint, mood) = WeatherMood.forCode(if (available) state.weatherCode ?: -1 else -1)
    Column(Modifier.fillMaxWidth().testTag("quote-section")
        .background(Brush.verticalGradient(listOf(Color(tint), MaterialTheme.colorScheme.background)))
        .padding(16.dp)) {
        Text(stringResource(R.string.quote_title),
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(mood, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Text(quote.text, modifier = Modifier.testTag("quote-text"),
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Serif, fontSize = 24.sp, lineHeight = 29.sp)
        quote.author?.let {
            Spacer(Modifier.height(16.dp))
            Text("— $it", modifier = Modifier.testTag("quote-author"),
                color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
        }
    }
}
