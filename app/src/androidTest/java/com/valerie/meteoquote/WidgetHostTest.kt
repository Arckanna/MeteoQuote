package com.valerie.meteoquote

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.valerie.meteoquote.widget.WeatherWidgetProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Bind a real widget through Android, including provider broadcasts and the production worker. */
@RunWith(AndroidJUnit4::class)
class WidgetHostTest {
    @Test fun addingWidgetLoadsDataThroughTheActualHost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = AppWidgetManager.getInstance(context)
        android.util.Log.i("WeatherWidget", "Existing launcher IDs: ${manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java)).joinToString()}")
        val host = AppWidgetHost(context, 91463)
        val id = host.allocateAppWidgetId()
        var hostView: AppWidgetHostView? = null
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        try {
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 220)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 80)
            }
            assertTrue("Android must bind our provider", manager.bindAppWidgetIdIfAllowed(id,
                ComponentName(context, WeatherWidgetProvider::class.java), options))
            val info = manager.getAppWidgetInfo(id)
            assertNotNull(info)
            if (android.os.Build.VERSION.SDK_INT >= 31) assertEquals(1, info.targetCellHeight)
            instrumentation.runOnMainSync {
                host.startListening()
                hostView = host.createView(context, id, info)
            }
            val workManager = WorkManager.getInstance(context)
            var condition = ""
            var city = ""
            val deadline = SystemClock.elapsedRealtime() + 75_000
            while (SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync {
                    condition = hostView?.findViewById<TextView>(R.id.widget_condition)?.text?.toString().orEmpty()
                    city = hostView?.findViewById<TextView>(R.id.widget_city)?.text?.toString().orEmpty()
                }
                val boundWork = workManager.getWorkInfosByTag("widget-$id").get()
                if (boundWork.isNotEmpty() && boundWork.all { it.state.isFinished } &&
                    condition.isNotBlank() && condition != "—" && condition != "Chargement…") break
                SystemClock.sleep(250)
            }
            val jobs = WorkManager.getInstance(context).getWorkInfosForUniqueWork("weather-widget")
                .get(5, TimeUnit.SECONDS).map { it.state }
            assertTrue("No final widget update: condition=$condition, city=$city, work=$jobs",
                condition.isNotBlank() && condition != "—" && condition != "Chargement…")
            assertTrue("The selected city must be available even when the network fails: $city",
                city.isNotBlank() && city != context.getString(R.string.selected_city))
            // Repeated system updates must not create another chain of weather jobs.
            val before = workManager.getWorkInfosByTag("widget-$id").get().size
            assertTrue("Binding must schedule this widget's worker", before > 0)
            repeat(3) {
                context.sendBroadcast(android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .setComponent(ComponentName(context, WeatherWidgetProvider::class.java))
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id)))
            }
            SystemClock.sleep(2000)
            assertEquals("Repeated provider broadcasts must be coalesced", before,
                workManager.getWorkInfosByTag("widget-$id").get().size)
            instrumentation.runOnMainSync {
                val view = requireNotNull(hostView)
                val density = context.resources.displayMetrics.density
                val w = (220 * density).toInt()
                val h = (80 * density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, w, h)
                assertEquals("Layout/provider updates must preserve the rendered city", city,
                    view.findViewById<TextView>(R.id.widget_city).text.toString())
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val dir = File(context.getExternalFilesDir(null), "widget-review").apply { mkdirs() }
                File(dir, "actual-host.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            // KEEP replaces a completed unique work's history; compare identities, not a growing chain.
            val beforeRefresh = workManager.getWorkInfosByTag("widget-$id").get().map { it.id }.toSet()
            instrumentation.runOnMainSync {
                val refresh = requireNotNull(hostView).findViewById<View>(R.id.widget_refresh)
                assertTrue("Refresh action must survive rebuilding", refresh.hasOnClickListeners())
                refresh.performClick()
            }
            val refreshDeadline = SystemClock.elapsedRealtime() + 10_000
            while (SystemClock.elapsedRealtime() < refreshDeadline &&
                workManager.getWorkInfosByTag("widget-$id").get().none { it.id !in beforeRefresh }) {
                SystemClock.sleep(100)
            }
            assertTrue("Manual refresh must still enqueue work",
                workManager.getWorkInfosByTag("widget-$id").get().any { it.id !in beforeRefresh })
        } finally {
            host.stopListening()
            host.deleteAppWidgetId(id)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
