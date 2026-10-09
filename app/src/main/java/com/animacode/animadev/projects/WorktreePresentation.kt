package com.animacode.animadev.projects

/** Disclosure affects presentation only; never changes catalog metadata. */
object WorktreePresentation {
    fun <T> rows(entries:List<T>,expanded:Boolean):List<T> = if(expanded)entries else emptyList()
}
