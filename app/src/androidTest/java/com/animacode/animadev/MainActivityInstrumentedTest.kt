package com.animacode.animadev

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Device smoke test: entry activity starts without a Termux / GitHub backend. */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    @Test fun appLaunchesAndResumesOnDevice() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.animacode.animadev",context.packageName)
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            scenario.onActivity {activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
                assertEquals("com.animacode.animadev",activity.packageName)
            }
        }
    }
}
