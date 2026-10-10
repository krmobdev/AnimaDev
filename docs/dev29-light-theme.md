# AnimaDev dev.29 — светлая тема Pulse
Дата: 2026-10-10. Ветка feature/termux-probe.
Проверенный коммит: e5a2ef189806244e0b14cd23a6bb83d19b56c41d.

Причина: MainActivity принудительно задавала dark=true при pulse=true; выбранная светлая тема игнорировалась. Карточки проектов/веток и отдельные подписи также использовали фиксированные тёмные цвета.

Исправление:
- Выбор light/dark/system независим от переключателя Pulse.
- Pulse получил собственную светлую палитру с контрастными зелёными акцентами.
- Карточки проектов, монитор в workspace, активные ветки и подписи сборки читают MaterialTheme.colorScheme.
- Цвет системных панелей определяется фактически выбранным режимом.
- Существующие interface_v1 preferences сохраняются; выбор темы не сбрасывается.
- Версия 0.4.0-beta.8-dev.29, versionCode 43. Помощник не менялся.

Проверки на исходниках, опубликованных в GitHub до запуска:
- testDebugUnitTest: 178/178, failed=0, skipped=0, 21 XML.
- lintDebug, assembleDebug, compileDebugAndroidTestKotlin: успешно.
- connectedDebugAndroidTest на физическом Pixel 7 (Android 17/API 37): 18/18, failed=0, skipped=0.
- Изолированный applicationId com.animacode.animadev.animadevtests.j1010ab00112233445566778899000029.
- Новый AppearanceThemeInstrumentedTest работает с реальным MainActivity и экраном настроек: тёмный Pulse → светлая тема; проверка фактической яркости фона; пересоздание Activity; переключение Pulse off/on с сохранением светлой темы; возврат к тёмной и повторное пересоздание.
- Три unit-теста проверяют приоритет light/dark над системным режимом и следование системной теме.
- APK ZIP CRC и SHA встроенного runner: OK. apksigner verify: OK, прежний debug-сертификат dcc04062e9d1cf6bbd8c0f897d77fe4635f76451c2a04691e2e0f3c4d9cc40ea.

Артефакт: AnimaDev_dev29.apk, 30689124 байт.
SHA256: 4e24c1c7cce0c36104fed94bd79edb69a3a11e68bc76230620088649d1c8b349
Downloads телефона с Termux и /sdcard/Download Pixel 7; контрольная сумма копий совпадает.
Команды AnimaBridge: 1010ab00112233445566778899000044 (Gradle и Pixel), 1010ab00112233445566778899000045 (верификация), 1010ab00112233445566778899000046 (доставка).
Данные установленного приложения владельца и QuizApp не очищались. Приёмка владельцем после установки APK остаётся отдельной от прогона в изолированном пакете.
