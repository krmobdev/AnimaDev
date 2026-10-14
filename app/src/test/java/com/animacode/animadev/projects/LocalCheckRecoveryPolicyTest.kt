package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test

class LocalCheckRecoveryPolicyTest {
    @Test fun staleButReadyHelperMustDisplayAnUpdateAction() {
        val old="a".repeat(64);val current="b".repeat(64)
        assertTrue(LocalCheckRecoveryPolicy.needsUpgrade(true,old,current))
        assertTrue(LocalCheckRecoveryPolicy.canUpdate(false,true,true,true))
        assertFalse(LocalCheckRecoveryPolicy.canQuery(false,true,true,true,true,true))
    }
    @Test fun onlyConfirmedCurrentHelperMayQueryExistingJob() {
        val sha="a".repeat(64)
        assertFalse(LocalCheckRecoveryPolicy.needsUpgrade(true,sha,sha))
        assertTrue(LocalCheckRecoveryPolicy.canQuery(false,true,true,true,true,false))
        assertFalse(LocalCheckRecoveryPolicy.canQuery(true,true,true,true,true,false))
        assertFalse(LocalCheckRecoveryPolicy.canQuery(false,false,true,true,true,false))
        assertFalse(LocalCheckRecoveryPolicy.canQuery(false,true,true,true,false,false))
    }
    @Test fun updaterNeverRunsIfUnauthorizedPendingOrNonlocal() {
        assertFalse(LocalCheckRecoveryPolicy.canUpdate(true,true,true,true))
        assertFalse(LocalCheckRecoveryPolicy.canUpdate(false,false,true,true))
        assertFalse(LocalCheckRecoveryPolicy.canUpdate(false,true,true,false))
    }
}
