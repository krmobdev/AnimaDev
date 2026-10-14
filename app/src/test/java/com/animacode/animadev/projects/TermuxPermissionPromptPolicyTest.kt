package com.animacode.animadev.projects

import org.junit.Assert.*
import org.junit.Test

class TermuxPermissionPromptPolicyTest {
    private fun show(resumed:Boolean=true,granted:Boolean=false,installed:Boolean=true,
                     skipped:Boolean=false,section:String="projects",route:String="git",
                     supported:Boolean=true)=TermuxPermissionPromptPolicy.shouldPrompt(
                         resumed,granted,installed,skipped,section,route,supported)

    @Test fun promptsForLocalGitAndBuildWhenPermissionMissing() {
        assertTrue(show(route="git"))
        assertTrue(show(route="workspace"))
        assertTrue(show(route="build"))
        assertTrue(show(route="tests"))
        assertTrue(show(route="branches"))
        assertTrue(show(section="termux",route="list",supported=false))
    }
    @Test fun neverLoopsAndNeverRequestsOutsideRelevantScreens() {
        assertFalse(show(granted=true))
        assertFalse(show(installed=false))
        assertFalse(show(skipped=true))
        assertFalse(show(resumed=false))
        assertFalse(show(route="list"))
        assertFalse(show(route="settings"))
        assertFalse(show(route="git",supported=false))
        assertFalse(show(section="settings"))
    }
}
