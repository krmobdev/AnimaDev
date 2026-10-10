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
    @Test fun translatesRepresentativeScreensAndErrors() {
        assertEquals("Refresh list",AppTranslation.translate("Обновить список",true))
        assertEquals("Safe checkout requires a local branch verified against origin. Local-only or divergent branches cannot be switched yet.",
            AppTranslation.translate("Для безопасного переключения нужна локальная ветка, подтверждённая в origin. Только локальные или расходящиеся ветки пока не переключаются.",true))
        assertEquals("Android Package Installer did not open. Check permission to install APKs.",
            AppTranslation.translate("Системный установщик Android не открылся. Проверьте разрешение на установку APK.",true))
        assertEquals("Some unit tests failed. Open the journal for test names and causes.",
            AppTranslation.translate("Есть проваленные unit-тесты. Откройте журнал: там имена тестов и причины.",true))
        assertEquals("Branch not authorized",AppTranslation.translate("Ветка не разрешена",true))
    }

    @Test fun formatsDynamicSummariesWithoutChangingIdentifiers() {
        assertEquals("187 of 189 passed",AppTranslation.translate("187 из 189 успешно",true))
        assertEquals("Failed: 2 · skipped: 0",AppTranslation.translate("Ошибок 2 · пропущено 0",true))
        assertEquals("Elapsed: 3 min 27 sec",AppTranslation.translate("Прошло: 3 мин 27 с",true))
        assertEquals("Loaded branches: 25 · more pages available",AppTranslation.translate("Загружено веток: 25 · есть следующая страница",true))
        assertEquals("Size: 55 MB · Git 7d3faa2d72da",AppTranslation.translate("Размер: 55 МБ · Git 7d3faa2d72da",true))
        assertEquals("Job · AnimaQuiz · Unit tests",AppTranslation.translate("Задание · AnimaQuiz · Unit-тесты",true))
        assertEquals("/storage/emulated/0/Downloads/Сборки/MyApp.apk",
            AppTranslation.translate("/storage/emulated/0/Downloads/Сборки/MyApp.apk",true))
        assertEquals("git -c core.pager=cat status --porcelain",
            AppTranslation.translate("git -c core.pager=cat status --porcelain",true))
        assertEquals("abc123 job_ID 2026-10-10",AppTranslation.translate("abc123 job_ID 2026-10-10",true))
    }

    @Test fun englishLocaleLeavesRussianUntouchedWhenNotSelected() {
        for(s in listOf("Ожидание запуска","Ошибок 2 · пропущено 0","Тесты не завершились успешно.","Запустить тесты")) {
            assertEquals(s,AppTranslation.translate(s,false))
        }
    }

    @Test fun englishSettingsSearch() {
        assertEquals("theme",SettingsPolicy.search("Theme",true).single().id)
        assertTrue(SettingsPolicy.search("Theme",false).isEmpty())
    }
}
