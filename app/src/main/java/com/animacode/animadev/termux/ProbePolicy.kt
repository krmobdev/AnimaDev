package com.animacode.animadev.termux

/** Pure policy: a received callback is not itself proof of a successful command. */
object ProbePolicy {
    const val TIMEOUT_MS = 30_000L
    const val MAX_OUTPUT = 8_192
    fun acceptsCallback(expected: String, received: String, state: String): Boolean =
        expected.isNotEmpty() && expected == received && state in setOf("REQUESTED", "UNKNOWN")

    fun succeeded(exitCode: Int?, termuxError: Int?, validProbe: Boolean): Boolean =
        exitCode == 0 && termuxError == -1 && validProbe

    fun expired(state: String, started: Long, now: Long): Boolean =
        state == "REQUESTED" && now - started >= TIMEOUT_MS
}
