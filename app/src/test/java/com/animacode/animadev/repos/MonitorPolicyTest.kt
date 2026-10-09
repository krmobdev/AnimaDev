package com.animacode.animadev.repos
import org.junit.Assert.*
import org.junit.Test
class MonitorPolicyTest {
    @Test fun thresholdsAreWarningOnly() { MonitorPolicy.thresholds.forEach { m ->
        assertFalse(MonitorPolicy.warning(m*60000L-1,0,0,0,m,"RUNNING"))
        assertTrue(MonitorPolicy.warning(m*60000L,0,0,0,m,"RUNNING"))
    } }
    @Test fun defaultTenMinutes() { assertEquals(10,MonitorPolicy.minutes(0));assertEquals(10,MonitorPolicy.minutes(90)) }
    @Test fun realProgressPostponesWarning() { assertFalse(MonitorPolicy.warning(600000,500000,0,0,5,"RUNNING")) }
    @Test fun continueWaitingSnoozesSameJob() { val until=MonitorPolicy.snooze(600000,10);assertEquals(1200000,until);assertFalse(MonitorPolicy.warning(700000,0,0,until,10,"RUNNING")) }
    @Test fun terminalOrUnknownDoesNotClaimHang() { listOf("SUCCEEDED","FAILED","UNKNOWN","INTERRUPTED").forEach { assertFalse(MonitorPolicy.warning(9000000,0,0,0,5,it)) } }
    @Test fun pendingQueriesAreSerialized() { assertFalse(MonitorPolicy.queryAllowed(true,true));assertTrue(MonitorPolicy.queryAllowed(false,true));assertFalse(MonitorPolicy.queryAllowed(false,false)) }
    @Test fun manualQueryDoesNotBlinkDuringNormalPolling() { for(t in 1000L..29000L step 1000)assertFalse(MonitorPolicy.manualVisible("RUNNING",1000,t)) }
    @Test fun manualQueryForUnknownOrDelay() { assertTrue(MonitorPolicy.manualVisible("UNKNOWN",0,0));assertTrue(MonitorPolicy.manualVisible("RUNNING",1000,31000)) }
    @Test fun lateNonceNeverAccepted() { assertFalse(RepoPolicy.acceptCallback("new","old"));assertFalse(RepoPolicy.acceptCallback("","")) }
    @Test fun unknownBlocksTestAndSync() { assertTrue(RepoPolicy.blocked("a","UNKNOWN"));assertFalse(RepoPolicy.mayTest("test/decomposition-v1",true,true,true)) }
    @Test fun previousRunnerVersionPreserved() { assertEquals("0.3.0",RepoPolicy.runnerVersion("bcca823e689fd56e05993fe9e61d8b2f7d904c054eb34964d39b3828cd24fa4b","0.3.1")) }
    @Test fun realResultCountsRemainValidated() { assertTrue(RepoPolicy.validCounts(396,396,0,0,210056));assertFalse(RepoPolicy.validCounts(396,395,0,0,210056)) }
    @Test fun liveQuietAndExternalTerminationDiffer() { assertEquals("Выполняется, новых сообщений пока нет",MonitorPolicy.label("RUNNING",true,false));assertEquals("Прервано извне",MonitorPolicy.label("INTERRUPTED",false,false)) }
}
