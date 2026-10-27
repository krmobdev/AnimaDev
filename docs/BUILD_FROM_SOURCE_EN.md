# Building AnimaDev from source

[Русский](BUILD_FROM_SOURCE.md) · [Home](../README_EN.md)

This guide builds **AnimaDev itself**. GitHub Releases, AnimaBridge Agent, Shizuku and the maintainer's private signing keys are **not required**.

## Requirements

- JDK **21**, Android SDK Platform **37** (current `compileSdk` in [app/build.gradle.kts](../app/build.gradle.kts)).
- The repository Gradle wrapper **9.5.0**; Android Gradle Plugin **9.3.1**, Kotlin **2.4.10** as declared in the source.
- Network access on the first build for Gradle and Maven dependencies.
- On-device Termux builds also need an Android-compatible **AAPT2** and a correctly configured SDK. Standard desktop AAPT2 may not run on Android.

## Build on a desktop with Android SDK

```sh
git clone https://github.com/krmobdev/AnimaDev.git
cd AnimaDev
java -version
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Use `gradlew.bat` on Windows. Configure JDK 21 as `JAVA_HOME`, and make Android SDK available via `ANDROID_HOME`, `ANDROID_SDK_ROOT` or Android Studio.

Output: `app/build/outputs/apk/debug/app-debug.apk`. This is locally debug-signed; it **will not update** a copy signed with a different key. Uninstalling an app can remove user data, so back up data before any reinstall.

## Build inside prepared Termux

Follow [Termux setup](TERMUX_SETUP_EN.md), then:

```sh
git clone https://github.com/krmobdev/AnimaDev.git
cd AnimaDev
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

The SDK path is **only an example**. Use the SDK actually installed on your device. If Gradle tries to run an incompatible desktop AAPT2, provide a working Termux AAPT2 through `-Pandroid.aapt2FromMavenOverride=/absolute/path/to/aapt2` where supported. Installing JDK alone does not install the Android SDK.

After dependencies are fully cached, you may add `--offline`. A first build with `--offline` may fail.

## Release, tests, caveats

The debug build is not an official release. To distribute your own signed APK, prepare and protect your **own** signing keys. The app's signed Release APK/AAB feature is for supported local Android projects and is separate from releasing AnimaDev itself.

JVM build/test success is not full on-device acceptance; test real Termux integration, launchers, widgets and Quick Settings tiles on your Android device. Check OS/background service restrictions when troubleshooting.

| Error | Check |
| --- | --- |
| SDK missing | Installed Platform 37 and SDK path variables |
| Missing Maven dependencies | First build with network access and without `--offline` |
| AAPT2 cannot execute in Termux | Android-compatible AAPT2 binary |
| Wrong JDK/AGP | JDK 21 and the Gradle versions in this repository |
| Install update incompatible | APK signatures differ; back up before uninstalling |

License: [0BSD](../LICENSE); third-party materials retain [their own licenses](THIRD_PARTY.md).
