package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test
class StageANavigationTest {
    @Test fun freshLauncherAlwaysShowsProjectsWithSelectionPreserved(){val n=NavigationState.freshLaunch("animaquiz");assertEquals("list",n.route);assertEquals("projects",n.section);assertEquals("animaquiz",n.project)}
    @Test fun freshLauncherWithoutSelectionShowsProjects(){assertEquals("list",NavigationState.freshLaunch("").route)}
    @Test fun explicitProjectOpenAndBackPreservesSelection(){val n=NavigationState.freshLaunch("old").openProject("chosen").back()!!;assertEquals("list",n.route);assertEquals("chosen",n.project)}
    @Test fun rotationDoesNotReapplyNotificationTarget(){assertEquals("",NavigationState.notificationTarget("job",true));assertEquals("job",NavigationState.notificationTarget("job",false))}
    @Test fun notificationJournalReturnsToItsProjectHistory(){val n=NavigationState.freshLaunch("animaquiz").openProject("animaquiz").journal("job").back()!!;assertEquals("history",n.route);assertEquals("animaquiz",n.project)}
    @Test fun branchViewerReturnsToSameProject(){val n=NavigationState().openProject("chosen").tool("branches").back()!!;assertEquals("workspace",n.route);assertEquals("chosen",n.project)}
    @Test fun settingsFromBranchViewerPreservesContext(){val n=NavigationState().openProject("chosen").tool("branches").settings().back()!!;assertEquals("branches",n.route);assertEquals("chosen",n.project)}
}
