# Termux setup for AnimaDev

[Русский](TERMUX_SETUP.md) · [Home](../README_EN.md) · [Build AnimaDev](BUILD_FROM_SOURCE_EN.md)

**Start here:** Termux runs local Git and Gradle commands invoked by AnimaDev. Normal Git/Gradle features **do not require** AnimaBridge Agent, Shizuku, a VPS or a separate backend server. The app does not silently install your toolchain.

## 1. Install Termux and authorize commands

1. Install a compatible Termux release from a trusted source and open it at least once.
2. Inside Termux, run `mkdir -p ~/.termux` and add `allow-external-apps=true` to `~/.termux/termux.properties` while preserving other settings.
3. Apply Termux settings and grant AnimaDev the `com.termux.permission.RUN_COMMAND` Android permission when requested. Some Termux builds require reopening the application.
4. In AnimaDev, open **Termux → Check environment**. This should probe your tools without silently installing system packages.

`RUN_COMMAND` authorizes applications to execute permitted commands under the Termux user. Grant it **only to trusted apps**.

## 2. Install base tools

In Termux, manually run:

```sh
pkg update
pkg install git python openjdk-21 curl unzip
```

Verify `java -version`, `git --version`, `python --version`. Shared storage access may require `termux-setup-storage` and an Android permission prompt; do this only when needed.

Configure Git and any GitHub SSH keys locally. Never commit tokens or private credentials.

## 3. Android SDK and Gradle

Android builds require an actual **Android SDK** (platform-tools, a platform compatible with the project's `compileSdk` and build-tools), plus Java. Installing the JDK alone **does not** install the Android SDK.

To build **AnimaDev itself**, use **Android SDK Platform 37**, JDK 21, the Gradle wrapper 9.5.0 and AGP 9.3.1. For another project, follow **that project's** Gradle configuration.

Set your SDK's actual location; example only:

```sh
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

**Termux note:** a standard desktop AAPT2 may not run on Android. If necessary, install an Android-compatible AAPT2 and use the Gradle override `-Pandroid.aapt2FromMavenOverride=/absolute/path/to/aapt2`. Adapt paths to your environment.

Use your project's checked-in wrapper:

```sh
./gradlew --version
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Do **not** use `--offline` for the first build if dependencies are not cached. Once downloaded, offline builds are possible. See [Build from source](BUILD_FROM_SOURCE_EN.md) for the app itself.

## 4. Connect an existing project

1. Open **Termux → Check environment**.
2. Add a Git checkout via **Projects**.
3. Verify the selected `origin`, branch and uncommitted changes before write operations.
4. Use the supported Git/Gradle actions. A task in `UNKNOWN` state must be queried again under the **same ID**, not blindly started a second time.

On-device instrumentation tests require separately configured and authorized ADB.

## 5. AnimaBridge Agent — optional separate service

**AnimaDev does not install the agent automatically.** If you have already installed AnimaBridge Agent and want to manage it through AnimaDev:

1. Install `termux-services` with `pkg install termux-services`; reopen Termux to initialize the `runit` supervisor.
2. Confirm that the **actual installed** agent directory contains `termux_agent/__main__.py`.
3. Read the checked-in [`scripts/install_animabridge_service.sh`](../scripts/install_animabridge_service.sh) before running it. It registers the service **initially down** and refuses to overwrite an existing registration.
4. **Never start a supervised agent on top of a manual instance.** Stop the manual agent safely first. Then use AnimaDev → Termux → AnimaBridge → **Start**.
5. Inspect local state using `sv status animabridge` in Termux. You may use the in-app controls or standard `sv` commands.

After a verified Start, AnimaDev removes runit's `normally down` marker to enable persistent supervisor startup. Stop restores that marker. Restart is only available when an actual agent process is running. Android's Quick Settings tile and home widget manage the same registered service — not an additional agent. **Local Python running does not prove remote server connectivity.**

## 6. Shizuku — optional

Under **Termux → Shizuku**, authorize access to an independently started Shizuku service and inspect the Binder connection. At present this is **diagnostic**, not a way of executing Python inside Termux's private home. Shizuku is not required for Git/Gradle or runit service control.

## 7. Safety and troubleshooting

- Keep JKS files, signing passwords, tokens, private keys and sensitive logs out of public GitHub and shared Downloads.
- Do not delete your Gradle cache or SDK as a first response to a setup error.
- Android may block Termux background launching after force-stop until Termux is opened again.
- If RUN_COMMAND fails, inspect permissions and the Termux integration before starting another agent.
- No official APKs are currently published on GitHub Releases. You can [build the app from source](BUILD_FROM_SOURCE_EN.md).
