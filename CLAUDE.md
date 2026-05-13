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

The Android command-line tools — `adb`, `fastboot`, `emulator` — are on `PATH` and available directly when needed.

## Module Architecture

Push Me is an Android soundboard app with 4 Gradle modules:

- **`app`** — Activities, Fragments, RecyclerView adapters, feature layer (playback, share, permissions, add-button). Entry point is `LandingActivity`. The Add Button flow lives at `feature/addbutton/` inside this module.
- **`model`** — Business logic: `Sound` data model, data managers, persistence. No Android UI dependencies.
- **`commons_android`** — Foundation library: Firebase init, Timber logging setup, annotations.
- **`commons_file`** — File handling utilities (audio I/O).

Dependency direction: `app` → `model`, `commons_android`, `commons_file`. No dynamic features today.

## Sources of truth for Android / Kotlin / Compose decisions

For non-trivial decisions, consult the authoritative source first (WebFetch the page, or invoke the linked skill) and cite it. Don't answer from training-data memory in version-sensitive areas — the platform moves. If the source is unreachable, say so and mark the answer as a heuristic.

| Area | Authoritative source |
|---|---|
| Jetpack Compose: state, recomposition, side-effects, performance, lifecycle-aware APIs | https://developer.android.com/develop/ui/compose |
| Navigation in Compose | Linked skill `navigation-3` (covers Navigation 3 install/migration, deep links, multi-backstack, Hilt/ViewModel integration) |
| XML view → Compose migration | Linked skill `migrate-xml-views-to-jetpack-compose` |
| Edge-to-edge / system bars / insets | Linked skill `edge-to-edge` |
| Coroutines, Flow, StateFlow, structured concurrency, dispatchers, testing with `runTest` | https://kotlinlang.org/docs/coroutines-guide.html and https://developer.android.com/kotlin/coroutines |
| Lifecycle: `repeatOnLifecycle`, `collectAsStateWithLifecycle`, lifecycle-aware components | https://developer.android.com/topic/libraries/architecture/lifecycle |
| App architecture (UI / domain / data, UDF, ViewModel + UI state, UI events) | https://developer.android.com/topic/architecture and https://developer.android.com/topic/architecture/ui-layer/events |
| DataStore (Preferences/Proto), migration from SharedPreferences | https://developer.android.com/topic/libraries/architecture/datastore |
| Background work, WorkManager, foreground services, exact alarms | https://developer.android.com/develop/background-work |
| Permissions / runtime permissions / scoped storage | https://developer.android.com/training/permissions |
| Accessibility in Compose (semantics, traversal, click labels, custom actions, large text) | https://developer.android.com/develop/ui/compose/accessibility — pairs with WCAG 2.2 AA (see § Accessibility) |
| AGP 9 migration | Linked skill `agp-9-upgrade` |
| R8 keep rules audit | Linked skill `r8-analyzer` |
| Play Billing | Linked skill `play-billing-library-version-upgrade` |
| Kotlin idioms, conventions, KEEP proposals | https://kotlinlang.org/docs/coding-conventions.html and KEEP at https://github.com/Kotlin/KEEP |
| Project-specific architectural decisions | `docs/adr/*.md` — read the relevant ADR before changing the area it governs |

## Project-specific overrides (read before changing platform-touching code)

Decisions this repo took that diverge from — or narrow — the generic Android recommendation. The override wins over the public docs *for this codebase*. If you think an override is wrong, raise it before changing — don't silently flip.

- **DI: manual factories, no Hilt.** ViewModels are constructed via `viewModelFactory { initializer { ... } }` (see `SoundsViewModel.kt`). Do not introduce Hilt, Koin, or any DI framework without superseding [ADR 0002](docs/adr/0002-no-hilt-manual-viewmodel-factory.md) (rationale, options considered, revisit criteria).
- **One-shot UI events: `Channel<T>` + `receiveAsFlow()`.** Pattern in `SoundsViewModel._scrollToTopEvent`. Reuse for new events; don't introduce event-as-state ad-hoc, don't add `SharedFlow` "for events". See [ADR 0003](docs/adr/0003-channel-for-one-shot-ui-events.md) for trade-offs and the explicit revisit criteria (delivery-guarantee flows, lost-event reports).
- **Async / state model:** `viewModelScope` + `StateFlow` for screen state. Pick `Channel` (existing convention, ADR 0003) for one-shot events.
- **Threading:** dispatchers are *constructor-injected* into ViewModels (default `Dispatchers.IO`). No `runBlocking` in production code, except the analytics-cache-prime exception ([ADR 0004](docs/adr/0004-datastore-sync-api-cache-prime.md)). No raw `Thread { ... }` or `AsyncTask`. No I/O on the main thread.
- **Persistence:** see § *Persistence* — DataStore Preferences only, `SharedPreferences` forbidden, the sync-API pattern is in-memory cache + async write-back.
- **Networking, image loading, Room:** none in the dependency graph today. New deps for any of these need an ADR before the feature PR.
- **Analytics, error tracking, StrictMode, security, design system, accessibility:** see the dedicated sections below — those rules are stricter than any generic Android guidance and override it.

