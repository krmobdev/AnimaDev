package com.animacode.animadev.repos
import org.junit.Assert.*
import org.junit.Test
class RepoPolicyTest {
    private fun sync(branch:String="test/decomposition-v1", dirty:Boolean=false, remote:Boolean=true, ahead:Int=0, behind:Int=1, checked:Boolean=true, runner:Boolean=true, blocked:Boolean=false) = RepoPolicy.maySync(branch,dirty,remote,ahead,behind,checked,runner,blocked)
    @Test fun journalFullIsDeterministicOnlyWithConfirmedRunnerRefusal() {
        assertTrue(RepoPolicy.journalCapacityRejected("UNKNOWN",0,-1,"HISTORY_FULL",false))
        assertFalse(RepoPolicy.journalCapacityRejected("UNKNOWN",0,-1,"JOB_BUSY",false))
        assertFalse(RepoPolicy.journalCapacityRejected("UNKNOWN",-1,-1,"HISTORY_FULL",false))
        assertFalse(RepoPolicy.journalCapacityRejected("RUNNING",0,-1,"HISTORY_FULL",false))
        assertFalse(RepoPolicy.journalCapacityRejected("UNKNOWN",0,-1,"HISTORY_FULL",true))
        assertEquals("0.4.0",RepoPolicy.runnerVersion(
            "33e2668e27ed92ac12b0ea6170dfcf91fff4ea0af5d08380a1038679bf549507","0.4.1"))
    }
    @Test fun staleJobCanBeQueriedAfterHelperUpdate() {
        val id="a".repeat(32)
        val original="b".repeat(64)
        org.junit.Assert.assertTrue(RepoPolicy.canRecoverJob(false,id,original,"c".repeat(64)))
        org.junit.Assert.assertTrue(RepoPolicy.canRecoverJob(false,id,"",original))
        org.junit.Assert.assertFalse(RepoPolicy.canRecoverJob(true,id,original,original))
        org.junit.Assert.assertFalse(RepoPolicy.canRecoverJob(false,"",original,original))
        org.junit.Assert.assertFalse(RepoPolicy.canRecoverJob(false,id,"bad",original))
    }
    @Test fun dynamicProjectAllowedOnItsVerifiedBranch() {
        assertTrue(RepoPolicy.mayTest("feature/development",true,true,false,"another-project"))
        assertFalse(RepoPolicy.mayTest("Detached HEAD",true,true,false,"another-project"))
        assertTrue(RepoPolicy.mayBuild("feature/development",false,true,true,false,true,0,0,"abc","abc","another-project"))
        assertFalse(RepoPolicy.mayBuild("feature/development",true,true,true,false,true,0,0,"abc","abc","another-project"))
    }
    @Test fun localBuildAndTestsUseCheckedLocalHeadEvenWhenDirty() {
        val local="a".repeat(40)
        assertTrue(RepoPolicy.mayTest("feature/local",false,true,false,
            projectId="local",localOnly=true,checked=true,commit=local))
        assertTrue(RepoPolicy.mayBuild("feature/local",true,false,true,false,true,
            -1,-1,local,"",projectId="local",localOnly=true))
        assertFalse(RepoPolicy.mayBuild("feature/local",true,false,true,false,false,
            -1,-1,local,"",projectId="local",localOnly=true))
        assertFalse(RepoPolicy.mayTest("feature/local",false,true,false,
            projectId="local",localOnly=true,checked=false,commit=local))
        assertFalse(RepoPolicy.mayBuild("Detached HEAD",false,false,true,false,true,
            0,0,local,"",projectId="local",localOnly=true))
        assertFalse(RepoPolicy.mayBuild("feature/local",false,false,true,false,true,
            0,0,local,"",projectId="animaquiz",localOnly=true))
    }
    @Test fun validFastForwardAllowed() { assertTrue(sync()) }
    @Test fun mainAllowedAfterVerifiedFastForward() { assertTrue(sync(branch="main"));assertFalse(sync(branch="main",checked=false)) }
    @Test fun masterBlocked() { assertFalse(sync(branch="master")) }
    @Test fun otherBranchBlocked() { assertFalse(sync(branch="feature/other")) }
    @Test fun dirtyBlocked() { assertFalse(sync(dirty=true)) }
    @Test fun wrongRemoteBlocked() { assertFalse(sync(remote=false)) }
    @Test fun localAheadBlocked() { assertFalse(sync(ahead=1)) }
    @Test fun divergedBlocked() { assertFalse(sync(ahead=1,behind=2)) }
    @Test fun alreadyCurrentBlocked() { assertFalse(sync(behind=0)) }
    @Test fun uncheckedBlocked() { assertFalse(sync(checked=false)) }
    @Test fun runnerDenialBlocked() { assertFalse(sync(runner=false)) }
    @Test fun unknownOutcomeBlocksNewSync() { assertFalse(sync(blocked=true)); assertTrue(RepoPolicy.blocked("id","UNKNOWN")) }
    @Test fun runningBlocksNewOperations() { assertTrue(RepoPolicy.blocked("id","RUNNING")); assertTrue(RepoPolicy.blocked("id","QUEUED")) }
    @Test fun terminalUnblocksOnlyKnownJob() { assertFalse(RepoPolicy.blocked("id","FAILED")); assertFalse(RepoPolicy.blocked("id","SUCCEEDED")) }
    @Test fun staleCallbackRejected() { assertFalse(RepoPolicy.acceptCallback("new","old")); assertFalse(RepoPolicy.acceptCallback("","")) }
    @Test fun lateMatchingCallbackAllowed() { assertTrue(RepoPolicy.acceptCallback("new","new")) }
    @Test fun mismatchedJobRejected() { assertFalse(RepoPolicy.validJob("one","SYNC","two","SYNC","SUCCEEDED")) }
    @Test fun mismatchedActionRejected() { assertFalse(RepoPolicy.validJob("one","SYNC","one","CHECK","SUCCEEDED")) }
    @Test fun unknownStateRejected() { assertFalse(RepoPolicy.validJob("one","SYNC","one","SYNC","MADE_UP")) }
    @Test fun acceptedIsNotFinished() { assertTrue(RepoPolicy.validJob("one","SYNC","one","SYNC","QUEUED")); assertTrue(RepoPolicy.blocked("one","QUEUED")) }
    @Test fun queryUsesJobRunnerAfterUpgrade() { assertEquals("old", RepoPolicy.runnerForQuery("QUERY","new","old","new","legacy")) }
    @Test fun queryUsesInstalledRunnerWhenJobHasNoSha() { assertEquals("old", RepoPolicy.runnerForQuery("QUERY","new","","old","legacy")) }
    @Test fun legacyQueryPreservesRunner() { assertEquals("legacy", RepoPolicy.runnerForQuery("QUERY","new","","","legacy")) }
    @Test fun newStartAlwaysUsesCurrentRunner() { assertEquals("new", RepoPolicy.runnerForQuery("START","new","old","old","legacy")) }
    @Test fun failedNetworkCheckAllowsExplicitRetry() { assertFalse(RepoPolicy.blocked("id","FAILED")); assertFalse(sync(checked=false)) }
    @Test fun instrumentationDoesNotDependOnStaleUiSnapshot() {
        assertTrue(RepoPolicy.requiresPreparedSnapshot("TEST"))
        assertFalse(RepoPolicy.requiresPreparedSnapshot("TEST_DEVICE"))
        assertFalse(RepoPolicy.requiresPreparedSnapshot("STATUS"))
    }
    @Test fun testsAllowedWithConfirmedProfile() { assertTrue(RepoPolicy.mayTest("test/decomposition-v1",true,true,false)) }
    @Test fun testsMainAllowedWithVerifiedOrigin() { assertTrue(RepoPolicy.mayTest("main",true,true,false));assertFalse(RepoPolicy.mayTest("main",false,true,false)) }
    @Test fun testsWrongRemoteBlocked() { assertFalse(RepoPolicy.mayTest("test/decomposition-v1",false,true,false)) }
    @Test fun testsMissingRunnerBlocked() { assertFalse(RepoPolicy.mayTest("test/decomposition-v1",true,false,false)) }
    @Test fun testsConflictBlocked() { assertFalse(RepoPolicy.mayTest("test/decomposition-v1",true,true,true)) }
    private fun build(dirty:Boolean=false, checked:Boolean=true, local:String="abc", remote:String="abc", ahead:Int=0, behind:Int=0) =
        RepoPolicy.mayBuild("test/decomposition-v1",dirty,true,true,false,checked,ahead,behind,local,remote)
    @Test fun debugBuildAllowedOnlyOnConfirmedMatchingSHA() {assertTrue(build());assertFalse(build(dirty=true));assertFalse(build(checked=false));assertFalse(build(local="abc",remote="def"));assertFalse(build(behind=1));assertFalse(build(ahead=1))}
    @Test fun junitCountsValid() { assertTrue(RepoPolicy.validCounts(3,1,1,1,125)) }
    @Test fun junitCountsMismatchRejected() { assertFalse(RepoPolicy.validCounts(4,1,1,1,125)) }
    @Test fun junitNegativeRejected() { assertFalse(RepoPolicy.validCounts(3,-1,1,3,125)) }
    @Test fun launchStateIsWaiting() { assertEquals("Ожидание",RepoPolicy.testState("QUEUED")); assertEquals("Результат неизвестен",RepoPolicy.testState("UNKNOWN")) }
    @Test fun upgradeQueriesUse021Version() { assertEquals("0.2.1",RepoPolicy.runnerVersion("7239fd9dd4979342bdfec98f0a10ef198358e8f8e6ecc8b3696be6ca0df5b0b2","0.3.0")) }
}
