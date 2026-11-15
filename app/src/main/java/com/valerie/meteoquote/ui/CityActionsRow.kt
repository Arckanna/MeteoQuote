package com.valerie.meteoquote.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/** Share a row only when both complete labels fit; large fonts keep full-width actions. */
class CityActionsRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val widths = (0 until childCount).map { index ->
            getChildAt(index).apply {
                measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightMeasureSpec)
            }.measuredWidth
        }
        val stack = widths.any { it > available / childCount.coerceAtLeast(1) }
        orientation = if (stack) VERTICAL else HORIZONTAL
        for (index in 0 until childCount) (getChildAt(index).layoutParams as LayoutParams).apply {
            width = if (stack) LayoutParams.MATCH_PARENT else 0
            weight = if (stack) 0f else 1f
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
