package com.animacode.animadev.projects

import android.content.Context
import java.io.File
import java.nio.file.Files

/** Only AnimaDev's own disposable exports, never Git, Gradle, SQLite or preferences. */
object AppCache {
    private val known=setOf("shared_apk","shared_reports")
    fun bytes(context:Context):Long=known.sumOf { name->
        val dir=File(context.cacheDir,name)
        if(Files.isSymbolicLink(dir.toPath()) || !dir.isDirectory)0L
        else dir.listFiles()?.filter {it.isFile && !Files.isSymbolicLink(it.toPath())}
            ?.sumOf {it.length()} ?: 0L
    }

    fun clear(context:Context):Int {
        var cleared=0
        for(name in known) {
            val dir=File(context.cacheDir,name)
            if(!dir.isDirectory || Files.isSymbolicLink(dir.toPath()))continue
            for(file in dir.listFiles().orEmpty()) {
                if(!file.isFile || Files.isSymbolicLink(file.toPath()))continue
                if(file.delete())cleared++
            }
        }
        return cleared
    }
}
