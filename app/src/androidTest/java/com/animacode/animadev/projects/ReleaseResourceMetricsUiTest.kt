package com.animacode.animadev.projects

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseResourceMetricsUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun showsLiveCpuRamAndSwapFromSanitizedSnapshot() {
        val metrics=JSONObject().put("cpu_percent",20).put("cpu_cores",8)
            .put("cpu_process_percent",158)
            .put("mem_total_mb",11216).put("mem_available_mb",4017)
            .put("swap_used_mb",5427).put("sampled_at",System.currentTimeMillis())
        composeRule.setContent {
            MaterialTheme { ReleaseResourceMetrics(metrics,System.currentTimeMillis()) }
        }
        composeRule.onNodeWithText("CPU · процессы Termux: 20% от 8 ядер").assertExists()
        composeRule.onNodeWithText("RAM свободно для приложений:",substring=true).assertExists()
        composeRule.onAllNodesWithText("RAM занято:",substring=true).onFirst().assertExists()
        composeRule.onNodeWithText("Swap / zRAM занято:",substring=true).assertExists()
    }
}
