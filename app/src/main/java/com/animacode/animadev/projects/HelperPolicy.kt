package com.animacode.animadev.projects

object HelperPolicy {
    fun label(installed:Boolean)=if(installed)"Обновить помощник" else "Подключить помощник"
    fun mayConnect(permission:Boolean,termuxInstalled:Boolean,pending:Boolean,blocked:Boolean)=permission && termuxInstalled && !pending && !blocked
    fun mayRun(permission:Boolean,termuxInstalled:Boolean,ready:Boolean,pending:Boolean,blocked:Boolean)=mayConnect(permission,termuxInstalled,pending,blocked) && ready
}

/** Running a real Gradle job is ordinary progress, not a failed recovery state. */
object TestScreenStatusPolicy {
    fun active(state: String) = state in setOf("REQUESTED", "QUEUED", "RUNNING")
    fun needsRecovery(state: String) = state == "UNKNOWN"
    fun unitBlockedMessage(action: String, state: String) = when {
        needsRecovery(state) -> "Статус предыдущего задания неизвестен. Проверь состояние в Termux выше."
        active(state) && action == "TEST_DEVICE" ->
            "Сейчас идут инструментальные тесты. Unit-тесты станут доступны после их завершения."
        active(state) -> "Выполняется другое задание. Unit-тесты ждут освобождения Gradle."
        else -> "Тесты временно недоступны."
    }
}

/** A visible launch button must represent every synchronous start gate. */
object DeviceTestLaunchPolicy {
    fun canStart(permission:Boolean,termuxInstalled:Boolean,helperReady:Boolean,
                 repoPending:Boolean,repoBlocked:Boolean,adbPending:Boolean,
                 releaseBusy:Boolean):Boolean =
        HelperPolicy.mayRun(permission,termuxInstalled,helperReady,repoPending,repoBlocked) &&
            !adbPending && !releaseBusy
}
