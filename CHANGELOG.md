# AnimaDev — Change Log / Список изменений

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
