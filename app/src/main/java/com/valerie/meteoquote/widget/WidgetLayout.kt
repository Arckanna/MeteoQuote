package com.valerie.meteoquote.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.valerie.meteoquote.R

/** Extra text is shown only when the host has reserved enough vertical space. */
internal object WidgetLayout {
    fun apply(context: Context, options: Bundle, views: RemoteViews, hasQuote: Boolean) {
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80)
        views.setViewVisibility(R.id.widget_condition,
            if (height >= 80 * context.resources.configuration.fontScale) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_quote,
            if (hasQuote && quoteFits(context, options)) View.VISIBLE else View.GONE)
    }
    fun quoteFits(context: Context, options: Bundle): Boolean =
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80) >=
            136 * context.resources.configuration.fontScale
}
