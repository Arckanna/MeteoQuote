package com.valerie.meteoquote.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import kotlin.math.max

/** Equal panels, stacked when scaled text needs more width. No device-specific offsets. */
class MetricLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    private val gap get() = resources.getDimensionPixelSize(com.valerie.meteoquote.R.dimen.space_medium)
    private var stacked = false
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val visible = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != GONE }
        stacked = width / resources.displayMetrics.density < 320 * resources.configuration.fontScale
        val childWidth = if (stacked || visible.size < 2) width else (width - gap) / 2
        val panels = visible.filterIsInstance<android.widget.LinearLayout>()
        panels.forEach { panel -> (0 until panel.childCount).forEach { panel.getChildAt(it).minimumHeight = 0 } }
        panels.forEach { it.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)) }
        if (panels.size == 2) {
            for (index in 0 until panels.first().childCount) {
                val rowHeight = panels.maxOf { it.getChildAt(index).measuredHeight }
                panels.forEach { it.getChildAt(index).minimumHeight = rowHeight }
            }
        }
        var height = 0
        visible.forEach {
            it.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            height = max(height, it.measuredHeight)
        }
        visible.forEach { it.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)) }
        val total = if (visible.isEmpty()) 0 else if (stacked) height * visible.size + gap * (visible.size - 1) else height
        setMeasuredDimension(width, resolveSize(total, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var offset = 0
        (0 until childCount).map { getChildAt(it) }.filter { it.visibility != GONE }.forEach {
            val x = if (stacked) 0 else if (layoutDirection == LAYOUT_DIRECTION_RTL) width - offset - it.measuredWidth else offset
            val y = if (stacked) offset else 0
            it.layout(x, y, x + it.measuredWidth, y + it.measuredHeight)
            offset += (if (stacked) it.measuredHeight else it.measuredWidth) + gap
        }
    }
}
