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
- [x] Termux environment / tools / SDK overview, real versions from probes
- [x] Settings index and nested sections use Emerald cards, preserving original preferences
- [x] About branded hero, changelog and bilingual bundled documentation
- [ ] Polish: 60-fps-friendly animations, insets, small-screen layouts, long labels
- [ ] RU / EN accessibility and screenshots / device instrumentation verification (new RU caption and navigation regression tests added)

## Non-negotiable correctness
- Never display reference/mock data as real Git, Gradle, SDK or test results
- Build remains blocked on unverified SHA/dirty tree and unsupported project
- Native Android APK installer, AAB open-folder, selected destination and passwords remain unchanged
- Do not touch Termux settings, delete caches automatically or alter Git branches
- Exact source GitHub feature branch only; no merge to main
