# AnimaDev — User Guide


## Home widget and app task behavior (dev.55)
AnimaDev opens its existing MainActivity task when tapped from a widget, launcher, or notification. It preserves the last in-app screen instead of stacking another MainActivity and leaving ghost copies behind when using Back. Home widgets now draw a compact panel inside launcher-provided space; if the launcher previously allocated 4×4 cells, shrink the widget to 4×3 cells using the Home-screen resize handles to recover desktop space. The widget never stops or starts AnimaBridge simply by opening the app.


## AnimaBridge Start and Restart buttons (dev.52)
Use Start when the service is stopped or the runit supervisor is offline. Restart is enabled only for a verified running Python agent, including running-with-autostart-disabled. The controller also refuses restart when the service is stopped or not ready, preventing an unexpected start.


## AnimaBridge persistent supervision (dev.51)
Start now clears the registration-time runit normally-down marker **only after** a real Python agent process is confirmed. This permits runsvdir to restart the agent after its own restart. Stop restores the marker, preserving an intentional stop. When the UI says Running, autostart disabled, press Start to enable persistent supervision without interrupting the current Python process. Restart checks that the PID changed. A local running process is not proof of a live server session.


## Launch AnimaBridge without opening Termux (dev.50)
Once Termux and RUN_COMMAND have been set up, tap Start in AnimaDev. If the runit supervisor is absent, AnimaDev initializes it inside the Termux UID via a background RUN_COMMAND and then starts the registered service. No manual terminal launch is required. Check is read-only and never initializes the supervisor. Shizuku's Android shell UID cannot directly access private Termux HOME. Supervisor-off, wrapper-starting and manual-agent-conflict are not treated as a healthy Python agent; startup is verified separately. Android may restrict service launches after Termux is force-stopped.


## GitHub-first in dev.47 (candidate)
After an explicit **Refresh origin from GitHub**, Termux fetches **all GitHub heads** even when the repository has a single-branch `remote.origin.fetch` refspec. Origin branches are paged before local-only branches; use **Load more** to see subsequent pages. Search covers loaded pages only. A stale origin ref cannot authorize switching: the live GitHub SHA is verified again. Fetch does not switch branches, reset worktrees or prune refs. Device acceptance is still pending.

## GitHub-first branch switching in dev.46
GitHub is the source of truth. In **Branches**, tap **Refresh origin from GitHub**, then select a remote branch. Termux creates a local tracking branch when none exists; existing local branches may only advance with fast-forward. A clean worktree, current HEAD, fetched origin SHA and live GitHub SHA are checked again immediately before switching. Divergent histories, local-only branches and uncommitted files are protected; no forced reset or deletion.


## Git recovery and local branches in dev.45
If Git CHECK is stuck in UNKNOWN due to the old 100-job limit, open Git/Tests, tap **Update local helper**, then **Check status in Termux** for the original job ID. The new runner accepts 10,000 job records while preserving the complete SQLite history. An unstarted request becomes confirmed NOT_STARTED, allowing a new check. Never clear the private Termux journal manually.
Local-only branches are not eligible for GitHub-first switching. Remote-only GitHub branches can be selected after an origin refresh, and divergence is denied without force or reset.


## New in dev.44
Debug APK, Release AAB, unit and device test launches automatically jump to the job result card. During Release, the app updates Termux process CPU usage across all cores, available/used RAM and zRAM around every five seconds. The Shizuku **Refresh** control checks Binder in the background and shows a spinner with automatically refreshed ping latency in milliseconds.

## Shizuku in dev.43
Open the **Termux → Shizuku** card. AnimaDev detects a running Shizuku service through Android Binder. Tap **Allow access** and approve in Shizuku. A connected session shows the API version and effective UID (typically **2000 / shell** when started through wireless debugging). **Refresh** rechecks the status; **Open Shizuku** opens its manager. AnimaDev never grants permission or starts the service automatically. No Render, VPN or ongoing `adb connect` is required. This version adds the connection and diagnostics; privileged Android operations are a separate next step.


## Improvements in dev.41
Pin a repository via its overflow menu → Pin to top. This only changes AnimaDev's display order.
Branch switching supports registered ordinary Git repositories when the worktree is clean, local and GitHub SHA agree and another worktree does not own the target branch. Use the explicit Refresh origin from GitHub action when local remote-tracking refs are stale; its guarded fetch never checks out, merges, resets, prunes, deletes worktree files or fetches submodules.
Build and test screens scroll to the active task. Release AAB displays allowlisted Gradle task events in the same ALL/IMPORTANT journal style as Debug. No signing secrets are logged.
The JKS store password opens the keystore; a different private key password is optional. Encrypted storage in Android Keystore is not validation; the signing operation confirms credentials.


