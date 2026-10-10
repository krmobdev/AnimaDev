# AnimaDev — Change Log / Список изменений

## 2026-10-11 01:25 GMT+03:00 · 0.4.0-beta.10-dev.36 (50)
- RU: Emerald Glass: нижняя навигация автоматически адаптируется к экрану менее 360 dp и увеличенному шрифту; статусы могут переноситься на две строки.
- EN: Emerald Glass bottom navigation adapts to screens below 360 dp and large font settings; status badges support wrapping.
- RU: Четырёхпозиционные вкладки (история и журналы) прокручиваются на узких экранах, выбор плавно анимируется без тяжёлых эффектов.
- EN: Four-item tabs scroll on narrow screens; selection uses lightweight color animation.
- RU: Рабочий экран проекта адаптирует ветку, SHA, кнопки и сетку инструментов при крупном шрифте. Добавлено экономное кэшированное зелёное свечение карточек.
- EN: Project dashboard adapts branch/SHA controls and tool tiles for large fonts; adds cached ambient card glow.
- RU: Добавлены UI-тесты навигации и вкладок с крупным шрифтом; запускаются только в отдельном пакете devicefixture, не затрагивая установленный AnimaDev.
- EN: Added large-font UI tests using an isolated devicefixture package, separate from the installed AnimaDev.
- RU: Визуальная приёмка по референсам и фактическая оценка 60 fps ещё необходимы.
- EN: Reference matching and on-device frame pacing validation are still required.

## 2026-10-11 00:19 GMT+03:00 · 0.4.0-beta.10-dev.35 (49)
- RU: Emerald Glass: переработаны монитор задания, живой журнал и журнал Release AAB с реальными статусами, этапами, временем, фильтрами и экспортом. Секреты не сохраняются в журнале.
- EN: Emerald Glass adds a redesigned live job monitor, streaming journal and sanitized Release AAB timeline with real statuses, stages, elapsed time, filters and export.
- RU: Вложенные настройки переведены на стеклянные карточки без изменения механизмов сохранения параметров.
- EN: Nested settings now use Emerald cards while preserving existing preference storage and controls.
- RU: Добавлены точные русские подписи для Emerald Glass; SHA, пути, Job ID и сырой вывод Gradle/Git не переводятся.
- EN: Added exact Russian Emerald captions while leaving SHA, paths, job IDs and raw Gradle/Git output unchanged.
- RU: Добавлены проверки локализации и обратной навигации из журнала в Tests, Build или проект.
- EN: Added regression tests for safe translation and journal back navigation.
- RU: Визуальная приёмка на устройстве и запуск instrumentation-тестов остаются обязательными.
- EN: Device visual acceptance and running instrumentation tests remain outstanding.

## 2026-10-11 00:07 GMT+03:00 · 0.4.0-beta.10-dev.34 (48)
- RU: Продолжен скин Emerald Glass: экраны Tests (Unit / On device), History с фильтрами, Branches с активной веткой и Termux с реальными версиями инструментов.
- EN: Continued Emerald Glass across Unit/Device tests, filtered History, active Branches and the real Termux tool inventory.
- RU: Добавлены карточки подписи и состояния Release AAB без изменений защищённого обмена JKS/паролями; оформление Git, SHA и журналов основано на реальных данных.
- EN: Styled Release AAB signing and status while keeping the existing protected keystore/password transfer and real Git/job data.
- RU: Обновлены главное меню Settings и About, сохранив все прежние настройки и рабочую ссылку GitHub.
- EN: Styled Settings and About without changing preferences, and preserved the working GitHub link.
- RU: В Emerald Projects добавлено рабочее меню перехода к настройкам, истории изменений и документации; старый Pulse доступен по-прежнему.
- EN: Added a functioning Emerald Projects menu for Settings, Change log, About and Documentation; Pulse remains available.
- RU: UI-тесты компилируются, реальный запуск на Android и визуальная приёмка владельцем всё ещё необходимы.
- EN: Device UI tests require a real Android run and owner visual acceptance.

