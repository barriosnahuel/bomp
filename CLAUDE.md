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

# Apply AGPLv3 copyright header to all .kt files (auto-fix for Spotless violations)
./gradlew spotlessApply
```

## Tooling & Environment
- **Android CLI**: Available via `adb` (Android Debug Bridge), `fastboot`, and `emulator`.

## Module Architecture

Push Me is an Android soundboard app with 4 Gradle modules:

- **`app`** — Main application module: Activities, Fragments, RecyclerView adapters, and the feature layer (playback, share, permissions, add-button). Entry point is `LandingActivity`. The Add Button flow lives at `feature/addbutton/` inside this module.
- **`model`** — Business logic library: `Sound` data model, data managers for loading/saving sounds, persistence. Has no Android UI dependencies.
- **`commons_android`** — Foundation library for the app: Firebase initialization, Timber logging setup, annotation utilities.
- **`commons_file`** — File handling utilities (reading/writing audio files).

Dependency direction: `app` → `model`, `commons_android`, `commons_file`. No dynamic features today — the Add Button flow used to live in a `:feature_addbutton` module but was promoted into `:app` since creating buttons is core to the product. Reintroduce dynamic features when freemium-style on-demand delivery is needed.

## Product & brand context (when relevant)

Product specs, brand language, and canonical naming live in the sibling backlog repo at `../push-me-backlog/`. Consult it when working on user-facing strings, micro-copy, feature/level naming, gamification, or social-layer behavior — these docs are the source of truth for the in-app vocabulary:

- [`../push-me-backlog/docs/brand-dna.md`](../push-me-backlog/docs/brand-dna.md) — canonical terminology (Bomp, Bomper, Bompear, Escala Richter levels: Bompín / Bompazo / Bompardo / Bompión, Inmortal as cloud-state descriptor)
- [`../push-me-backlog/CLAUDE.md`](../push-me-backlog/CLAUDE.md) — Product Language glossary and spec conventions
- [`../push-me-backlog/backlog/`](../push-me-backlog/backlog/) — pending feature specs (the "why" behind features)

Skip for refactors, dep bumps, build config, and platform-wiring fixes — those don't need brand context. If the sibling path isn't present (CI, alternate checkout layout), proceed with the in-repo strings as authoritative and surface the gap to the user.

## Repo writing language

Contributor-facing files in this repo are written in **English**: `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `CODE_OF_CONDUCT.md`, ADRs under `docs/adr/`, `.github/` templates, `CLAUDE.md` itself, code comments, commit messages, PR descriptions, and handoff notes. This applies to any new file you create whose audience is contributors or maintainers.

The only exception is **embedded examples of user-facing copy**: when a doc demonstrates how a localized string should read (e.g. the ❌/✓ examples under § Copy & localization, or a snippet quoted from `strings.xml`), the example stays in its target locale (typically es-AR) so the rule is illustrated faithfully. The surrounding prose explaining the example is still in English.

User-facing surfaces (in-app strings, store listings, push notifs, Play Console "What's New", marketing emails) are out of scope for this rule and follow § Copy & localization — they ship in the target locale.

## Copy & localization

When generating user-facing copy in any locale (in-app strings, store listings, push notifs, changelogs, emails) the output must read **native to the target locale**, not as a literal translation from another language — and must not contradict the brand DNA or the published legal policies.

**Sources of truth to consult before drafting copy** (paths relative to `/Users/barrios.nahuel/Workspace/push-me/`):

