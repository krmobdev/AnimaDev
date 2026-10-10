package com.animacode.animadev.projects

/** Text and safe actions for the Git screen; independent of Compose and Termux. */
object GitScreenStatus {
    fun headline(
        working: Boolean, state: String, hasJob: Boolean,
        hasSnapshot: Boolean, branchAllowed: Boolean, originOk: Boolean,
        dirty: Boolean, checked: Boolean, ahead: Int, behind: Int,
        justSynced: Boolean
    ): String = when {
        working -> "Git · операция выполняется"
        hasJob && state in setOf("FAILED", "TIMED_OUT", "INTERRUPTED") -> "Ошибка Git · подробности ниже"
        hasJob && state == "UNKNOWN" -> "Результат Git неизвестен"
        !hasSnapshot -> "Сначала проверьте Git"
        !branchAllowed -> "Ветка не разрешена"
        !originOk -> "Origin не подтверждён"
        dirty -> "Есть локальные изменения"
        !checked -> "GitHub ещё не проверен"
        ahead > 0 && behind > 0 -> "Ветки разошлись"
        ahead > 0 -> "Есть локальные коммиты"
        behind > 0 -> "Доступны обновления"
        justSynced -> "Исходники обновлены"
        else -> "Исходники актуальны"
    }

    fun commitWord(n: Int): String = when {
        n % 100 in 11..14 -> "коммитов"
        n % 10 == 1 -> "коммит"
        n % 10 in 2..4 -> "коммита"
        else -> "коммитов"
    }
}
