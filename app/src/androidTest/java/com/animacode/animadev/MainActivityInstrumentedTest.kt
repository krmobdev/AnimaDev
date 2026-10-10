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
        // Fresh per-job IDs protect the owner's installed application and secrets.
        assertTrue(context.packageName.matches(Regex(
            "com\\\\.animacode\\\\.animadev(?:\\\\.devicefixture)?(?:\\\\.animadevtests\\\\.j[a-f0-9]{32})?")))
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            scenario.onActivity {activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
                assertEquals(context.packageName,activity.packageName)
            }
        }
    }
}
