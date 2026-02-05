package com.valerie.meteoquote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class WeatherWidgetProvider : AppWidgetProvider() {

    companion object {
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private const val ACTION_REFRESH = "com.valerie.meteoquote.ACTION_REFRESH"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, WeatherWidgetProvider::class.java)
            )
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun updateAppWidget(
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
        appWidgetManager.updateAppWidget(appWidgetId, views)

        // Charger les données en arrière-plan
        scope.launch {
            try {
                val cityRepository = CityRepository(context)
                val weatherRepository = WeatherRepository()
                val quoteRepository = QuoteRepository(context)

                val cities = cityRepository.loadCities()
                val city = if (cities.isNotEmpty()) cities[0] else cityRepository.defaultCities[0]

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
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                // En cas d'erreur, afficher un message
                views.setTextViewText(R.id.widget_temp, "—°C")
                views.setTextViewText(R.id.widget_condition, "Erreur")
                views.setViewVisibility(R.id.widget_quote, android.view.View.GONE)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Ne pas annuler le scope ici car il est partagé
    }
}
