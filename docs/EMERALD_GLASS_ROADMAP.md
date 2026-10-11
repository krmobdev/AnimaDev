# Emerald Glass skin implementation / План второго скина

**User-approved direction:** dark graphite + emerald accent, close to supplied references.
**Architecture:** independent opt-in Compose presentation. Legacy Pulse/Material and all
repository, job, Git, signing and storage state remain unchanged. No invented data.

## Sprint 1 — implemented (Android acceptance pending)
- [x] Dedicated `emerald_glass` preference, defaults off; Pulse remains available
- [x] Dark color scheme, spacing, reusable gradient cards and handcrafted Compose icon tiles
- [x] Project list: real names, branches, dirty/clean status, SHA, add/remove actions
- [x] Reference-style root Projects / Termux bottom navigation
- [x] Project dashboard: branch + copy SHA, Git / Tests / Build / History actions
- [x] Dashboard Build Now routes to existing guarded Build screen
- [x] Build screen foundation: read-only real configuration, guarded Debug build action
- [x] Build and unit-test validation · dev.33: 196/196 unit, UI tests compiled, Debug APK built
- [ ] Pixel visual acceptance and accessibility verification

## Sprint 2 — planned
- [x] Release AAB: redesigned keystore/password/status sections; secure signing unchanged
- [x] Test UI: Unit / On-device tabs and guarded actions; preserved existing journal navigation
- [x] History: action filters and real job statuses
- [x] Branches: active branch, actual SHA and existing safe verified checkout
- [x] Job monitor / journal: Emerald status, stage, controls, safe raw logs, event timeline

## Sprint 3 — planned
- [x] dev.39: Show all discovered linked worktrees before Git/Tests/Build, including local branch, path, SHA and file counts (superseded by dev.40 compact layout)
- [x] dev.40: Move worktree section AFTER Git / Tests / Build / History, collapsed by default
- [x] dev.40: Lazy worktree rows on expand, hide/show toggle, compact count/status and refresh
- [x] dev.40: Add Compose UI regression fixture covering collapsed and expanded states
- [x] dev.39: Clear warning that worktree presence and main SHA equality do not prove any GitHub push
- [ ] Device acceptance: verify full catalog enumeration, large-font cards and copy-path details
- [x] Owner deleted AnimaQuiz feature/design-three-themes locally and on GitHub; main 3.7.22 (101) verified clean
- [x] Termux environment / tools / SDK overview, real versions from probes
- [x] Settings index and nested sections use Emerald cards, preserving original preferences
- [x] Remove Emerald three-dot popups on Projects and project tools; use dedicated graphitic toolbar/gear and Settings navigation hub (dev.37)
- [x] Changelog, documentation and About are navigable Settings cards with Back preserving original tool
- [x] dev.38: Fix custom toolbar status-bar overlap, move branch action above tools, and stack worktree labels for readable Russian text
- [x] dev.38: Separate GitHub commit agreement from local file changes and expose changed filenames inline
- [ ] Device visual regression on the user's Android status bar and branch screen screenshots
- [x] About branded hero, changelog and bilingual bundled documentation
- [x] Small-screen tabs, bottom navigation, project tile grid, SHA layout and long labels adapt to width/font scale
- [x] Lightweight selection transitions and cached ambient glow (no heavy blur)
- [ ] Validate 60 fps frame pacing, insets, TalkBack, screenshot reference matching on device
- [x] 2026-10-11: Run dev.40 Emerald Compose instrumentation on awake, unlocked Pixel 7 (API 37), isolated `devicefixture`: **5 executed, 4 passed, 1 failed**; the new collapsed-worktrees test passed
- [ ] Investigate `EmeraldGlassUiTest.projectCardDisplaysActualGitMetadataAndOpens`: `Clean` status exists in initial run but `assertIsDisplayed` fails; before-click reproduction and a later absent-node result mean its visibility/fixture stability is unresolved
- [ ] Rerun full Emerald suite **5/5 green** on Pixel 7 after fixing card status visibility; do not count compilation as device acceptance
- [ ] RU / EN TalkBack, screenshot comparison and frame-pacing on device (RU caption and back-navigation unit regression tests added)

## Non-negotiable correctness
- Never display reference/mock data as real Git, Gradle, SDK or test results
- Build remains blocked on unverified SHA/dirty tree and unsupported project
- Native Android APK installer, AAB open-folder, selected destination and passwords remain unchanged
- Do not touch Termux settings, delete caches automatically or alter Git branches
- Exact source GitHub feature branch only; no merge to main