**Each substantive override is backed by an ADR.** This section is the index; rationale + revisit criteria live in `docs/adr/*.md`. Grep-able invariants are enforced by `scripts/check-adr-invariants.sh` (CircleCI job `adr-invariants`) — failures name the ADR you broke. On dependency bumps (`gradle/libs.versions.toml`, wrapper, any `build.gradle(.kts)`) or after running an upgrade skill, re-read this section and § *Sources of truth* in the same PR.

### Known migration debt

Things the current codebase does that are *not* the recommended pattern. New code uses the recommended form; existing call-sites migrate when touched.

- **`collectAsState()` → `collectAsStateWithLifecycle()`.** The lifecycle-aware variant stops collection in `STOPPED`, avoiding wasted work and stale `StateFlow` references. New Composables collecting a ViewModel `StateFlow` must use `collectAsStateWithLifecycle()`; existing call-sites migrate when their file is touched.
- **String resources: every user-facing string via `stringResource(R.string.app_*)`.** Plain best practice (i18n + a11y + maintainability). No hardcoded literals in Composables. `contentDescription` for non-decorative `Icon`/`Image` is mandatory and must come from a string resource (see § *Accessibility*); decorative assets use `contentDescription = null` explicitly.

## Stateful Composables — `rememberSaveable` for durable state

`remember { mutableStateOf(...) }` is for transient gesture state (drag offset, dismissable popups). State that represents user progress — typed text, an opened overlay/sheet/sub-screen, an in-flight error — goes through `rememberSaveable` so an Activity recreate (rotation, theme change, system kill) does not silently rewind the user. For non-autosaveable types (sealed classes with data, etc.), write an explicit `Saver` next to the Composable; canonical template is `SaveOutcomeSaver` at the bottom of `AddButtonScreen.kt`, including which variants intentionally collapse on restore and why.

