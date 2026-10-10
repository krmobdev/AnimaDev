# AnimaDev · 0.4.0-beta.10-dev.34

Android 13+ companion for **local Git, tests and Android builds in Termux**. Written in Kotlin and Jetpack Compose (Material You / Pulse). No mandatory VPS, AnimaBridge or GitHub API token is needed to use the installed app.

**English:** Local Android Git/Gradle assistant with safe per-project operations and persistent history.
**Русский:** Локальный Android-инструмент управления Git и сборками с журналами и безопасными ограничениями.

## Функционал / Features

| Раздел / Area | Возможности / Capabilities |
|---|---|
| Projects / Проекты | Обнаружение существующих локальных Git-репозиториев и worktree, сохранение вкладок; просмотр ветки, SHA и состояния |
| Git | Проверка origin / GitHub SHA, разрешённые операции и явная безопасная синхронизация fast-forward |
| Tests / Тесты | Unit-тесты Gradle, Android instrumentation через разрешённый ADB, JUnit-отчёты |
| Debug APK | Повторное выполнение Gradle задач с `--rerun-tasks`, проверка SHA-256 APK и установка через системный Android Package Installer / FileProvider |
| Release AAB | Подпись через JKS с защищённой передачей, проверка артефакта, сохранение в выбранную папку |
| Jobs / Задания | Время, этап, журнал, восстановление по ID, запрос статуса и поддерживаемая отмена Ctrl+C |
| History / История | Старые задания и журнал отдельно от рабочего экрана |
| Termux | Диагностика Git, Python, Java, SDK; встроенное руководство по установке компонентов |
| Files / Файлы | Выбранная папка в основной памяти или стандартные Downloads; очистка только временного кэша AnimaDev |
| Help / Справка | Локальная документация, история изменений и сведения о версии |
| Language / Язык | Русский, English и системный язык. Метки и диагностические сообщения локализованы, сырой вывод терминала сохраняется неизменным |

## Быстрый старт / Quick start

1. Install Termux from a trusted source, open it once and explicitly grant RUN_COMMAND integration. / Установите Termux, откройте и разрешите RUN_COMMAND.
2. Open **Termux → Check environment**, read **Build setup guide** and verify Java, SDK and Git.
3. Add an existing local Git repository under **Projects** (nothing is cloned automatically). / Добавьте локальный Git-репозиторий.
4. In Git tools verify branch, origin and GitHub SHA before TEST/BUILD. / Сверьте GitHub SHA и чистоту дерева.
5. Inspect Job ID and Journal during execution. **QUERY** does not start another job. / Проверяйте статус прежнего задания по ID.
6. For Debug tap Build and open APK through the **native Android installer**. AAB is for publishing, not direct device installation.
## Два скина / Two skins

- **Pulse / Material:** existing UI preserved. Stored settings, project data and results stay intact.
- **Emerald Glass (opt-in):** graphite/emerald theme inspired by owner-approved mockups.
  Implemented across Projects, project dashboard, root navigation, Build, Tests, History, Branches, Termux, Settings index and About. Additional polish is pending Android visual acceptance.
- Switch using **Settings → Appearance → Emerald Glass**. Turning it off restores
  your previous Pulse/Material style without resetting projects or jobs.
- [Emerald Glass implementation checklist](docs/EMERALD_GLASS_ROADMAP.md).

## Documentation / Документация

- [Список изменений / Changelog](CHANGELOG.md) — newest entries at top, including date/time and version.
- [Руководство пользователя / User guide (RU)](docs/USER_GUIDE.md)
- [User Guide (EN)](docs/USER_GUIDE_EN.md)
- [Подготовка Termux (RU)](docs/TERMUX_SETUP.md)
- [Termux build setup (EN)](docs/TERMUX_SETUP_EN.md)
- [Архитектура / Architecture](docs/architecture.md)
- [План поставки / Delivery roadmap](docs/BUILD_DELIVERY_ROADMAP.md)

The guides and change log are bundled offline into the APK from these **same GitHub files**, so editing a document in GitHub and rebuilding updates in-app help automatically.

## Safety / Безопасность

- No automatic push, force updates, destructive clean/reset, or implicit Git branch switch.
- Never expose signing secrets or SSH keys in logs, Git or command arguments.
- Run one Gradle job per protected build slot. UNKNOWN requires confirmation, not a duplicate START.
- Cache clearing deletes only app-owned temporary copies and exports, never Termux, SDK, Gradle dependency cache, job SQLite or projects.
- Installation uses a checksum-verified copy and Android's system Package Installer.
- Project execution requires an explicitly supported and verified profile. Additional worktrees may be read-only.

## Development / Разработка

- Active integration branch: `feature/termux-probe`; do not merge to `main` without explicit owner review.
- Kotlin, Compose, AGP 9.3.1, Gradle 9.5.0, compileSdk 37, minSdk 33, Java 21 toolchain (bytecode 17).
- Gradle (on an already prepared Termux instance):

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --rerun-tasks --console=plain
```

Real Android UI/device acceptance requires on-device tests, not just successful compilation.

For older stage reports and design notes, see the [docs/](docs/) directory. Old beta constraints are historical and superseded by the current `AGENTS.md` owner authorization.
