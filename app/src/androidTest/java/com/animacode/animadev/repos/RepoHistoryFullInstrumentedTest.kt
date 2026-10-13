package com.animacode.animadev.repos

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No Termux commands or private user data are accessed. */
@RunWith(AndroidJUnit4::class)
class RepoHistoryFullInstrumentedTest {
    @Test fun certainFullJournalAllowsOnlySafeRunnerRecovery() {
        val response=JSONObject()
            .put("protocol_version",2).put("runner_version","0.4.0")
            .put("request_id","e".repeat(32))
            .put("ok",false).put("error_code","HISTORY_FULL")
        val state=JSONObject().put("state","UNKNOWN").put("mode","QUERY")
            .put("callback_exit",0).put("termux_err",-1)
            .put("transport_stdout",response.toString())
        assertTrue(RepoGateway.historyCapacityFailure(state))
        state.put("callback_exit",-1)
        assertFalse(RepoGateway.historyCapacityFailure(state))
        state.put("callback_exit",0)
        state.put("transport_stdout",JSONObject(response.toString())
            .put("error_code","JOB_BUSY").toString())
        assertFalse(RepoGateway.historyCapacityFailure(state))
        state.put("history_full_recovery",true)
        assertTrue(RepoGateway.historyCapacityFailure(state))
    }
}
