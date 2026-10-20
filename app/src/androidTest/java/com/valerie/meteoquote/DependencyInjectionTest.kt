package com.valerie.meteoquote

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.valerie.meteoquote.data.CityRepository
import com.valerie.meteoquote.widget.WeatherWidgetWorker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DependencyInjectionTest {
    @Test
    fun appAndWorkerShareTheDailyQuoteRepository() {
        val application = ApplicationProvider.getApplicationContext<MeteoQuoteApplication>()
        val worker = TestListenableWorkerBuilder<WeatherWidgetWorker>(application)
            .setWorkerFactory(application.workManagerConfiguration.workerFactory).build()
        val workerRepository = WeatherWidgetWorker::class.java.getDeclaredField("quoteRepository")
            .apply { isAccessible = true }.get(worker)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val model = MainActivity::class.java.getDeclaredField("viewModel")
                    .apply { isAccessible = true }.get(activity)
                val appRepository = model.javaClass.getDeclaredField("quoteRepository")
                    .apply { isAccessible = true }.get(model)
                assertSame(appRepository, workerRepository)
            }
        }
    }

    @Test
    fun workerCanBeCreatedWithoutAnActivity() {
        val application = ApplicationProvider.getApplicationContext<MeteoQuoteApplication>()
        // Exercise the production Application configuration, including lazy WorkManager startup.
        assertNotNull(WorkManager.getInstance(application))
        val worker = TestListenableWorkerBuilder<WeatherWidgetWorker>(application)
            .setWorkerFactory(application.workManagerConfiguration.workerFactory)
            .build()
        assertNotNull(worker)
    }

    @Test
    fun cityRepositorySurvivesActivityRecreation() {
        lateinit var repository: CityRepository
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { repository = it.cityRepository }
            scenario.recreate()
            scenario.onActivity { assertSame(repository, it.cityRepository) }
        }
    }
}
