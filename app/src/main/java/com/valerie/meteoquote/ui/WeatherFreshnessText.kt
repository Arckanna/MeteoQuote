package com.valerie.meteoquote.ui

import android.content.Context
import com.valerie.meteoquote.R
import com.valerie.meteoquote.data.WeatherFreshness
import java.time.Duration
import java.time.Instant

/** Observation age controls the warning; download age must never hide old conditions. */
object WeatherFreshnessText {
    fun summary(context: Context, observedAt: Instant?, fetchedAt: Instant?, now: Instant): String {
        if (observedAt == null || observedAt.isAfter(now) || fetchedAt?.isAfter(now) == true)
            return context.getString(R.string.weather_data_unknown)
        val current = WeatherFreshness.isCurrent(observedAt, fetchedAt, now)
        val timestamp = if (current) fetchedAt else observedAt
        if (timestamp == null) return context.getString(R.string.weather_download_unknown)
        val minutes = Duration.between(timestamp, now).toMinutes()
        val resource = when {
            minutes < 1 && current -> R.string.weather_updated_now
            minutes < 60 -> if (current) R.string.weather_updated_minutes else R.string.weather_observed_minutes
            minutes < 1440 -> if (current) R.string.weather_updated_hours else R.string.weather_observed_hours
            else -> if (current) R.string.weather_updated_days else R.string.weather_observed_days
        }
        val amount = when {
            minutes < 60 -> minutes
            minutes < 1440 -> minutes / 60
            else -> minutes / 1440
        }
        val age = context.getString(resource, amount)
        return if (current) age else context.getString(R.string.weather_data_old) + "\n" + age
    }
}
