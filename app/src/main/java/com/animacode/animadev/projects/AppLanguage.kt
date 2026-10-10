package com.animacode.animadev.projects

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

/** Persisted preference: system, ru or en. Never mutates the device's global locale. */
val LocalAppLanguage=compositionLocalOf { "system" }

object AppLanguage {
    fun english(value:String, system:String=Locale.getDefault().language):Boolean=
        when(value) {"en"->true;"ru"->false;else->system.equals("en",ignoreCase=true)}
}
