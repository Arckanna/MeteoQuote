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
    fun weatherRepository(transport: HttpTransport, clock: Clock): WeatherRepository =
        OpenMeteoWeatherRepository(transport, clock)

    @Provides @Singleton
    fun cityRepository(@ApplicationContext context: Context, transport: HttpTransport): CityRepository =
        AndroidCityRepository(context, transport)

    @Provides @Singleton
    fun quoteRepository(@ApplicationContext context: Context, clock: Clock): QuoteRepository =
        LocalQuoteRepository(context, clock)
}
