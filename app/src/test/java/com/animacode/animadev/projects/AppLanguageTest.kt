package com.animacode.animadev.projects

import org.junit.Assert.*
import org.junit.Test

class AppLanguageTest {
    @Test fun explicitLocaleOverridesSystem() {
        assertTrue(AppLanguage.english("en","ru"))
        assertFalse(AppLanguage.english("ru","en"))
        assertTrue(AppLanguage.english("system","en"))
        assertFalse(AppLanguage.english("system","ru"))
    }
    @Test fun translatesUIWithoutMutatingProtocolValues() {
        assertEquals("Install APK",AppTranslation.translate("Установить APK",true))
        assertEquals("Установить APK",AppTranslation.translate("Установить APK",false))
        assertEquals("Open journal",AppTranslation.translate("Открыть журнал",true))
        assertEquals("Build slot busy: ",AppTranslation.translate("Очередь занята: ",true))
        assertEquals("3f9c...",AppTranslation.translate("3f9c...",true))
    }
    @Test fun englishSettingsSearch() {
        assertEquals("theme",SettingsPolicy.search("Theme",true).single().id)
        assertTrue(SettingsPolicy.search("Theme",false).isEmpty())
    }
}
