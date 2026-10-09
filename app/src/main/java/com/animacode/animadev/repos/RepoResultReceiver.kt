package com.animacode.animadev.repos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

class RepoResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = RepoStore(context); val record = store.read()
        val uri = intent.data ?: return
        val id = uri.lastPathSegment ?: return
        if (intent.action != RepoGateway.CALLBACK || uri.scheme != "animadev" || uri.host != "repos" ||
            !RepoPolicy.acceptCallback(record.optString("transport_id"), id)) return
        try {
            val result = intent.getBundleExtra("result") ?: error("bundle")
            val out = result.getString("stdout", "") ?: ""
            val err = result.getString("stderr", "") ?: ""
            check(out.length <= 24_000 && err.length <= 8192)
            check(result.containsKey("exitCode") && result.containsKey("err"))
            val j = JSONObject(out)
            check(j.getInt("protocol_version") == RepoGateway.PROTOCOL && j.getString("runner_version") == record.optString("transport_runner_version", "0.2.0") && j.getString("request_id") == id)
            record.put("pending", false).put("callback_exit", result.getInt("exitCode"))
                .put("termux_err", result.getInt("err")).put("transport_stdout", out.take(24_000))
                .put("transport_stderr", redact(err).take(8192))
            val successfulTransport = result.getInt("exitCode") == 0 && result.getInt("err") == -1
            if (record.optString("mode") == "INSTALL") {
                val sha = context.assets.open("runner/runner.sha256").bufferedReader().use { it.readText().trim() }
                val ready = successfulTransport && j.optBoolean("ok") && j.optString("sha256") == sha
                record.put("ready", ready).put("message", if (ready) "Локальный помощник готов. Нажмите «Обновить состояние»." else "Не удалось установить runner. ${j.optString("error_code")}")
                if (ready) record.put("installed_sha", sha)
            } else if (!j.optBoolean("ok") || !successfulTransport) {
                val code = j.optString("error_code", "RESULT_MALFORMED")
                // Any uncertainty retains the job ID. QUERY resolves it without issuing another START.
                record.put("state", "UNKNOWN").put("message", j.optString("message", "Ответ не подтверждён."))
                if (code == "RUNNER_CHECKSUM_MISMATCH") record.put("ready", false)
                val active = j.optJSONObject("active_job")
                if (code == "JOB_BUSY" && active != null) {
                    check(active.getString("project_id") == RepoGateway.PROJECT)
                    check(active.getString("action") in setOf("STATUS", "CHECK", "SYNC", "TEST"))
                    check(active.getString("job_id").matches(Regex("[a-f0-9]{32}")))
                    record.put("job_id", active.getString("job_id")).put("action", active.getString("action"))
                }
            } else {
                val job = j.getJSONObject("job")
                check(job.getString("project_id") == RepoGateway.PROJECT)
                check(RepoPolicy.validJob(record.getString("job_id"), record.getString("action"),
                    job.getString("job_id"), job.getString("action"), job.getString("state")))
                if (job.getString("state") == "SUCCEEDED") check(job.getInt("exit_code") == 0)
                if (job.getString("action") == "TEST") {
                    val tests = job.optJSONObject("tests")
                    if (tests != null) check(RepoPolicy.validCounts(tests.getLong("total"), tests.getLong("passed"),
                        tests.getLong("failed"), tests.getLong("skipped"), tests.getLong("duration_ms")))
                    if (job.getString("state") == "SUCCEEDED") check(tests != null && tests.getLong("failed") == 0L)
                    record.put("test_job", job)
                }
                record.put("state", job.getString("state")).put("job", job)
                    .put("message", job.optString("message")).put("runner_version", j.getString("runner_version"))
                val snapshot = job.optJSONObject("snapshot")
                if (snapshot != null) {
                    check(snapshot.getString("local_commit").matches(Regex("[a-f0-9]{40,64}")))
                    check(snapshot.optInt("ahead", -1) >= 0 && snapshot.optInt("behind", -1) >= 0)
                    record.put("snapshot", snapshot)
                }
            }
            // Never retain raw stderr or unknown diagnostic output with secrets.
            record.put("transport_stdout", redact(record.optString("transport_stdout")))
            record.remove("transport_id") // Duplicate terminal callback cannot rewrite state.
            val currentSha = context.assets.open("runner/runner.sha256").bufferedReader().use { it.readText().trim() }
            if (!RepoPolicy.blocked(record.optString("job_id"), record.optString("state")) &&
                record.optBoolean("ready") && record.optString("installed_sha", RepoGateway.LEGACY_SHA) != currentSha) {
                record.put("ready", false)
            }
            store.write(record)
        } catch (_: Exception) {
            store.edit { it.put("pending", false).put("state", "UNKNOWN")
                .put("message", "RESULT_MALFORMED: ответ Termux не подтверждён. Уточняем состояние по ID; не повторяем запуск.") }
        }
    }
    companion object {
        fun redact(text: String): String = text
            .replace(Regex("(?is)-----BEGIN [^-]*PRIVATE KEY-----.*?(?:-----END [^-]*PRIVATE KEY-----|$)"), "[REDACTED]")
            .replace(Regex("(?i)(authorization\\s*[:=]\\s*)[^\\r\\n]+"), "$1[REDACTED]")
            .replace(Regex("(?i)((?:token|password|secret|api[_-]?key)\\s*[:=]\\s*)[^\\s,;]+"), "$1[REDACTED]")
            .replace(Regex("(?:gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+)"), "[REDACTED]")
            .replace(Regex("(https?://)[^\\s/@]+:[^\\s/@]+@"), "$1[REDACTED]@")
    }
}
