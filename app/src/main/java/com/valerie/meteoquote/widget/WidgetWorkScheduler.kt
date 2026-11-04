package com.valerie.meteoquote.widget

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager

/** Rendering must drain every intent, including a forced refresh received during a render.
 * Network work has a separate queue per place and may safely share an outstanding download.
 */
internal object WidgetWorkScheduler {
    const val RENDER_WORK = "weather-widget"

    fun render(manager: WorkManager, request: OneTimeWorkRequest): Operation =
        manager.enqueueUniqueWork(RENDER_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)

    fun network(manager: WorkManager, key: String, request: OneTimeWorkRequest): Operation =
        manager.enqueueUniqueWork("weather-widget-network-$key", ExistingWorkPolicy.KEEP, request)
}
