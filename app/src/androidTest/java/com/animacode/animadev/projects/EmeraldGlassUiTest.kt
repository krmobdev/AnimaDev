package com.animacode.animadev.projects

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
        check(ctx.packageName == "com.animacode.animadev.devicefixture") {
            "Refusing to run Emerald UI fixture against installed AnimaDev"
        }
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

    @Test fun compactTabsRespondToSelectionAtLargeFontScale() {
        var chosen=-1
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,1.5f)) {
                MaterialTheme(colorScheme=neonColorScheme()) {
                    var selected by remember {mutableIntStateOf(0)}
                    NeonTabs(
                        labels=listOf("All","Tests","Build jobs","Git"),
                        selected=selected,onSelect={index->selected=index;chosen=index})
                }
            }
        }
        compose.onNodeWithText("Tests").assertIsDisplayed().performClick()
        compose.waitForIdle()
        org.junit.Assert.assertEquals(1,chosen)
    }

    @Test fun bottomNavigationRemainsClickableWithLargeFont() {
        var termux=false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,1.45f)) {
                MaterialTheme(colorScheme=neonColorScheme()) {
                    NeonBottomBar(onProjects={},onTermux={termux=true},
                        projectsSelected=true)
                }
            }
        }
        compose.onNodeWithText("Termux").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertTrue(termux)
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
