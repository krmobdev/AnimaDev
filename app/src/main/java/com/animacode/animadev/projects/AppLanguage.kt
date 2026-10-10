package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

/** Persisted preference: system, ru or en. Never mutates the device's global locale. */
val LocalAppLanguage=compositionLocalOf { "system" }

object AppLanguage {
    fun english(value:String, system:String=Locale.getDefault().language):Boolean=
        when(value) {"en"->true;"ru"->false;else->system.equals("en",ignoreCase=true)}
    fun english(context:Context):Boolean {
        val preference=context.getSharedPreferences("interface_v1",Context.MODE_PRIVATE)
            .getString("language","system") ?: "system"
        return english(preference,context.resources.configuration.locales[0].language)
    }
}
