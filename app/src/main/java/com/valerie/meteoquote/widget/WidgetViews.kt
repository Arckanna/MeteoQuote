package com.valerie.meteoquote.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.valerie.meteoquote.MainActivity
import com.valerie.meteoquote.R

internal object WidgetViews {
    const val ACTION_REFRESH = "com.valerie.meteoquote.ACTION_REFRESH"

    /** Last rendered widget state, used when a launcher rebuilds the view after PACKAGE_CHANGED. */
    fun remember(context: Context, city: String, temperature: String, condition: String, code: Int, quote: String?) {
        val state = org.json.JSONObject().put("city", city).put("temperature", temperature)
            .put("condition", condition).put("code", code).put("quote", quote.orEmpty())
        context.getSharedPreferences("meteoquote_prefs", Context.MODE_PRIVATE).edit()
            .putString("widget_render_v1", state.toString()).apply()
    }

    fun restored(context: Context): Pair<RemoteViews, Boolean>? = try {
        val json = context.getSharedPreferences("meteoquote_prefs", Context.MODE_PRIVATE)
            .getString("widget_render_v1", null)
        if (json == null) null else {
            val state = org.json.JSONObject(json)
            val city = state.getString("city")
            val temperature = state.getString("temperature")
            val condition = state.getString("condition")
            val quote = state.optString("quote")
            loading(context, city).apply {
                setTextViewText(R.id.widget_temp, temperature)
                setTextViewText(R.id.widget_condition, condition)
                setTextViewText(R.id.widget_quote, quote)
                setImageViewResource(R.id.widget_icon, com.valerie.meteoquote.util.WeatherUtils.wmoToIconRes(state.getInt("code")))
                setContentDescription(R.id.widget_content, "$city, $temperature Celsius, $condition")
            } to quote.isNotEmpty()
        }
    } catch (_: org.json.JSONException) { null } catch (_: ClassCastException) { null }

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
