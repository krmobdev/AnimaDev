package com.animacode.animadev.repos

object RepoPolicy {
    fun runnerVersion(sha: String, current: String): String = when (sha) {
        "e96b432836ff0fb1f23f9455a1440a73fbcb7a675720871d323c4209bb178a4e" -> "0.2.0"
        "7239fd9dd4979342bdfec98f0a10ef198358e8f8e6ecc8b3696be6ca0df5b0b2" -> "0.2.1"
        "bcca823e689fd56e05993fe9e61d8b2f7d904c054eb34964d39b3828cd24fa4b" -> "0.3.0"
        "8a46e5764337cc6da1674a96fe36dae27cda27ea5970fbf3e1f21cce9e249092" -> "0.3.1"
        else -> current
    }
    fun mayTest(branch: String, remote: Boolean, ready: Boolean, blocked: Boolean,
                projectId: String = "animaquiz"): Boolean =
        branch.isNotBlank() && branch != "Detached HEAD" &&
            (projectId != "animaquiz" || branch == "test/decomposition-v1") &&
            remote && ready && !blocked
    fun mayBuild(branch: String, dirty: Boolean, remoteOk: Boolean, ready: Boolean, blocked: Boolean,
                 checked: Boolean, ahead: Int, behind: Int, local: String, remote: String,
                 projectId: String = "animaquiz"): Boolean =
        branch.isNotBlank() && branch != "Detached HEAD" &&
            (projectId != "animaquiz" || branch == "test/decomposition-v1") &&
            !dirty && remoteOk && ready && !blocked && checked &&
            ahead == 0 && behind == 0 && local.isNotBlank() && local == remote
    fun validCounts(total: Long, passed: Long, failed: Long, skipped: Long, duration: Long): Boolean =
        listOf(total, passed, failed, skipped, duration).all { it >= 0 && it <= 1_000_000_000L } &&
            passed + failed + skipped == total
    fun testState(state: String): String = when (state) {
        "REQUESTED", "QUEUED" -> "Ожидание"
        "RUNNING" -> "Выполнение"
        "SUCCEEDED" -> "Успешно"
        "FAILED", "TIMED_OUT", "INTERRUPTED" -> "Ошибка"
        "UNKNOWN" -> "Результат неизвестен"
        else -> "Не запускались"
    }
    fun runnerForQuery(mode: String, current: String, job: String, installed: String, legacy: String): String =
        if (mode == "START") current else job.ifEmpty { installed.ifEmpty { legacy } }
    val activeStates = setOf("REQUESTED", "QUEUED", "RUNNING", "UNKNOWN")
    val terminalStates = setOf("SUCCEEDED", "FAILED", "TIMED_OUT", "INTERRUPTED", "NOT_STARTED")
    fun blocked(jobId: String, state: String) = jobId.isNotEmpty() && state in activeStates
    fun maySync(branch: String, dirty: Boolean, remoteOk: Boolean, ahead: Int, behind: Int,
                checked: Boolean, runnerCanSync: Boolean, blocked: Boolean,
                projectId: String = "animaquiz"): Boolean =
        branch.isNotBlank() && branch != "Detached HEAD" &&
            (projectId != "animaquiz" || branch == "test/decomposition-v1") &&
            !dirty && remoteOk && ahead == 0 && behind > 0 &&
            checked && runnerCanSync && !blocked
    fun acceptCallback(expected: String, received: String) = expected.isNotEmpty() && expected == received
    fun validJob(expectedId: String, expectedAction: String, id: String, action: String, state: String) =
        expectedId == id && expectedAction == action && state in (activeStates + terminalStates)
}
