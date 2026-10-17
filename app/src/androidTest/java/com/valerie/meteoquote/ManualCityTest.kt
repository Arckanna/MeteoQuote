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
    @Test fun decliningLocationLeavesManualSearchAvailable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<android.widget.Button>(R.id.btnLocate).performClick() }
            var dismissed = false
            for (attempt in 0 until 20) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                val deny = root?.findAccessibilityNodeInfosByViewId(
                    "com.android.permissioncontroller:id/permission_deny_button")?.firstOrNull()
                val later = root?.findAccessibilityNodeInfosByText("Plus tard")?.firstOrNull()
                val cancel = root?.findAccessibilityNodeInfosByText("Annuler")?.firstOrNull()
                val target = deny ?: later ?: cancel
                if (target != null) {
                    target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    dismissed = true
                    break
                }
                android.os.SystemClock.sleep(150)
            }
            assertTrue("Location permission dialog was not found", dismissed)
            instrumentation.waitForIdleSync()
            // A permanent denial can open the existing explanatory dialog.
            instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText("Plus tard")?.firstOrNull()
                ?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            scenario.onActivity {
                assertEquals(PackageManager.PERMISSION_DENIED, it.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
                assertTrue(it.findViewById<android.widget.EditText>(R.id.etCitySearch).isEnabled)
                assertTrue(it.findViewById<android.widget.Button>(R.id.btnAddCity).isEnabled)
            }
        }
    }

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