When a `LaunchedEffect` calls `FocusRequester.requestFocus()` on first composition, wait for the first frame then guard the call: `withFrameNanos { } ; runCatching { requestFocus() }.onFailure { Tracker.log(...) ; Tracker.track(RuntimeException("static title", it)) }`. The bare call silently no-ops if the node is not yet attached (the user loses the IME on the screen's primary input). Reference: `AddButtonScreen.kt`, `SearchOverlay.kt`.

## Persistence

Use **Jetpack DataStore Preferences** for any new persistent key-value storage. Pattern lives in `model/.../SoundsRepository.kt` (top-level `Context.bompsStore` delegate via `preferencesDataStore(...)` + `ReplaceFileCorruptionHandler`). Mirror it. `WelcomeStickerStore`, `DataStoreFirstFlagStore`, `DataStoreCounterStore` are reference implementations.

`SharedPreferences` is **forbidden**. The grep `getSharedPreferences|EncryptedSharedPreferences` must return zero hits in `src/main` across all modules. Reviewers reject any new SharedPrefs in PRs.

When a call site needs a synchronous read on top of DataStore (e.g. analytics tracker firing events right before navigating to a chooser), use the in-memory-cache + async-write-back pattern from `DataStoreFirstFlagStore.kt` / `DataStoreCounterStore.kt`. This is the **only** documented exception to the no-`runBlocking`-in-production rule — see [ADR 0004](docs/adr/0004-datastore-sync-api-cache-prime.md) for context, scope, and why we don't generalize it. Test fixtures (every store ships `clearForTest()`) are documented in `CONTRIBUTING.md` § *Testing → Test fixtures*.

## Worktree setup

Invariants:

- The repo commits **scrubbed dummy** `google-services.json` at `app/src/{debug,release}/google-services.json` (real `project_id` / `project_number` / `package_name`, fake `mobilesdk_app_id` and `api_key`) so CI compiles and GitGuardian doesn't flag real keys. Real values live only in the working tree, hidden from git via `git update-index --skip-worktree` — **never** unmark skip-worktree and `git add` without first stashing the real file (CONTRIBUTING.md § *Firebase config file* has the safe edit sequence).
- Two Firebase projects back the build types: `bomp-prod` for release (`com.github.barriosnahuel.vossosunboton`) and `bomp-debug` for debug (`com.github.barriosnahuel.vossosunboton.debug`). The Google Services Gradle plugin auto-resolves per-variant JSON by `package_name`.
- Release signing requires `nahuelbarrios.keystore-appbundle.pkcs12` and `secure.properties` (with `key.alias`, `key.password`, `store.password`) in the project root — not committed. Debug builds use the included debug keystore and work without these.
- Bundled audio files (`model/src/debug/res/raw/*.mp3` and `*.ogg`) are not committed; without them debug builds compile and run but the Explore tab is empty.

Setup procedures (fresh clone swap, primary-worktree copy, safe edit of the dummy) live in CONTRIBUTING.md § *Firebase config file*.

## Android resources naming

Every resource name must start with the `resourcePrefix` defined in the module's `build.gradle`:

| Module | Prefix |
|---|---|
| `app` | `app_` |
| `commons_android` | `commons_android_` |
| `commons_file` | `commons_file_` |
| `model` | `model_` |

Logical sub-areas inside `:app` (e.g. Add Button at `feature/addbutton/`) use a secondary prefix on top of `app_` for grouping — `app_addbutton_*`, `app_about_*`. Cluster new resources by feature.

Android Lint enforces this (`ResourceName` check). Violating it fails the build.

## Copyright headers

Every `.kt` source file must start with the AGPLv3 copyright block (enforced by Spotless at CI):

```
/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
```

If `./gradlew check` fails with a Spotless violation, run `./gradlew spotlessApply` to auto-fix.

**Do not remove or hide the About screen.** It's the "Appropriate Legal Notices" mechanism required by AGPLv3 §0 (paired with the headers above). Entry point: TopAppBar overflow menu in `LandingScreen.kt`.

## Bug fixes — TDD workflow

When the user reports a bug or says we are going to fix one, follow TDD:

1. **Write a failing test first** that reproduces the bug. Run it to confirm it fails for the right reason.
2. **Fix the production code** with the minimum change to make it pass.
3. **Run the full test suite** (`./gradlew test`).

Skip TDD only when the bug lives exclusively in UI rendering or platform wiring that can't be exercised by unit / Robolectric tests (e.g. a pure layout glitch). Note why TDD was skipped.

For **production bugs reported by users** (not local-repro), scope frequency, affected versions, OS distribution, and recurring stack frames in Crashlytics + BigQuery first — see `CONTRIBUTING.md` § *BigQuery export*.

## Features — test coverage workflow

Before writing production code for a new feature, identify and agree on the minimum test scenarios:

1. **Happy path** — works as intended under normal conditions.
2. **Failure modes at system boundaries** — audio I/O, MediaPlayer errors, permissions denied, Play feature delivery failures.
3. **Smoke test** — see § Activity smoke tests.

Implement tests **alongside** the feature, not after. Any scenario not listed before starting is out of scope for the current PR — note in the PR description.

Skip a scenario only when it lives exclusively in platform wiring not exercisable by unit / Robolectric tests. Note why.

## Test naming convention

Test names are descriptive sentences, never opaque identifiers. Reports list them verbatim, so they read like a spec.

- **JVM tests** under `src/test/` (Robolectric, pure Kotlin): backtick-quoted strings, sentence-case, no trailing period.
  ```kotlin
  @Test
  fun `searchResults emits empty list when query is blank`() { ... }
  ```
- **Instrumented tests** under `src/androidTest/`: camelCase descriptive names — backticks with spaces require DEX format 040, which the current AGP/D8 won't emit at `minSdk` 23. Migrate to backticks when that boundary moves.
  ```kotlin
  @Test
  fun swipeRightPinsACustomSound() { ... }
  ```

## Test assertions

No bare `kotlin.assert(...)` in test sources — see [ADR 0006](docs/adr/0006-no-kotlin-assert-in-tests.md). Grep-enforced by `scripts/check-adr-invariants.sh` and the CircleCI `test-assertion-guard` job. Use Truth `assertThat(...)`, JUnit `assertEquals`/`assertTrue`/`assertNotNull`, or the Compose UI Test API (`assertCountEquals`, `assertIsDisplayed`). The local check command is in CONTRIBUTING.md § *Testing → Test assertions*.

## Activity smoke tests

Every `Activity` in `app` must have a smoke test (in `app/src/test/`, extending `AbstractRobolectricTest`) that verifies it reaches `Lifecycle.State.RESUMED` without crashing:

```kotlin
ActivityScenario.launch(MyActivity::class.java).use { scenario ->
    assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)
}
```

Mock singleton factories (e.g. `PlayerControllerFactory`) that would crash under Robolectric — canonical template: `LandingActivityTest`. Full-screen Composables with their own business logic (PackageManager calls, raw resource reads, significant state) need a `createComposeRule()` smoke test — canonical template: `AboutScreenTest`.

## Local UI test suite

Instrumented UI/functional tests live under `app/src/androidTest/`. CircleCI intentionally does not run them — see [ADR 0001](docs/adr/0001-local-ui-test-suite.md). Setup, run commands, and report paths live in CONTRIBUTING.md § *Testing → Local UI test suite*.

### When to run

- After any change to a Composable, ViewModel, intent flow, navigation, deep link, or persistence layer.
- Not required for: CHANGELOG, copy strings, README, comments, off-device tooling config.

### Synchronization (avoid bare `waitForIdle()` for state-dependent nodes)

`waitForIdle()` only flushes Compose recompositions — not the `DataStore → StateFlow → render` chain (canonical race in this repo, PR #1111). For nodes whose existence depends on ViewModel/DataStore state, use the `awaitNode*` helpers in `app/src/androidTest/.../ComposeTestExtensions.kt` (rationale in KDoc):

```kotlin
composeRule.awaitNodeWithContentDescription(pinLabel()).performClick()
composeRule.awaitNodeWithText(homeTabLabel()).assertIsDisplayed()
composeRule.awaitNode(hasSetTextAction()).performTextInput(name)
```

Bare `waitForIdle()` / `waitUntil { onAllNodes(...).isNotEmpty() }` is still correct after deterministic actions (`performClick`, `pressBack`); before negative assertions like `assertCountEquals(0)`; before `.onFirst()` chains; and when the matcher would multi-match — the helpers' terminal `onNode*` throws on multi-match.

## Pre-PR checklist

Before opening a PR for any feature or bug fix, verify:

- [ ] Happy path is covered by at least one test
- [ ] Failure modes at system/external boundaries have tests (file I/O, MediaPlayer, permissions, network, Play feature delivery)
- [ ] New `Activity` has a smoke test (§ Activity smoke tests)
- [ ] New full-screen Composable with business logic has a `createComposeRule()` smoke test
- [ ] Composables with durable state (text input, opened overlays/sheets, sub-screens, in-flight error) have at least one `scenario.recreate()` test (§ *Stateful Composables*)
- [ ] Any skipped scenario is explicitly noted with a reason
- [ ] Self code review: re-read every changed file as a reviewer, not author — logic gaps, missing edge cases, unclear naming

Then run the **Pre-push checklist** below.

## Pre-push checklist

CI linters that must pass:
- **KtLint** — style (part of `check`; auto-fix `ktlintFormat`)
- **Detekt** — static analysis (config `config/detekt/detekt-config.yml`; max line length 150)
- **Spotless** — AGPLv3 headers (part of `check`; auto-fix `spotlessApply`)
- **Android Lint** — rules in `config/android/android-lint.xml`

Run `./gradlew check -x test && ./gradlew test` before pushing — catches the same failures CI reports without waiting for a full CI run.

**Functional changes also require the local UI test suite** (§ *Local UI test suite*). If the change touches Composables, ViewModels, intents, navigation, deep links, or persistence — run the instrumented suite on an emulator before pushing. CircleCI does not execute it. Cosmetic-only changes (CHANGELOG, copy strings, README, comments) are exempt.

## Analytics events

Firebase Analytics goes through the `AnalyticsTracker` wrapper at `commons_android/.../analytics/`. Three sibling files are the catalogue: `AnalyticsEvent` (sealed class, one subclass per custom event), `CanonicalScreenName` (every `screen_view` literal), `AnalyticsUserProperty` (user property names + lifetime counter keys).

Hard rules:

- Never call `FirebaseAnalytics.getInstance(...)` or `.logEvent(...)` outside the wrapper — the `analytics-wrapper-guard` CI job fails the build.
- Auto `screen_view` is disabled via manifest meta-data; every screen emits `tracker.logScreen(CanonicalScreenName.X)` manually with a canonical literal.
- The `first_*` variant is emitted by the wrapper when the event declares `hasFirstVariant = true` — call-sites never reference `first_*` directly.
- In tests, substitute via `AnalyticsTrackerProvider.setForTest(FakeAnalyticsTracker())` and assert with `fake.assertEmitted(...)` / `fake.assertScreenView(...)` — never mock `AnalyticsTracker` directly. The fake lives in `:commons_android` test fixtures.

When adding a new track, follow `CONTRIBUTING.md` § "Analytics events 📊" — naming rules, regression-test matrix, and DebugView / `adb logcat -s FA FA-SVC` verification commands. Don't shortcut the manual smoke before merging; aggregated Reports dashboards have a 24–48 h delay.

## Error tracking (non-fatals)

Non-fatal exceptions go to Firebase Crashlytics through the `Tracker` wrapper at `commons_android/.../error/Trackable.kt`. Two methods, very different effects:

| Method | Effect | When to use |
|---|---|---|
| `Tracker.track(throwable)` | `recordException(...)` — full stack trace, **shows up as non-fatal in the dashboard** | Any caught exception you want operations to see |
| `Tracker.log(message)` | `log(...)` — **breadcrumb only**, attached to the next crash/non-fatal recorded after it; invisible in the dashboard until then | Right before a `Tracker.track(...)` to attach context — not as a standalone report |

Hard rules:

- **Do NOT rely on `Timber.e(throwable, message)` to surface a non-fatal.** The `ErrorTrackerTree` (planted in debug and release) forwards only the formatted message via `Tracker.log(...)` — the throwable parameter is silently dropped. `Timber.e` / `Timber.w` are fine for logcat-only diagnostics during dev.
- **Wrapper message MUST be static.** Crashlytics shows the latest event's message as the issue title in the dashboard, so dynamic interpolation (`"... for $name"`) makes titles flicker between events and breaks BigQuery searches by message. Attach per-event context as a Crashlytics breadcrumb via `Tracker.log("module.field=value")` emitted **immediately before** `Tracker.track(...)`. Module = stable feature/surface concept, mirroring the `CanonicalScreenName` literal when one exists (`addbutton` ↔ `ADD_SOUND`, `about` ↔ `ABOUT`, `search` ↔ `SEARCH_SOUND`). Don't use the source directory if it's a layout grouping that may shift over time — `SearchOverlay.kt` lives under `ui/home/` but its module is `search`, not `home`, because the surrounding tab can change without the search feature changing. Multiple breadcrumbs allowed — emit one `Tracker.log` per key.
- **Don't say "button" for a Sound/Bomp in error or log messages.** Use neutral "audio" so the brand doesn't leak into ops/BigQuery. Framework/code identifiers (`addbutton/`, `AddButtonFeature`, Material `Button`) are out of scope.
- For caught exceptions, follow the established pattern (see `PlayerControllerImpl.kt`): wrap the cause in a `RuntimeException` whose **static** message describes the operation, then hand it to `Tracker.track`. The wrapper message becomes the Crashlytics title; the original throwable is preserved as `cause`.
  ```kotlin
  } catch (e: ActivityNotFoundException) {
      Tracker.log("about.url=$url")
      Tracker.track(RuntimeException("Could not launch ACTION_VIEW", e))
      // ...recovery UI (snackbar, fallback) goes here
  }
  ```
- Expected and recoverable exceptions (e.g. user dismissed a chooser) don't need `Tracker.track`. Reserve it for things you want to investigate.
- In tests, `AbstractRobolectricTest` already mocks `Tracker.track` / `Tracker.log` to no-ops in `@Before` — **do not re-mock**. Subclasses just `verify(exactly = 1) { Tracker.track(any()) }` (and `verify(atLeast = 1) { Tracker.log(any()) }` for breadcrumb sites) to lock in the invocation contract. Don't assert exact breadcrumb text (overspecification). Override the global stub only when you need to capture the throwable (`every { Tracker.track(capture(slot)) } answers { nothing }`).

For SQL post-mortem on accumulated crash history, see `CONTRIBUTING.md` § *BigQuery export*. Releases-only — `bomp-prod` exports Crashlytics, Analytics, and Performance to BigQuery (`us` multi-region, daily); `bomp-debug` does not export.

## StrictMode debug audit

Single source of truth: `app/src/debug/.../StrictModeConfigurator.kt` (debug-only, never reaches release). Unknown violations crash debug and instrumented runs by design — `Tracker.track(StrictModeException(violation))` with the wrapper message `"StrictMode: <ViolationClassName>"`. Filter logcat with `adb logcat | grep StrictMode` (operator workflow in `CONTRIBUTING.md` § *Terminal: StrictMode violations*).

When a new violation surfaces, choose in this order:

1. **Top app-code frame is ours** (`com.github.barriosnahuel.vossosunboton.*`): fix the production code. Don't filter.
2. **Scopable to a known-OK call-site we own** (e.g. SDK init that legitimately reads disk on first call): wrap with `StrictMode.allowThreadDiskReads()` + `try/finally` at that call-site. Canonical example: `AnalyticsTrackerProvider.createTracker`. Don't add a matcher.
3. **Third-party class running its own code** (Compose, Espresso, GMS, framework finalizers): add a `KnownThirdPartyViolation` to the list with a comment naming the library + (when public) the upstream issue. Use `methodNameContains` when the class prefix would over-match (the framework's own `android.os.StrictMode` does); use `fileNameContains` when the classes are obfuscated and unstable (GMS Dynamite ships as `m7.*` etc., loader/module identifier lives in `StackTraceElement.fileName`).

## Security boundaries

Concrete rules for input/output validation and component exposure.

### Inbound URI validation

When the app receives a `Uri` via `Intent.EXTRA_STREAM` or `ACTION_SEND` (today only `AddButtonActivity`), validate before opening the stream:

- **Scheme allowlist:** only `content` and `file` pass; reject everything else (`http`, `javascript`, `data`).
- **MIME type:** `ContentResolver.getType(uri)` must start with `audio/`. If null, reject.
- **Size cap:** reject inputs over 50 MB (≈4× a 5-min MP3 at 320 kbps; rejects pathological inputs while leaving headroom). Resolve via `ContentResolver.openAssetFileDescriptor(uri, "r")?.length` or `OpenableColumns.SIZE`. Unknown size also rejects.
- **Failure mode:** surface a typed feedback string-res to the caller (same channel as `app_feedback_generic_error_contact_support`). Never throw raw.

Canonical implementation: `AddButtonFeature.saveNewButtonAsync`. Any future inbound-URI surface must call the same validator.

### Deep link path allowlist

`push-me://open<path>` routes against a closed allowlist in `LandingActivity.handleDeeplink`. Today: `/home` → `MY_SOUNDS`, `/explore` → `EXPLORE_SOUNDS`. **Unknown paths fall back to `MY_SOUNDS`** (the safe default) — never silently route to Explore or any other tab. New destinations require an explicit branch in the `when`; the `else` stays `MY_SOUNDS`.

### Backup hygiene

Before adding any new DataStore preference file or persistent file path that could contain sensitive data (auth tokens, account identifiers, private user content), add explicit `<exclude>` entries to both `app/src/main/res/xml/app_backup_rules.xml` and `app_data_extraction_rules.xml`. Today nothing sensitive is stored — the rules `<include>` the `Music` external directory and the DataStore preference files. When that changes, the exclusion ships in the same commit as the new key.

### Exported components default to false

New `Activity`/`Service`/`Receiver` declarations in `AndroidManifest.xml` default `android:exported="false"`. Set `true` only when the component has an `<intent-filter>` for external callers; document which intents and from where (launcher, share sheet, deep link) in a comment above. The two exported activities today: `LandingActivity` (LAUNCHER + `push-me://open` deep link) and `AddButtonActivity` (system share-sheet `ACTION_SEND` with `audio/*`).

## Accessibility (WCAG 2.2 AA)

All UI development and generated assets (store listing, What's New, changelogs) target **WCAG 2.2 Level AA**. Key requirements:

- **Contrast – text (1.4.3):** ≥ 4.5:1 for normal text; ≥ 3:1 for large text (≥ 18 sp or ≥ 14 sp bold)
- **Contrast – non-text (1.4.11):** ≥ 3:1 for interactive UI components (icon-only buttons, input borders, focus indicators)
- **Color not sole indicator (1.4.1):** never use color alone — pair with an icon, label, or pattern
- **Content descriptions (1.1.1):** every `Icon`/`Image` conveying information needs a non-null `contentDescription`; purely decorative assets use `contentDescription = null`
- **Touch targets (2.5.8):** minimum 24 × 24 dp; prefer 48 × 48 dp for primary actions
- **Labels match names (2.5.3):** visible button/field labels match the accessible name used by screen readers

Verify contrast when adding or changing colors. Use the [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/) or the Material Theme Builder. The brand palette in `AppTheme.kt` was designed to meet AA across all roles. **Critical role pairs are auto-verified by `AppThemeContrastTest`** — if you change the palette and a test fails, fix the theme, not the test.

## Design system

Neo-Club palette (ink × acid). Single source of truth: `app/src/main/java/…/ui/theme/AppTheme.kt` (hex values + role mappings).

Tokens: `Ink1000`, `Ink900`, `Ink800`, `Ink50`, `Paper`, `Acid400`, `AcidDark`, `Blood600`. See `AppTheme.kt` for hex values.

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
- **Components inside always-dark bars** (TopAppBar using `secondary`): use `primaryContainer` (= Acid400 in both modes) for accent elements like cursor, underline, icons — not `primary`, which is AcidDark in light mode and nearly invisible on a dark bar.
- **Adding a new color:** add the constant to `AppTheme.kt`, map it to an M3 role in both `LightColors` and `DarkColors`, then add a contrast assertion for the relevant pair in `AppThemeContrastTest`.

## Product & brand context (when relevant)

Product specs, brand language, and canonical naming live in the sibling backlog repo at `../push-me-backlog/`. Consult when working on user-facing strings, micro-copy, feature/level naming, gamification, or social-layer behavior:

- [`docs/brand-dna.md`](../push-me-backlog/docs/brand-dna.md) — canonical terminology (Bomp, Bomper, Bompear, Escala Richter levels: Bompín / Bompazo / Bompardo / Bompión, Inmortal as cloud-state descriptor)
- [`CLAUDE.md`](../push-me-backlog/CLAUDE.md) — Product Language glossary and spec conventions
- [`backlog/`](../push-me-backlog/backlog/) — pending feature specs (the "why")

Skip for refactors, dep bumps, build config, platform-wiring fixes. Sibling path absent → proceed with in-repo strings as authoritative and surface the gap.

## Repo writing language

Contributor-facing files (`README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `CODE_OF_CONDUCT.md`, ADRs, `.github/` templates, `CLAUDE.md`, code comments, commit messages, PR descriptions, handoff notes) are written in **English**. Embedded examples of user-facing copy (❌/✓ snippets, `strings.xml` quotes) stay in their target locale to illustrate the rule faithfully. User-facing surfaces (in-app strings, store listings, push notifs, "What's New", marketing emails) follow § *Copy & localization*.

## Copy & localization

User-facing copy in any locale (in-app strings, store listings, push notifs, changelogs, emails) must read **native to the target locale**, not as a literal translation — and must not contradict brand DNA or published legal policies.

Sources of truth: `../push-me-backlog/docs/brand-dna.md` (canonical terms, anti-positioning bans), `../push-me-backlog/CLAUDE.md` (Product Language glossary + reserved terms), `../push-me-ghpages/privacy-policy.html`, `../push-me-ghpages/data-safety.html`. Missing path → flag the gap and proceed with in-repo strings as authoritative.

**Default locale.** `app/src/main/res/values/strings.xml` is the **English** master; Spanish-AR copy lives in `values-es/strings.xml`.

Hard rules:

- **No calque translations; locale-aware register.** Phrases natural in the source can be wrong in the target. Match the register the locale expects: US English marketing → contractions, short sentences, imperatives, concrete nouns; Spanish-AR → voseo and warmth. Verify with a native speaker or a current idiom reference, not Google Translate. Concrete calque examples and the en-US listing post-mortem live in `CONTRIBUTING.md` § *Copy guide*.
- **ASO awareness for store-listing copy.** Integrate high-volume queries the target market actually searches — organically, without breaching brand-DNA bans (`soundboard`, `audio sticker`, `panel`, `viralizá`, `share with friends/followers` as a CTA). Those are positioning bans, not vocabulary bans — a category descriptor like `voice notes` is fine because it names the input, not the brand position.
- **Brand-DNA invariants.** Proper nouns Bomp / Bomper / Bompear / Bompeable NEVER translate. The manifesto closing ("Un audio de los tuyos no es un mensaje, es un abrazo que se escucha." or a locale-equivalent that preserves meaning) is invariant across surfaces and locales.
- **Reserved-term check.** Before using any term from `brand-dna.md` or the Product Language glossary, verify it isn't reserved for a non-shipped feature or specific technical state. Reserved today: `Inmortal`/`immortal` (state descriptor for a Bompión synced via Saved Games SDK — Pro, **not shipped yet**); `Bompardo` and `Bompión` (Escala Richter levels 4 and 5, gated by share milestones — don't apply to a generic Bomp); `Bomptástico` (internal telemetry only, never in UI). ❌ "Tus Bomps son inmortales" overstates Auto Backup. ✓ "Tus Bomps quedan respaldados en tu Google Drive".
- **Policy-contradiction check (no overclaims).** Before any absolute claim (`imborrable`, `permanent`, `forever`, `never lost`, `100% private`, `always`, `nunca se pierde`, `we never see your data`) open `privacy-policy.html` and `data-safety.html` and check the claim doesn't contradict a published statement or strip a user right. Invariants today: user can delete Bomps one-by-one or by uninstall (ARCO §05); Auto Backup is OS-controllable (PP §02); Firebase collects pseudonymous crash logs, performance, aggregated interactions (DS §01) — copy can't claim "no data ever leaves the device".
- **Read-aloud check before ship.** Read every paragraph aloud as a native speaker. Stumbles, false friends, weird tense, "wait, what?" reactions are blockers — fix before submitting.
- **Cross-surface consistency.** If headers use `Save. Name. Bomp.`, body copy uses the same verbs ("give it a name", not "give it a label"). Cross-reference all surfaces of a locale (title, short, full, screenshots, feature graphic, video script) before shipping.

## Store listing asset generation

Store listing PNGs (icon, feature graphic) render from SVG masters under `store-listing/`. Canonical pipeline: `rsvg-convert` (`brew install librsvg`); brand font Inter must be installed system-wide. Tooling tradeoffs, install command, exact export commands, and screenshot capture all live in `CONTRIBUTING.md` § *Store listing*. For copy in screenshots / feature graphic taglines, see § *Copy & localization*.

## Labels and milestone

Apply exactly **one type label** (`a:*` or `an:*`) plus **zero or more concern labels** (`c:*`) to every PR before merging. Do not call `gh label list` — use these tables.

For the milestone, read the `## [unreleased]` line in `CHANGELOG.md` — the version in parentheses is the milestone name (e.g. `(v2.0.0)` → milestone `v2.0.0`). Do not call `gh api repos/.../milestones`.

### Type — user-facing (appear in `### Added/Changed/Fixed/Removed` of the CHANGELOG)

| Label | When to use |
|---|---|
| `a:feature` | PR that adds new user-facing functionality |
| `a:fix` | PR that corrects a user-visible bug |
| `an:enhancement` | PR that improves existing user-facing functionality (UX polish, copy refinement, behavior tweaks) — strictly user-facing, not a catch-all |

### Type — internal (appear under `### For nerds 🤓` of the CHANGELOG, or omitted)

| Label | When to use |
|---|---|
| `a:refactor` | PR that restructures code without changing observable behavior |
| `a:test` | PR for test infrastructure, flaky test fixes, new test suites or helpers |
| `a:build` | PR that touches CI, Gradle, linters, repo tooling, or dependency bumps |
| `a:docs` | PR for contributor-facing docs: CLAUDE.md, ADRs, README, CONTRIBUTING, `.github/` templates |

### Concern — cross-cutting (orthogonal, stackable, also apply to issues)

| Label | When to use |
|---|---|
| `c:accessibility` | Digital accessibility (WCAG): contrast, content descriptions, touch targets, screen reader |
| `c:performance` | User-perceivable performance (cold start, scroll, app size, frame budget) |
| `c:security` | Security boundaries: input validation, exported components, backup hygiene |
| `c:i18n` | Localization: translations, store listings per locale, locale-aware copy |
| `c:observability` | Analytics instrumentation, Crashlytics, logging, BigQuery |
| `c:dependencies` | Library, plugin, or Gradle wrapper version bumps (applied automatically by Dependabot together with `a:build`) |

### Issues and lifecycle

| Label | When to use |
|---|---|
| `a:bug` | Issue reporting something broken |
| `a:feature-request` | Issue requesting functionality not yet implemented |
| `stale` | Issue or PR with no recent activity — candidate for closing |

### Examples of valid combinations

- WCAG contrast fix: `a:fix` + `c:accessibility`
- New screen with localized copy: `a:feature` + `c:i18n`
- URI validation hardening: `a:fix` + `c:security`
- AAB size optimization: `an:enhancement` + `c:performance`
- Dependabot bump: `a:build` + `c:dependencies` (auto-applied)
- New Firebase Analytics event: `a:build` + `c:observability`
- Flaky test stabilization: `a:test`
- README rewrite: `a:docs`

## Third-party notices

`app/src/main/res/raw/app_third_party_notices.txt` lists all runtime dependencies with their license attribution. **Update this file whenever you add or remove a runtime dependency** (`implementation`, not `testImplementation` or `debugImplementation`). Entry format is in `CONTRIBUTING.md` § *Third-party notices*.

## Changelog

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format:
- Sections: `Added`, `Changed`, `Fixed`, `Removed` under `## [unreleased]`
- Each entry is a single sentence starting with a capital letter, no trailing period
- For dependency bumps, write one line summarising the overall bump (e.g. "Bumped all dependencies to latest stable"), not one line per library
- As part of each commit, if the change is user-visible or architecturally significant, update `## [unreleased]` before committing
- Never add a `Fixed` entry for a bug introduced in the same `[unreleased]` cycle. If end-users never experienced the regression, no changelog entry — git history provides the traceability
- **User-facing first, technical under "For nerds":** within `## [unreleased]`, list user-facing changes (visible UI, labels, copy, behavior, permissions, perf the user can feel) under `### Added/Changed/Fixed/Removed`, then technical/contributor-only changes (build/CI, dep bumps, refactors, test infra, docs, analytics instrumentation) under a `### For nerds 🤓` subsection with `#### Added/Changed/Fixed/Removed` sub-headings (omit any that would be empty). Applies only to `[unreleased]` going forward; released versions stay as written.

## Handoff notes & issue tracking

GitHub Issues are open for external feature requests and bug reports. Out-of-scope work identified during development is noted in the PR description, not opened as a tracking issue.

`handoff/` contains session handoff documents with decisions, key file paths, and pending work. Ignored by git. Only read these when the user explicitly references them to continue a previous topic.
