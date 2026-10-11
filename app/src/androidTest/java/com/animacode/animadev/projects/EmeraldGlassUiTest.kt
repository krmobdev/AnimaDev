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
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
        compose.waitForIdle()
        compose.onNodeWithText("AnimaQuiz").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertTrue(opened)
        compose.waitForIdle()
        // The project tile is clickable and merges semantics in the default tree.
        // Check the real status and branch Text nodes in the unmerged tree.
        // The system locale can be Russian or English. The UI translates
        // the git status chip; both variants must actually be visible.
        val statusText=if(compose.onAllNodesWithText(
                "Чисто",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
            ) "Чисто" else "Clean"
        compose.onNodeWithText(statusText,useUnmergedTree=true).assertIsDisplayed()
        compose.waitForIdle()
        compose.onNodeWithText("main",useUnmergedTree=true).assertIsDisplayed()
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
        compose.waitForIdle()
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
        compose.waitForIdle()
        compose.onNodeWithText("Termux").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertTrue(termux)
    }

    @Test fun worktreesRemainCollapsedUntilRequestedAndToolsStayVisible() {
        val project=LocalProject("animaquiz","AnimaQuiz",ProjectPolicy.QUIZ,
            "main","7d3faa2",dirty=false)
        compose.setContent {
            var expanded by remember {mutableStateOf(false)}
            MaterialTheme(colorScheme=neonColorScheme()) {
                Column {
                    Text("Инструменты")
                    EmeraldWorktreeOverview(
                        root=project,relatedCount=21,permitted=true,
                        pending=false,scanned=true,expanded=expanded,
                        onToggle={expanded=!expanded},onRefresh={}
                    )
                    if(expanded)Text("test/another-worktree")
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Инструменты").assertIsDisplayed()
        compose.onNodeWithText("Показать каталоги (21)").assertIsDisplayed()
        compose.onNodeWithText("test/another-worktree").assertDoesNotExist()
        compose.onNodeWithText("Показать каталоги (21)").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Скрыть список").assertIsDisplayed()
        compose.onNodeWithText("test/another-worktree").assertIsDisplayed()
        compose.onNodeWithText("Скрыть список").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("test/another-worktree").assertDoesNotExist()
    }

    @Test fun buildEntryHasWorkingCallback() {
        var opened=false
        compose.setContent {
            MaterialTheme(colorScheme=neonColorScheme()) {
                NeonPrimaryAction("Build Now","Latest commit · a1b2c3d",
                    onClick={opened=true})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Build Now").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertTrue(opened)
    }
}
