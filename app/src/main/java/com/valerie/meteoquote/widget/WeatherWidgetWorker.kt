package com.valerie.meteoquote.widget
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.valerie.meteoquote.MainActivity
import com.valerie.meteoquote.R
import com.valerie.meteoquote.data.CityRepository
import com.valerie.meteoquote.data.QuoteRepository
import com.valerie.meteoquote.data.WeatherRepository
import com.valerie.meteoquote.util.WeatherUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val ACTION_REFRESH = "com.valerie.meteoquote.ACTION_REFRESH"

    override suspend fun doWork(): Result {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val ids = manager.getAppWidgetIds(ComponentName(applicationContext, WeatherWidgetProvider::class.java))
        // One network request regardless of how many widgets are installed.
        if (ids.isNotEmpty()) updateAppWidget(applicationContext, manager, ids[0])
        return Result.success()
    }
    private suspend fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        fun publish(hasQuote: Boolean) {
            val component = ComponentName(context, WeatherWidgetProvider::class.java)
            appWidgetManager.getAppWidgetIds(component).forEach { id ->
                views.setViewVisibility(R.id.widget_quote,
                    if (hasQuote && WidgetLayout.quoteFits(context, appWidgetManager.getAppWidgetOptions(id)))
                        android.view.View.VISIBLE else android.view.View.GONE)
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        // Intent pour ouvrir l'app
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_temp, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_content, pendingIntent)

        // Intent pour refresh
        val refreshIntent = Intent(context, WeatherWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, 0, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent)

        // Afficher un état de chargement initial
        views.setTextViewText(R.id.widget_temp, "…")
        views.setTextViewText(R.id.widget_condition, "Chargement…")
        publish(false)

        // Charger les données en arrière-plan
        run {
            try {

                val cities = cityRepository.loadCities()
                val city = if (cities.isNotEmpty()) cities[cityRepository.selectedIndex(cities.size)] else cityRepository.defaultCities[0]

                val result = withContext(Dispatchers.IO) {
                    weatherRepository.fetchWeather(city.lat, city.lon)
                }
                val (temp, code) = result.current
                val condition = WeatherUtils.wmoToLabel(code)
                val quote = quoteRepository.getQuote(code)

                // Mettre à jour les vues (peut être fait depuis n'importe quel thread)
                views.setTextViewText(R.id.widget_city, city.label)
                views.setTextViewText(R.id.widget_temp, "${temp.toInt()}°")
                views.setContentDescription(R.id.widget_temp, "${temp.toInt()} degrés Celsius")
                views.setTextViewText(R.id.widget_condition, condition)
                views.setContentDescription(R.id.widget_content, "${city.label}, ${temp.toInt()} degrés Celsius, $condition")
                views.setImageViewResource(R.id.widget_icon, WeatherUtils.wmoToIconRes(code))
                
                views.setTextViewText(R.id.widget_quote, quote)

                // Mettre à jour le widget
                publish(true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // En cas d'erreur, afficher un message
                views.setTextViewText(R.id.widget_temp, "—°")
                views.setTextViewText(R.id.widget_condition, "Erreur")
                views.setViewVisibility(R.id.widget_quote, android.view.View.GONE)
                views.setContentDescription(R.id.widget_content, "Météo indisponible")
                publish(false)
            }
        }
    }

}
