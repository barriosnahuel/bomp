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

## Activity smoke tests

Every `Activity` in the `app` module must have a corresponding smoke test that verifies it reaches `Lifecycle.State.RESUMED` without crashing. Place it alongside the Activity in `app/src/test/`, extend `AbstractRobolectricTest`, and use:

```kotlin
ActivityScenario.launch(MyActivity::class.java).use { scenario ->
    assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)
}
```

Mock any singleton factories (e.g. `PlayerControllerFactory`) that would crash under Robolectric. See `LandingActivityTest` for the canonical example.

**Dynamic feature modules** (e.g. `feature_addbutton`) cannot use Robolectric — Robolectric's `ShadowPackageParser` rejects split APKs (`Expected base APK, but found split`). Activities in those modules require instrumented tests if smoke coverage is needed.

## Code Quality

All three linters run on CI and must pass:
- **KtLint** — style (runs as part of `check`; auto-fix with `ktlintFormat`)
- **Detekt** — static analysis (config: `config/detekt/detekt-config.yml`; max line length 150)
- **Android Lint** — lint rules in `config/android/android-lint.xml`

## Setup Notes

- Replace `app/google-services.json` with a real Firebase config. The included file is a placeholder, excluded from tracking via `git update-index --skip-worktree app/google-services.json`.
- Release signing requires `nahuelbarrios.keystore-appbundle.pkcs12` and `secure.properties` (with `key.alias`, `key.password`, `store.password`) in the project root — not committed.
- Debug builds use the included debug keystore and work without the above.

## Pre-push checklist

Before pushing any branch, always run:

```bash
./gradlew test && ./gradlew check -x test
```

This catches the same failures CI will report (unit tests, ktlint, detekt, checkstyle, Android lint) without waiting for a full CI run.

## CI

CircleCI runs three parallel jobs on PRs:
1. **test** — `./gradlew test`
2. **code-analysis** — `./gradlew check -x test` + `app:lintVitalRelease`
3. **build** — assembles app and bundle (skips checks)

## Handoff notes

`handoff/` contains session handoff documents (one per working session) with decisions taken, key file paths, and pending work. Ignored by git. When starting a new session, check the latest file in this directory for context.

## Accessibility (WCAG 2.2 AA)

All UI development and generated assets (store listing, What's New, changelogs) must target **WCAG 2.2 Level AA**. Key requirements:

- **Contrast – text (1.4.3):** ≥ 4.5:1 for normal text; ≥ 3:1 for large text (≥ 18 sp or ≥ 14 sp bold)
- **Contrast – non-text (1.4.11):** ≥ 3:1 for interactive UI components (icon-only buttons, input borders, focus indicators)
- **Color not sole indicator (1.4.1):** Never use color alone to convey state — pair with an icon, label, or pattern
- **Content descriptions (1.1.1):** Every `Icon`/`Image` conveying information needs a non-null `contentDescription`; purely decorative assets use `contentDescription = null`
- **Touch targets (2.5.8):** Minimum 24 × 24 dp; prefer 48 × 48 dp for primary actions
- **Labels match names (2.5.3):** Visible button/field labels must match the accessible name used by screen readers

Verify contrast when adding or changing colors. Use the [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/) or the Material Theme Builder. The brand palette in `AppTheme.kt` was designed to meet AA across all color roles.

## Labels

Available labels for this repository. Apply exactly one `a:` label to every PR before merging. Do not call `gh label list` — use this table.

| Label | When to use |
|---|---|
| `a:bug` | Issue reporting something broken |
| `a:feature` | PR that adds new user-facing functionality |
| `a:feature-request` | Issue requesting a feature not yet built |
| `a:fix` | PR that corrects a reported bug |
| `an:enhancement` | PR that improves existing functionality without adding new features |
| `dependencies` | PR that updates library, plugin, or Gradle wrapper versions (applied automatically by Dependabot) |
| `stale` | Issue or PR with no recent activity — candidate for closing |

## Changelog

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format:
- Sections: `Added`, `Changed`, `Fixed`, `Removed` under `## [unreleased]`
- Each entry is a single sentence starting with a capital letter, no trailing period
- For dependency bumps, write one line summarising the overall bump (e.g. "Bumped all dependencies to latest stable"), not one line per library
- After every commit, check whether the change is user-visible or architecturally significant; if so, update `## [unreleased]` before committing
- Never add a `Fixed` entry for a bug introduced in the same `[unreleased]` cycle. If end-users never experienced the regression, it has no changelog entry — git history provides the traceability
