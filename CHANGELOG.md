# AnimaDev — Change Log / Список изменений

## 2026-10-14 · 0.4.0-beta.10-dev.51 (65) — кандидат / candidate
- RU: Восстановление заблокированного Git/CHECK FlyLife: если старый запрос не успел зарегистрировать локальный проект в Termux, новый runner принимает проверенный профиль в режиме QUERY. При отсутствии задания сохраняет NOT_STARTED, при наличии возвращает реальное состояние. START не повторяется.
- RU: На экране Git разрешено установить *новую версионированную копию* помощника при UNKNOWN локальном CHECK — даже если прежнее состояние заблокировано. Старые журналы, данные проекта и исходники сохраняются. GitHub-проекты по-прежнему защищены прежней политикой.
- EN: Recover an orphaned local-only CHECK without retrying START. Allow explicit helper version update while this specific job is UNKNOWN, then a profile-bound QUERY against the same durable ID, yielding existing status or a NOT_STARTED tombstone.

## 2026-10-14 · 0.4.0-beta.10-dev.50 (64) — кандидат / candidate
- RU: Исправлен фактический сбой FlyLife CHECK: Android ожидал runner_version 0.4.1, встроенный Termux runner dev.49 выдавал 0.4.0. Корректный ответ отвергался как RESULT_MALFORMED, сохраняя UNKNOWN и блокируя Debug APK.
- RU: Новый runner отправляет 0.4.1, строго по протоколу. Старые задания dev.49 доступны через QUERY с точным SHA старого runner и ожидаемой версией 0.4.0; ни Git CHECK, ни сборка автоматически не повторяются.
- EN: Align Termux runner JSON wire version with Android (0.4.1). Map the known dev.49 runner SHA to its true legacy wire version so an UNKNOWN CHECK can be queried by ID without replaying START. No data deletion or unsafe job reset.

## 2026-10-14 07:43 GMT+0300 · 0.4.0-beta.10-dev.49 (63) — кандидат / candidate
- RU: Исправлен реальный случай FlyLife: каталог ошибочно передавал «Origin не настроен» как значение remote, поэтому локальный Git-проект оставался «Только просмотр».
- RU: Отсутствующий origin теперь кодируется пустым значением; сохранённые старые записи мигрируются при чтении. Рабочие изменения разрешены в локальной Debug-сборке; неподтверждённый внешний remote по-прежнему отклоняется. Добавлены регрессионные тесты по каталогу и миграции.
- EN: Fix the actual FlyLife no-origin bug: a localized display label was stored as the remote value, leaving local projects in read-only mode. Catalog now uses an empty remote for no-origin, and existing persisted labels are migrated on load; security checks for unexpected remotes remain intact.

