package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test
class HelperPolicyTest {
    @Test fun healthyRunningJobIsNotARecoveryError() {
        listOf("REQUESTED", "QUEUED", "RUNNING").forEach { state ->
            assertTrue(TestScreenStatusPolicy.active(state))
            assertFalse(TestScreenStatusPolicy.needsRecovery(state))
        }
    }
    @Test fun onlyUnknownJobNeedsRecoveryCard() {
        assertTrue(TestScreenStatusPolicy.needsRecovery("UNKNOWN"))
        listOf("SUCCEEDED", "FAILED", "INTERRUPTED", "NOT_STARTED").forEach {
            assertFalse(TestScreenStatusPolicy.needsRecovery(it))
            assertFalse(TestScreenStatusPolicy.active(it))
        }
    }
    @Test fun deviceTestOccupiesSingleGradleSlotWithoutErrorLanguage() {
        val text = TestScreenStatusPolicy.unitBlockedMessage("TEST_DEVICE", "RUNNING")
        assertTrue(text.contains("инструментальные тесты"))
        assertTrue(text.contains("после их завершения"))
        assertFalse(text.contains("ошиб"))
    }
    @Test fun unknownJobHasActionableRecoveryMessage() {
        assertTrue(TestScreenStatusPolicy.unitBlockedMessage("TEST_DEVICE","UNKNOWN")
            .contains("Проверь состояние"))
    }
    @Test fun oldHelperLabel() {assertEquals("Обновить помощник",HelperPolicy.label(true))}
    @Test fun noPermissionNoExecution() {assertFalse(HelperPolicy.mayRun(false,true,true,false,false))}
    @Test fun oldResultIsNotReady() {assertFalse(HelperPolicy.mayRun(true,true,false,false,false))}
    @Test fun activeOrUnknownJobBlocksInstall() {assertFalse(HelperPolicy.mayConnect(true,true,false,true))}
    @Test fun pendingConnectionCannotBeRepeated() {assertFalse(HelperPolicy.mayConnect(true,true,true,false))}
    @Test fun installedConfirmedHelperCanRun() {assertTrue(HelperPolicy.mayRun(true,true,true,false,false))}
    @Test fun deviceButtonCannotIgnoreBusyReleaseJob() {
        assertFalse(DeviceTestLaunchPolicy.canStart(true,true,true,false,false,false,true))
    }
    @Test fun deviceButtonCannotIgnorePendingAdbCallback() {
        assertFalse(DeviceTestLaunchPolicy.canStart(true,true,true,false,false,true,false))
    }
    @Test fun deviceButtonAllowsFreshRunnerPreflightEvenWithoutAdbUiConfirmation() {
        assertTrue(DeviceTestLaunchPolicy.canStart(true,true,true,false,false,false,false))
    }
    @Test fun deviceButtonRejectsUnresolvedRepoJobOrMissingPermission() {
        assertFalse(DeviceTestLaunchPolicy.canStart(true,true,true,false,true,false,false))
        assertFalse(DeviceTestLaunchPolicy.canStart(false,true,true,false,false,false,false))
        assertFalse(DeviceTestLaunchPolicy.canStart(true,true,true,true,false,false,false))
    }

}
