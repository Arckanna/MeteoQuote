package com.valerie.meteoquote.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.valerie.meteoquote.MainActivity
import com.valerie.meteoquote.R

internal object WidgetViews {
    const val ACTION_REFRESH = "com.valerie.meteoquote.ACTION_REFRESH"
    const val ACTION_RENDER = "com.valerie.meteoquote.ACTION_RENDER"

    fun rendered(context: Context, city: String, result: com.valerie.meteoquote.data.model.WeatherResult?,
        quote: String, failure: String? = null): RemoteViews = loading(context, city).apply {
        val temp = result?.current?.first?.let { String.format(java.util.Locale.FRANCE, "%.0f°", it) } ?: "—°"
        val condition = result?.let { com.valerie.meteoquote.util.WeatherUtils.wmoToLabel(it.current.second) }
            ?: "Aucune météo enregistrée"
        val now = java.time.Instant.now()
        val age = result?.fetchedAt?.let { com.valerie.meteoquote.data.CachePolicy.label(it, now) }
        val status = listOfNotNull(age, condition, failure).joinToString(" · ")
        if (result?.fetchedAt?.let { !com.valerie.meteoquote.data.CachePolicy.fresh(it.toEpochMilli(), now) } == true)
            setTextViewText(R.id.widget_city, "Ancien · $city")
        setTextViewText(R.id.widget_temp, temp)
        setTextViewText(R.id.widget_condition, status)
        setTextViewText(R.id.widget_quote, quote)
        setImageViewResource(R.id.widget_icon, com.valerie.meteoquote.util.WeatherUtils.wmoToIconRes(result?.current?.second ?: 3))
        setContentDescription(R.id.widget_temp, "$temp Celsius")
        setContentDescription(R.id.widget_content, "$city, $temp Celsius, $status, $quote")
    }

    fun loading(context: Context, city: String): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_weather).apply {
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setOnClickPendingIntent(R.id.widget_content, open)
            val refresh = Intent(context, WeatherWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, 0, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            setTextViewText(R.id.widget_city, city)
            setTextViewText(R.id.widget_temp, "…")
            setTextViewText(R.id.widget_condition, "Chargement…")
            setContentDescription(R.id.widget_content, "$city, chargement météo")
        }
}
