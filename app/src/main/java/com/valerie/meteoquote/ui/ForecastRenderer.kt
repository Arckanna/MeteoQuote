package com.valerie.meteoquote.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.valerie.meteoquote.R
import com.valerie.meteoquote.data.model.DailyForecast
import com.valerie.meteoquote.data.model.HourlyForecast
import com.valerie.meteoquote.util.WeatherUtils
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Presentation only: columns share a grid and do not depend on text length. */
class ForecastRenderer(
    private val context: Context,
    private val containerHourly: LinearLayout,
    private val containerDaily: LinearLayout
) {
    private fun dp(value: Float) = (value * context.resources.displayMetrics.density).toInt()
    private fun degrees(value: Double) = String.format(Locale.FRANCE, "%.0f°", value)

    fun renderHourly(items: List<HourlyForecast>, useDarkText: Boolean) {
        containerHourly.removeAllViews()
        val hour = DateTimeFormatter.ofPattern("HH'h'", Locale.FRANCE)
        val spoken = DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH 'heures'", Locale.FRANCE)
        items.forEachIndexed { index, h ->
            val item = LayoutInflater.from(context).inflate(R.layout.item_hourly, containerHourly, false)
            item.layoutParams = LinearLayout.LayoutParams(
                dp(80f * context.resources.configuration.fontScale.coerceAtLeast(1f)),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = if (index == items.lastIndex) 0 else dp(8f) }
            item.findViewById<TextView>(R.id.hourTime).text = h.time.format(hour)
            item.findViewById<ImageView>(R.id.hourIcon).setImageResource(WeatherUtils.wmoToIconRes(h.code))
            item.findViewById<TextView>(R.id.hourTemp).text = degrees(h.temp)
            group(item, "${h.time.format(spoken)}, ${WeatherUtils.wmoToLabel(h.code)}, ${degrees(h.temp)}")
            containerHourly.addView(item)
        }
    }

    fun renderDaily(items: List<DailyForecast>, useDarkText: Boolean) {
        containerDaily.removeAllViews()
        val date = DateTimeFormatter.ofPattern("EEE\nd MMM", Locale.FRANCE)
        val spoken = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)
        items.forEach { d ->
            val row = LayoutInflater.from(context).inflate(R.layout.item_daily, containerDaily, false)
            row.findViewById<TextView>(R.id.dayDate).text = d.date.format(date)
            row.findViewById<ImageView>(R.id.dayIcon).setImageResource(WeatherUtils.wmoToIconRes(d.code))
            row.findViewById<TextView>(R.id.dayMin).text = "min\n${degrees(d.tmin)}"
            row.findViewById<TextView>(R.id.dayMax).text = "max\n${degrees(d.tmax)}"
            group(row, "${d.date.format(spoken)}, ${WeatherUtils.wmoToLabel(d.code)}, minimum ${degrees(d.tmin)}, maximum ${degrees(d.tmax)}")
            containerDaily.addView(row)
        }
    }

    private fun group(view: View, description: String) {
        ViewCompat.setScreenReaderFocusable(view, true)
        view.contentDescription = description
        val group = view as android.view.ViewGroup
        for (i in 0 until group.childCount) group.getChildAt(i).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}
