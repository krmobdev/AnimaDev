package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test
class ProjectPolicyTest {
    @Test fun onlyRegisteredQuizCanExecute() { listOf("STATUS","CHECK","SYNC","TEST").forEach { assertTrue(ProjectPolicy.canExecute("animaquiz",ProjectPolicy.QUIZ,it)) } }
    @Test fun otherProjectCannotFallBackToQuiz() { assertFalse(ProjectPolicy.canExecute("other",ProjectPolicy.QUIZ,"TEST"));assertFalse(ProjectPolicy.canExecute("animaquiz",ProjectPolicy.HOME+"/other","SYNC")) }
    @Test fun noBuildOrShellAction() { listOf("BUILD","SHELL","PUSH","RESET","REBASE").forEach { assertFalse(ProjectPolicy.canExecute("animaquiz",ProjectPolicy.QUIZ,it)) } }
    @Test fun canonicalShapedPathsOnly() { assertTrue(ProjectPolicy.permittedPath(ProjectPolicy.QUIZ));assertTrue(ProjectPolicy.permittedPath(ProjectPolicy.HOME+"/anima-workspace/AnimaDev")) }
    @Test fun traversalAndProtectedDirectoriesDenied() { listOf("/.ssh","/storage/project","/android-sdk","/foo/../quizapp","//quizapp","/quizapp/").forEach { assertFalse(ProjectPolicy.permittedPath(ProjectPolicy.HOME+it)) } }
    @Test fun arbitraryDeepPathDenied() { assertFalse(ProjectPolicy.permittedPath(ProjectPolicy.HOME+"/other/nested"));assertFalse(ProjectPolicy.permittedPath(ProjectPolicy.HOME+"/anima-workspace/a/b")) }
    @Test fun outsideHomeDenied() { assertFalse(ProjectPolicy.permittedPath("/storage/emulated/0/project")) }
    @Test fun selectionRetainedIfRegistered() { assertEquals("p2",ProjectPolicy.nextSelection(listOf("p1","p2"),"p2")) }
    @Test fun removedSelectionDoesNotPickDifferentProject() { assertEquals("",ProjectPolicy.nextSelection(listOf("p1"),"p2")) }
    @Test fun validIdentityAndPathPairRequired() { assertFalse(ProjectPolicy.validId("animaquiz",ProjectPolicy.HOME+"/other"));assertFalse(ProjectPolicy.validId("a".repeat(32),ProjectPolicy.QUIZ)) }
    @Test fun selectionSurvivesNewController() {
        var saved="";val storage=object:SelectionStorage { override fun load()=saved;override fun save(id:String):Boolean { saved=id;return true } }
        assertTrue(ProjectSelection(storage).choose(listOf("p1","p2"),"p2"));assertEquals("p2",ProjectSelection(storage).read(listOf("p1","p2")))
    }
    @Test fun rejectedSelectionDoesNotOverwriteStoredId() {
        var saved="p1";val storage=object:SelectionStorage { override fun load()=saved;override fun save(id:String):Boolean { saved=id;return true } }
        assertFalse(ProjectSelection(storage).choose(listOf("p1"),"p2"));assertEquals("p1",saved)
    }
    @Test fun futureBuildModelHasNoExecutionApi() { val c=BuildConfiguration("p1","commit","debug",CacheMode.RERUN_NO_BUILD_CACHE,"Downloads","app.apk",BuildKind.DEBUG_APK);assertEquals("p1",c.projectId);assertEquals(3,CacheMode.entries.size);assertEquals(5,BuildKind.entries.size) }
}
