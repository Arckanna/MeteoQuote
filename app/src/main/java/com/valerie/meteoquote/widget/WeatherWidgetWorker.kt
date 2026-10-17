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
import java.time.LocalDate
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WeatherWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
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

        // Intent pour ouvrir l'app
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_temp, pendingIntent)

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
        appWidgetManager.updateAppWidget(ComponentName(context, WeatherWidgetProvider::class.java), views)

        // Charger les données en arrière-plan
        run {
            try {
                val cityRepository = CityRepository(context)
                val weatherRepository = WeatherRepository()
                val quoteRepository = QuoteRepository(context)

                val cities = cityRepository.loadCities()
                val city = if (cities.isNotEmpty()) cities[cityRepository.selectedIndex(cities.size)] else cityRepository.defaultCities[0]

                val result = withContext(Dispatchers.IO) {
                    weatherRepository.fetchWeather(city.lat, city.lon)
                }
                val (temp, code) = result.current
                val condition = WeatherUtils.wmoToLabel(code)
                val quote = quoteRepository.getQuote(LocalDate.now(), code, advance = false)

                // Mettre à jour les vues (peut être fait depuis n'importe quel thread)
                views.setTextViewText(R.id.widget_city, city.label)
                views.setTextViewText(R.id.widget_temp, "${temp.toInt()}°C")
                views.setTextViewText(R.id.widget_condition, condition)
                views.setImageViewResource(R.id.widget_icon, WeatherUtils.wmoToIconRes(code))
                
                // Afficher la citation si elle tient (masquer si trop longue)
                if (quote.length <= 80) {
                    views.setTextViewText(R.id.widget_quote, quote)
                    views.setViewVisibility(R.id.widget_quote, android.view.View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_quote, android.view.View.GONE)
                }

                // Mettre à jour le widget
                appWidgetManager.updateAppWidget(ComponentName(context, WeatherWidgetProvider::class.java), views)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // En cas d'erreur, afficher un message
                views.setTextViewText(R.id.widget_temp, "—°C")
                views.setTextViewText(R.id.widget_condition, "Erreur")
                views.setViewVisibility(R.id.widget_quote, android.view.View.GONE)
                appWidgetManager.updateAppWidget(ComponentName(context, WeatherWidgetProvider::class.java), views)
            }
        }
    }

}
