package com.animacode.animadev.repos
import org.junit.Assert.*
import org.junit.Test
class RepoPolicyTest {
    private fun sync(branch:String="test/decomposition-v1", dirty:Boolean=false, remote:Boolean=true, ahead:Int=0, behind:Int=1, checked:Boolean=true, runner:Boolean=true, blocked:Boolean=false) = RepoPolicy.maySync(branch,dirty,remote,ahead,behind,checked,runner,blocked)
    @Test fun validFastForwardAllowed() { assertTrue(sync()) }
    @Test fun mainBlocked() { assertFalse(sync(branch="main")) }
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
}
