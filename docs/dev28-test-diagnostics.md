# AnimaDev dev.28 — проверка исправлений, 2026-10-10

Исходники: ветка feature/termux-probe, коммит 7907051410d6e56830ccdad13a6edcc6cab610b6.
Работа выполнена через MCP AnimaBridge Stage1 в Termux. Каждый коммит отправлен на GitHub до соответствующих проверок.

## Исправления

- Текстовые и тональные действия заменены общей кнопкой ActionButton: явная рамка 1.5 dp, высота минимум 48 dp, центрированная подпись, ширина карточки. Диалоговые действия сохраняют компактный размер. Обновление помощника использует этот компонент в экранах подключения, тестов, сборки и журнала.
- Instrumentation entry test принимает строго заданные пакеты AnimaDev, devicefixture и изолированный суффикс .animadevtests.j<32 hex>. Сохраняются проверки запуска Activity и совпадения фактического пакета Activity с targetContext. Ранее whitelist отвергал создаваемую помощником идентичность.
- JournalCache проверяет metadata и history относительно подтверждённого project_id запроса, а не только legacy animaquiz. Чужие проекты остаются запрещены.
- Подтверждённые метаданные результата сохраняются до чтения страниц. Журнал может показать состояние, количество тестов, код и причину ошибки ещё до загрузки полного лога. Добавлен доступный путь обновления помощника в журнале.
- Ошибки TEST_DEVICE названы инструментальными, включая старые сохранённые сообщения. Не меняются состояния заданий и фактические JUnit counts.
- Новые JUnit failure_details содержат ограниченные и очищенные имена тестов и причины; UI выводит их в результатах и журнале.
- История относит TEST_DEVICE к тестам, включает сохранённое device_test_job.
- Устранена неявная зависимость debug lint от generated release assets и пять ошибок NonObservableLocale; текущая Android locale читается через LocalConfiguration.

## Выполненные проверки

- Полный runner fixture suite: 209/209, 0 failures.
  python -m unittest discover -s runner -p 'test_*.py'
- Android JVM unit suite: 175/175, 0 failed, 0 skipped, 20 JUnit XML.
- :app:lintDebug: успешно, без отключения проверок и без baseline.
- :app:assembleDebug и :app:compileDebugAndroidTestKotlin: успешно.
- :app:connectedDebugAndroidTest: 17/17, 0 failed, 0 skipped.
  Физический Pixel 7, Android 17 / API 37; ADB 192.168.1.132:44163.
  Изолированный пакет: com.animacode.animadev.animadevtests.j1010ab00112233445566778899000028.
  Использован init script, сгенерированный реальным runner.device_init_script; Gradle class filters не применялись.
- Набор на устройстве: MainActivity 1, PulseWorkspace 8, SigningSecretVault 1, ActionButton 3, JournalCache 4.
  Проверены светлая/тёмная темы, ширина и центрирование кнопки, disabled-состояние, приём dynamic project logs/history, отказ чужому проекту с откатом курсора, сохранение причины ошибки после повторного открытия кэша.
- Целостность APK: ZIP CRC OK; SHA встроенного runner совпадает с assets/runner/runner.sha256.
- apksigner verify: успешно. Сертификат совпадает с предыдущим dev.27.
  SHA256 сертификата: dcc04062e9d1cf6bbd8c0f897d77fe4635f76451c2a04691e2e0f3c4d9cc40ea.

## Артефакт

Версия: 0.4.0-beta.8-dev.28, versionCode 42.
Файл: /storage/emulated/0/Download/AnimaDev_0.4.0-beta.8-dev.28_debug.apk
Размер: 31610139 байт.
SHA256: 53913390070a791a848ae350487535187d10df00111ca4ff311966934cf89175
Контрольная сумма копии в Downloads проверена отдельно и совпадает с исходным APK.

После установки APK нажать «Обновить помощник». Установка helper идёт штатным механизмом с проверкой SHA; runner history и jobs.sqlite3 сохраняются. Старые завершённые результаты не переписываются как успешные.

## Границы проверки

Это полный существующий Android instrumentation suite AnimaDev, дополненный семью регрессионными проверками. UI проверялся на Pixel с отдельным application ID; реальные данные владельца и QuizApp не очищались. Полная связка UI установленного приложения владельца → Termux RUN_COMMAND → live callbacks после обновления helper в этой проверке не запускалась. Перенос APK выполнен в Downloads устройства с Termux; удалённые действия GitHub выполнялись только push тестовой ветки, без Actions и merge.

Команды проверки в реестре AnimaBridge: 1010ab00112233445566778899000017 (runner fixtures), 1010ab00112233445566778899000030 (финальные Gradle проверки и Pixel), 1010ab00112233445566778899000033 (JUnit counts, APK CRC, непрерывность сертификата).