## 2026-10-14 07:29 GMT+0300 · 0.4.0-beta.10-dev.48 (62) — кандидат / candidate
- RU: Локальные Git-проекты без \`origin\` теперь поддерживают диагностику, проверку локального HEAD, Debug APK, unit и инструментальные тесты. Незакоммиченные исходники допускаются в локальном режиме и явно отмечаются. Никакого обращения к GitHub для этих операций.
- RU: GitHub-проекты сохраняют строгую проверку SHA/чистого дерева. Локальная синхронизация и подписание Release AAB пока отключены, без скрытых обходов. Только зарегистрированный корневой Git-репозиторий; внешние пути и worktree не поддерживаются для запуска.
- RU: Обновлён встроенный Termux runner с контрольными суммами, добавлены unit- и Python-регрессии. Android-приёмка ещё требуется.
- EN: True local Git repositories without origin can now run offline local HEAD checks, Debug APK builds, unit and device tests, including uncommitted sources with honest provenance. No GitHub fetch is invoked in local mode.
- EN: GitHub-backed profiles keep strict remote SHA and clean-tree validation. Local SYNC and signed Release AAB remain unavailable; no implicit Git mutations, path bypasses or acceptance claims.


## 2026-10-13 07:03 GMT+03:00 · 0.4.0-beta.10-dev.47 (61) — кандидат / candidate
- RU: «Обновить origin с GitHub» явно получает все refs/heads/* независимо от узкого remote.origin.fetch. Показ веток начинается с origin, поэтому новые GitHub-ветки доступны на первых страницах даже при множестве локальных веток.
- RU: Сохраняются незакоммиченные файлы, локальные коммиты, журнал и все worktree. Без reset, clean, push, переключения текущей ветки при fetch и удаления remote-tracking refs. Перед checkout SHA повторно сверяется с GitHub.
- RU: Добавлены автономные проверки списка из 115 веток и refspec при ограниченной конфигурации. Реальная Android-приёмка и установка APK ещё не выполнялись.
- EN: Fetch explicitly discovers all GitHub branch heads regardless of a narrow fetch refspec. Origin branches are paged first; 115-ref fixture tests cover pagination, with no reset/clean/prune/push or worktree deletion.
- EN: Candidate only. Device installation, Android integration tests and owner acceptance remain pending.

- RU: 13.10.2026: на Realme проверены 4/4 Python-теста новых веток, 150 реальных Git-ссылок (106 origin + 44 local) за 5 страниц без дубликатов и отдельная временная SQLite-база из 10 000 записей. На следующем запросе получен HISTORY_FULL без потери истории. Это не заменяет сборку APK и инструментальные Android-тесты.
- EN: Realme offline checks passed: 4/4 new catalog tests, 150 actual refs across 5 pages with no duplicates, and a 10,000-row temporary SQLite boundary check preserving all records. Android APK/build/instrumentation acceptance is still pending.


## 2026-10-13 · 0.4.0-beta.10-dev.46 (60)
- RU: Исправлен GitHub-first выбор ветки: после обновления origin доступны ветки, существующие только на GitHub. Termux проверяет текущую чистоту дерева, SHA локальной/удалённой ссылки и серверный SHA, создаёт отслеживающую ветку или обновляет отстающую строго через fast-forward.
- RU: Разошедшиеся истории, локальные ветки без origin, изменившийся GitHub SHA и незакоммиченные файлы блокируются с понятной причиной. Никаких force, reset, clean, удалений или push.
- EN: Remote-only branches can now be tracked and checked out from freshly fetched GitHub refs. Existing local branches are only fast-forwarded; divergent histories and uncommitted changes remain protected.


## 2026-10-13 · 0.4.0-beta.10-dev.45 (59)
- RU: Исправлена блокировка Git CHECK после 100 заданий (HISTORY_FULL): расширена ёмкость долговременного SQLite-журнала до 10 000 без удаления ранее записанных результатов. Android распознаёт подтверждённый отказ, предлагает обновить Termux runner и позволяет безопасно уточнить прежний Job ID новым runner. Никаких автоматических повторов Git, Gradle или принудительной очистки.
- EN: Fixed Git CHECK stuck in UNKNOWN after the 100-job limit. The persistent SQLite job capacity is raised to 10,000 without deleting existing records; Android offers a safe runner-upgrade and exact-ID recovery path.
- RU: Разрешено явное переключение на существующие локальные ветки без upstream, только с повторной проверкой чистоты дерева и SHA. Нет fetch, push, reset, merge, принудительного checkout или удаления. Разошедшиеся SHA по-прежнему блокируются.
- EN: Existing local-only Git branches can now be selected after strict worktree/SHA verification, with no network access or destructive Git operations.
- RU: Новый embedded runner 0.4.1; сохранена совместимость с историей заданий старого runner 0.4.0.
- EN: Runner 0.4.1 preserves old job history and exact-ID recovery for 0.4.0.


## 2026-10-12 · 0.4.0-beta.10-dev.44 (58)
- RU: Исправлена автопрокрутка к статусу после запуска Debug APK, Release AAB, unit- и инструментальных тестов — теперь через позиции LazyColumn вместо неинициализированного bringIntoView.
- EN: Build/test actions reliably jump to the job card; lazy lists scroll to the target item even when it was not composed yet.
- RU: Release AAB отображает обновляемые каждые ~5 секунд CPU-процентов процессов Termux по всем ядрам, свободную и занятую RAM и zRAM. Цифры читаются локально без секретов.
- EN: Release AAB shows sanitized live Termux CPU, available/used RAM and swap/zRAM readings.
- RU: Shizuku проверяет Binder в фоне, показывает индикатор проверки и обновляемый пинг (мс); без блокировки интерфейса.
- EN: Responsive Shizuku Binder refresh with spinner and automatically updated latency.


## 2026-10-12 22:30 GMT+03:00 · 0.4.0-beta.10-dev.43 (57)
- RU: В разделе Termux появилась отдельная карточка Shizuku: подключение к работающей Android Binder-службе, запрос разрешения по нажатию и отображение состояния, API/UID. Никаких новых серверов или обязательного ADB.
- EN: New Shizuku card inside Termux: direct Binder connection to an existing service, user-initiated permission request, live service/UID/API status. No new server or mandatory ADB.
- RU: Старые данные Git, сборок и журналов остаются без изменений; функции Termux работают без Shizuku. Добавлены unit-проверки состояний разрешений.
- EN: Existing Git, build and journal data are preserved; Termux works without Shizuku. New permission-state unit tests.


## 2026-10-12 12:15 GMT+03:00 · 0.4.0-beta.10-dev.42 (56)
- RU: В разделе «Ветки» добавлена отдельная кнопка «Обновить origin с GitHub»: по явному нажатию выполняется ограниченный безопасный fetch без checkout, merge, reset, prune, удаления файлов и submodule-запросов.
- EN: Branches offers explicit guarded GitHub origin refresh: remote-tracking references only, without checkout, merge, reset, prune, file removal or submodule fetch.
- RU: При несовпадении SHA старый локальный снимок можно обновить из GitHub и повторно проверить целевую ветку. Fetch запрещён для грязного дерева, worktree и неподтверждённого origin.
- EN: Update stale origin SHA snapshots before selecting a branch. Dirty repositories, worktrees and unverified origins are denied.
- RU: Три отдельные Python-проверки подтверждают отсутствие Git-операций, меняющих пользовательские файлы при обновлении origin. Прокрутка тестов больше не повторяется на каждом обновлении статуса.
- EN: Three tests verify no user worktree changes from origin refresh. Tests no longer scroll on every status transition.


## 2026-10-12 11:45 GMT+03:00 · 0.4.0-beta.10-dev.41 (55)
- RU: Release AAB теперь показывает безопасный прогресс Gradle по задачам с сохранением последних диагностических этапов без вывода паролей и данных JKS. Журнал Release приведён к стилю Debug (все/важные события).
- EN: Release AAB now reports allowlisted Gradle task progress and sanitized diagnostics without JKS contents or passwords. The Release journal follows Debug's ALL/IMPORTANT presentation.
- RU: Фоновая сборка сама находит установленный Android SDK в Termux, даже когда Android RUN_COMMAND не передал ANDROID_HOME.
- EN: Background Release automatically locates the existing Termux SDK when RUN_COMMAND omits ANDROID_HOME.
- RU: Переключение веток разрешено для любых зарегистрированных обычных Git-репозиториев с чистым деревом, совпадающим SHA в GitHub и локальной веткой; без force/reset. Репозитории можно закрепить сверху.
- EN: Guarded branch switching supports registered ordinary Git checkouts with clean worktrees and matching GitHub SHA. Projects can be pinned at the top.
- RU: Карточки инструментов выровнены по высоте. Debug, Release и тесты автоматически прокручиваются к карточке запущенного задания. Пояснены сохранение и проверка пароля JKS.
- EN: Equal-height tool cards, automatic scrolling to active builds/tests, and clearer keystore password saved-versus-verified states.


## 2026-10-12 01:00 GMT+03:00 · 0.4.0-beta.10-dev.40 (54) — RMX3301 Android tests 32/32 PASS
- RU: Все 32 инструментальных теста выполнены через Termux/ADB на RMX3301 в изолированном пакете: 32 успешных, 0 ошибок, 0 пропусков; Gradle BUILD SUCCESSFUL.
- EN: All 32 Android instrumentation tests ran via Termux/ADB on RMX3301 in an isolated app package: 32 passed, zero failures/errors/skips; Gradle BUILD SUCCESSFUL.
- RU: Тест Pulse теперь находит нужный переключатель по стабильному Compose testTag, не путая его с Emerald Glass; исправлены проверка ID изолированного пакета и устойчивость поиска кнопки Debug APK к поясняющему тексту.
- EN: Pulse test now targets a stable Compose testTag instead of Emerald Glass; isolated package ID validation and Debug APK button matcher now reflect the current UI.
- RU: Только тестовые проверки и невидимая тестовая семантика; версия приложения и данные установленного AnimaDev не изменялись. Pixel 7 доступен по ADB, но был отключён на время запуска тестов на одном устройстве.
- EN: Only test assertions and invisible UI test semantics changed; installed AnimaDev version/data were not altered. Pixel 7 remained ADB-reachable but was temporarily disconnected for single-device testing.

## 2026-10-11 · Pixel 7 instrumentation retest — 5/5 PASS (dev.40 unchanged)
- RU: Повторно выполнены 5 Compose UI-тестов на реальном Pixel 7 (Android API 37) в изолированном пакете devicefixture. XML-отчёт: 5 тестов, 0 ошибок, 0 пропусков; BUILD SUCCESSFUL.
- EN: Five Compose UI tests reran on real Pixel 7 (API 37), isolated devicefixture. XML: 5 tests, zero failures/errors/skips; BUILD SUCCESSFUL.
- RU: Причина прежнего падения — тест ожидал английское Clean на телефоне с русской локалью, где правильная строка — «Чисто». Проверка теперь поддерживает оба языка и требует видимости статуса.
- EN: The previous failure was test-only: expected English Clean while the Russian UI correctly renders Чисто. The assertion now accepts both locales and checks visibility.
- RU: Код приложения, его версия и подписанная сборка dev.40 не менялись; рабочие данные AnimaDev не затрагивались.
- EN: No application runtime/version changes; installed AnimaDev data remained untouched.

## 2026-10-11 · Pixel 7 device QA of dev.40 (no app version change)
- RU: На Pixel 7 (API 37) реально запущены пять Compose UI-тестов в отдельном пакете devicefixture: 4 прошли, 1 упал.
- EN: Five actual Compose instrumentation tests ran on Pixel 7 (API 37) in isolated devicefixture: 4 passed, 1 failed.
- RU: Новый тест сворачиваемого списка рабочих каталогов прошёл. Непройденный тест — видимость статуса Clean на карточке проекта; причина остаётся на проверке.
- EN: The collapsed worktree-list regression passed. Project-card Clean status visibility is unresolved; this is not a fully green device test run.

## 2026-10-11 12:17 GMT+03:00 · 0.4.0-beta.10-dev.40 (54)
- RU: По обратной связи исправлена неудобная компоновка: «Ветки и переключение», Git, тесты, сборка и история теперь всегда выше рабочих каталогов.
- EN: Fixed dashboard hierarchy: Branches, Git, Tests, Build and History appear before the Worktrees section.
- RU: Блок «Рабочие каталоги» по умолчанию свёрнут в небольшую карточку со счётчиком, статусом текущей ветки и отдельной кнопкой обновления.
- EN: Worktrees default to a compact collapsed summary with count, current-branch status and refresh.
- RU: Полный список открывается по нажатию и скрывается обратно. Каталоги выводятся отдельными ленивыми элементами прокрутки, без создания десятков тяжёлых карточек сразу.
- EN: Expand/collapse on demand; rows use the outer LazyColumn for efficient on-screen rendering.
- RU: Обновлён Compose UI-тест раскрытия/сворачивания. Никаких изменений Git, файлов и функций сборки.
- EN: Added a Compose UI regression test for collapsed-by-default behavior without changing Git/file/build operations.
- RU: Ночная ветка AnimaQuiz feature/design-three-themes удалена самим владельцем; main 3.7.22 (101) сохранена и проверена.
- EN: Owner completed the requested branch cleanup; AnimaQuiz main release 3.7.22 (101) remains verified.

## 2026-10-11 11:57 GMT+03:00 · 0.4.0-beta.10-dev.39 (53)
- RU: Проект теперь показывает основной каталог и все найденные связанные Git-worktree прямо над инструментами. Видны путь, ветка, SHA и число незакоммиченных файлов, если Git подтвердил его.
- EN: The project dashboard now exposes every discovered linked worktree above tool cards, with path, branch, local SHA and safe uncommitted-file count.
- RU: «Обновить рабочие каталоги» заново читает их в Termux без checkout, fetch или изменения файлов; дополнительные сведения и копирование пути — по нажатию на карточку.
- EN: Refresh rereads local worktrees in Termux without checkout, fetch or writes; detail dialog exposes a copyable path.
- RU: Состояние отправки в GitHub обозначено как непроверенное в обзоре; совпадение коммитов в main не подтверждает отправку работы из отдельного worktree.
- EN: A clean main or matching SHAs never falsely certifies a different worktree as pushed to GitHub.
- RU: В «Ветках» объяснено отличие Git-ссылок от рабочих каталогов; Pulse сохранён.
- EN: Branches explains refs versus worktrees; Pulse remains unchanged.
- RU: Удаление ночной ветки AnimaQuiz запрошено отдельно, но из доступных коннекторов заблокировано защитой Git. Удаление не выполнялось.
- EN: Destructive cleanup of AnimaQuiz night branch remains blocked by the connected Git tool's guard; no branch was deleted.

## 2026-10-11 11:31 GMT+03:00 · 0.4.0-beta.10-dev.38 (52)
- RU: Исправлено наложение шестерёнки/заголовка Emerald Glass на статус-бар Android. Пользовательские верхние панели теперь учитывают системный отступ.
- EN: Fix Emerald custom toolbar overlapping the Android system status bar by respecting status-bar window insets.
- RU: «Ветки и переключение» вынесены сразу под названием проекта, текущей веткой и SHA; дублирующая кнопка удалена из нижней части карточки инструментов.
- EN: Move Branches & switching beside project branch/SHA, removing the buried duplicate beneath tool tiles.
- RU: Исправлен перенос надписи «Рабочее дерево» на экранe Branches; заголовок, статус и пояснение теперь расположены вертикально.
- EN: Correct broken Working tree label layout using a responsive stacked status section.
- RU: Git объясняет различие между совпадающими коммитами и незакоммиченными файлами. При 1–3 изменениях показываются их имена. Автоматический reset/checkout/push запрещён.
- EN: Explain matching remote commits versus an uncommitted working tree, showing up to three changed filenames; never perform automatic reset/checkout/push.
- RU: Ветки origin — последний локальный снимок, а не доказательство актуальности GitHub; это явно обозначено на экране.
- EN: Branches view now clearly labels origin refs as local snapshots, not live GitHub state.

## 2026-10-11 10:24 GMT+03:00 · 0.4.0-beta.10-dev.37 (51)
- RU: Emerald Glass больше не использует старое выпадающее меню с тремя точками. Во всех вложенных экранах, включая Git, открыт единый графитовый заголовок с отдельной шестерёнкой.
- EN: Emerald Glass removes the legacy three-dot popup. Project tools, including Git, now have a consistent graphite toolbar and a direct Settings action.
- RU: На главной странице Projects вместо всплывающего меню — кнопка настроек. Экран Settings дополнен крупными карточками журнала изменений, документации и раздела «О программе».
- EN: Projects now links straight to Settings, which includes accessible cards for changelog, documentation and About.
- RU: Возврат из справки и списка изменений ведёт обратно в Settings, а затем к предыдущему инструменту; Git и Termux не теряют контекст.
- EN: Support pages return to Settings, then to the invoking tool; Git and Termux context is preserved.
- RU: Pulse и его меню оставлены без изменений; установленные данные, Git и процесс сборки не затронуты.
- EN: Pulse, persisted data, Git operations and build workflows are unchanged.

## 2026-10-11 01:34 GMT+03:00 · 0.4.0-beta.10-dev.36 (50) · Device-test diagnostics
- RU: Compose UI-тесты реально запускались на Pixel 7 в отдельном пакете devicefixture, не затрагивая рабочий AnimaDev. 4/4 теста не увидели иерархию, так как дисплей находился в режиме Dozing / lockscreen. Прогон нужен на разблокированном экране.
- EN: Four isolated Emerald Compose tests were executed on Pixel 7. All were blocked by the phone being dozing/locked (no visible Compose hierarchy), not by asserted UI behavior. Repeat on an unlocked device.
- RU: Миграция TestRule на Compose v2 сделана; экран телефона автоматически не разблокировался.
- EN: Migrated the fixture to Compose TestRule v2; did not wake or bypass the device lock.

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
