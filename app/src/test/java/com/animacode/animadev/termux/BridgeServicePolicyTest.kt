package com.animacode.animadev.termux

import org.junit.Assert.*
import org.junit.Test

class BridgeServicePolicyTest {
    @Test fun statusParserRejectsUnrelatedServices() {
        assertEquals("running", BridgeServicePolicy.parseStatus(
            "run: /data/data/com.termux/files/usr/var/service/animabridge: (pid 156) 40s"))
        assertEquals("stopped", BridgeServicePolicy.parseStatus(
            "down: /data/data/com.termux/files/usr/var/service/animabridge: 3s, normally up"))
        assertEquals("unknown", BridgeServicePolicy.parseStatus("run: /tmp/ssh: (pid 4) 5s"))
        assertEquals("unknown", BridgeServicePolicy.parseStatus("supervise not running"))
    }
    @Test fun controlsRequireVerifiedStateAndNoPendingRequest() {
        assertTrue(BridgeServicePolicy.permits("up", "stopped", false))
        assertFalse(BridgeServicePolicy.permits("up", "running", false))
        assertTrue(BridgeServicePolicy.permits("restart", "running", false))
        assertTrue(BridgeServicePolicy.permits("down", "running", false))
        assertFalse(BridgeServicePolicy.permits("down", "unknown", false))
        assertFalse(BridgeServicePolicy.permits("up", "stopped", true))
        assertTrue(BridgeServicePolicy.permits("status", "unknown", false))
        assertFalse(BridgeServicePolicy.permits("execute", "running", false))
    }
}
