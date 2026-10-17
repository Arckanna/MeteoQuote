package com.valerie.meteoquote.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.valerie.meteoquote.util.WeatherUtils
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Only renders forecast rows; owns neither network requests nor persisted state. */
class ForecastRenderer(
    private val context: Context,
    private val containerHourly: LinearLayout,
    private val containerDaily: LinearLayout
) {
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private fun iconBackground(view: ImageView) {
        view.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0x33000000)
        }
        view.setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    fun renderHourly(items: List<com.valerie.meteoquote.data.model.HourlyForecast>, useDarkText: Boolean) {
        containerHourly.removeAllViews()
        val fmtHour = DateTimeFormatter.ofPattern("HH'h'", Locale.getDefault())
        val textColor = if (useDarkText) 0xFF111111.toInt() else Color.WHITE

        items.forEach { h ->
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(12) }
            }

            val iv = ImageView(context).apply {
                setImageResource(WeatherUtils.wmoToIconRes(h.code))
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                contentDescription = WeatherUtils.wmoToLabel(h.code)
            }


            iconBackground(iv)
            val tvT = TextView(context).apply {
                text = String.format(Locale.getDefault(), "%.0f°", h.temp)
                textSize = 16f
                setTextColor(textColor)
            }
            val tvH = TextView(context).apply {
                text = h.time.format(fmtHour)
                textSize = 12f
                setTextColor(textColor)
            }

            col.addView(iv); col.addView(tvT); col.addView(tvH)
            containerHourly.addView(col)
        }
    }

    fun renderDaily(items: List<com.valerie.meteoquote.data.model.DailyForecast>, useDarkText: Boolean) {
        containerDaily.removeAllViews()
        val fmtDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        val textColor = if (useDarkText) 0xFF111111.toInt() else Color.WHITE

        items.forEach { d ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
                setPadding(0, dp(6), 0, dp(6))
            }

            val tvDate = TextView(context).apply {
                text = d.date.format(fmtDay)
                textSize = 14f
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val iv = ImageView(context).apply {
                setImageResource(WeatherUtils.wmoToIconRes(d.code))
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(12) }
                contentDescription = WeatherUtils.wmoToLabel(d.code)
            }


            iconBackground(iv)
            val tvTemps = TextView(context).apply {
                text = String.format(Locale.getDefault(), "min %.0f° / max %.0f°", d.tmin, d.tmax)
                textSize = 14f
                setTextColor(textColor)
            }

            row.addView(tvDate); row.addView(iv); row.addView(tvTemps)
            containerDaily.addView(row)
        }
    }

}
