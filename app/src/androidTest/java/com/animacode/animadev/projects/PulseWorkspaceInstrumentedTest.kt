package com.animacode.animadev.projects

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import com.animacode.animadev.repos.StopJobButton
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs with on-device Compose semantics. No connection to GitHub or Termux. */
@RunWith(AndroidJUnit4::class)
class PulseWorkspaceInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun activeMainBranchIsVisibleAndOldProjectControlLabelIsAbsent() {
        compose.setContent {
            MaterialTheme {
                ActiveBranchHero(branch="main",sha="1ecd10ca410c",dirty=false)
            }
        }
        compose.onNodeWithText("main").assertIsDisplayed()
        compose.onNodeWithText("Активная ветка").assertIsDisplayed()
        compose.onNodeWithText("PROJECT CONTROL").assertDoesNotExist()
    }

    @Test fun stopButtonStaysEnabledDuringAutomaticQueries() {
        val pending=mutableStateOf(false)
        var clicks=0
        compose.setContent {MaterialTheme {
            StopJobButton(true,pending.value,"QUERY",false) {clicks++}
        }}
        repeat(8) {
            compose.runOnIdle {pending.value=!pending.value}
            compose.onNodeWithText("Остановить · Ctrl+C").assertIsDisplayed().assertIsEnabled()
        }
        compose.onNodeWithText("Остановить · Ctrl+C").performClick()
        compose.runOnIdle {assertEquals(1,clicks)}
    }

    @Test fun queuedStopDoesNotAllowDuplicateClicks() {
        compose.setContent {MaterialTheme {StopJobButton(true,true,"QUERY",true) {error("duplicate")}}}
        compose.onNodeWithText("Отправляем остановку…").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun heroRendersInDarkTheme() {
        compose.setContent {MaterialTheme(colorScheme=darkColorScheme()) {
            ActiveBranchHero("feature/termux-probe","123456789abc",false)
        }}
        compose.onNodeWithText("feature/termux-probe").assertIsDisplayed()
    }

    @Test fun heroRendersInLightTheme() {
        compose.setContent {MaterialTheme(colorScheme=lightColorScheme()) {
            ActiveBranchHero("feature/termux-probe","123456789abc",true)
        }}
        compose.onNodeWithText("feature/termux-probe").assertIsDisplayed()
    }

    @Test fun branchHeroRendersLongBranchWithoutThrowing() {
        compose.setContent {
            MaterialTheme {
                ActiveBranchHero(branch="test/decomposition-v1",
                    sha="2b4a66829bcc",dirty=true)
            }
        }
        compose.onNodeWithText("test/decomposition-v1").assertIsDisplayed()
        compose.onNodeWithText("PROJECT CONTROL").assertDoesNotExist()
    }
}
