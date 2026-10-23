package com.valerie.meteoquote.di

import android.content.Context
import com.valerie.meteoquote.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides @Singleton
    fun transport(): HttpTransport = HttpTransport()

    @Provides @Singleton
    fun clock(): Clock = Clock.systemUTC()

    @Provides @Singleton
    fun weatherDatabase(@ApplicationContext context: Context): com.valerie.meteoquote.data.local.WeatherDatabase =
        androidx.room.Room.databaseBuilder(context,
            com.valerie.meteoquote.data.local.WeatherDatabase::class.java, "meteoquote_weather.db").build()

    @Provides @Singleton
    fun weatherRepository(transport: HttpTransport, clock: Clock,
        database: com.valerie.meteoquote.data.local.WeatherDatabase,
        cities: CityRepository, @ApplicationContext context: Context): WeatherRepository =
        OfflineWeatherRepository(OpenMeteoWeatherRepository(transport, clock), database.weather(), clock,
            retainedKeys = {
                val saved = cities.loadCities().ifEmpty { cities.defaultCities }.map { CachePolicy.key(it.lat, it.lon) }
                val recent = runCatching {
                    val json = context.getSharedPreferences("meteoquote_prefs", Context.MODE_PRIVATE)
                        .getString("recent_cities_json", "[]") ?: "[]"
                    val values = org.json.JSONArray(json)
                    (0 until values.length()).mapNotNull { i -> runCatching {
                        values.getJSONObject(i).let { CachePolicy.key(it.getDouble("lat"), it.getDouble("lon")) }
                    }.getOrNull() }
                }.getOrDefault(emptyList())
                saved + recent
            })

    @Provides @Singleton
    fun cityRepository(@ApplicationContext context: Context, transport: HttpTransport): CityRepository =
        AndroidCityRepository(context, transport)

    @Provides @Singleton
    fun quoteRepository(@ApplicationContext context: Context, clock: Clock): QuoteRepository =
        LocalQuoteRepository(context, clock)
}
