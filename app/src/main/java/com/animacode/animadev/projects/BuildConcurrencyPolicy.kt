package com.animacode.animadev.projects

/** A single build slot across all projects, irrespective of Debug/Release type. */
object BuildConcurrencyPolicy {
    private val activeStates = setOf("REQUESTED", "QUEUED", "RUNNING", "UNKNOWN")
    val launchMonitor=Any()

    fun repoBusy(jobId:String, state:String, action:String):Boolean =
        jobId.isNotBlank() && state in activeStates &&
            action in setOf("TEST", "TEST_DEVICE", "BUILD_DEBUG", "BUILD_RELEASE")
    fun releaseBusy(state:String):Boolean=state in setOf("REQUESTED", "QUEUED", "RUNNING", "UNKNOWN")

    fun owner(debugProject:String, debugJobId:String, debugState:String, debugAction:String,
              releaseProject:String, releaseState:String):String? = when {
        releaseBusy(releaseState) -> releaseProject.ifBlank {"Release AAB"}
        repoBusy(debugJobId,debugState,debugAction) -> debugProject.ifBlank {"Debug APK"}
        else -> null
    }

    fun waitingMessage(owner:String, ownProject:String):String =
        if(owner==ownProject) "Сборка этого проекта уже выполняется или её результат не подтверждён. " +
            "Дождитесь завершения либо уточните статус по ID."
        else "Сборочный слот занят проектом «$owner». " +
            "До подтверждения результата другую Debug/Release сборку запускать нельзя."
}
