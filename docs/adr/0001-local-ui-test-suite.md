# ADR 0001 — Local-only instrumented UI test suite

- **Status:** Accepted
- **Date:** 2026-04-26
- **Supersedes:** —

## Context

The off-device test suite (`src/test/`, Robolectric + Compose UI Test + JUnit4) covers
ViewModels, composition logic, share intents at the unit level, theme contrast, and
smoke-renders for every Activity and full-screen Composable. CircleCI runs `./gradlew test`
on every PR and that catches the bulk of regressions cheaply.

What it does **not** cover:

- Real `MediaPlayer` / `SoundPool` playback (Robolectric shadows don't drive the audio
  pipeline).
- Real swipe gestures with haptic feedback (`HapticFeedbackConstants.CONFIRM/REJECT`,
  API 30+).
- The system Share chooser dialog (cross-process UI, only visible from
  outside the app's window).
- The Web browser launched by the About screen's "Source code" button.
- Deep-link entry points (`push-me://open/home`, `/explore`) including the fallback when
  no bundled audios exist.
- Inter-Activity navigation that crosses process boundaries (e.g. `AddButtonActivity`
  invoked via an external `ACTION_SEND`).
- WCAG checks driven by the live accessibility tree.

Manual QA filled this gap. That's slow and skipped under deadline pressure.

## Decision drivers

1. **Do not slow CI.** `connectedAndroidTest` needs an emulator and a few minutes per run;
   CircleCI today is `compile → detekt/ktlint/spotless/lint → test → bundle` and is fast
   enough to keep contributor friction low.
2. **One command, no manual steps.** A developer with the SDK CLI installed should be able
   to run the entire suite with one Gradle invocation.
3. **Deterministic.** No flakiness on real audio files (we don't depend on the user's
   `model/src/debug/res/raw/` being populated); the suite ships its own ~200ms silent MP3.
4. **Accessibility is part of the feature, not a bolt-on.** Every screen test should
   exercise its own a11y assertions, not be deferred to a separate sweep that's easy to
   skip.

## Considered options

### Option A — Roborazzi (off-device screenshot tests)
Snapshot diffs of every screen, no emulator required. Fast to run.
- ✗ Doesn't drive `MediaPlayer`/`SoundPool` or system intents.
- ✗ Golden image management is high-friction (binary blobs in PRs, locale/theme drift).
- ✗ Asserts pixels, not behavior. Swipe-to-pin or undo-snackbar interactions can't be
  expressed.

### Option B — Maestro / Appium scripted UI flows
External tool, drives the emulator via accessibility services.
- ✗ YAML/Groovy DSL outside the Kotlin/Gradle workflow — context switch.
- ✗ No type-safe access to the app's source (semantics, content descriptions are strings).
- ✗ Adds a second CLI/binary contributors must install.

### Option C — Real TalkBack scripted via UI Automator
Closest to "validated by a screen reader user".
- ✗ Notoriously brittle: TalkBack focus order can change between Android releases.
- ✗ Requires per-API tweaking; high maintenance for low payoff.
- ✗ Doesn't catch contrast or touch-target failures by itself.

### Option D — Native Compose UI Test + Espresso + UI Automator + ATF (chosen)
- ✓ Built into the Android tooling we already use.
- ✓ Type-safe Kotlin; tests live next to the code under test.
- ✓ `AccessibilityChecks.enable()` from `espresso-accessibility` runs Google's
  Accessibility Test Framework on every Espresso/UI Automator interaction (contrast, touch
  target, content description, duplicate labels).
- ✓ Compose UI Test exposes `assertContentDescriptionEquals`, `assertHasClickAction`,
  semantics matchers — used per-screen for the things ATF can't see (Compose-only
  interactions don't currently auto-trigger ATF).
- ✓ UI Automator handles cross-process UI (Share chooser dialog).
- ✓ Espresso Intents stubs the browser launch and the `ACTION_SEND` outbound chooser, so
  no real Chrome / no real "Send via" dialog blocks the suite.

## Decision

Adopt **Option D**, isolated under `app/src/androidTest/`:

- Gradle source set already excluded from `./gradlew test` and `./gradlew check`.
- Not invoked by `.circleci/config.yml` (the `test` job runs `./gradlew test` only).
- A single command runs the suite: `./gradlew app:connectedDebugAndroidTest`.
- Operator workflow: run `./scripts/setup-test-emulator.sh` once to create the AVD, boot
  the emulator, then invoke the Gradle task.
- Each screen test file owns its accessibility assertions. There is no
  `AccessibilitySweepTest` collector — a11y is part of the feature, not a separate concern.

## Consequences

### Positive

- Functional regressions in audio playback, swipes, intents, and deep links are caught
  automatically before push.
- A11y regressions surface during normal test runs, not at release time.
- `CLAUDE.md` is updated so AI-driven contributions know to invoke this suite when a
  change touches functional behavior (not just CHANGELOG/copy/docs).

### Negative / tradeoffs

- Requires an emulator to run the suite. Developers without one need to run
  `./scripts/setup-test-emulator.sh` once (~5 minutes including system image download).
- Compose-driven interactions don't auto-trigger ATF — explicit semantics assertions are
  required per screen test. This is documented in `AbstractUiTest`'s KDoc and enforced by
  PR review (the per-screen test is supposed to include a11y bullets in the test plan).
- Adds ~6 androidTest dependencies (Espresso, Espresso Intents, Espresso Accessibility,
  UI Automator, AndroidX test runner/rules) and one ~1KB MP3 asset.

### Out of scope

- Pixel-level visual regression (Roborazzi/Paparazzi) — can be added later as a
  complement without disturbing this setup.
- Performance testing (frame timing, jank) — not in scope.
- Full TalkBack-scripted runs — explicitly rejected in Option C above.
