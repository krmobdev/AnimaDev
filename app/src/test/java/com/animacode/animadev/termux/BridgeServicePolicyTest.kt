package com.animacode.animadev.termux

import org.junit.Assert.*
import org.junit.Test

class BridgeServicePolicyTest {
    @Test fun controlScriptPersistsExplicitServicePreference() {
        val script = BridgeServicePolicy.CONTROL_SCRIPT
        assertTrue(script.contains("DOWN_FLAG = SERVICE + \"/down\""))
        assertTrue(script.contains("""re.search(r"\(pid (\d+)\)", output)"""))
        assertTrue(script.contains("with open(DOWN_FLAG, \"a\")"))
        assertTrue(script.contains("os.unlink(DOWN_FLAG)"))
        assertTrue(script.contains("agent_pid(state.stdout)"))
        assertTrue(script.contains("action != \"restart\" or prior_pid != pid"))
        assertTrue(script.contains("Manual agent detected."))
    }

    @Test fun statusParserRejectsUnrelatedServices() {
        assertEquals("running", BridgeServicePolicy.parseStatus(
            "run: /data/data/com.termux/files/usr/var/service/animabridge: (pid 156) 40s"))
        assertEquals("stopped", BridgeServicePolicy.parseStatus(
            "down: /data/data/com.termux/files/usr/var/service/animabridge: 3s, normally up"))
        assertEquals("unknown", BridgeServicePolicy.parseStatus("run: /tmp/ssh: (pid 4) 5s"))
        assertEquals("supervisor_off", BridgeServicePolicy.parseStatus(
            "supervisor-off: /data/data/com.termux/files/usr/var/service/animabridge"))
        assertEquals("starting", BridgeServicePolicy.parseStatus(
            "starting: /data/data/com.termux/files/usr/var/service/animabridge"))
        assertEquals("conflict", BridgeServicePolicy.parseStatus(
            "conflict: /data/data/com.termux/files/usr/var/service/animabridge"))
        assertEquals("ephemeral", BridgeServicePolicy.parseStatus(
            "ephemeral: /data/data/com.termux/files/usr/var/service/animabridge"))
        assertEquals("unknown", BridgeServicePolicy.parseStatus("supervise not running"))
    }
    @Test fun controlsRequireVerifiedStateAndNoPendingRequest() {
        assertTrue(BridgeServicePolicy.permits("up", "stopped", false))
        assertFalse(BridgeServicePolicy.permits("up", "running", false))
        assertTrue(BridgeServicePolicy.permits("up", "supervisor_off", false))
        assertFalse(BridgeServicePolicy.permits("up", "starting", false))
        assertTrue(BridgeServicePolicy.permits("down", "conflict", false))
        assertFalse(BridgeServicePolicy.permits("restart", "conflict", false))
        assertTrue(BridgeServicePolicy.permits("up", "ephemeral", false))
        assertTrue(BridgeServicePolicy.permits("restart", "ephemeral", false))
        assertTrue(BridgeServicePolicy.permits("restart", "stopped", false))
        assertTrue(BridgeServicePolicy.permits("restart", "running", false))
        assertTrue(BridgeServicePolicy.permits("down", "running", false))
        assertFalse(BridgeServicePolicy.permits("down", "unknown", false))
        assertFalse(BridgeServicePolicy.permits("up", "stopped", true))
        assertTrue(BridgeServicePolicy.permits("status", "unknown", false))
        assertFalse(BridgeServicePolicy.permits("execute", "running", false))
    }
}
