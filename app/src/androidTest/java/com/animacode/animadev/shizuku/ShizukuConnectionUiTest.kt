package com.animacode.animadev.shizuku

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.animacode.animadev.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.runner.RunWith

/** Does not request or silently grant Android shell permissions. */
@RunWith(AndroidJUnit4::class)
class ShizukuConnectionUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun showsOptionalShizukuCardInsideTermux() {
        composeRule.onAllNodesWithText("Termux").onFirst().performClick()
        composeRule.onNodeWithTag("shizuku_connection_card").assertExists()
        composeRule.onNodeWithTag("shizuku_refresh").performClick()
        composeRule.onNodeWithTag("shizuku_ping").assertExists()
        val refreshBounds = composeRule.onNodeWithTag("shizuku_refresh")
            .fetchSemanticsNode().boundsInRoot
        val pingBounds = composeRule.onNodeWithTag("shizuku_ping")
            .fetchSemanticsNode().boundsInRoot
        val cardBounds = composeRule.onNodeWithTag("shizuku_connection_card")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Ping must be to the right of the check button", pingBounds.left >= refreshBounds.right)
        assertTrue("Ping must be vertically centred beside the button",
            abs(pingBounds.center.y - refreshBounds.center.y) <= refreshBounds.height / 4f)
        assertTrue("Ping must stay inside the Shizuku card", pingBounds.right <= cardBounds.right)

    }
}
