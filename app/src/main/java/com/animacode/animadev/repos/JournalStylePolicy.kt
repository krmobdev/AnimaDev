package com.animacode.animadev.repos

enum class LogTone { NORMAL, SUCCESS, ERROR, WARNING }
data class LogStyle(val start:Int,val end:Int,val tone:LogTone,val timestampEnd:Int)

/** Interpret anchored event/output structure, never arbitrary words inside arguments. */
object JournalStylePolicy {
    private val stamp=Regex("^\\[\\d{2}:\\d{2}:\\d{2}]")
    private val channel=Regex("^(event|step|command|stdout|stderr):\\s*",RegexOption.IGNORE_CASE)
    fun styles(text:String):List<LogStyle> {
        var offset=0
        return text.split('\n').map { line ->
            val prefix=stamp.find(line)?.value.orEmpty()
            val body=line.removePrefix(prefix).trimStart()
            val ch=channel.find(body)?.groupValues?.get(1)?.lowercase().orEmpty()
            val raw=body.replaceFirst(channel,"").trimStart()
            val payload=if(ch=="event")raw.removePrefix("Итог: ") else raw
            val tone=when {
                ch=="command" -> LogTone.NORMAL
                Regex("^(BUILD SUCCESSFUL(?:\\s|$)|SUCCEEDED(?:\\s|$)|SUCCESS(?:\\s|$)|Успешно(?:\\s|$)|(?:state|status)[=: ]+SUCCEEDED(?:\\s|$))",RegexOption.IGNORE_CASE).containsMatchIn(payload)->LogTone.SUCCESS
                Regex("^(BUILD FAILED(?:\\s|$)|FAILURE:|FAILED(?:\\s|$)|ERROR(?:[: ]|$)|FATAL:|e: |(?:state|status)[=: ]+(?:FAILED|ERROR)(?:\\s|$))",RegexOption.IGNORE_CASE).containsMatchIn(payload)->LogTone.ERROR
                Regex("^(WARNING(?:[: ]|$)|WARN(?:[: ]|$)|w: |Предупреждение(?:[: ]|$))",RegexOption.IGNORE_CASE).containsMatchIn(payload)->LogTone.WARNING
                else -> LogTone.NORMAL
            }
            LogStyle(offset,offset+line.length,tone,if(prefix.isEmpty())offset else offset+prefix.length).also {offset+=line.length+1}
        }
    }
    /** Chunk at line boundaries; a long line is preserved rather than detached from its timestamp. */
    fun chunks(text:String,target:Int=3000):List<String> {
        val result=mutableListOf<String>();val buffer=StringBuilder()
        val lines=text.split('\n')
        lines.forEachIndexed { i,line ->
            val part=line+if(i<lines.lastIndex)"\n" else ""
            if(buffer.isNotEmpty() && buffer.length+part.length>target){result.add(buffer.toString());buffer.clear()}
            if(part.length>24_000) {
                if(buffer.isNotEmpty()){result.add(buffer.toString());buffer.clear()}
                result.addAll(part.chunked(24_000))
            } else buffer.append(part)
        }
        if(buffer.isNotEmpty())result.add(buffer.toString())
        return result
    }
}
