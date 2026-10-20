package com.valerie.meteoquote.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle

/** Extra text is shown only when the host has reserved enough vertical space. */
internal object WidgetLayout {
    fun quoteFits(context: Context, options: Bundle): Boolean =
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80) >=
            136 * context.resources.configuration.fontScale
}
