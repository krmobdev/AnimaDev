# AnimaDev — User Guide

## Overview
AnimaDev is an Android companion for local Git repositories in Termux. It inspects branches and GitHub SHA alignment, runs authorized Gradle tests/builds and keeps job journals. Builds run locally in Termux; finished APKs are handed to the **native Android Package Installer**.

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
