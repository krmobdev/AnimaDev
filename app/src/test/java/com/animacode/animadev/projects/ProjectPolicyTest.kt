package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test
class ProjectPolicyTest {
    @Test fun catalogIdentityEnablesOnlyExactGitHubProject() {
        val path=ProjectPolicy.HOME+"/anima-workspace/AnimaNotes"
        val id=ProjectPolicy.stableId(path)
        assertTrue(ProjectPolicy.validId(id,path))
        assertTrue(ProjectPolicy.canExecute(id,path,"STATUS",false,"github.com/krmobdev/AnimaNotes"))
        assertTrue(ProjectPolicy.canExecute(id,path,"BUILD_DEBUG",false,"github.com/krmobdev/AnimaNotes"))
        assertFalse(ProjectPolicy.canExecute(id,path,"TEST",true,"github.com/krmobdev/AnimaNotes"))
        assertFalse(ProjectPolicy.canExecute(id,path,"TEST",false,"https://evil.invalid/thing"))
        assertFalse(ProjectPolicy.canExecute("a".repeat(32),path,"TEST",false,"github.com/krmobdev/AnimaNotes"))
    }
    @Test fun localProjectAllowsFixedJobsWithoutOriginButNeverSync() {
        val path=ProjectPolicy.HOME+"/anima-workspace/FlyLife"
        val id=ProjectPolicy.stableId(path)
        assertTrue(ProjectPolicy.localOnly(id,path))
        assertTrue(ProjectPolicy.canExecute(id,path,"STATUS"))
        assertTrue(ProjectPolicy.canExecute(id,path,"CHECK"))
        assertTrue(ProjectPolicy.canExecute(id,path,"TEST"))
        assertTrue(ProjectPolicy.canExecute(id,path,"TEST_DEVICE"))
        assertTrue(ProjectPolicy.canExecute(id,path,"BUILD_DEBUG"))
        assertFalse(ProjectPolicy.canExecute(id,path,"SYNC"))
        assertFalse(ProjectPolicy.canExecute(id,path,"TEST",true))
        assertFalse(ProjectPolicy.canExecute(id,path,"TEST",false,"https://untrusted.invalid/repo"))
        assertFalse(ProjectPolicy.canExecute("bad",path,"BUILD_DEBUG"))
    }
    @Test fun onlyRegisteredQuizCanExecute() { listOf("STATUS","CHECK","SYNC","TEST","TEST_DEVICE").forEach { assertTrue(ProjectPolicy.canExecute("animaquiz",ProjectPolicy.QUIZ,it)) } }
    @Test fun instrumentationRequiresTrustedProjectButNotOtherPrivilege() {
        assertTrue(ProjectPolicy.canExecute("animaquiz",ProjectPolicy.QUIZ,"TEST_DEVICE"))
        val path=ProjectPolicy.HOME+"/anima-workspace/AnimaDev"
        val id=ProjectPolicy.stableId(path)
        assertTrue(ProjectPolicy.canExecute(id,path,"TEST_DEVICE",false,"github.com/krmobdev/AnimaDev"))
        assertFalse(ProjectPolicy.canExecute(id,path,"TEST_DEVICE",true,"github.com/krmobdev/AnimaDev"))
        assertFalse(ProjectPolicy.canExecute(id,path,"TEST_DEVICE",false,"https://evil.invalid/repo"))
        assertFalse(ProjectPolicy.canExecute("animaquiz",ProjectPolicy.QUIZ,"SHELL"))
    }
    @Test fun otherProjectCannotFallBackToQuiz() { assertFalse(ProjectPolicy.canExecute("other",ProjectPolicy.QUIZ,"TEST"));assertFalse(ProjectPolicy.canExecute("animaquiz",ProjectPolicy.HOME+"/other","SYNC")) }
    @Test fun noBuildOrShellAction() { listOf("BUILD","SHELL","PUSH","RESET","REBASE").forEach { assertFalse(ProjectPolicy.canExecute("animaquiz",ProjectPolicy.QUIZ,it)) } }
    @Test fun canonicalShapedPathsOnly() { assertTrue(ProjectPolicy.permittedPath(ProjectPolicy.QUIZ));assertTrue(ProjectPolicy.permittedPath(ProjectPolicy.HOME+"/anima-workspace/AnimaDev")) }
    @Test fun traversalAndProtectedDirectoriesDenied() { listOf("/.ssh","/storage/project","/android-sdk","/foo/../quizapp","//quizapp","/quizapp/").forEach { assertFalse(ProjectPolicy.permittedPath(ProjectPolicy.HOME+it)) } }
    @Test fun nestedPathAllowed() { assertTrue(ProjectPolicy.permittedPath(ProjectPolicy.HOME+"/other/nested"));assertTrue(ProjectPolicy.permittedPath(ProjectPolicy.HOME+"/anima-workspace/a/b")) }
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
    @Test fun depthLimit() { assertTrue(ProjectPolicy.permittedPath(ProjectPolicy.HOME+"/a/b/c/d/e/f"));assertFalse(ProjectPolicy.permittedPath(ProjectPolicy.HOME+"/a/b/c/d/e/f/g")) }
    @Test fun nestedServicePathDenied() { assertFalse(ProjectPolicy.permittedPath(ProjectPolicy.HOME+"/projects/credentials/repo")) }
    @Test fun worktreeProfileCannotExecute() { assertFalse(ProjectPolicy.canExecute("animaquiz",ProjectPolicy.QUIZ,"TEST",true));assertFalse(ProjectPolicy.canExecute("animaquiz",ProjectPolicy.QUIZ,"SYNC",true)) }
}
