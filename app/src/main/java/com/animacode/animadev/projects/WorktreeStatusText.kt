package com.animacode.animadev.projects

/** Read-only Git catalog status. Neither a branch nor a clean tree proves that GitHub is up-to-date. */
object WorktreeStatusText {
    fun describe(dirty:Boolean?,files:Int?):String = when(dirty) {
        true -> if(files!=null) "Локальные изменения: $files файлов (ещё не в коммите)"
            else "Есть незакоммиченные файлы"
        false -> "Рабочие файлы без изменений"
        null -> "Состояние файлов не проверено"
    }
}