## Overview
AnimaDev is an Android companion for local Git repositories in Termux. It inspects branches and GitHub SHA alignment, runs authorized Gradle tests/builds and keeps job journals. Builds run locally in Termux; finished APKs are handed to the **native Android Package Installer**.

## Git revisions and uncommitted files
Equal local and GitHub commit SHAs do **not** imply a clean working tree. Uncommitted changes and untracked files are separate from commit history. AnimaDev can display changed filenames and never resets, commits or pushes them automatically. Safe fast-forward requires a verified clean worktree. Branches refresh reads locally cached origin refs in Termux; Git checks the actual GitHub commit separately.

## Locating an agent's separate working tree
Project tools (Git, Tests, Build, History) always appear BEFORE the Worktrees section. Worktrees are collapsed by default to a small summary of the active branch, other-worktree count and refresh action. Choose Show worktrees to expand or Hide list to collapse. Refresh is available without expanding. The view shows the current directory alongside all discovered linked worktrees, with branch, local SHA, path and uncommitted file count. Tap a worktree to copy its path. **This is a local Termux snapshot, not a live GitHub verification.** Changes in another worktree cannot be inferred from the main working-tree status. No worktree is reset, checked out, or deleted automatically.

## First-time setup
1. Install Termux from a trusted source and open it once.
2. Enable its RUN_COMMAND integration explicitly in Termux settings. AnimaDev does not change that preference for you.
3. In AnimaDev open Termux and tap Check environment.
4. Use Build environment setup for the detailed toolchain instructions.
5. Add an existing local repository from Projects and verify its branch, cleanliness and GitHub SHA.

## Git and repositories
GitHub SHA checks compare local HEAD with the authorized remote branch. Synchronization requires an explicit fast-forward action. AnimaDev does not silently push, force, reset or discard changes. Some worktrees may be read-only under the current project profile.

## Tests and journal
Unit tests run local Gradle tasks; On device runs instrumentation tests using configured ADB. Every task has an ID and a journal. Querying its status never starts a second Gradle process. Cancellation is exposed only for supported task types.

## Debug APK
1. Verify GitHub SHA and a clean tree.
2. Choose a storage folder under primary shared storage in Files & downloads, or use the default Downloads.
3. Build Debug APK reruns Gradle tasks with `--rerun-tasks`; it does not remove dependency caches.
4. To install, select the verified APK when prompted. AnimaDev checks SHA-256, copies it to private cache and invokes Android Package Installer through FileProvider. Allow installing from this app if Android asks.
5. Clear temporary AnimaDev cache removes only temporary APK copies and exported reports. Projects, signing settings and history stay intact.

## Release AAB
A signed AAB requires a JKS and alias. Passwords are transferred over a local protected channel and must not enter arguments, repositories or logs. AAB is for publishing, not direct installation. Open its folder and review the Release journal in History.

## Appearance skins
Emerald Glass is an opt-in second visual style under Settings → Appearance, disabled by default. It uses dark graphite panels and emerald highlights. Projects, Git, jobs, signing, history and folder preferences survive switching. Projects, the project dashboard, Build, Tests, History, Branches, Termux, the Settings index and About now have Emerald styling. Existing controls and safety policies remain in place; live monitor, filtered journals and nested settings also use Emerald cards. Emerald Glass replaces the old three-dot menu with a Settings gear. Change log, Documentation and About are available as full-size Settings cards. Back returns to the previous tool. Raw logs, SHA and paths are not translated; small screens allow horizontal scrolling of multi-tab filters, and navigation stacks icons above labels at larger font sizes. On-device visual acceptance is still required.

## Language
Open Settings → Appearance → Language to choose System default, Russian or English. User-facing labels, statuses and diagnostics follow the selected language. Raw Git/Gradle logs are left in their original language, and paths, hashes and job IDs are not rewritten.

## Troubleshooting
- SHA mismatch: recheck Git; never bypass a dirty-worktree guard.
- Missing dependencies: check JDK 21, Android SDK, Gradle wrapper and offline dependency caches.
- UNKNOWN job: query the existing ID before trying to start any new job.
- APK will not install: confirm the actual chosen file and the system's install-source permission.
- Folder unavailable: select primary storage or reset to Downloads.

## Data safety
Do not delete Termux or Git directories to clear app cache. AnimaDev cache cleaning does not remove sources, job journals, SQLite, keystores or saved credentials.

### Release APK and AnimaBridge service control (dev.49)
Project > Build offers Debug APK, signed Release APK and signed Release AAB. The APK uses the existing SAF-selected JKS, alias and protected credential transport. After Git SHA verification, Gradle assembleRelease is followed by zipalign, APK signing, certificate verification, APK package/version validation and checksum-verified export. GitHub Release publishing is not automatic.
Termux > AnimaBridge supports Check, Start, Stop and Restart of the explicitly registered runit service; a manually running agent is never adopted or terminated automatically.
