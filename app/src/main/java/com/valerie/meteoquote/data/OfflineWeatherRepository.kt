package com.valerie.meteoquote.data

import com.valerie.meteoquote.data.local.*
import com.valerie.meteoquote.data.model.WeatherResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.time.Clock
import java.time.Instant

/** Room is authoritative. Weather and air are replaced atomically and independently. */
class OfflineWeatherRepository(
    private val remote: OpenMeteoWeatherRepository,
    private val dao: WeatherDao,
    private val clock: Clock,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val retainedKeys: () -> List<String> = { emptyList() }
) : WeatherRepository {
    private data class Request(val work: Deferred<Unit>, var users: Int = 0)
    private val requests = mutableMapOf<String, Request>()
    private val lock = Any()
    private data class Progress(val weather: Boolean = false, val air: Boolean = false, val failure: Exception? = null)

    private suspend fun <T> storage(block: suspend () -> T): T = try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw WeatherStorageException(e) }

    private suspend fun shared(key: String, block: suspend () -> Unit) {
        val request = synchronized(lock) {
            requests.getOrPut(key) { Request(scope.async(start = CoroutineStart.LAZY) { block() }) }
                .also { it.users++ }
        }
        try { request.work.await() } finally {
            synchronized(lock) {
                request.users--
                if (request.users == 0) {
                    if (requests[key] === request) requests.remove(key)
                    request.work.cancel()
                }
            }
        }
    }

    private suspend fun prune(key: String) = storage {
        dao.prune(clock.millis() - CachePolicy.EXPIRY_MILLIS, (retainedKeys() + key).distinct())
    }

    private suspend fun refresh(lat: Double, lon: Double, force: Boolean) {
        val key = CachePolicy.key(lat, lon)
        shared("weather:$key") {
            val old = storage { dao.weather(key) }
            if (!force && old != null && CachePolicy.fresh(old.fetchedAt, clock.instant())) return@shared
            val body = remote.weatherBody(lat, lon)
            // Parse before replacement: a bad response must never destroy the previous record.
            val parsed = remote.parseWeather(body)
            storage { dao.putWeather(WeatherRecord(key, lat, lon, parsed.zoneId, clock.millis(), body)) }
        }
    }

    private suspend fun refreshAir(lat: Double, lon: Double, force: Boolean) {
        val key = CachePolicy.key(lat, lon)
        shared("air:$key") {
            val old = storage { dao.air(key) }
            if (!force && old != null && CachePolicy.fresh(old.fetchedAt, clock.instant())) return@shared
            val body = withTimeoutOrNull(5_000) { remote.airBody(lat, lon) }
                ?: throw java.net.SocketTimeoutException("AQI timeout")
            val hourly = JSONObject(body).getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val values = hourly.getJSONArray("european_aqi")
            require(times.length() == values.length())
            for (i in 0 until times.length()) {
                val value = times.get(i)
                if (value !is Number) java.time.LocalDateTime.parse(value.toString())
                if (!values.isNull(i)) require(values.getDouble(i).let { it.isFinite() && it >= 0 })
            }
            storage { dao.putAir(AirRecord(key, clock.millis(), body)) }
        }
    }

    private fun decode(weather: WeatherRecord?, air: AirRecord?): WeatherResult? {
        if (weather == null || !CachePolicy.usable(weather.fetchedAt, clock.instant())) return null
        val usableAir = air?.takeIf { CachePolicy.age(it.fetchedAt, clock.instant()) in 0 until CachePolicy.OLD_MILLIS }
        return remote.parseWeather(weather.payload, usableAir?.payload).copy(
            fetchedAt = Instant.ofEpochMilli(weather.fetchedAt),
            airFetchedAt = usableAir?.let { Instant.ofEpochMilli(it.fetchedAt) },
            isOld = CachePolicy.old(weather.fetchedAt, clock.instant()))
    }

    override suspend fun cachedWeather(lat: Double, lon: Double): WeatherResult? = storage {
        val key = CachePolicy.key(lat, lon)
        val (weather, air) = dao.snapshot(key)
        decode(weather, air)
    }

    /** A bounded network operation for workers; they render cachedWeather before calling it. */
    override suspend fun fetchWeather(lat: Double, lon: Double): WeatherResult {
        prune(CachePolicy.key(lat, lon))
        refresh(lat, lon, true)
        return cachedWeather(lat, lon) ?: throw IllegalStateException("No stored weather")
    }

    override fun weatherUpdates(lat: Double, lon: Double): Flow<WeatherResult> = refreshUpdates(lat, lon, true)

    override fun refreshUpdates(lat: Double, lon: Double, force: Boolean): Flow<WeatherResult> = flow {
        val key = CachePolicy.key(lat, lon)
        prune(key)
        val cached = cachedWeather(lat, lon)
        cached?.let { emit(it.copy(isRefreshing = true)) }
        coroutineScope {
            val progress = MutableStateFlow(Progress(weather = true))
            val job = launch {
                try {
                    refresh(lat, lon, force)
                    progress.value = Progress(air = true)
                    try {
                        refreshAir(lat, lon, force)
                        progress.value = Progress()
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        progress.value = Progress(failure = e.takeIf { it is WeatherStorageException })
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { progress.value = Progress(failure = e) }
            }
            try {
                combine(dao.observeWeather(key), dao.observeAir(key), progress) { _, _, operation ->
                    // Read at emission time so freshness is never reset by observing the database.
                    val result = cachedWeather(lat, lon)
                    if (result == null && operation.failure != null) throw NoCachedWeatherException(operation.failure)
                    result?.copy(isRefreshing = operation.weather,
                        isAirQualityLoading = operation.air,
                        refreshError = operation.failure?.let {
                            if (it is WeatherStorageException) "Stockage météo indisponible"
                            else "Actualisation impossible. Dernières données conservées."
                        })
                }.filterNotNull().distinctUntilChanged().collect { emit(it) }
            } finally { job.cancel() }
        }
    }.flowOn(Dispatchers.IO)
}
