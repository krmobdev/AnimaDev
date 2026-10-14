package com.animacode.animadev.repos

object RepoPolicy {
    /** A confirmed HISTORY_FULL refusal occurs before a job is reserved. */
    fun journalCapacityRejected(state:String, transportExit:Int, termuxErr:Int,
                                error:String, success:Boolean):Boolean =
        state=="UNKNOWN" && transportExit==0 && termuxErr == -1 &&
            error=="HISTORY_FULL" && !success
    // The Termux device runner verifies Git and ADB itself on each launch.
    // Only local JVM tests still use the stored UI snapshot gate.
    fun requiresPreparedSnapshot(action: String): Boolean = action == "TEST"
    fun runnerVersion(sha: String, current: String): String = when (sha) {
        // dev.48/dev.49 shipped a 0.4.0 runner while Android expected 0.4.1.
        // Old CHECK results must remain queryable by their original SHA, never restarted.
        "4127f6f52b0995b113869702ef9fb0dd98f4d229bd5028a283a027dceae8a9c2" -> "0.4.0"
        "33e2668e27ed92ac12b0ea6170dfcf91fff4ea0af5d08380a1038679bf549507" -> "0.4.0"
        "e96b432836ff0fb1f23f9455a1440a73fbcb7a675720871d323c4209bb178a4e" -> "0.2.0"
        "7239fd9dd4979342bdfec98f0a10ef198358e8f8e6ecc8b3696be6ca0df5b0b2" -> "0.2.1"
        "bcca823e689fd56e05993fe9e61d8b2f7d904c054eb34964d39b3828cd24fa4b" -> "0.3.0"
        "8a46e5764337cc6da1674a96fe36dae27cda27ea5970fbf3e1f21cce9e249092" -> "0.3.1"
        else -> current
    }
    fun mayTest(branch: String, remote: Boolean, ready: Boolean, blocked: Boolean,
                projectId: String = "animaquiz", localOnly: Boolean=false,
                checked: Boolean=false, commit: String=""): Boolean =
        branch.isNotBlank() && branch != "Detached HEAD" &&
            (projectId != "animaquiz" || branch in setOf("main","test/decomposition-v1")) &&
            (if(localOnly) checked && commit.matches(Regex("[a-f0-9]{40,64}")) else remote) &&
            ready && !blocked
    fun mayBuild(branch: String, dirty: Boolean, remoteOk: Boolean, ready: Boolean, blocked: Boolean,
                 checked: Boolean, ahead: Int, behind: Int, local: String, remote: String,
                 projectId: String = "animaquiz", localOnly: Boolean=false): Boolean =
        branch.isNotBlank() && branch != "Detached HEAD" &&
            (projectId != "animaquiz" || branch in setOf("main","test/decomposition-v1")) &&
            ready && !blocked && checked &&
            (if(localOnly) projectId!="animaquiz" && local.matches(Regex("[a-f0-9]{40,64}"))
             else !dirty && remoteOk && ahead==0 && behind==0 &&
                  local.isNotBlank() && local==remote)
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
    // Query never retries START. The SHA is from a previously installed
    // local runner, not a user-entered value.
    fun canRecoverJob(pending:Boolean,jobId:String,jobRunnerSha:String,installedSha:String):Boolean =
        !pending && jobId.matches(Regex("[a-f0-9]{32}")) &&
            (jobRunnerSha.ifBlank {installedSha}).matches(Regex("[a-f0-9]{64}"))
    fun runnerForQuery(mode: String, current: String, job: String, installed: String, legacy: String): String =
        if (mode == "START") current else job.ifEmpty { installed.ifEmpty { legacy } }
    val activeStates = setOf("REQUESTED", "QUEUED", "RUNNING", "UNKNOWN")
    val terminalStates = setOf("SUCCEEDED", "FAILED", "TIMED_OUT", "INTERRUPTED", "NOT_STARTED")
    fun blocked(jobId: String, state: String) = jobId.isNotEmpty() && state in activeStates
    fun maySync(branch: String, dirty: Boolean, remoteOk: Boolean, ahead: Int, behind: Int,
                checked: Boolean, runnerCanSync: Boolean, blocked: Boolean,
                projectId: String = "animaquiz"): Boolean =
        branch.isNotBlank() && branch != "Detached HEAD" &&
            (projectId != "animaquiz" || branch in setOf("main","test/decomposition-v1")) &&
            !dirty && remoteOk && ahead == 0 && behind > 0 &&
            checked && runnerCanSync && !blocked
    fun acceptCallback(expected: String, received: String) = expected.isNotEmpty() && expected == received
    fun validJob(expectedId: String, expectedAction: String, id: String, action: String, state: String) =
        expectedId == id && expectedAction == action && state in (activeStates + terminalStates)
}
