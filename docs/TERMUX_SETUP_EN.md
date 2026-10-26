# Termux — local Android build setup

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

## AnimaBridge runit service (dev.48)

AnimaDev controls only a named, pre-registered `animabridge` runit service through the authorized Termux RUN_COMMAND integration, not through Shizuku's shell UID.

1. In Termux, install `pkg install termux-services`, then open a fresh Termux session so `runsvdir` starts.
2. From the AnimaDev source checkout run `bash scripts/install_animabridge_service.sh ~/anima-workspace/AnimaBridge-3.5` once. The script creates a stable `~/.local/share/animabridge/active` link and a service initially **DOWN**. It does not stop an existing manually running agent.
3. Stop the manually started agent yourself with Ctrl+C before adopting the service. In AnimaDev → Termux → AnimaBridge, tap Check, then Start.
4. Stop and Restart require a verified running state. An unconfirmed callback or timeout requires a new Check before any further action. Changing the actual agent location only changes the Termux-side `active` link.

No device pairing secrets, Termux-private files or arbitrary executable paths are exposed to AnimaDev or Shizuku. A runit process state is not equivalent to proof of a healthy Render connection.
