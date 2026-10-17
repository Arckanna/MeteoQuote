package com.valerie.meteoquote

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualCityTest {
    @Test fun launchesAndOffersManualSearchWithoutLocationPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<android.widget.EditText>(R.id.etCitySearch).isShown)
                assertTrue(activity.findViewById<android.widget.Button>(R.id.btnAddCity).isEnabled)
                assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            scenario.recreate()
            scenario.onActivity { assertTrue(it.findViewById<android.widget.Button>(R.id.btnAddCity).isEnabled) }
        }
    }
}
