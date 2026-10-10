package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test

class OperationPolicyTest {
    @Test fun resultsCannotCrossProjectOrOperation() {
        assertTrue(OperationResultPolicy.matches("animaquiz","animaquiz","TEST","TEST"))
        assertFalse(OperationResultPolicy.matches("animaquiz","animadev","TEST","TEST"))
        assertFalse(OperationResultPolicy.matches("animaquiz","animaquiz","BUILD_DEBUG","TEST_DEVICE"))
    }
    @Test fun onlyVerifiedDownloadApkOrAabCanBeOpened() {
        val sha="a".repeat(64)
        assertTrue(ArtifactPolicy.valid("/storage/emulated/0/Download/App_debug.apk",sha,2048))
        assertTrue(ArtifactPolicy.valid("/storage/emulated/0/Download/App_release.aab",sha,2048))
        listOf("/data/local/tmp/App.apk","/storage/emulated/0/Download/sub/App.apk",
            "/storage/emulated/0/Download/App.apk;bad","/storage/emulated/0/Download/App.zip").forEach {
            assertFalse(ArtifactPolicy.valid(it,sha,2048))
        }
        assertFalse(ArtifactPolicy.valid("/storage/emulated/0/Download/App.apk","bad",2048))
        assertFalse(ArtifactPolicy.valid("/storage/emulated/0/Download/App.apk",sha,0))
    }
}
