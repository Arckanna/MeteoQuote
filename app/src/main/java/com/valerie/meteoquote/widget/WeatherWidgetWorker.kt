package com.valerie.meteoquote.widget
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.valerie.meteoquote.R
import com.valerie.meteoquote.data.CityRepository
import com.valerie.meteoquote.data.QuoteRepository
import com.valerie.meteoquote.data.WeatherRepository
import com.valerie.meteoquote.util.WeatherUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

@androidx.hilt.work.HiltWorker
class WeatherWidgetWorker @dagger.assisted.AssistedInject constructor(
    @dagger.assisted.Assisted context: Context,
    @dagger.assisted.Assisted params: WorkerParameters,
    private val cityRepository: CityRepository,
    private val weatherRepository: WeatherRepository,
    private val quoteRepository: QuoteRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(applicationContext, WeatherWidgetProvider::class.java)
        val ids = (manager.getAppWidgetIds(component).toList() +
            (inputData.getIntArray(WIDGET_IDS) ?: intArrayOf()).toList()).distinct().toIntArray()
        if (com.valerie.meteoquote.BuildConfig.DEBUG) android.util.Log.d("WeatherWidget", "Refreshing widget IDs: ${ids.joinToString()}")
        if (ids.isEmpty()) return Result.success()
        val cities = cityRepository.loadCities().ifEmpty { cityRepository.defaultCities }
        val city = cities.getOrNull(cityRepository.selectedIndex(cities.size))
        val views = WidgetViews.loading(applicationContext, city?.label.orEmpty())
        fun publish(hasQuote: Boolean) {
            val currentIds = (ids.toList() + manager.getAppWidgetIds(component).toList()).distinct()
            currentIds.forEach { id ->
                WidgetLayout.apply(applicationContext, manager.getAppWidgetOptions(id), views, hasQuote)
                manager.updateAppWidget(id, views)
            }
        }
        publish(false)
        // Charger les données en arrière-plan
        run {
            try {

                requireNotNull(city) { "No selected city" }

                val result = withContext(Dispatchers.IO) {
                    weatherRepository.fetchWeather(city.lat, city.lon)
                }
                val (temp, code) = result.current
                val condition = WeatherUtils.wmoToLabel(code)
                val quote = quoteRepository.getQuote(LocalDate.now(), code, advance = false)

                // Mettre à jour les vues (peut être fait depuis n'importe quel thread)
                views.setTextViewText(R.id.widget_city, city.label)
                views.setTextViewText(R.id.widget_temp, "${temp.toInt()}°")
                views.setContentDescription(R.id.widget_temp, "${temp.toInt()} degrés Celsius")
                views.setTextViewText(R.id.widget_condition, condition)
                views.setContentDescription(R.id.widget_content, "${city.label}, ${temp.toInt()} degrés Celsius, $condition")
                views.setImageViewResource(R.id.widget_icon, WeatherUtils.wmoToIconRes(code))
                
                views.setTextViewText(R.id.widget_quote, quote)
                WidgetViews.remember(applicationContext, city.label, "${temp.toInt()}°", condition, code, quote)

                // Mettre à jour le widget
                publish(true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // En cas d'erreur, afficher un message
                views.setTextViewText(R.id.widget_temp, "—°")
                views.setTextViewText(R.id.widget_condition, "Météo indisponible")
                views.setViewVisibility(R.id.widget_quote, android.view.View.GONE)
                views.setContentDescription(R.id.widget_content, "Météo indisponible")
                WidgetViews.remember(applicationContext, city?.label.orEmpty(), "—°", "Météo indisponible", 3, null)
                publish(false)
            }
        }
        return Result.success()
    }

    companion object { const val WIDGET_IDS = "widget_ids" }
}
