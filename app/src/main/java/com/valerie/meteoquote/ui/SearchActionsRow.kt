package com.valerie.meteoquote.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout

/** Keep search usable at large font sizes; actions move below when a word cannot fit. */
class SearchActionsRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val search = getChildAt(0) as EditText
        val actions = getChildAt(1)
        actions.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightMeasureSpec)
        val longestWord = search.hint.toString().split(' ').maxOf { search.paint.measureText(it) }
        val minimumSearchWidth = longestWord + search.compoundPaddingLeft + search.compoundPaddingRight
        val stack = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - actions.measuredWidth < minimumSearchWidth
        orientation = if (stack) VERTICAL else HORIZONTAL
        (search.layoutParams as LayoutParams).apply {
            width = if (stack) LayoutParams.MATCH_PARENT else 0
            weight = if (stack) 0f else 1f
        }
        (actions.layoutParams as LayoutParams).gravity = if (stack) Gravity.END else Gravity.CENTER_VERTICAL
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
