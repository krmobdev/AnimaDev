# AnimaDev

[Русский](README.md) · [Build from source](docs/BUILD_FROM_SOURCE_EN.md) · [0BSD License](LICENSE) · [Changelog](CHANGELOG.md)

**AnimaDev** is an Android application for managing local Git projects, Gradle builds and tests **on your phone through Termux**. It helps inspect branches, compare GitHub commits, run supported Gradle tasks, review logs, and build APK/AAB artifacts while keeping your projects and signing secrets in Termux.

**Status:** public beta `0.4.0-beta.10-dev.57`, Android 13+. There are currently **no GitHub Releases or prebuilt official APKs**. You can build the app yourself. Builds/tests in the project do not guarantee compatibility with every Android ROM.

## Features

- Local Git repositories/worktrees, branch comparison, safe, explicit syncing.
- Gradle/JUnit tasks, Debug APK, locally signed Release APK/AAB with your own JKS, job history and logs.
- Optional diagnostic Shizuku integration (no direct Termux command execution through Shizuku).
- Optional AnimaBridge `runit` controls, Android Quick Settings tile and two informative 4×2 / 4×3 home widgets.
- Russian and English UI and multiple themes.

AnimaDev **does not ship or install AnimaBridge Agent**. The widget/tile manage a *separately installed and registered* Termux service; all normal project tools work without it.

## Requirements

For the app: Android 13+, [Termux](https://github.com/termux/termux-app) and Android `RUN_COMMAND` permission. For Android builds: Git, JDK 21, Android SDK and Gradle dependencies. For AnimaDev itself: SDK Platform **37**, Android Gradle Plugin **9.3.1**, Gradle wrapper **9.5.0**, JDK **21**. Shizuku is optional; AnimaBridge Agent + `termux-services` is only for agent controls.

## Quick build

On a computer with JDK 21 and Android SDK set up:

```sh
git clone https://github.com/krmobdev/AnimaDev.git
cd AnimaDev
./gradlew :app:assembleDebug --no-daemon --console=plain
```

Windows: `gradlew.bat`. Output: `app/build/outputs/apk/debug/app-debug.apk`. This **debug APK** is signed using your local debug key and may not update an installation signed with a different key. Uninstalling an app can delete its data. Termux/Android-device builds need an Android-compatible AAPT2 and appropriate SDK environment; read [Build from source](docs/BUILD_FROM_SOURCE_EN.md) and [Termux setup](docs/TERMUX_SETUP_EN.md). Do not use `--offline` on the first build before dependencies are cached.

## First use

1. Install Termux and the locally built APK. Open Termux at least once.
2. Configure Termux `allow-external-apps=true` and grant AnimaDev the Android **RUN_COMMAND** permission; see [setup guide](docs/TERMUX_SETUP_EN.md).
3. Open AnimaDev → **Termux → Check**, and confirm the probe works.
4. Add an *existing* Git checkout in **Projects**, inspect its branch and commit, then run supported tasks.
5. An `UNKNOWN` task result is not a success or failure: recheck the same task ID before retrying.

If AnimaBridge was separately installed and registered with `runit`, you can add the Quick Settings tile or home widget. A running local Python process **does not prove server connectivity**. Avoid starting duplicate manual and supervised agents.

## Guides

- [User guide (EN)](docs/USER_GUIDE_EN.md) · [Руководство (RU)](docs/USER_GUIDE.md)
- [Build from source (EN)](docs/BUILD_FROM_SOURCE_EN.md) · [Сборка (RU)](docs/BUILD_FROM_SOURCE.md)
- [Termux setup (EN)](docs/TERMUX_SETUP_EN.md) · [Termux (RU)](docs/TERMUX_SETUP.md)
- [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Third-party notices](docs/THIRD_PARTY.md)

## License and releases

There are **no official releases** yet: build from source, use your own signing key if you redistribute builds, and never share signing credentials. The project-owned code is licensed under **[0BSD](LICENSE)**: use, modify and redistribute for any purpose, including commercial use, with **no attribution requirement**. Bundled third-party resources and dependencies keep their respective licenses; see [third-party notices](docs/THIRD_PARTY.md).

Development takes place on `dev`; owner-approved changes go to `main`. Report issues via [GitHub Issues](https://github.com/krmobdev/AnimaDev/issues).