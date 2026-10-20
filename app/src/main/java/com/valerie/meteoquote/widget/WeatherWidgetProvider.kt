package com.valerie.meteoquote.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.valerie.meteoquote.R
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        // Resize locally: no additional weather request is needed.
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        views.setViewVisibility(R.id.widget_quote,
            if (WidgetLayout.quoteFits(context, options)) View.VISIBLE else View.GONE)
        manager.partiallyUpdateAppWidget(id, views)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = enqueue(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.valerie.meteoquote.ACTION_REFRESH") enqueue(context)
    }

    private fun enqueue(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "weather-widget", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WeatherWidgetWorker>().build()
        )
    }
}
