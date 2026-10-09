package com.animacode.animadev.repos
import org.json.JSONObject
object TestSummary {
    fun format(t:JSONObject):String=JournalPresentation.summary(t.optInt("total"),t.optInt("passed"),t.optInt("failed"),t.optInt("skipped"),t.optLong("duration_ms"),t.optInt("report_files"))
}
