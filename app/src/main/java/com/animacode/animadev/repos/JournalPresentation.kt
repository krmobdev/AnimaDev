package com.animacode.animadev.repos

/** Presentation only: never rewrites the authoritative log or ALL cache. */
object JournalPresentation {
    private val channel=Regex("^\\[\\d{2}:\\d{2}:\\d{2}\\] (event|step|command|stdout|stderr):\\s*(.*)$")
    private val significant=Regex("(?i)(warning|error|failed|failure|exception|ошиб|предупреж|успеш|итог|запуск|начало|заверш|BUILD SUCCESSFUL|exit[ =:]|SHA|\\d+ tests? (completed|passed))")
    fun important(text:String):String {
        var keep=true
        return text.lineSequence().filter {line->
            val m=channel.find(line)
            if(m!=null) {
                val kind=m.groupValues[1];val body=m.groupValues[2]
                keep=kind !in setOf("command","step") && (kind=="event" || significant.containsMatchIn(body))
            }
            keep
        }.joinToString("\n")
    }
    fun summary(total:Int,passed:Int,failed:Int,skipped:Int,junitMillis:Long,reports:Int)=
        "Тесты: $passed/$total успешно\nПровалено: $failed · Пропущено: $skipped\nСуммарное время JUnit: $junitMillis мс · Файлов отчётов: $reports\n"
    fun chunks(text:String):List<String> {
        val result=mutableListOf<String>();val normal=StringBuilder();val command=StringBuilder()
        fun flushNormal(){if(normal.isNotEmpty()){result.addAll(JournalStylePolicy.chunks(normal.toString()));normal.clear()}}
        fun flushCommand(){if(command.isNotEmpty()){result.add(command.toString());command.clear()}}
        text.splitToSequence('\n').forEach {line->
            val m=channel.find(line)
            if(m?.groupValues?.get(1)=="command") {flushNormal();flushCommand();command.append(line).append('\n')}
            else if(command.isNotEmpty() && m==null && command.length+line.length<24_000)command.append(line).append('\n')
            else {flushCommand();normal.append(line).append('\n')}
        }
        flushCommand();flushNormal();return result
    }
    fun isCommand(text:String)=channel.find(text.lineSequence().firstOrNull() ?: "")?.groupValues?.get(1)=="command"
}
