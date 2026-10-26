# AnimaDev · 0.4.0-beta.10-dev.46

**AnimaDev** — Android-приложение для тех, кто разрабатывает и собирает проекты **прямо на телефоне через Termux**. Оно объединяет Git и worktree, Gradle-тесты, сборку Debug APK и подписанных Release AAB, мониторинг задач, журналы и историю операций. Требуется Android 13+; интерфейс написан на Kotlin и Jetpack Compose.

**English:** An Android 13+ companion for local Git, Gradle tests, APK/AAB builds and job history in Termux. No mandatory PC, VPS, AnimaBridge or GitHub API token is required by the installed app.

**Статус / Status:** `0.4.0-beta.10-dev.46`. Владелец подтвердил ручное тестирование приложения на устройстве 11.10.2026; автоматизированная UI-проверка Emerald ещё не завершена. Публичный GitHub Release с готовым APK пока не опубликован — см. [инструкцию по подготовке Termux](docs/TERMUX_SETUP.md) и раздел разработки ниже.

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
| AnimaBridge service | Управление зарегистрированной службой runit: Status / Start / Stop / Restart через Termux RUN_COMMAND; независимый от расположения агента интерфейс |
| Shizuku | Прямое подключение к уже работающей службе через Binder, запрос разрешения, состояние и UID; без зависимости от ADB/Render |
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

## Worktree overview / Рабочие каталоги

**Main project tools come first.** The Worktrees section below the tools is collapsed by default; it shows the current branch/status and number of other worktrees. Tap **Show** to expand lazy-loaded entries, or **Hide** to collapse them again. The read-only Termux catalog discovers linked working directories. Each entry includes its branch, path, local HEAD, and uncommitted-file count when available. Tap another worktree for copyable path details; AnimaDev never switches to or mutates it implicitly. Refresh reads from Termux, **not live GitHub**. Compare the actual remote branch separately before claiming changes were pushed.

## Git commit vs working tree / Коммиты и рабочие файлы

Matching local and GitHub commit IDs confirms commit alignment **only**. A dirty working tree means tracked or untracked local file differences still exist. AnimaDev reports filenames and blocks unsafe fast-forward/checkout rather than silently resetting or committing user files. The Branches view lists cached local/origin references; refresh reads them from Termux without a network fetch. Git → Check updates verifies the current GitHub commit separately.

## Emerald navigation / Навигация Emerald

The new skin has no legacy three-dot overflow on the Projects or project tool screens. A dedicated gear action opens Settings, where Change log, Documentation and About are first-class cards. Back returns to the previous tool through Settings. The older Pulse skin remains unchanged.

## Android UI testing / Проверка на устройстве

Emerald Compose tests use a separate `-PanimadevDeviceFixture=true` package so the installed AnimaDev application and its data remain untouched. Pixel 7 was reachable via ADB; the 2026-10-11 run was inconclusive because its screen was dozing/locked, so all four tests reported no Compose hierarchy. Rerun with the device awake and unlocked; do not treat this as a successful UI test.

## Два скина / Two skins

- **Pulse / Material:** existing UI preserved. Stored settings, project data and results stay intact.
- **Emerald Glass (opt-in):** graphite/emerald theme inspired by owner-approved mockups.
  Implemented across Projects, project dashboard, root navigation, Build, Tests, History, Branches, Termux, Settings index and About. Live job monitoring, journal filtering/export and nested settings now use Emerald components. Adaptive narrow-screen layouts, large-font navigation/tabs and lightweight color transitions have been added. The owner has confirmed a manual on-device check; automated Emerald UI acceptance still requires a conclusive run in the isolated device-fixture package.
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

- `main` is the owner-approved baseline; ongoing integration work happens in `feature/termux-probe`. Do not merge further development changes without owner review.
- Kotlin, Compose, AGP 9.3.1, Gradle 9.5.0, compileSdk 37, minSdk 33, Java 21 toolchain (bytecode 17).
- Gradle (on an already prepared Termux instance):

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --rerun-tasks --console=plain
```

Real Android UI/device acceptance requires on-device tests, not just successful compilation.

For older stage reports and design notes, see the [docs/](docs/) directory. Old beta constraints are historical and superseded by the current `AGENTS.md` owner authorization.

## AnimaBridge service (experimental dev.48)

The Termux page has an **AnimaBridge** service card. First install `termux-services` and register the runit service using [scripts/install_animabridge_service.sh](scripts/install_animabridge_service.sh), following [docs/TERMUX_SETUP.md](docs/TERMUX_SETUP.md). The service is initially **down**: the running manual 3.5 agent is not stopped or duplicated. Once a manual agent is stopped, the app can start it via the registered service. The app never stores the agent's filesystem path or pairing secret; it invokes only fixed `sv status/up/down/restart` operations through Termux RUN_COMMAND. Shizuku is a separate Binder connection and cannot open Termux-private files.
