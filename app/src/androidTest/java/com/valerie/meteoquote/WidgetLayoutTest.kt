package com.valerie.meteoquote

import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valerie.meteoquote.widget.WidgetLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WidgetLayoutTest {
    @Test fun remoteViewsFitCompactExpandedAndLargeTextSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for ((name, width, height, font) in listOf(
                Profile("tiny", 180, 60, 1f),
                Profile("compact", 180, 80, 1f),
                Profile("expanded", 280, 160, 1f),
                Profile("large-text", 320, 160, 2f))) {
                val configuration = Configuration(instrumentation.targetContext.resources.configuration)
                    .apply { fontScale = font }
                val context = instrumentation.targetContext.createConfigurationContext(configuration)
                val density = context.resources.displayMetrics.density
                val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height) }
                val showQuote = WidgetLayout.quoteFits(context, options)
                val remote = RemoteViews(context.packageName, R.layout.widget_weather).apply {
                    setTextViewText(R.id.widget_city, "Saint-Remy-en-Bouzemont-Saint-Genest-et-Isson")
                    setTextViewText(R.id.widget_temp, "−12°")
                    setTextViewText(R.id.widget_condition, "Plutôt nuageux")
                    setTextViewText(R.id.widget_quote, "Au-dessus des nuages, le ciel est toujours bleu. — Proverbe")
                    WidgetLayout.apply(context, options, this, hasQuote = true)
                }
                val root = remote.apply(context, null) as ViewGroup
                val w = (width * density).toInt()
                val h = (height * density).toInt()
                root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, w, h)
                assertBounds(root)
                val temp = root.findViewById<TextView>(R.id.widget_temp)
                assertTrue("Temperature must fit $name", temp.layout.getLineMax(0) <= temp.width)
                val refresh = root.findViewById<View>(R.id.widget_refresh)
                assertTrue(refresh.width >= 48 * density - 1)
                assertTrue(refresh.height >= 48 * density - 1)
                assertEquals(name == "expanded", showQuote)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "widget-review").apply { mkdirs() }
                File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    private fun assertBounds(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            assertTrue("Widget content clipped vertically", child.top >= 0 && child.bottom <= parent.height)
            assertTrue("Widget content clipped horizontally", child.left >= 0 && child.right <= parent.width)
            if (child is ViewGroup) assertBounds(child)
        }
    }

    private data class Profile(val name: String, val width: Int, val height: Int, val font: Float)
}
