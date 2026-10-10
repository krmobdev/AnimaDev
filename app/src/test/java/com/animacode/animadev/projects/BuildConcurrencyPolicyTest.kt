package com.animacode.animadev.projects

import org.junit.Assert.*
import org.junit.Test

class BuildConcurrencyPolicyTest {
    @Test fun debugBuildBlocksReleaseAcrossProjects() {
        assertEquals("AnimaQuiz",BuildConcurrencyPolicy.owner("AnimaQuiz","a".repeat(32),"RUNNING","BUILD_DEBUG","Other",""))
        assertEquals("AnimaQuiz",BuildConcurrencyPolicy.owner("AnimaQuiz","a".repeat(32),"UNKNOWN","BUILD_DEBUG","Other",""))
    }
    @Test fun releaseBlocksDebugSameAndOtherProject() {
        assertEquals("AnimaQuiz",BuildConcurrencyPolicy.owner("Other","","FAILED","BUILD_DEBUG","AnimaQuiz","REQUESTED"))
        assertTrue(BuildConcurrencyPolicy.waitingMessage("AnimaQuiz","Other").contains("AnimaQuiz"))
        assertTrue(BuildConcurrencyPolicy.waitingMessage("AnimaQuiz","AnimaQuiz").contains("этого проекта"))
    }
    @Test fun completedBuildAllowsNext() {
        assertNull(BuildConcurrencyPolicy.owner("A","a".repeat(32),"SUCCEEDED","BUILD_DEBUG","A","SUCCEEDED"))
        assertNull(BuildConcurrencyPolicy.owner("A","a".repeat(32),"FAILED","BUILD_DEBUG","A","FAILED"))
    }
    @Test fun unknownStatusMustNotUnlockBuild() {
        assertTrue(BuildConcurrencyPolicy.releaseBusy("UNKNOWN"))
        assertTrue(BuildConcurrencyPolicy.repoBusy("a".repeat(32),"UNKNOWN","BUILD_DEBUG"))
        assertFalse(BuildConcurrencyPolicy.repoBusy("","RUNNING","BUILD_DEBUG"))
    }
    @Test fun gitStatusNotCountedAsBuild() {
        assertNull(BuildConcurrencyPolicy.owner("A","a".repeat(32),"RUNNING","CHECK","A","FAILED"))
    }
}
