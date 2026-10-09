# 0.4.0 Beta: проекты и границы исполнения

minSdk 33. Навигация верхнего уровня — Проекты/Termux, внутри проекта четыре
инструмента: Git/Тесты/Сборка/История. Тема system/light/dark и dynamic colors
хранятся отдельно в interface_v1. Реестр projects_v1 добавляется без изменения
repos_v2, monitor_settings, runner jobs.sqlite3 и Android journal_cache.sqlite.
Первое открытие реестра добавляет прежний AnimaQuiz. Удаление записи не трогает
файлы и задания; выбранный ID не подменяется другим проектом после удаления.

Исполнение Beta разрешено только паре id=animaquiz и канонический ~/quizapp.
Gateway требует явные projectId/projectPath при START, без значений по умолчанию.
Новые локальные репозитории добавляются для просмотра; их TEST/CHECK/SYNC не
подключены и не перенаправляются на QuizApp. Полноценные профили действий —
отдельный следующий этап. Runner повторно проверяет ветку/origin/чистоту/ff.

Read-only catalog имеет отдельный nonce/callback, не создаёт задания START.
Сканирование ограничено непосредственными публичными каталогами HOME и
HOME/anima-workspace (один уровень). Hidden, storage, SDK, cache, venv и опасные
символьные ссылки исключены. Проверяется обычный каталог .git; worktree .git-файл
в Beta не подключается. Лимиты 40 репозиториев, 120 каталогов, ~12 секунд обзора,
2 секунды на Git-чтение и 64 KiB вывода. Конфигурации include/filter/url/helpers
блокируют метаданные; хуки/fsmonitor отключены, global/system Git config не
исполняется. В постоянный каталог remote попадает только безопасный GitHub label;
нестандартные URL и userinfo не сохраняются.

Termux обзор проверяет Python/Git/Java, фиксированный SDK и сохранённые Gradle
дистрибутивы без запуска Gradle-проекта. Управление пакетами, обновления, Wake Lock
и фоновое обслуживание остаются недоступными. В будущем это отдельные фиксированные
операции с проверяемыми результатами и явным разрешением владельца.

Файловая отправка отчёта — Android Sharesheet, ACTION_SEND text/plain и URI через
FileProvider с временным grant. Provider открывает только cache/shared_reports,
не весь cache/files/Termux. Выбор получателя и отправка принадлежат пользователю.
Отчёт использует snapshot выбранного задания (ветка/SHA), не текущую ветку
другого проекта. Недоступные исторические поля помечаются как не сохранённые.
Новые страницы журнала содержат snapshot; старые страницы/история сохраняются.

Будущая модель BuildConfiguration содержит проект, revision (ветка/коммит),
Gradle profile, cache mode, destination, artifact name и BuildKind:
DEBUG_APK/RELEASE_APK/SIGNED_AAB/DEBUG_AND_TESTS/RELEASE_AND_TESTS.
В Beta у модели нет execute API. CacheMode: incremental; rerun-tasks + no-build-cache;
clean + no-build-cache. Глобальный dependency cache автоматически не удалять.
Будущие release secrets — отдельное защищённое хранилище, не prefs и не логи.
Другие ветки TEST/BUILD в будущем — отдельный worktree, не переключение основной
рабочей копии. Альтернативные профили, подписанная сборка и отмена сейчас отсутствуют.
