package com.valerie.meteoquote.widget

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager

/** Rendering must drain every intent, including a forced refresh received during a render.
 * Automatic downloads share outstanding work per place; a manual request replaces it
 * so it cannot inherit an automatic cache/backoff decision or retry delay.
 */
internal object WidgetWorkScheduler {
    const val RENDER_WORK = "weather-widget"

    fun render(manager: WorkManager, request: OneTimeWorkRequest): Operation =
        manager.enqueueUniqueWork(RENDER_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)

    fun network(manager: WorkManager, key: String, request: OneTimeWorkRequest,
        force: Boolean = false): Operation =
        manager.enqueueUniqueWork("weather-widget-network-$key",
            if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
}
