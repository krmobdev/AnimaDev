package com.animacode.animadev.repos

object RepoPolicy {
    fun runnerForQuery(mode: String, current: String, job: String, installed: String, legacy: String): String =
        if (mode == "START") current else job.ifEmpty { installed.ifEmpty { legacy } }
    val activeStates = setOf("REQUESTED", "QUEUED", "RUNNING", "UNKNOWN")
    val terminalStates = setOf("SUCCEEDED", "FAILED", "TIMED_OUT", "INTERRUPTED", "NOT_STARTED")
    fun blocked(jobId: String, state: String) = jobId.isNotEmpty() && state in activeStates
    fun maySync(branch: String, dirty: Boolean, remoteOk: Boolean, ahead: Int, behind: Int,
                checked: Boolean, runnerCanSync: Boolean, blocked: Boolean): Boolean =
        branch == "test/decomposition-v1" && !dirty && remoteOk && ahead == 0 && behind > 0 &&
            checked && runnerCanSync && !blocked
    fun acceptCallback(expected: String, received: String) = expected.isNotEmpty() && expected == received
    fun validJob(expectedId: String, expectedAction: String, id: String, action: String, state: String) =
        expectedId == id && expectedAction == action && state in (activeStates + terminalStates)
}
