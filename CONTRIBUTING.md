# Contributing to AnimaDev / Участие в проекте

[Русский](#русский) · [English](#english)

## Русский

Спасибо за интерес к AnimaDev! Приветствуются сообщения об ошибках, понятные инструкции, перевод, тесты и небольшие исправления.

1. Сначала посмотрите [Issues](https://github.com/krmobdev/AnimaDev/issues). Не прикладывайте токены, SSH-ключи, пароли, JKS, закрытые пути и журналы с личными данными.
2. Для изменений форкните репозиторий и создайте отдельную ветку. Обычно PR следует направлять в `dev`, а не непосредственно в `main`; решения о включении изменений принимает сопровождающий.
3. Для исходников используйте JDK 21, Android SDK 37 и штатный Gradle wrapper. Инструкции: [сборка](docs/BUILD_FROM_SOURCE.md).
4. Перед PR запускайте `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon`. Для сборок в Termux нужен совместимый AAPT2. UI и поведение Termux/виджетов проверяйте на устройстве отдельно.
5. Описывайте назначение изменений, что проверено и что не проверено. Изменения поведения документируйте в руководствах и [CHANGELOG](CHANGELOG.md).

Важно: команды RUN_COMMAND управляют средой Termux. Не добавляйте скрытый запуск программ, выполнение произвольных команд, экспорт ключей или автоматические разрушительные Git-операции.

## English

Bug reports, documentation, translations, tests and focused pull requests are welcome.

1. Search [Issues](https://github.com/krmobdev/AnimaDev/issues). Never post tokens, private keys, passwords, keystores or sensitive logs.
2. Fork and work on a topic branch. Normally target the `dev` branch; `main` receives reviewed changes.
3. Follow [Build from source](docs/BUILD_FROM_SOURCE_EN.md). Use JDK 21, Android SDK Platform 37 and the repository wrapper.
4. Run `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon` after dependencies are installed. Real-device tests are needed for widgets, the tile and Termux integration.
5. Summarize changes and test evidence; update user guides and the changelog when changing behavior.

By submitting contributions, ensure you have the rights to submit them for distribution under the project's [0BSD license](LICENSE), except independently licensed third-party materials whose notices must remain intact.
