package com.valerie.meteoquote.ui

import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.valerie.meteoquote.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp-mdpi")
class CityActionsLayoutTest {
    @Test fun visibleActionLabelsFitSmallScreenAtDoubleFontSize() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(base.resources.configuration).apply { fontScale = 2f }
        val context = ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.Theme_MeteoQuote)
        val root = LayoutInflater.from(context).inflate(R.layout.activity_main, null)
        val width = (288 * context.resources.displayMetrics.density).toInt()
        for ((id, label) in listOf(R.id.btnLocate to "Ma position", R.id.btnManageCities to "Gérer mes villes")) {
            val button = root.findViewById<TextView>(id)
            assertEquals(label, button.text.toString())
            button.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            button.layout(0, 0, button.measuredWidth, button.measuredHeight)
            assertTrue(button.measuredHeight >= 48 * context.resources.displayMetrics.density)
            assertTrue(button.layout.height <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
            for (line in 0 until button.layout.lineCount) {
                assertEquals(0, button.layout.getEllipsisCount(line))
                assertTrue(button.layout.getLineWidth(line) <= button.width - button.compoundPaddingLeft - button.compoundPaddingRight)
            }
        }
    }
}
