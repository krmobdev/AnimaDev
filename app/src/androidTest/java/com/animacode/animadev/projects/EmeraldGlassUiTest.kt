package com.animacode.animadev.projects

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI-only fixture: does not start Git, Termux or Gradle operations. */
@RunWith(AndroidJUnit4::class)
class EmeraldGlassUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun projectCardDisplaysActualGitMetadataAndOpens() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        check(ctx.packageName.contains(".animadevtests."))
        var opened=false
        val project=LocalProject("animaquiz","AnimaQuiz",ProjectPolicy.QUIZ,
            "main","a1b2c3d123456789","git@github.com:krmobdev/quizapp.git",false)
        compose.setContent {
            CompositionLocalProvider(LocalNeonSkin provides true) {
                MaterialTheme(colorScheme=neonColorScheme()) {
                    NeonProjectCard(project,"main",false,false,
                        onClick={opened=true},onMore={})
                }
            }
        }
        compose.onNodeWithText("AnimaQuiz").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertTrue(opened)
        compose.onNodeWithText("Clean").assertIsDisplayed()
        compose.onNodeWithText("main").assertIsDisplayed()
    }

    @Test fun buildEntryHasWorkingCallback() {
        var opened=false
        compose.setContent {
            MaterialTheme(colorScheme=neonColorScheme()) {
                NeonPrimaryAction("Build Now","Latest commit · a1b2c3d",
                    onClick={opened=true})
            }
        }
        compose.onNodeWithText("Build Now").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertTrue(opened)
    }
}
