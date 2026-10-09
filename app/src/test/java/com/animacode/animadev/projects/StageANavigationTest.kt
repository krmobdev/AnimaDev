package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test
class StageANavigationTest {
    @Test fun branchViewerReturnsToSameProject(){val n=NavigationState().openProject("chosen").tool("branches").back()!!;assertEquals("workspace",n.route);assertEquals("chosen",n.project)}
    @Test fun settingsFromBranchViewerPreservesContext(){val n=NavigationState().openProject("chosen").tool("branches").settings().back()!!;assertEquals("branches",n.route);assertEquals("chosen",n.project)}
}
