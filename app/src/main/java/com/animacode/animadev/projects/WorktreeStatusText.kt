package com.animacode.animadev.projects

/** A local status is not evidence of any push, PR, or remote comparison. */
object WorktreeStatusText {
    private fun countLabel(count:Int):String {
        val word=if(count%100 in 11..14)"файлов" else when(count%10) {
            1->"файл"
            2,3,4->"файла"
            else->"файлов"
        }
        return "$count $word"
    }
    fun describe(dirty:Boolean?,files:Int?):String=when(dirty) {
        true->if(files!=null)"Локальные изменения: ${countLabel(files)} (ещё не в коммите)"
            else "Есть незакоммиченные файлы"
        false->"Рабочие файлы без изменений"
        null->"Состояние файлов не проверено"
    }
}