- `../push-me-backlog/docs/brand-dna.md` — canonical terminology, reserved terms, anti-positioning bans
- `../push-me-backlog/CLAUDE.md` — Product Language glossary (incl. which terms are gated to non-shipped features like Pro/cloud)
- `../push-me-ghpages/privacy-policy.html` — published Privacy Policy (rights ARCO, retention, third-party data)
- `../push-me-ghpages/data-safety.html` — published Data Safety declaration (what's collected, shared, optional)

If any of these paths is missing (CI, alternate checkout), do **not** invent the claim — flag the gap to the user and proceed with the in-repo strings as authoritative.

**Default locale for in-app strings.** `app/src/main/res/values/strings.xml` is the **English** master; Spanish-AR copy lives in `values-es/strings.xml`.

Hard rules:

- **No calque translations.** A phrase that's natural in the source can be wrong in the target. Examples we hit and fixed during the en-US listing: "save a day" (calque of "salvar un día" — correct English idiom is `save the day`); "on the other side" (calque of "del otro lado" — in English this means *afterlife*; the phone idiom is `on the other end`); "your audios" (calque of "tus audios" — `audio` is a mass noun in English, the natural plural is `voice notes` / `voice clips` / `voice memos`).
- **Use the target locale's idioms and collocations.** Verify with a native speaker or a current idiom reference, not Google Translate. When in doubt, prefer the simpler concrete word the audience already uses every day.
- **Punchy register, locale-aware.** US English marketing leans on contractions, short sentences, imperative verbs, and concrete sensory nouns. Spanish-AR leans on voseo and warmth. Match the register the locale expects, not a generic "neutral" tone.
- **Vocabulary the target audience uses.** If the source describes the input as "audios", the English version should name it the way English speakers do (`voice notes`, `voice memos`). Mapping is not 1:1.
- **ASO awareness for store-listing copy.** For Play Console copy (title, short description, full description, screenshot headlines, feature graphic taglines), integrate the high-volume queries the target market actually searches — organically, without breaching the brand-DNA bans (`soundboard`, `audio sticker`, `panel`, `viralizá`, `share with friends/followers` as a CTA). Those are positioning bans, not vocabulary bans — a category descriptor (e.g. `voice notes`) is fine because it names the input, not the brand position.
- **Brand-DNA invariants.** The proper nouns Bomp / Bomper / Bompear / Bompeable NEVER translate. The manifesto closing ("Un audio de los tuyos no es un mensaje, es un abrazo que se escucha." / locale-equivalent that preserves meaning) is invariant across surfaces and locales.
- **Brand-DNA-precision check (reserved terms).** Before using any term that appears in `brand-dna.md` or the Product Language glossary, verify it is not reserved for a non-shipped feature or a specific technical state. If it is, pick a locale-native synonym instead — even if the reserved term sounds right. Today's reserved terms: `Inmortal` / `immortal` (state descriptor for a Bompión synced to cloud via Saved Games SDK — a Pro feature **not shipped yet**); `Bompardo` and `Bompión` (Escala Richter levels 4 and 5, gated by share milestones — do not apply to a generic Bomp); `Bomptástico` (internal telemetry label only, never appears in UI).
  - ❌ "Tus Bomps son inmortales" to describe Auto Backup → ✗ `Inmortal` is reserved for the not-yet-shipped Pro cloud-sync state, this overstates the feature.
  - ✓ "Tus Bomps quedan respaldados en tu Google Drive" — accurate, locale-native, does not borrow reserved vocabulary.
- **Policy-contradiction check (no overclaims).** Before writing any absolute claim — e.g. `imborrable`, `permanent`, `forever`, `never lost`, `100% private`, `always`, `nunca se pierde`, `we never see your data` — open `../push-me-ghpages/privacy-policy.html` and `../push-me-ghpages/data-safety.html` and check the claim does not contradict a published statement or strip a user right declared there. Concrete invariants today: the user can delete Bomps one-by-one or by uninstall (ARCO §05); Auto Backup is user-controllable from the OS settings (PP §02); Firebase collects pseudonymous crash logs, performance, and aggregated interactions (DS §01) — copy cannot claim "no data ever leaves the device".
  - ❌ "Tus Bomps son imborrables" → ✗ contradicts the user's right to delete declared in Privacy Policy §05 and Data Safety §02.
  - ✓ "Tus Bomps quedan guardados hasta que vos decidas borrarlos" — preserves the user's deletion right and matches the published policy.
- **Read-aloud check before ship.** Read every paragraph aloud as a native speaker of the target locale. Stumbles, false friends, weird tense, or "wait, what?" reactions are blockers — fix before submitting to Play.
- **Cross-surface consistency.** If a verb pattern is `Save. Name. Bomp.` in headers, the body copy must use the same verbs ("give it a name", not "give it a label"). Cross-reference all surfaces of a locale (title, short, full, screenshots, feature graphic, video script) before shipping a locale.

## Bug fixes — TDD workflow

When the user reports a bug or says we are going to fix a bug, always follow Test-Driven Development:

1. **Write a failing test first** that reproduces the bug. Run it to confirm it fails for the right reason.
2. **Fix the production code** with the minimum change needed to make the test pass.
3. **Run the full test suite** (`./gradlew test`) to verify nothing regressed.

Skip TDD only when the bug lives exclusively in UI rendering or platform wiring that cannot be exercised by unit or Robolectric tests (e.g. a pure layout glitch). In that case, note why TDD was skipped.

## Features — test coverage workflow

Before writing production code for a new feature, identify and agree on the minimum test scenarios:

1. **Happy path** — the feature works as intended under normal conditions.
2. **Failure modes at system boundaries** — external inputs that can fail: audio file I/O, MediaPlayer errors, permissions denied, Play Store feature delivery failures, etc.
3. **Smoke test** — see the Activity smoke tests section below for requirements.

Implement the tests **alongside** the feature, not after. Any scenario not listed before starting is out of scope for the current PR — note it in the PR description.

Skip a test scenario only when it lives exclusively in platform wiring that cannot be exercised by unit or Robolectric tests (e.g. a pure layout change). In that case, note why it was skipped.

## Test naming convention

Test names are **descriptive sentences**, never opaque identifiers like `testFoo1`. Reports list them verbatim, so they should read like a spec.

- **JVM tests** under `src/test/` (Robolectric, pure Kotlin): use **backtick-quoted strings**, sentence-case, no trailing period.
  ```kotlin
  @Test
  fun `searchResults emits empty list when query is blank`() { ... }
  ```
- **Instrumented tests** under `src/androidTest/`: use **camelCase** descriptive names — backticks with spaces require DEX format 040, which D8 in the current AGP version refuses to emit even with `minSdk` overrides on the test variant.
  ```kotlin
  @Test
  fun swipeRightPinsACustomSound() { ... }
  ```
  When the app's `minSdk` (currently 23) and AGP both move past the DEX 040 boundary, migrate instrumented tests to backticks for consistency.

## Activity smoke tests

Every `Activity` in the `app` module must have a corresponding smoke test that verifies it reaches `Lifecycle.State.RESUMED` without crashing. Place it alongside the Activity in `app/src/test/`, extend `AbstractRobolectricTest`, and use:

```kotlin
ActivityScenario.launch(MyActivity::class.java).use { scenario ->
    assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)
}
```

Mock any singleton factories (e.g. `PlayerControllerFactory`) that would crash under Robolectric. See `LandingActivityTest` for the canonical example.

If a future Activity ends up in a dynamic feature module again, note that Robolectric's `ShadowPackageParser` rejects split APKs (`Expected base APK, but found split`) — those Activities need instrumented tests for smoke coverage.

Full-screen composables with their own business logic (PackageManager calls, raw resource reads, or significant state) must also have a `createComposeRule()` smoke test that verifies they render without crashing. See `AboutScreenTest` as the canonical example:

```kotlin
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class MyScreenTest : AbstractRobolectricTest() {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `MyScreen renders without crashing`() {
        composeTestRule.setContent { AppTheme { MyScreen(onBack = {}) } }
        composeTestRule.waitForIdle()
    }
}
```


## Analytics events

Firebase Analytics goes through the `AnalyticsTracker` wrapper at
`commons_android/.../analytics/`. Three sibling files are the catalogue the
rest of the app references — read them when in doubt:
`AnalyticsEvent` (sealed class, one subclass per custom event),
`CanonicalScreenName` (every `screen_view` literal),
`AnalyticsUserProperty` (user property names + lifetime counter keys).

Hard rules that affect how to write code:

- Never call `FirebaseAnalytics.getInstance(...)` or `.logEvent(...)` outside
  the wrapper — the `analytics-wrapper-guard` CI job fails the build.
- Auto `screen_view` is disabled via manifest meta-data; every screen emits
  `tracker.logScreen(CanonicalScreenName.X)` manually with a canonical literal.
- The `first_*` variant is emitted by the wrapper when the event declares
  `hasFirstVariant = true` — call-sites never reference `first_*` directly.
- In tests, substitute the tracker via `AnalyticsTrackerProvider.setForTest(FakeAnalyticsTracker())`
  and assert with `fake.assertEmitted(...)` / `fake.assertScreenView(...)` — never mock
  `AnalyticsTracker` directly. The fake lives in `:commons_android` test fixtures.

When adding a new track, follow `CONTRIBUTING.md` § "Analytics events 📊" — the
naming rules, regression-test matrix, and DebugView / `adb logcat -s FA FA-SVC`
verification commands live there. Do not shortcut the manual smoke step before
merging; the aggregated Reports dashboards have a 24–48 h delay and do not
confirm a single new event.


## Error tracking (non-fatals)

Non-fatal exceptions go to Firebase Crashlytics through the `Tracker` wrapper at
`commons_android/.../error/Trackable.kt`. Two methods, very different effects:

- `Tracker.track(throwable)` → calls `FirebaseCrashlytics.recordException(...)`.
  **This is what shows up as a non-fatal in the Crashlytics dashboard** with the
  full stack trace. Use this for any caught exception you want operations to
  see.
- `Tracker.log(message)` → calls `FirebaseCrashlytics.log(...)`. **Breadcrumb
  only**: it is attached to the next crash/non-fatal recorded after it, and is
  invisible in the dashboard until then. Useful right before a `Tracker.track(...)`
  to attach extra context, not as a standalone report.

Hard rules that affect how to write error-handling code:

- **Do NOT rely on `Timber.e(throwable, message)` to surface a non-fatal.** The
  `ErrorTrackerTree` Timber tree (planted in debug and release) forwards only
  the formatted message via `Tracker.log(...)` — the throwable parameter is
  silently dropped. As a maintainer you will not see those events as non-fatals
  in the dashboard. `Timber.e` / `Timber.w` are fine for logcat-only diagnostic
  output during development.
- For caught exceptions, follow the established pattern (see
  `PlayerControllerImpl.kt`): wrap the cause in a `RuntimeException` whose
  message describes the operation, then hand it to `Tracker.track`. The wrapper
  message becomes the searchable Crashlytics title; the original throwable is
  preserved as `cause` with full stack trace.
  ```kotlin
  } catch (e: ActivityNotFoundException) {
      Tracker.track(RuntimeException("Could not launch ACTION_VIEW for $url", e))
      // ...recovery UI (snackbar, fallback) goes here
  }
  ```
- A caught exception that is **expected and recoverable** (e.g. user dismissed
  a chooser) does not need `Tracker.track`. Reserve it for things you want to
  investigate.
- In tests, you can mock `Tracker` with MockK (see `PlayerControllerTest.kt`):
  `every { Tracker.track(any()) } answers { nothing }`.


## StrictMode debug audit

Single source of truth: `app/src/debug/.../StrictModeConfigurator.kt` (debug-only,
never reaches release builds). Both ThreadPolicy and VmPolicy use `detectAll()`
plus an explicit `detectNonSdkApiUsage()`; **`penaltyLog()` and `penaltyDeath()`
are intentionally not set on the builders** — both fire before `penaltyListener`
and bypass the filter. Every detected violation flows through `reportViolation()`,
which filters via `KNOWN_THIRD_PARTY_VIOLATIONS` and on a hit calls
`Tracker.track(StrictModeException(violation))` AND posts a throw to the main
looper so the process dies. The wrapper exception's message is
`"StrictMode: <ViolationClassName>"`, so the single logcat line emitted by
`Tracker.track` reads `Tracking error to Firebase Crashlytics: StrictMode: <…>`
under the `Tracker` tag — searchable via `grep StrictMode` without a dedicated
tag. Unknown violations crash debug runs and the instrumented suite until a
matcher is added — by design, so nothing slips past silently. Logcat, Crashlytics
and process state stay in sync — the matcher list is the only way to silence any
of them.

When a new violation surfaces, choose in this order:

1. **Top app-code frame is ours** (`com.github.barriosnahuel.vossosunboton.*`):
   fix the production code. Don't filter.
2. **Scopable to a known-OK call-site we own** (e.g. an SDK init that
   legitimately reads disk on first call): wrap with
   `StrictMode.allowThreadDiskReads()` + `try/finally` at that call-site.
   Canonical example: `AnalyticsTrackerProvider.createTracker`. Don't add a
   matcher.
3. **Third-party class running its own code** (Compose, Espresso, GMS,
   framework finalizers): add a `KnownThirdPartyViolation` to the list with a
   comment naming the library + (when public) the upstream issue. Use
   `methodNameContains` when the class prefix would over-match (the framework's
   own `android.os.StrictMode` is one such case), or `fileNameContains` when the
   classes are obfuscated and unstable (GMS Dynamite modules ship as `m7.*` etc.
   and the loader / module identifier lives in `StackTraceElement.fileName`).

Filter logcat with `adb logcat | grep StrictMode` (or scope by tag and grep:
`adb logcat -s Tracker:E | grep StrictMode`) — operator workflow lives in
`CONTRIBUTING.md` § "Terminal: StrictMode violations".


## Worktree setup

After creating a new worktree, always run these commands to replace the dummy `google-services.json` and copy the bundled audio files from the main worktree:

```bash
cp "$(git rev-parse --git-common-dir)/../app/google-services.json" app/google-services.json
git update-index --skip-worktree app/google-services.json
cp "$(git rev-parse --git-common-dir)/../model/src/debug/res/raw/"*.mp3 model/src/debug/res/raw/ 2>/dev/null || true
cp "$(git rev-parse --git-common-dir)/../model/src/debug/res/raw/"*.ogg model/src/debug/res/raw/ 2>/dev/null || true
```
- Release signing requires `nahuelbarrios.keystore-appbundle.pkcs12` and `secure.properties` (with `key.alias`, `key.password`, `store.password`) in the project root — not committed.
- Debug builds use the included debug keystore and work without the above.
- Bundled audio files (`model/src/debug/res/raw/*.mp3` and `*.ogg`) are not committed. Without them the debug build still compiles and runs, but the Explore tab will be empty.

## Android resources naming

Every resource name must start with the `resourcePrefix` defined in the module's `build.gradle`:

| Module | Prefix |
|---|---|
| `app` | `app_` |
| `commons_android` | `commons_android_` |
| `commons_file` | `commons_file_` |
| `model` | `model_` |

Logical sub-areas inside `:app` (e.g. the Add Button flow at `feature/addbutton/`) use a secondary prefix on top of `app_` for grouping — `app_addbutton_*`, `app_about_*`, etc. Keep new resources clustered by feature this way.

Android Lint enforces this rule (`ResourceName` check). Violating it causes a build failure.

## Copyright headers

Every `.kt` source file must start with the AGPLv3 copyright block (enforced by Spotless at CI time):

```
/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
```

If `./gradlew check` fails with a Spotless violation, run `./gradlew spotlessApply` to auto-fix all files.

## About screen

**Do not remove or hide the About screen.** It is the "Appropriate Legal Notices" mechanism required by AGPLv3 §0. Its entry point is the TopAppBar overflow menu in `LandingScreen.kt`.

## Pre-PR checklist

Before opening a PR for any feature or bug fix, verify:

- [ ] Happy path is covered by at least one test
- [ ] Failure modes at system/external boundaries have tests (file I/O, MediaPlayer, permissions, network, Play feature delivery)
- [ ] New `Activity` has a smoke test (see Activity smoke tests section)
- [ ] New full-screen Composable with business logic has a `createComposeRule()` smoke test
- [ ] Any skipped scenario is explicitly noted with a reason (not silently omitted)
- [ ] Self code review: re-read every changed file as a reviewer, not as the author — look for logic gaps, missing edge cases, and unclear naming

Once all items pass, run the **Pre-push checklist** below before pushing.

## Pre-push checklist

All linters run on CI and must pass:
- **KtLint** — style (runs as part of `check`; auto-fix with `ktlintFormat`)
- **Detekt** — static analysis (config: `config/detekt/detekt-config.yml`; max line length 150)
- **Spotless** — AGPLv3 copyright headers (runs as part of `check`; auto-fix with `spotlessApply`)
- **Android Lint** — lint rules in `config/android/android-lint.xml`

Before pushing any branch, always run:

```bash
./gradlew check -x test && ./gradlew test
```

This catches the same failures CI will report (ktlint, detekt, Spotless, Android lint, unit tests) without waiting for a full CI run.

**Functional changes also require the local UI test suite** (see next section). If the change touches user-facing behavior — Composables, ViewModels, intents, navigation, deep links, persistence — run the instrumented suite on an emulator before pushing. CircleCI does not execute it. Cosmetic-only changes (CHANGELOG, copy strings, README, comments) are exempt.

## Local UI test suite

Instrumented UI/functional tests live under `app/src/androidTest/`. They drive a real emulator using Compose UI Test + Espresso + UI Automator + Espresso Accessibility Checks. CircleCI intentionally does not run them — the rationale, alternatives considered, and tradeoffs are in [`docs/adr/0001-local-ui-test-suite.md`](docs/adr/0001-local-ui-test-suite.md).

### Setup (one-time)

```bash
# Creates the AVD `push_me_test` (idempotent, ~5 min the first time including system image download)
./scripts/setup-test-emulator.sh
```

### Run the full suite

```bash
# 1. Boot the emulator (background)
emulator -avd push_me_test -no-snapshot-save -no-boot-anim &
adb wait-for-device shell 'while [[ $(getprop sys.boot_completed) != 1 ]]; do sleep 1; done'

# 2. Run all instrumented tests (UTP installs + runs natively)
./gradlew app:connectedDebugAndroidTest
```

HTML report: `app/build/reports/androidTests/connected/debug/index.html`. Raw
XML: `app/build/outputs/androidTest-results/connected/debug/`.

### Run a single test class

```bash
./gradlew app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.github.barriosnahuel.vossosunboton.ui.home.SearchOverlayTest
```

### When to run

- After any change to a Composable, ViewModel, intent flow, navigation, deep link, or persistence layer.
- Not required for changes limited to: CHANGELOG, copy strings, README, comments, configuration of off-device tooling.

## Accessibility (WCAG 2.2 AA)

All UI development and generated assets (store listing, What's New, changelogs) must target **WCAG 2.2 Level AA**. Key requirements:

- **Contrast – text (1.4.3):** ≥ 4.5:1 for normal text; ≥ 3:1 for large text (≥ 18 sp or ≥ 14 sp bold)
- **Contrast – non-text (1.4.11):** ≥ 3:1 for interactive UI components (icon-only buttons, input borders, focus indicators)
- **Color not sole indicator (1.4.1):** Never use color alone to convey state — pair with an icon, label, or pattern
- **Content descriptions (1.1.1):** Every `Icon`/`Image` conveying information needs a non-null `contentDescription`; purely decorative assets use `contentDescription = null`
- **Touch targets (2.5.8):** Minimum 24 × 24 dp; prefer 48 × 48 dp for primary actions
- **Labels match names (2.5.3):** Visible button/field labels must match the accessible name used by screen readers

Verify contrast when adding or changing colors. Use the [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/) or the Material Theme Builder. The brand palette in `AppTheme.kt` was designed to meet AA across all color roles. **All critical role pairs are automatically verified by `AppThemeContrastTest`** — if you change the palette and a test fails, fix the theme, not the test.

## Security boundaries

Concrete rules for input/output validation and component exposure. These match
the existing concrete style of the rest of this doc — narrow, enforceable, no
generic policy framing.

### Inbound URI validation

When the app receives a `Uri` via `Intent.EXTRA_STREAM` or `ACTION_SEND` (today
only `AddButtonActivity`), validate it before opening the stream:

- **Scheme allowlist:** only `content` and `file` pass; reject everything else
  (e.g. `http`, `javascript`, `data`).
- **MIME type:** `ContentResolver.getType(uri)` must start with `audio/`. If
  null, reject — no MIME means we don't know what we're opening.
- **Size cap:** reject inputs over 50 MB (≈4× a 5-min MP3 at 320 kbps; rejects
  pathological inputs while leaving headroom). Resolve size via
  `ContentResolver.openAssetFileDescriptor(uri, "r")?.length` or
  `OpenableColumns.SIZE`. Unknown size also rejects.
- **Failure mode:** surface a typed feedback string-res to the caller (same
  channel as `app_feedback_generic_error_contact_support`). Never throw raw.

The canonical implementation lives in `AddButtonFeature.saveNewButtonAsync`.
Any future inbound-URI surface must call the same validator.

### Deep link path allowlist

`push-me://open<path>` routes against a closed allowlist of known destinations
declared in `LandingActivity.handleDeeplink`. Today: `/home` → `MY_SOUNDS`,
`/explore` → `EXPLORE_SOUNDS`. **Unknown paths fall back to `MY_SOUNDS`** (the
safe default) — never silently route to Explore or any other tab. New
destinations require an explicit branch in the `when`; the `else` stays
`MY_SOUNDS`.

### Backup hygiene

Before adding any new SharedPreferences key or file path that could contain
sensitive data (auth tokens, account identifiers, private user content), add
explicit `<exclude>` entries to both `app/src/main/res/xml/app_backup_rules.xml`
and `app/src/main/res/xml/app_data_extraction_rules.xml`. Today nothing
sensitive is stored, so the rules are intentionally permissive (`<include>` of
`my-prefs` and the `Music` external dir). When that changes, the exclusion
ships in the same commit as the new key.

### Exported components default to false

New `Activity`/`Service`/`Receiver` declarations in `AndroidManifest.xml`
default `android:exported="false"`. Set `true` only when the component has an
`<intent-filter>` for external callers; in that case, add a comment above the
declaration documenting which intents it accepts and from where (launcher,
system share sheet, deep link, etc.). The two exported activities today are
`LandingActivity` (LAUNCHER + `push-me://open` deep link) and
`AddButtonActivity` (system share-sheet `ACTION_SEND` with `audio/*`).

## Store listing asset generation

Store listing PNGs (icon, feature graphic) are rendered from SVG masters under `store-listing/`. The canonical pipeline is **`rsvg-convert`** (`brew install librsvg`) — fast, CLI, reproducible, no GUI. Use Inkscape only when you need to tweak typography by hand before export.

Before exporting any asset, verify the required fonts are installed system-wide. The brand stack is **Inter** (Roboto + system-ui as fallbacks). The Inter distribution lives zipped at `store-listing/brand/fonts/Inter.zip` (committed under SIL OFL — `OFL.txt` is inside the archive). Install on macOS with:

```bash
unzip -j -o store-listing/brand/fonts/Inter.zip "*.ttf" -d ~/Library/Fonts/
```

(`-j` flattens the nested `static/` subdirectory; `-o` overwrites silently.)

If a future asset needs a different font family, drop its zipped distribution at `store-listing/brand/fonts/<Family>.zip` (license file inside) and document the install step here.

The full human walkthrough — tooling tradeoffs, exact export commands, screenshot capture from the running emulator — lives in `CONTRIBUTING.md` § "Store listing".

When writing locale copy (title, short, full description, screenshot headlines, feature graphic tagline), see § "Copy & localization" for naturalness, idiom, and ASO rules.

## Design system

The app uses the **Neo-Club** palette (ink × acid). Single source of truth: `app/src/main/java/…/ui/theme/AppTheme.kt`.

### Palette

| Token | Value | Notes |
|---|---|---|
| `Ink1000` | `#0B0B0C` | Near-black; dark bg, top-bar backgrounds |
| `Ink900` | `#141415` | Card/nav bg in dark mode |
| `Ink800` | `#1C1C1D` | Snackbar bg in light mode |
| `Ink50` | `#F1F0EA` | Card/nav bg in light mode |
| `Paper` | `#FAFAF7` | Bone-white; light bg, top-bar text |
| `Acid400` | `#D7FF3A` | Signal yellow-green; all filled actions |
| `AcidDark` | `#3E5400` | Acid darkened for text on light surfaces |
| `Blood600` | `#C72C2F` | Destructive (light error, dark errorContainer) |

### Semantic role → intent mapping

| Role | Light | Dark | Used for |
|---|---|---|---|
| `primary` | AcidDark | Acid400 | Nav text, active labels |
| `primaryContainer` | Acid400 | Acid400 | FAB, buttons, swipe-pin bg, search accent |
| `onPrimaryContainer` | Ink1000 | Ink1000 | Text/icons on acid fills |
| `secondary` | Ink1000 | Ink900 | Top-bar backgrounds (always dark) |
| `onSecondary` | Paper | Paper | Top-bar title and icons |
| `surfaceVariant` | Ink50 | Ink900 | Card backgrounds, bottom-nav background |
| `onSurfaceVariant` | Ink500 | Ink300 | Card muted text, unselected nav icons |
| `inverseSurface` | Ink800 | Paper | Snackbar background |
| `inversePrimary` | Acid400 | AcidDark | Snackbar action button text |
| `error` / `errorContainer` | Blood scale | Blood scale | Swipe-delete background, error states |

### Rules for component authors

- **Never add `isSystemInDarkTheme()` / `isDark` in component files.** If the same semantic role needs to look different per mode, the role mapping in `AppTheme.kt` is wrong — fix the theme, not the component.
- **Never hardcode a color literal in a component** (e.g. `Color(0xFF2E7D32)`). Use the closest semantic role from `AppTheme.kt`.
- **Components inside always-dark bars** (TopAppBar using `secondary`): use `primaryContainer` (= Acid400 in both modes) for accent elements like cursor, underline, and icons — not `primary`, which is AcidDark in light mode and nearly invisible on a dark bar.
- **Adding a new color:** add the constant to `AppTheme.kt`, map it to an M3 role in both `LightColors` and `DarkColors`, then add a contrast assertion for the relevant pair in `AppThemeContrastTest`.

## Labels and milestone

Apply exactly one `a:` label to every PR before merging. Do not call `gh label list` — use this table.

For the milestone, read the `## [unreleased]` line in `CHANGELOG.md` — the version in parentheses is the milestone name (e.g. `(v2.0.0)` → milestone `v2.0.0`). Do not call `gh api repos/.../milestones`.

| Label | When to use |
|---|---|
| `a:bug` | Issue reporting something broken |
| `a:feature` | PR that adds new user-facing functionality |
| `a:feature-request` | Issue requesting a feature not yet built |
| `a:fix` | PR that corrects a reported bug |
| `an:enhancement` | PR that improves existing functionality without adding new features |
| `dependencies` | PR that updates library, plugin, or Gradle wrapper versions (applied automatically by Dependabot) |
| `stale` | Issue or PR with no recent activity — candidate for closing |

## Third-party notices

`app/src/main/res/raw/app_third_party_notices.txt` lists all runtime dependencies with their license attribution. **Update this file whenever you add or remove a runtime dependency** (`implementation`, not `testImplementation` or `debugImplementation`).

Each entry follows this format:

```
--------------------------------------------------------------------------------
Library Name
Copyright (C) Author
License Name
https://project-url
--------------------------------------------------------------------------------
```

## Changelog

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format:
- Sections: `Added`, `Changed`, `Fixed`, `Removed` under `## [unreleased]`
- Each entry is a single sentence starting with a capital letter, no trailing period
- For dependency bumps, write one line summarising the overall bump (e.g. "Bumped all dependencies to latest stable"), not one line per library
- As part of each commit, if the change is user-visible or architecturally significant, update `## [unreleased]` before committing
- Never add a `Fixed` entry for a bug introduced in the same `[unreleased]` cycle. If end-users never experienced the regression, it has no changelog entry — git history provides the traceability
- **User-facing first, technical under "For nerds":** within `## [unreleased]`, list user-facing changes under the standard `### Added/Changed/Fixed/Removed` headings, then put technical/contributor-only changes under a `### For nerds 🤓` subsection with `#### Added/Changed/Fixed/Removed` sub-headings (omit any that would be empty). A change is **user-facing** if a normal user would notice it: visible UI, labels, copy, behavior, permissions, performance they can feel. **Technical** means: build/CI/tooling, dependency bumps, internal refactors, test infrastructure, Play Console assets internal to the repo, README/docs, analytics instrumentation. This split applies only to `[unreleased]` and going forward — released versions stay as written

## Handoff notes

`handoff/` contains session handoff documents with decisions taken, key file paths, and pending work. Ignored by git. Only read these files when the user explicitly references them to continue a previous topic.

## Issue tracking

GitHub Issues are open for external feature requests and bug reports. Out-of-scope work identified during development is noted in the PR description; session context goes in handoff notes.