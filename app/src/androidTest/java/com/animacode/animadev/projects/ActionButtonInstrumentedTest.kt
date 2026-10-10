package com.animacode.animadev.projects
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionButtonInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private fun check(light:Boolean) {
        var clicks=0
        var alignment:TextAlign?=null
        compose.setContent {
            MaterialTheme(colorScheme=if(light)lightColorScheme() else darkColorScheme()) {
                Box(Modifier.width(280.dp)) {
                    ActionButton(onClick={clicks++},modifier=Modifier.testTag("action")) {
                        alignment=LocalTextStyle.current.textAlign
                        Text("Обновить помощник")
                    }
                }
            }
        }
        compose.onNodeWithTag("action").assertIsDisplayed().assertIsEnabled()
            .assertWidthIsEqualTo(280.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {assertEquals(1,clicks);assertEquals(TextAlign.Center,alignment)}
    }
    @Test fun helperButtonIsCentredAndClickableInDarkTheme()=check(false)
    @Test fun helperButtonIsCentredAndClickableInLightTheme()=check(true)
    @Test fun disabledHelperDoesNotStartInstallation() {
        compose.setContent {MaterialTheme {ActionButton(onClick={error("disabled click")},enabled=false) {Text("Обновить помощник")}}}
        compose.onNodeWithText("Обновить помощник").assertIsNotEnabled().performClick()
    }
}
