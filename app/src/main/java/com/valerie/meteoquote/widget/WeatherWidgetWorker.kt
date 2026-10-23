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
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return Result.success()
        fun selected(): com.valerie.meteoquote.data.model.City? {
            val cities = cityRepository.loadCities().ifEmpty { cityRepository.defaultCities }
            return cities.getOrNull(cityRepository.selectedIndex(cities.size))
        }
        val city = selected() ?: return Result.success()
        val key = com.valerie.meteoquote.data.CachePolicy.key(city.lat, city.lon)
        if (inputData.getBoolean(NETWORK, false) &&
            inputData.getString(NETWORK_KEY)?.let { it != key } == true) return Result.success()
        fun stillSelected() = selected()?.let {
            com.valerie.meteoquote.data.CachePolicy.key(it.lat, it.lon) == key
        } == true
        fun publish(result: com.valerie.meteoquote.data.model.WeatherResult?, failure: String? = null) {
            if (!stillSelected()) return
            val quote = quoteRepository.getQuote(result?.current?.second?.takeIf { !result.isOld })
            val views = WidgetViews.rendered(applicationContext, city.label, result, quote, failure)
            manager.getAppWidgetIds(component).forEach { id ->
                WidgetLayout.apply(applicationContext, manager.getAppWidgetOptions(id), views, true)
                manager.updateAppWidget(id, views)
            }
        }
        var cached: com.valerie.meteoquote.data.model.WeatherResult? = null
        try {
            cached = weatherRepository.cachedWeather(city.lat, city.lon)
            publish(cached)
            if (!inputData.getBoolean(NETWORK, false)) {
                if (inputData.getBoolean(FORCE, false) || cached?.fetchedAt?.let {
                    com.valerie.meteoquote.data.CachePolicy.fresh(it.toEpochMilli(), java.time.Instant.now())
                } != true) {
                    val request = androidx.work.OneTimeWorkRequestBuilder<WeatherWidgetWorker>()
                        .setInputData(androidx.work.workDataOf(NETWORK to true, NETWORK_KEY to key))
                        .setConstraints(androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                        .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        "weather-widget-network-$key", androidx.work.ExistingWorkPolicy.KEEP, request)
                }
                return Result.success()
            }
            val result = weatherRepository.fetchWeather(city.lat, city.lon)
            publish(result)
            return Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            publish(cached, if (e is com.valerie.meteoquote.data.WeatherStorageException)
                "Stockage indisponible" else "Actualisation impossible")
            val retryable = when (e) {
                is com.valerie.meteoquote.data.HttpStatusException -> e.statusCode in setOf(408, 429, 500, 502, 503, 504)
                is com.valerie.meteoquote.data.InvalidResponseException -> false
                is java.io.IOException -> true
                else -> false
            }
            return if (inputData.getBoolean(NETWORK, false) && retryable && runAttemptCount < 2) Result.retry()
                else Result.failure()
        }
    }

    companion object {
        const val WIDGET_IDS = "widget_ids"
        const val NETWORK = "network"
        const val NETWORK_KEY = "network_key"
        const val FORCE = "force"
    }
}
