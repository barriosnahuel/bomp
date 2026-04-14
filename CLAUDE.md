# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run unit tests
./gradlew test

# Run all checks (tests + linting)
./gradlew check

# Run linting only (detekt + ktlint + checkstyle)
./gradlew check -x test

# Run Android lint for release
./gradlew app:lintVitalRelease

# Auto-fix Kotlin code style
./gradlew ktlintFormat

# Run detekt static analysis
./gradlew detekt

# Build (skip checks)
./gradlew app:build -x check

# Build release bundle for Play Store
./gradlew app:bundle

# Run a single test class
./gradlew :model:test --tests "com.github.barriosnahuel.vossosunboton.model.SomeTest"
```

## Module Architecture

Push Me is an Android soundboard app with 5 Gradle modules:

- **`app`** — Main application module: Activities, Fragments, RecyclerView adapters, and the feature layer (playback, share, permissions). Entry point is `LandingActivity`.
- **`model`** — Business logic library: `Sound` data model, data managers for loading/saving sounds, persistence. Has no Android UI dependencies.
- **`commons_android`** — Foundation library for the app and feature modules: Firebase initialization, Timber logging setup, annotation utilities.
- **`commons_file`** — File handling utilities (reading/writing audio files).
- **`feature_addbutton`** — Dynamic feature module (on-demand delivery via Play Store) for adding custom buttons.

Dependency direction: `app` → `model`, `commons_android`, `commons_file`; `feature_addbutton` → `app`.

## Key Packages in `app`

- `ui.home` — `LandingActivity`, fragments (packaged vs. saved sounds), and `RecyclerView` adapters
- `feature.playback` — `MediaPlayer`-based audio playback
- `feature.share` — Sharing non-packaged audios to other apps
- `feature.base` — Base classes, runtime permission handling

## Code Quality

All three linters run on CI and must pass:
- **KtLint** — style (runs as part of `check`; auto-fix with `ktlintFormat`)
- **Detekt** — static analysis (config: `config/detekt/detekt-config.yml`; max line length 150)
- **Android Lint** — lint rules in `config/android/android-lint.xml`

## Setup Notes

- Replace `app/google-services.json` with a real Firebase config. The included file is a placeholder, excluded from tracking via `git update-index --skip-worktree app/google-services.json`.
- Release signing requires `nahuelbarrios.keystore-appbundle.pkcs12` and `secure.properties` (with `key.alias`, `key.password`, `store.password`) in the project root — not committed.
- Debug builds use the included debug keystore and work without the above.

## CI

CircleCI runs three parallel jobs on PRs:
1. **test** — `./gradlew test`
2. **code-analysis** — `./gradlew check -x test` + `app:lintVitalRelease`
3. **build** — assembles app and bundle (skips checks)

## Changelog

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format:
- Sections: `Added`, `Changed`, `Fixed`, `Removed` under `## [unreleased]`
- Each entry is a single sentence starting with a capital letter, no trailing period
- For dependency bumps, write one line summarising the overall bump (e.g. "Bumped all dependencies to latest stable"), not one line per library
- After every commit, check whether the change is user-visible or architecturally significant; if so, update `## [unreleased]` before committing
