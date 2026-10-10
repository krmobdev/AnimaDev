package com.animacode.animadev.projects

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animacode.animadev.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real activity/settings, not a separately themed preview. */
@RunWith(AndroidJUnit4::class)
class AppearanceThemeInstrumentedTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun chooseTheme(label:String) {
        compose.onNodeWithText("Тема").performClick()
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
    }
    private fun assertLight(light:Boolean) {
        val pixels=compose.onRoot().captureToImage().toPixelMap()
        val brightness=pixels[5,pixels.height-20].luminance()
        assertTrue("Actual app background luminance=$brightness; light=$light",
            if(light)brightness>0.8f else brightness<0.2f)
    }
    @Test fun pulseHonoursThemeImmediatelyAndAfterActivityRecreation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        // This suite runs only in the dedicated per-job application.
        context.getSharedPreferences("interface_v1",0).edit()
            .putBoolean("pulse",true).putString("theme","dark").commit()
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            compose.onNodeWithContentDescription("Меню приложения").performClick()
            compose.onNodeWithText("Настройки").performClick()
            compose.onNodeWithText("Внешний вид").performClick()
            compose.onAllNodes(isToggleable())[0].assertIsOn()
            assertLight(false)
            chooseTheme("Светлая")
            assertLight(true)
            scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithText("Светлая").assertIsDisplayed()
            compose.onAllNodes(isToggleable())[0].assertIsOn()
            assertLight(true)
            // Turning Pulse off and on must also preserve the chosen light mode.
            compose.onAllNodes(isToggleable())[0].performClick().assertIsOff()
            assertLight(true)
            compose.onAllNodes(isToggleable())[0].performClick().assertIsOn()
            assertLight(true)
            chooseTheme("Тёмная")
            assertLight(false)
            scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithText("Тёмная").assertIsDisplayed()
            assertLight(false)
        }
    }
}
