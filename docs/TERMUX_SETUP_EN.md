# Termux — local Android build setup

## Complete GitHub branch fetch in dev.47 (candidate)
The **Refresh origin from GitHub** action fetches all `refs/heads/*` regardless of the local `remote.origin.fetch` setting. This does not reset or clean the worktree, prune remote-tracking refs, or check out another branch. Switching still verifies the live GitHub SHA before acting.

## Optional Shizuku
AnimaDev dev.43 connects directly to a running Shizuku service using the official Android Binder API. On the **Termux → Shizuku** card, request access and approve it in the Shizuku manager. This connection requires no `rish`, root, remote server or persistent ADB connection. The owner must start Shizuku separately; non-root installations usually need a restart after phone reboot. Existing Termux tools continue working independently.


## Background Release AAB in AnimaDev dev.41
The background worker uses the existing `~/android-sdk` if RUN_COMMAND does not provide ANDROID_HOME, without writing project files.
Release progress contains allowlisted Gradle task names and fixed diagnostic summaries only. Signing secrets are never logged. Prime missing dependencies using one unsigned online AAB build before an offline build.


Run these commands **manually in Termux** after checking what they do. AnimaDev never silently installs toolchains.

## 1. Base packages
```sh
pkg update
pkg install git python openjdk-21 curl unzip
termux-setup-storage
```
Verify with `java -version`, `python --version`, `git --version`. Accept Android storage access only if needed.

## 2. Android SDK
Obtain Android SDK command-line tools, platform-tools, the platform and build-tools versions required by your project's Gradle configuration. Set ANDROID_HOME/ANDROID_SDK_ROOT to your real SDK location. The command `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"` is an **example only**: use the compileSdk/buildTools versions your project actually needs. Standard desktop AAPT2 may not run on Termux's Android architecture, so use a compatible Termux AAPT2 if your toolchain requires it.

## 3. Gradle
Use the repository's own `./gradlew`. Once dependencies have been downloaded, validate:
```sh
./gradlew --version
./gradlew :app:assembleDebug --offline --no-daemon --rerun-tasks --console=plain
```
An initial offline build may fail before dependencies are downloaded. Avoid deleting ~/.gradle or your Android SDK during troubleshooting.

## 4. AnimaDev integration
Allow RUN_COMMAND in Termux and grant the requested Android permission. In the app, choose Check environment, then inspect Java, Android SDK and Gradle. Verify GitHub branch and SHA before running jobs. Device tests also require authorized ADB.

## 5. Security
Keep JKS, passwords and SSH private keys out of shared Downloads. Do not blindly execute untrusted repository scripts. This guide does not change repositories or install anything automatically.


## Release metrics and APK install cache (candidate, 19 October 2026)
Release CPU and memory metrics are sampled from Termux procfs, without invoking free. If procfs CPU counters are restricted, one top -n 1 snapshot without the forced delay is used; some values may be absent without affecting the build. The Android installer uses a rolling private cache of up to three verified APK copies; original artifacts in the chosen destination are never removed.