## 2026-10-10 23:50 GMT+03:00 · 0.4.0-beta.10-dev.33 (47)
- RU: Начат отдельный скин Emerald Glass по утверждённым макетам: чёрно-графитовая палитра, зелёные акценты, стеклянные карточки и векторная графика Compose.
- EN: Started the opt-in Emerald Glass skin from approved references: dark graphite, emerald highlights, frosted cards and Compose-drawn vector graphics.
- RU: Новый экран списка проектов показывает реальные ветки, состояние дерева, SHA, сохранены добавление и удаление проектов.
- EN: New Projects screen uses real branches, worktree status and SHA; existing add/remove actions remain.
- RU: Новый рабочий экран проекта с реальными Git/Tests/Build/History действиями, копированием SHA и защищённым переходом к сборке.
- EN: Redesigned project dashboard with functional Git/Tests/Build/History actions, copy SHA and guarded Build entry.
- RU: Начато оформление Debug APK Build, существующие Git-проверки и подпись Release AAB не меняются. Pulse доступен как прежде.
- EN: Began Debug APK Build restyling without changing Git guards or Release signing; Pulse is preserved.
- RU: План оставшихся экранов и проверок — docs/EMERALD_GLASS_ROADMAP.md.
- EN: Remaining screens and acceptance checklist — docs/EMERALD_GLASS_ROADMAP.md.

## 2026-10-10 23:11 GMT+03:00 · 0.4.0-beta.9-dev.32 (46)
- RU: Расширена английская локализация экранов Git, веток, тестов, сборок, монитора, истории, подписи и Termux.
- EN: Expanded English coverage for Git, branches, tests, builds, job monitoring, history, signing and Termux.
- RU: Переведены сообщения ошибок Android-установщика, ADB, GitHub SHA и защищённой передачи Release AAB; технические коды остаются исходными.
- EN: Localized native installer, ADB, GitHub SHA and secure Release AAB error messages while preserving technical error codes.
- RU: Добавлены английские форматы длительности, числа тестов, памяти и статистики репозиториев; пути, SHA и идентификаторы не переводятся.
- EN: Localized elapsed time, test totals, RAM and repository counts without modifying paths, SHA values or job IDs.
- RU: Добавлены регрессионные unit-тесты английских строк и сохранности технических значений. Сырой журнал Gradle/Git остаётся без перевода.
- EN: Added translation and technical-data preservation tests. Raw Gradle/Git logs remain in their original language.

## 2026-10-10 20:36 CEST · 0.4.0-beta.9-dev.31 (45)
- RU: Добавлена очистка безопасного временного кэша AnimaDev (копии APK и отчётов); данные, Git и Gradle не удаляются.
- EN: Clear AnimaDev temporary APK/report cache without touching Git, Gradle dependencies, databases or jobs.
- RU: Debug APK выполняется с полной повторной обработкой задач Gradle (`--rerun-tasks`).
- EN: Debug APK uses a full Gradle task rerun (`--rerun-tasks`).
- RU: Монитор активного задания на рабочем экране: этап, время, журнал, запрос статуса и безопасная отмена.
- EN: Running-job workspace monitor: stage, elapsed time, journal, status query and safe cancellation.
- RU: В меню добавлены «Список изменений», «О программе» и «Документация»; инструкция для подготовки Termux.
- EN: Added Change log, About and Documentation screens; detailed Termux environment setup guide.
- RU: Добавлена подготовка интерфейса для выбора английского языка.
- EN: Added language selection and initial English UI translations.

## 2026-10-10 · 0.4.0-beta.8-dev.30 (44)
- RU: Установка APK через системный установщик Android и `FileProvider`, без команды Termux при открытии.
- EN: Android native APK installer via `FileProvider`; no Termux command when opening the installer.
- RU: Сохранение вкладки тестов/сборки после выхода из журнала.
- EN: Preserve Tests/Build subtab after leaving the journal.
- RU: Исправлены две проверки регрессии; unit-тесты 189/189.
- EN: Fixed two regression tests; all 189 unit tests passed.

## 2026-10-10 · 0.4.0-beta.8 (prior changes)
- RU: Выбранная папка для APK/AAB, проверка SHA-256, отдельный журнал Release.
- EN: Custom primary-storage APK/AAB destination, SHA-256 verification and Release event journal.
- RU: Ссылку на папку AAB заменили вместо действия «Установить AAB».
- EN: Replaced misleading AAB installation action with Open folder.
- RU: История завершённых сборок отделена от рабочего экрана.
- EN: Completed builds are kept in History rather than the active Build screen.

## 2026-10-09 · 0.4.0-beta series
- RU: Material You / Pulse, проекты, Git, локальные unit/инструментальные тесты, журнал с поиском и экспортом, монитор и Termux.
- EN: Material You / Pulse, projects, Git, local unit/device tests, searchable/exportable journal, job monitor and Termux overview.

---
Entries are newest first. Dates above describe recorded development milestones, not Play Store publication dates.
Записи в обратном хронологическом порядке. Эти даты не означают публикацию в Google Play.
