package com.animacode.animadev.repos
import org.json.JSONObject
object TestSummary {
    fun details(t:JSONObject?):String = buildString {
        val failures=t?.optJSONArray("failure_details") ?: return@buildString
        repeat(minOf(10,failures.length())) { index ->
            val failure=failures.optJSONObject(index) ?: return@repeat
            append("Проваленный тест: ")
            append(RepoResultReceiver.redact(failure.optString("class_name")).take(160))
            append("#")
            append(RepoResultReceiver.redact(failure.optString("test_name")).take(160))
            append("\n")
            append(RepoResultReceiver.redact(failure.optString("message")).take(400))
            append("\n")
        }
    }
    fun format(t:JSONObject):String {
        val required=listOf("total","passed","failed","skipped","duration_ms","report_files")
        if(required.any { !t.has(it) || t.isNull(it) })return "Статистика JUnit сохранена не полностью; откройте доступный журнал.\n"
        return JournalPresentation.summary(t.getInt("total"),t.getInt("passed"),t.getInt("failed"),t.getInt("skipped"),t.getLong("duration_ms"),t.getInt("report_files"))+details(t)
    }
}
