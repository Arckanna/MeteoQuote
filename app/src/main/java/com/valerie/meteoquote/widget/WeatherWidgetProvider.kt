package com.valerie.meteoquote.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class WeatherWidgetProvider : AppWidgetProvider() {
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
