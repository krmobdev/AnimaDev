package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test
class NavigationPolicyTest {
    @Test fun buildBackReturnsToProjectNotExit() {
        val previous=NavigationState().openProject("animaquiz").tool("build").back()
        assertNotNull(previous)
        assertEquals("workspace",previous!!.route)
        assertEquals("animaquiz",previous.project)
        assertEquals("projects",previous.section)
    }
    @Test fun toolBackKeepsProject() { val n=NavigationState().openProject("one").tool("tests").back()!!;assertEquals("workspace",n.route);assertEquals("one",n.project) }
    @Test fun rootSwitchClearsTool() { val n=NavigationState().openProject("one").tool("git").switchRoot("termux").back()!!;assertEquals("list",n.route);assertEquals("projects",n.section);assertEquals("one",n.project) }
    @Test fun journalFromTestsReturnsToTests() { val n=NavigationState().openProject("one").tool("tests").journal("owned").back()!!;assertEquals("tests",n.route);assertEquals("one",n.project);assertEquals("",n.job) }
    @Test fun journalOpenedFromBuildReturnsToBuild() {
        val n=NavigationState(section="projects",route="journal",project="animaquiz",
            job="active",journalParent="build").back()!!
        assertEquals("build",n.route)
        assertEquals("animaquiz",n.project)
        assertEquals("",n.job)
    }
    @Test fun journalOpenedFromWorkspaceReturnsToWorkspace() {
        val n=NavigationState(section="projects",route="journal",project="animaquiz",
            job="owned",journalParent="workspace").back()!!
        assertEquals("workspace",n.route)
        assertEquals("",n.job)
    }
    @Test fun testTabsDoNotChangeJournalBackTarget() {
        val n=NavigationState(section="projects",route="journal",project="animaquiz",
            job="deviceTest",journalParent="tests").back()!!
        assertEquals("tests",n.route)
        assertEquals("animaquiz",n.project)
    }
    @Test fun journalFromHistoryReturnsToHistory() { val n=NavigationState().openProject("one").tool("history").journal("owned").back()!!;assertEquals("history",n.route) }
    @Test fun notificationJournalDefaultsToHistory() { val n=NavigationState().openProject("one").journal("owned").back()!!;assertEquals("history",n.route) }
    @Test fun journalFromGitReturnsToGit() { val n=NavigationState().openProject("one").tool("git").journal("owned").back()!!;assertEquals("git",n.route) }
    @Test fun settingsReturnToPreviousTool() { assertEquals("git",NavigationState().openProject("one").tool("git").settings().back()!!.route) }
    @Test fun termuxSettingsReturnToTermux() { val n=NavigationState().switchRoot("termux").settings().back()!!;assertEquals("termux",n.section);assertTrue(n.root) }
    @Test fun rootBackRequestsExit() { assertNull(NavigationState().back()) }
    @Test fun workspaceHasNoBottomNavigation() { assertFalse(NavigationState().openProject("one").root) }
    @Test fun openingAnotherProjectClearsJournal() { val n=NavigationState().openProject("one").journal("old").openProject("two");assertEquals("two",n.project);assertEquals("",n.job);assertEquals("workspace",n.route) }
    @Test fun copiedSavedFieldsRestoreContext() { val before=NavigationState().openProject("one").tool("tests").journal("owned");val after=NavigationState(before.section,before.route,before.project,before.job,before.settingsParent,before.journalParent);assertEquals(before,after);assertEquals("tests",after.back()!!.route) }
    @Test fun reopeningSettingsPreservesBackTarget() { assertEquals("git",NavigationState().openProject("one").tool("git").settings().settings().back()!!.route) }
    @Test fun recreationDoesNotReplayNotificationNavigation() { assertEquals("",NavigationState.notificationTarget("owned",true));assertEquals("owned",NavigationState.notificationTarget("owned",false)) }
}
