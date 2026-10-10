package com.animacode.animadev.projects

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
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
        compose.onNodeWithText("●  ACTIVE").assertIsDisplayed()
        compose.onNodeWithText("PROJECT CONTROL").assertDoesNotExist()
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
