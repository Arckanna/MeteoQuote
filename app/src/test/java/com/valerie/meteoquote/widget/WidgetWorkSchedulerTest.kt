package com.valerie.meteoquote.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.WorkManagerTestInitHelper
import com.valerie.meteoquote.R
import com.valerie.meteoquote.data.*
import com.valerie.meteoquote.data.model.City
import com.valerie.meteoquote.data.model.WeatherResult
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WidgetWorkSchedulerTest {
    private lateinit var context: Context
    private lateinit var manager: WorkManager
    private val cities = mockk<CityRepository>()
    private val weather = mockk<WeatherRepository>()
    private val quotes = mockk<QuoteRepository>()
    private val selected = AtomicInteger(0)
    private val places = listOf(City("A", 43.0, 1.0), City("B", 45.0, 4.0))
    private val rendered = CopyOnWriteArrayList<RemoteViews>()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        every { cities.loadCities() } returns places
        every { cities.selectedIndex(any()) } answers { selected.get() }
        every { quotes.getQuote(any()) } returns "Citation"
        coEvery { weather.cachedWeather(any(), any()) } returns null
        val widgets = mockk<AppWidgetManager>(relaxed = true)
        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns widgets
        every { widgets.getAppWidgetIds(any<ComponentName>()) } returns intArrayOf(1)
        every { widgets.getAppWidgetOptions(1) } returns Bundle()
        every { widgets.updateAppWidget(1, any<RemoteViews>()) } answers { rendered.add(secondArg()); Unit }
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String,
                workerParameters: WorkerParameters): ListenableWorker? =
                if (workerClassName == WeatherWidgetWorker::class.java.name)
                    WeatherWidgetWorker(appContext, workerParameters, cities, weather, quotes)
                else null
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setWorkerFactory(factory).build())
        manager = WorkManager.getInstance(context)
    }

    @After fun teardown() {
        manager.cancelAllWork().result.get(10, TimeUnit.SECONDS)
        WorkManagerTestInitHelper.closeWorkDatabase()
        unmockkStatic(AppWidgetManager::class)
    }

    private fun request(force: Boolean = false) = OneTimeWorkRequestBuilder<WeatherWidgetWorker>()
        .setInputData(workDataOf(WeatherWidgetWorker.FORCE to force)).build()

    private suspend fun finished(request: OneTimeWorkRequest): WorkInfo = withTimeout(10_000) {
        manager.getWorkInfoByIdFlow(request.id).first { it?.state?.isFinished == true }!!
    }

    @Test fun selectionBIsRenderedAfterSuspendedAWithoutNetworkOrCache() = runBlocking {
        renderLatestSelectionAfterBarrier(failFirstRead = false)
    }

    @Test fun cacheFailureInADoesNotCancelAlreadyQueuedB() = runBlocking {
        renderLatestSelectionAfterBarrier(failFirstRead = true)
    }

    private suspend fun renderLatestSelectionAfterBarrier(failFirstRead: Boolean) {
        val readA = CompletableDeferred<Unit>()
        val releaseA = CompletableDeferred<Unit>()
        coEvery { weather.cachedWeather(43.0, 1.0) } coAnswers {
            readA.complete(Unit) // Worker A has captured its selected city.
            releaseA.await()
            if (failFirstRead) throw WeatherStorageException(IllegalStateException("Cache unavailable"))
            null
        }
        val a = request()
        WidgetWorkScheduler.render(manager, a).result.get(10, TimeUnit.SECONDS)
        try {
            withTimeout(10_000) { readA.await() }
            selected.set(1)
            val b = request()
            WidgetWorkScheduler.render(manager, b).result.get(10, TimeUnit.SECONDS)
            assertEquals(WorkInfo.State.BLOCKED, manager.getWorkInfoById(b.id).get()!!.state)
            releaseA.complete(Unit)
            assertEquals(WorkInfo.State.SUCCEEDED, finished(a).state)
            assertEquals(WorkInfo.State.SUCCEEDED, finished(b).state)
            val view = rendered.last().apply(context, FrameLayout(context))
            assertEquals("B", view.findViewById<TextView>(R.id.widget_city).text.toString())
            assertTrue(view.findViewById<TextView>(R.id.widget_condition).text.contains("Aucune météo enregistrée"))
            coVerify(exactly = 0) { weather.fetchWeather(any(), any()) }
        } finally { releaseA.complete(Unit) }
    }

    @Test fun forcedRequestSurvivesLaterOrdinaryRenderAndNetworkRemainsDeduplicated() = runBlocking {
        val readA = CompletableDeferred<Unit>()
        val releaseA = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        val fresh = WeatherResult(20.0 to 0, emptyList(), emptyList(), 0.0, 0.0,
            null, -1, -1, null, fetchedAt = Instant.now())
        coEvery { weather.cachedWeather(any(), any()) } coAnswers {
            if (reads.incrementAndGet() == 1) {
                readA.complete(Unit)
                releaseA.await()
            }
            fresh
        }
        val a = request()
        WidgetWorkScheduler.render(manager, a).result.get(10, TimeUnit.SECONDS)
        try {
            withTimeout(10_000) { readA.await() }
            val forced = request(force = true)
            val forcedAgain = request(force = true)
            val ordinary = request()
            for (next in listOf(forced, forcedAgain, ordinary)) {
                WidgetWorkScheduler.render(manager, next).result.get(10, TimeUnit.SECONDS)
            }
            releaseA.complete(Unit)
            for (next in listOf(a, forced, forcedAgain, ordinary)) {
                assertEquals(WorkInfo.State.SUCCEEDED, finished(next).state)
            }
            val key = CachePolicy.key(43.0, 1.0)
            val downloads = manager.getWorkInfosForUniqueWork("weather-widget-network-$key").get()
            assertEquals(1, downloads.size)
            assertEquals(WorkInfo.State.ENQUEUED, downloads.single().state)
            coVerify(exactly = 0) { weather.fetchWeather(any(), any()) }
        } finally { releaseA.complete(Unit) }
    }
}
