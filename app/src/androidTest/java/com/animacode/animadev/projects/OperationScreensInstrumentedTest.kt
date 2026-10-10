package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.animacode.animadev.repos.RepoStore
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Real screens and private fixture state; no Git, builds or Termux commands are sent. */
@RunWith(AndroidJUnit4::class)
class OperationScreensInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val project=LocalProject("animaquiz","AnimaQuiz",ProjectPolicy.QUIZ,
        "test/decomposition-v1","a".repeat(40),"git@github.com:krmobdev/quizapp.git",false)
    private var previous:String?=null
    @Before fun fixtureOnly() {
        check(context.packageName.matches(Regex("com\\.animacode\\.animadev\\.animadevtests\\.j[a-f0-9]{32}")))
        previous=RepoStore(context).prefs.getString("record",null)
    }
    @After fun restore() {
        RepoStore(context).prefs.edit().apply {
            if(previous==null)remove("record") else putString("record",previous)
        }.commit()
    }
    private fun seed(state:String="FAILED"):JSONObject {
        val jid="d".repeat(32)
        val job=JSONObject().put("project_id","animaquiz").put("job_id",jid)
            .put("action","TEST_DEVICE").put("state",state)
            .put("started_at",System.currentTimeMillis()/1000.0)
            .put("tests",JSONObject().put("total",10).put("passed",9).put("failed",1).put("skipped",0))
            .put("message","Не прошёл тест на устройстве").put("error_code","TEST_FAILED")
        val runner=context.assets.open("runner/runner.sha256").bufferedReader().use {it.readText().trim()}
        val record=JSONObject().put("project_id","animaquiz").put("ready",true).put("installed_sha",runner)
            .put("job_id",jid).put("action","TEST_DEVICE").put("state",state)
            .put("job",job).put("device_test_job",job)
        assertTrue(RepoStore(context).write(record))
        return record
    }
    @Test fun testResultsStayInsideSelectedSectionAndNeverInHeader() {
        seed()
        compose.setContent {MaterialTheme {
            ProjectTools(context,project,"tests",false,null,{}, {}, {})
        }}
        compose.onNodeWithTag("operation-header").assertIsDisplayed()
        compose.onNodeWithText("9 из 10 успешно").assertDoesNotExist()
        compose.onNodeWithText("На устройстве").performClick()
        compose.onNodeWithText("9 из 10 успешно").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("9 из 10 успешно") and hasAnyAncestor(hasTestTag("operation-header")))
            .assertDoesNotExist()
        compose.onAllNodesWithText("9 из 10 успешно").assertCountEquals(1)
        compose.onNodeWithText("Unit-тесты").performScrollTo().performClick()
        compose.onNodeWithText("9 из 10 успешно").assertDoesNotExist()
    }
    @Test fun liveDeviceJobDoesNotRenderAboveUnitSection() {
        seed("RUNNING")
        compose.setContent {MaterialTheme {
            ProjectTools(context,project,"tests",false,null,{}, {}, {})
        }}
        compose.onNodeWithText("Задание · AnimaQuiz · Инструментальные тесты · ADB").assertDoesNotExist()
        compose.onNodeWithText("На устройстве").performClick()
        compose.onNodeWithText("Задание · AnimaQuiz · Инструментальные тесты · ADB")
            .performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Задание · AnimaQuiz · Инструментальные тесты · ADB") and
            hasAnyAncestor(hasTestTag("operation-header"))).assertDoesNotExist()
    }
    @Test fun buildScreenSeparatesApkAndAabAndNeverShowsTests() {
        seed()
        compose.setContent {MaterialTheme {
            BuildScreen(context,project,false,null,{}, {})
        }}
        compose.onNodeWithText("Собрать Debug APK").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("9 из 10 успешно").assertDoesNotExist()
        compose.onNodeWithText("Собрать Release AAB").assertDoesNotExist()
        compose.onNodeWithText("Release AAB").performScrollTo().performClick()
        compose.onNodeWithText("Собрать Release AAB").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Собрать Debug APK").assertDoesNotExist()
    }
    @Test fun apkButtonUsesSystemInstallerFlowNotTermuxOpen() {
        val artifact=JSONObject().put("path","/storage/emulated/0/Download/fixture.apk")
            .put("sha256","a".repeat(64)).put("size",2048)
        compose.setContent {MaterialTheme {BuildArtifactCard(context,artifact)}}
        compose.onNodeWithText("Установить APK").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Открыть · установить приложение").assertDoesNotExist()
        val intent=ArtifactGateway.intent(context,artifact)
        assertEquals(ArtifactOpenReceiver::class.java.name,intent.component?.className)
        assertEquals("com.animacode.animadev.OPEN_ARTIFACT",intent.action)
        assertEquals(artifact.getString("sha256"),intent.getStringExtra("sha256"))
    }
    @Test fun aabOffersFolderInsteadOfInstaller() {
        val artifact=JSONObject().put("path","/storage/emulated/0/Download/fixture.aab")
            .put("sha256","a".repeat(64)).put("size",2048)
        compose.setContent {MaterialTheme {BuildArtifactCard(context,artifact)}}
        compose.onNodeWithText("Открыть папку").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Установить APK").assertDoesNotExist()
    }
    @Test fun previousBuildFailureOnlyAppearsInHistoryAfterGitCheck() {
        val record=seed()
        val failed=JSONObject().put("project_id","animaquiz").put("job_id","e".repeat(32))
            .put("action","BUILD_DEBUG").put("state","FAILED").put("error_code","GRADLE_FAILED")
            .put("message","Не удалось собрать Debug APK")
        record.put("action","CHECK").put("state","SUCCEEDED").put("build_job",failed)
        RepoStore(context).write(record)
        compose.setContent {MaterialTheme {BuildScreen(context,project,false,null,{}, {})}}
        compose.onNodeWithText("Последняя Debug-сборка").assertDoesNotExist()
        compose.onNodeWithText("Не удалось собрать Debug APK").assertDoesNotExist()
        compose.onNodeWithText("9 из 10 успешно").assertDoesNotExist()
    }

}
