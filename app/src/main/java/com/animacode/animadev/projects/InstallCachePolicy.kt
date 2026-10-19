package com.animacode.animadev.projects

import java.io.File
import java.nio.file.Files

/** A small, private cache of verified install copies; never touches original APKs. */
internal object InstallCachePolicy {
    const val MAX_APKS=3
    private val verifiedName=Regex("[a-f0-9]{64}\\.apk")

    fun prune(directory:File,current:File) {
        val copies=directory.listFiles()?.filter { file->
            file.isFile && !Files.isSymbolicLink(file.toPath()) &&
                verifiedName.matches(file.name)
        } ?: return
        copies.sortedWith(compareByDescending<File> {it.absolutePath==current.absolutePath}
            .thenByDescending {it.lastModified()}.thenBy {it.name})
            .drop(MAX_APKS)
            .forEach {old->runCatching {old.delete()} }
    }
}
