package com.animacode.animadev.repos
import org.json.JSONObject
object TestSummary {
    fun format(t:JSONObject):String {
        val required=listOf("total","passed","failed","skipped","duration_ms","report_files")
        if(required.any { !t.has(it) || t.isNull(it) })return "Статистика JUnit сохранена не полностью; откройте доступный журнал.\n"
        return JournalPresentation.summary(t.getInt("total"),t.getInt("passed"),t.getInt("failed"),t.getInt("skipped"),t.getLong("duration_ms"),t.getInt("report_files"))
    }
}
