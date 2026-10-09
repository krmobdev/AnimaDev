package com.animacode.animadev.repos

/** Pure observation policy. None of these transitions can request START or kill a process. */
object MonitorPolicy {
    val thresholds = listOf(5,10,15,20,30)
    fun minutes(value: Int) = if (value in thresholds) value else 10
    fun warning(now: Long, progress: Long, start: Long, snoozedUntil: Long, minutes: Int, state: String): Boolean =
        state == "RUNNING" && now >= snoozedUntil && now - maxOf(progress,start) >= minutes(minutes)*60_000L
    fun snooze(now: Long, minutes: Int) = now + minutes(minutes)*60_000L
    fun queryAllowed(pending: Boolean, hasId: Boolean) = !pending && hasId
    fun manualVisible(state: String, lastConfirmed: Long, now: Long) =
        state == "UNKNOWN" || (state in setOf("REQUESTED","QUEUED","RUNNING") && lastConfirmed > 0 && now-lastConfirmed >= 30_000L)
    fun label(state: String, quiet: Boolean, warning: Boolean): String = when(state) {
        "REQUESTED","QUEUED" -> "Ожидание запуска"
        "RUNNING" -> if(warning) "Возможно зависание" else if(quiet) "Выполняется, новых сообщений пока нет" else "Выполняется"
        "SUCCEEDED" -> "Успешно"
        "FAILED","TIMED_OUT" -> "Завершено с ошибкой"
        "INTERRUPTED" -> "Прервано извне"
        "UNKNOWN" -> "Результат неизвестен"
        "NOT_STARTED" -> "Не запускалось"
        else -> "Нет задания"
    }
}
