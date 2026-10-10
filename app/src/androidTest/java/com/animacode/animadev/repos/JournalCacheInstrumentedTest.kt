package com.animacode.animadev.repos
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
@RunWith(AndroidJUnit4::class)
class JournalCacheInstrumentedTest {
    private val project="11223344556677889900aabbccddeeff00"
    private fun id()=UUID.randomUUID().toString().replace("-","")
    private fun job(id:String)=JSONObject().put("job_id",id).put("project_id",project)
        .put("action","TEST_DEVICE").put("state","FAILED").put("error_code","TESTS_FAILED")
        .put("message","Есть проваленные unit-тесты")
        .put("tests",JSONObject().put("total",10).put("passed",9).put("failed",1)
            .put("skipped",0).put("duration_ms",100).put("report_files",1)
            .put("failure_details",JSONArray().put(JSONObject().put("class_name","Launch")
                .put("test_name","packageIdentity").put("message","unexpected package"))))
    private fun cache()=JournalCache(InstrumentationRegistry.getInstrumentation().targetContext)
    @Test fun dynamicProjectPagesAreReadableAndSummaryIsPresent() {
        val id=id()
        val page=JSONObject().put("job_id",id).put("offset",0).put("next_offset",12)
            .put("text","actual test output").put("metadata",job(id))
        cache().use {
            it.receive(page,id,"ALL",0,project)
            assertEquals(12L,it.cursor(id,"ALL"))
            val text=it.text(id,"ALL")
            assertTrue(text.contains("actual test output"))
            assertTrue(text.contains("Launch#packageIdentity"))
            assertFalse(text.contains("unit-тесты"))
            assertFalse(text.contains("~/quizapp"))
        }
    }
    @Test fun foreignProjectPageIsRejectedWithoutAdvancingCursor() {
        val id=id()
        val page=JSONObject().put("job_id",id).put("offset",0).put("next_offset",12)
            .put("text","foreign output").put("metadata",job(id).put("project_id","other"))
        cache().use {
            try {it.receive(page,id,"ALL",0,project);fail("foreign project accepted")}
            catch(expected:IllegalArgumentException) {}
            assertEquals(0L,it.cursor(id,"ALL"))
            assertFalse(it.text(id,"ALL").contains("foreign output"))
        }
    }
    @Test fun confirmedResultSurvivesReopenBeforeAnyLogPagesArrive() {
        val id=id()
        cache().use {it.rememberJob(job(id),project)}
        cache().use {
            val text=it.text(id,"ALL")
            assertTrue(text.contains("Launch#packageIdentity"))
            assertTrue(text.contains("unexpected package"))
            assertTrue(text.contains("инструментальные"))
        }
    }
    @Test fun dynamicProjectHistoryIsAcceptedButOtherProjectIsRejected() {
        val id=id()
        cache().use {
            it.history(JSONObject().put("items",JSONArray().put(job(id))),project)
            assertTrue(it.jobs().any {row->row.optString("job_id")==id})
            try {it.history(JSONObject().put("items",JSONArray().put(job(id()).put("project_id","other"))),project);fail("foreign history")}
            catch(expected:IllegalArgumentException) {}
        }
    }
}
