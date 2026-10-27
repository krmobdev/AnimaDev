# AnimaDev — User Guide

[Русский](USER_GUIDE.md) · [Home](../README_EN.md) · [Build from source](BUILD_FROM_SOURCE_EN.md) · [Changelog](../CHANGELOG.md)

AnimaDev helps you manage **existing Git projects on Android through Termux**: inspect branches, run Gradle tests, build APK/AAB files and review job logs. **AnimaBridge Agent is optional** for Git and Gradle. Shizuku is optional too.

## 1. First-time setup

1. Install **Termux** from a trusted source and open it at least once.
2. Enable Termux `RUN_COMMAND` integration: configure `allow-external-apps=true` in `~/.termux/termux.properties` and grant AnimaDev the Android RUN_COMMAND permission. See [Termux setup](TERMUX_SETUP_EN.md).
3. Open **AnimaDev → Termux → Check environment** and confirm the integration works.
4. Add an **existing** local Git repository under **Projects**. AnimaDev does not clone repositories automatically.
5. Open the project's tools. Before changing branches, check the working tree, `origin` and the remote commit SHA.

To run Gradle tasks inside Termux, install **JDK 21**, the Android SDK and an Android-compatible AAPT2. First-time dependency downloads need network access. See [Termux setup](TERMUX_SETUP_EN.md).

## 2. Projects, Git and worktrees

Each project's main tools are **Git**, **Tests**, **Build** and **History**. Expand the *Worktrees* section to inspect linked working directories, branches, paths, local HEAD and uncommitted-file counts.

- **Refresh local catalog** reads Termux state; it **does not** fetch GitHub.
- **Refresh origin from GitHub** explicitly fetches remote branch references.
- **Check updates / SHA** compares local and remote information.
- **Switch branch** requires a clean working tree, fresh expected SHAs and a safe transition.
- Synchronization never silently runs destructive `git reset --hard`, `git clean` or `force push`. Conflicting or uncommitted work is protected.

**Remember:** matching local and remote commit SHAs does **not** prove the working tree is clean. Check modified and untracked files separately. AnimaDev does not silently switch or delete another worktree.

## 3. Tests, builds and job history

**Tests.** Supported projects can run allowlisted Gradle unit tests. On-device instrumentation requires separate ADB setup and authorization. Results are confirmed only from successful completion and fresh reports.

**Debug APK.** Open **Build → Debug APK**. Check your branch and sources first. The resulting APK is verified by size and SHA-256, then opened with Android's system package installer. Permission to install APKs from AnimaDev may be required.

**Release APK / AAB.** Use **your own** JKS, alias and passwords. A signed APK is installable; an AAB is for distribution through an app store, not direct installation. Never put JKS files or passwords in GitHub or shared Downloads.

**History and Journal.** Every operation has an ID. `UNKNOWN` means the outcome was **not confirmed** — recheck the **same ID** before starting anything else. Closing AnimaDev's UI does not necessarily stop a running Gradle process in Termux.

**Files.** Choose an authorized device-storage folder or Downloads. Clearing AnimaDev's temporary cache is not intended to delete your Git repositories, Termux history, signing keys or SDK.

## 4. AnimaBridge — optional agent control

AnimaDev **does not bundle or install AnimaBridge Agent**. The agent must be installed separately and registered with Termux `termux-services` before the app can manage its `animabridge` service (see [Termux setup](TERMUX_SETUP_EN.md)).

Open **Termux → AnimaBridge**:

- **Check:** inspect local runit/Python status without starting anything.
- **Start:** enable and start the existing service; after a verified launch, persistent runit autostart is enabled.
- **Stop:** stop the service and disable its autostart until explicitly started again.
- **Restart:** available only while a real agent process is confirmed running. Use Start when stopped.

Do not run a manually started agent alongside another copy supervised by `runit`.

### Quick Settings tile and home widget

Use **Add to Quick Settings** on the AnimaBridge card. After system confirmation the in-app button shows the tile as added; remove it through Android's Quick Settings editor.

Use **Add home widget** or your launcher's **Home → Widgets → AnimaDev** menu. Widgets offer status and Start/Stop/Restart/Check actions depending on their size. Resize using the launcher.

Tapping a widget's title returns to AnimaDev's **existing task** instead of stacking a second Activity. Widgets and the tile share the same service gateway — they do not create a second agent.

**Important:** runit supervision, a live local Python process, persistent autostart and **confirmed server connectivity** are distinct states. A local running indicator does not prove a network connection. Android OEM background execution policies can restrict launches after Termux has been force-stopped.

## 5. Shizuku, appearance and language

**Shizuku is optional.** AnimaDev currently uses it for Binder connection diagnostics; it **does not** use Shizuku to execute commands inside Termux's private directory. Start and authorize Shizuku separately.

Under **Settings → Appearance**, select Pulse / Emerald Glass and System / Russian / English. Raw command logs, paths and SHAs remain untranslated. Changing themes should not clear your projects or job history.

## 6. Troubleshooting

| Symptom | What to check |
| --- | --- |
| Termux cannot be reached | Termux installation, `allow-external-apps`, RUN_COMMAND permission and the Termux Check screen |
| JDK / Android SDK / Gradle error | JDK 21, correct SDK path and [setup](TERMUX_SETUP_EN.md) |
| AAPT2 execution fails on Android | Use an Android-compatible AAPT2 and the correct Gradle configuration |
| Task says `UNKNOWN` | Query the **original job ID**; do not blindly launch a duplicate |
| Matching SHA but local changes | `git status`; Git commit equality does not imply a clean working tree |
| APK cannot replace installed version | Signing keys may differ; **back up data before uninstalling** |
| Python runs but remote link is unavailable | Inspect agent connectivity separately; `sv status` proves only local process state |
| Launcher rejects one-tap widget addition | Add AnimaBridge through the launcher's widget picker |

## 7. Source, license and help

- [Build AnimaDev from source](BUILD_FROM_SOURCE_EN.md)
- [Termux environment setup](TERMUX_SETUP_EN.md)
- [Changelog](../CHANGELOG.md) for historical release notes
- [Third-party notices](THIRD_PARTY.md)
- [Report an issue](https://github.com/krmobdev/AnimaDev/issues)

AnimaDev-owned code is licensed under [0BSD](../LICENSE). Third-party components retain their own licenses.
