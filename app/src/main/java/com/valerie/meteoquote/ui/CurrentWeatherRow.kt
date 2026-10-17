package com.valerie.meteoquote.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.valerie.meteoquote.R

/** Measure before layout so a large temperature never briefly occupies a narrow column. */
class CurrentWeatherRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val temperature = findViewById<TextView>(R.id.tvTemp)
        val icon = findViewById<android.view.View>(R.id.ivIcon)
        val gap = resources.getDimensionPixelSize(R.dimen.space_small)
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val required = icon.layoutParams.width + gap + temperature.paint.measureText(temperature.text.toString())
        val stack = required > available
        orientation = if (stack) VERTICAL else HORIZONTAL
        (temperature.layoutParams as LayoutParams).apply {
            width = if (stack) LayoutParams.MATCH_PARENT else 0
            weight = if (stack) 0f else 1f
            marginStart = if (stack) 0 else gap
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
