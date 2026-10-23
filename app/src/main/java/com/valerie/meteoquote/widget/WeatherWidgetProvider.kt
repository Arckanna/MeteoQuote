package com.valerie.meteoquote.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.work.*
import com.valerie.meteoquote.data.CityRepository
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class WeatherWidgetProvider : AppWidgetProvider() {
    @Inject lateinit var cities: CityRepository
    @Inject lateinit var weather: com.valerie.meteoquote.data.WeatherRepository
    @Inject lateinit var quotes: com.valerie.meteoquote.data.QuoteRepository

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        renderCached(context, manager, intArrayOf(id))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Package/component changes invalidate the launcher's RemoteViews, including its data.
        // Restore a complete view without starting another worker for this system callback.
        val prefs = context.getSharedPreferences("widget_schedule", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val due = ids.filter {
            val age = now - prefs.getLong("attempt_$it", 0)
            age < 0 || age >= 60_000 || !prefs.contains("attempt_$it")
        }.toIntArray()
        // WorkManager component changes can cause another APPWIDGET_UPDATE broadcast.
        // Do not turn those broadcasts into an unbounded chain of network requests.
        if (due.isEmpty()) {
            renderCached(context, manager, ids)
            return
        }
        prefs.edit().apply { due.forEach { putLong("attempt_$it", now) } }.apply()
        enqueue(context, due)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetViews.ACTION_REFRESH || intent.action == WidgetViews.ACTION_RENDER) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, javaClass))
            context.getSharedPreferences("widget_schedule", Context.MODE_PRIVATE).edit()
                .apply { ids.forEach { putLong("attempt_$it", System.currentTimeMillis()) } }.apply()
            enqueue(context, ids, force = intent.action == WidgetViews.ACTION_REFRESH)
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        context.getSharedPreferences("widget_schedule", Context.MODE_PRIVATE).edit()
            .apply { ids.forEach { remove("attempt_$it") } }.apply()
    }

    /** Rebuild invalidated launcher views without scheduling another WorkManager cycle. */
    private fun renderCached(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scheduling.execute {
            try {
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeout(5_000) {
                        val saved = cities.loadCities().ifEmpty { cities.defaultCities }
                        val city = saved.getOrNull(cities.selectedIndex(saved.size)) ?: return@withTimeout
                        val cached = weather.cachedWeather(city.lat, city.lon)
                        val latest = cities.loadCities().ifEmpty { cities.defaultCities }
                        if (latest.getOrNull(cities.selectedIndex(latest.size)) != city) return@withTimeout
                        val quote = quotes.getQuote(cached?.current?.second?.takeIf { !cached.isOld })
                        ids.forEach { id ->
                            val views = WidgetViews.rendered(context, city.label, cached, quote)
                            WidgetLayout.apply(context, manager.getAppWidgetOptions(id), views, true)
                            manager.updateAppWidget(id, views)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("WeatherWidget", "Cached widget rendering failed", e)
            } finally { pending.finish() }
        }
    }

    private fun enqueue(context: Context, ids: IntArray, force: Boolean = false) {
        if (ids.isEmpty()) return
        val request = OneTimeWorkRequestBuilder<WeatherWidgetWorker>()
            .setInputData(workDataOf(WeatherWidgetWorker.WIDGET_IDS to ids, WeatherWidgetWorker.FORCE to force))
        ids.forEach { request.addTag("widget-$it") }
        // Earlier Android versions require a foreground service for expedited work.
        if (Build.VERSION.SDK_INT >= 31) {
            request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        val pending = goAsync()
        scheduling.execute {
            try {
                WorkManager.getInstance(context).enqueueUniqueWork("weather-widget",
                    ExistingWorkPolicy.KEEP, request.build()).result.get(8, TimeUnit.SECONDS)
            } catch (failure: Exception) {
                android.util.Log.w("WeatherWidget", "Widget scheduling failed", failure)
                val manager = AppWidgetManager.getInstance(context)
                ids.forEach { id ->
                    val views = WidgetViews.loading(context, context.getString(com.valerie.meteoquote.R.string.app_name))
                    views.setTextViewText(com.valerie.meteoquote.R.id.widget_temp, "—")
                    views.setTextViewText(com.valerie.meteoquote.R.id.widget_condition, "Ouvrir l’application")
                    views.setContentDescription(com.valerie.meteoquote.R.id.widget_content, "Actualisation indisponible. Ouvrir l’application.")
                    WidgetLayout.apply(context, manager.getAppWidgetOptions(id), views, hasQuote = false)
                    manager.updateAppWidget(id, views)
                }
            } finally { pending.finish() }
        }
    }

    private companion object { val scheduling = Executors.newSingleThreadExecutor() }
}
