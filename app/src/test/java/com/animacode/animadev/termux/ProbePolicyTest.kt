package com.animacode.animadev.termux

import org.junit.Assert.*
import org.junit.Test

class ProbePolicyTest {
    @Test fun noSuccessWithoutExitCode() { assertFalse(ProbePolicy.succeeded(null, -1, true)) }
    @Test fun nonzeroExitFails() { assertFalse(ProbePolicy.succeeded(1, -1, true)) }
    @Test fun missingInternalStatusFails() { assertFalse(ProbePolicy.succeeded(0, null, true)) }
    @Test fun termuxInternalFailureFails() { assertFalse(ProbePolicy.succeeded(0, 1, true)) }
    @Test fun wrongNonceOrSchemaFails() { assertFalse(ProbePolicy.succeeded(0, -1, false)) }
    @Test fun completeVerifiedResultSucceeds() { assertTrue(ProbePolicy.succeeded(0, -1, true)) }
    @Test fun callbackForAnotherRequestIgnored() { assertFalse(ProbePolicy.acceptsCallback("a", "b", "REQUESTED")) }
    @Test fun unsolicitedCallbackIgnored() { assertFalse(ProbePolicy.acceptsCallback("", "", "IDLE")) }
    @Test fun duplicateTerminalCallbackIgnored() { assertFalse(ProbePolicy.acceptsCallback("a", "a", "SUCCESS")) }
    @Test fun lateMatchingCallbackAccepted() { assertTrue(ProbePolicy.acceptsCallback("a", "a", "UNKNOWN")) }
    @Test fun timeoutDoesNotInventFailure() {
        assertFalse(ProbePolicy.expired("SUCCESS", 0, 40_000))
        assertFalse(ProbePolicy.expired("REQUESTED", 10_000, 39_999))
        assertTrue(ProbePolicy.expired("REQUESTED", 10_000, 40_000))
    }
}
