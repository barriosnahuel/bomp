# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What goes in this file

This file is loaded into every agent's context window. Hard limit: **40K chars** — Claude Code performance degrades above this (enforced by `scripts/check-adr-invariants.sh`). Target ≤ 35K to leave headroom for the next invariant.

Before adding content here, ask: **does the agent need this while typing the next line of code?**

- **Yes** — invariants, hard nos, project-specific overrides on platform docs → here.
- **No, it's a procedure or run command** → `CONTRIBUTING.md`.
- **No, it's a decision + rationale + revisit criteria** → `docs/adr/*.md`.

Small canonical snippets (≤ 5 lines) may duplicate across CLAUDE.md and CONTRIBUTING.md when the agent needs them at write-time *and* the human at reference-time. Bash blocks, setup procedures, operator workflows should not — link to CONTRIBUTING.md.

Run `/claude-md-audit` when this file approaches 40K or feels bloated.

## Commands

```bash
./gradlew test                 # Unit tests
./gradlew check                # Tests + linting
./gradlew check -x test        # Linting only (detekt + ktlint + spotless + Android Lint)
./gradlew ktlintFormat         # Auto-fix Kotlin style
./gradlew detekt               # Static analysis
./gradlew spotlessApply        # Auto-fix AGPLv3 copyright headers
./gradlew app:build -x check   # Build, skip checks
./gradlew :model:test --tests "com.github.barriosnahuel.vossosunboton.model.SomeTest"   # Single test class
```

Release-only commands (`app:lintVitalRelease`, `app:bundle`) in CONTRIBUTING.md § *Release builds*; emulator workflows in § *Testing → Local UI test suite*. The Android CLI tools — `adb`, `fastboot`, `emulator` — are on `PATH`.

## Module Architecture

Push Me is an Android soundboard app with 4 Gradle modules:

- **`app`** — Activities, Fragments, RecyclerView adapters, feature layer (playback, share, permissions, add-button). Entry: `LandingActivity`. Add Button flow: `feature/addbutton/`.
- **`model`** — Business logic: `Sound` data model, data managers, persistence. No Android UI dependencies.
- **`commons_android`** — Foundation library: Firebase init, Timber logging setup, annotations.
- **`commons_file`** — File handling utilities (audio I/O).

Dependency direction: `app` → `model`, `commons_android`, `commons_file`. No dynamic features today.

## Sources of truth for Android / Kotlin / Compose decisions

For non-trivial decisions, consult the authoritative source first (WebFetch or linked skill) and cite it. Don't answer from training memory in version-sensitive areas — the platform moves. Unreachable source → say so, mark answer as heuristic.

| Area | Authoritative source |
|---|---|
| Jetpack Compose: state, recomposition, side-effects, performance, lifecycle | https://developer.android.com/develop/ui/compose |
| Navigation in Compose | Linked skill `navigation-3` |
| XML view → Compose migration | Linked skill `migrate-xml-views-to-jetpack-compose` |
| Edge-to-edge / system bars / insets | Linked skill `edge-to-edge` |
| Coroutines, Flow, StateFlow, dispatchers, `runTest` | https://kotlinlang.org/docs/coroutines-guide.html + https://developer.android.com/kotlin/coroutines |
| Lifecycle: `repeatOnLifecycle`, `collectAsStateWithLifecycle` | https://developer.android.com/topic/libraries/architecture/lifecycle |
| App architecture (UI/domain/data, UDF, ViewModel + UI state, UI events) | https://developer.android.com/topic/architecture + .../ui-layer/events |
| DataStore (Preferences/Proto), migration from SharedPreferences | https://developer.android.com/topic/libraries/architecture/datastore |
| Background work, WorkManager, foreground services, exact alarms | https://developer.android.com/develop/background-work |
| Permissions / runtime permissions / scoped storage | https://developer.android.com/training/permissions |
| Accessibility in Compose (semantics, click labels, custom actions, large text) | https://developer.android.com/develop/ui/compose/accessibility — pairs with WCAG 2.2 AA (§ Accessibility) |
| AGP 9 migration | Linked skill `agp-9-upgrade` |
| R8 keep rules audit | Linked skill `r8-analyzer` |
| Play Billing | Linked skill `play-billing-library-version-upgrade` |
| Kotlin idioms, conventions, KEEP proposals | https://kotlinlang.org/docs/coding-conventions.html + KEEP at https://github.com/Kotlin/KEEP |
| Project-specific architectural decisions | `docs/adr/*.md` — read the relevant ADR before changing that area |

## Project-specific overrides (read before changing platform-touching code)

Decisions diverging from or narrowing the generic Android recommendation. The override wins over public docs *for this codebase*. If you think an override is wrong, raise it before changing — don't silently flip.

- **DI: manual factories, no Hilt.** ViewModels constructed via `viewModelFactory { initializer { ... } }` (see `SoundsViewModel.kt`). Don't introduce Hilt, Koin, or any DI framework without superseding [ADR 0002](docs/adr/0002-no-hilt-manual-viewmodel-factory.md).
- **One-shot UI events: `Channel<T>` + `receiveAsFlow()`.** Pattern in `SoundsViewModel._scrollToTopEvent`. Reuse for new events; don't add `SharedFlow` "for events". See [ADR 0003](docs/adr/0003-channel-for-one-shot-ui-events.md) for trade-offs and revisit criteria.
- **Async / state model:** `viewModelScope` + `StateFlow` for screen state. `Channel` (ADR 0003) for one-shot events.
- **Threading:** dispatchers *constructor-injected* into ViewModels (default `Dispatchers.IO`). No `runBlocking` in production (except analytics-cache-prime, [ADR 0004](docs/adr/0004-datastore-sync-api-cache-prime.md)). No raw `Thread { ... }` / `AsyncTask`. No I/O on the main thread.
- **Persistence:** see § *Persistence* — DataStore Preferences only; `SharedPreferences` forbidden; sync-API pattern is in-memory cache + async write-back.
- **Networking, image loading, Room:** none in the dependency graph today. New deps for any of these need an ADR before the feature PR.
- **Analytics, error tracking, StrictMode, security, design system, accessibility:** see the dedicated sections below — those rules are stricter than any generic Android guidance and override it.

**Each substantive override is backed by an ADR.** Rationale + revisit criteria in `docs/adr/*.md`; grep invariants enforced by `scripts/check-adr-invariants.sh` (CI job `adr-invariants`) — failures name the ADR. Re-read this section and § *Sources of truth* on dependency bumps or upgrade-skill runs.

### Known migration debt

Things the current codebase does that are *not* the recommended pattern. New code uses the recommended form; existing call-sites migrate when touched.

- **`collectAsState()` → `collectAsStateWithLifecycle()`.** The lifecycle-aware variant stops collection in `STOPPED`, avoiding wasted work and stale `StateFlow` refs. New Composables: `collectAsStateWithLifecycle()`. Existing call-sites migrate when the file is touched.
- **String resources: every user-facing string via `stringResource(R.string.app_*)`.** Plain best practice (i18n + a11y + maintainability). No hardcoded literals in Composables. `contentDescription` for non-decorative `Icon`/`Image` is mandatory and from a string resource (see § *Accessibility*); decorative assets use `contentDescription = null` explicitly.

## Stateful Composables — `rememberSaveable` for durable state

`remember { mutableStateOf(...) }` is for transient gesture state. Durable progress (typed text, opened overlay/sheet/sub-screen, in-flight error) uses `rememberSaveable` so an Activity recreate (rotation, theme, system kill) doesn't silently rewind the user. Non-autosaveable types need an explicit `Saver` next to the Composable; canonical: `SaveOutcomeSaver` in `AddButtonScreen.kt`.

`LaunchedEffect` calling `FocusRequester.requestFocus()` on first composition: wait for the first frame, then guard: `withFrameNanos { } ; runCatching { requestFocus() }.onFailure { Tracker.log(...) ; Tracker.track(RuntimeException("static title", it)) }`. Bare call no-ops silently if the node isn't yet attached (user loses the IME on the primary input). Reference: `AddButtonScreen.kt`, `SearchOverlay.kt`.

## Persistence

Use **Jetpack DataStore Preferences** for new persistent key-value storage. Pattern: `model/.../SoundsRepository.kt` (top-level `Context.bompsStore` via `preferencesDataStore(...)` + `ReplaceFileCorruptionHandler`). Reference impls: `WelcomeStickerStore`, `DataStoreFirstFlagStore`, `DataStoreCounterStore`.

`SharedPreferences` is **forbidden** — grep `getSharedPreferences|EncryptedSharedPreferences` must return zero hits in `src/main` across all modules.

For synchronous reads on top of DataStore (e.g. analytics before nav to a chooser), use the in-memory cache + async write-back pattern from `DataStoreFirstFlagStore.kt` / `DataStoreCounterStore.kt`. Only documented exception to the no-`runBlocking`-in-production rule — see [ADR 0004](docs/adr/0004-datastore-sync-api-cache-prime.md). Test fixtures (`clearForTest()` on every store) documented in CONTRIBUTING.md § *Testing → Test fixtures*.

## Worktree setup

Invariants:

- Scrubbed dummy `google-services.json` at `app/src/{debug,release}/` (real `project_id`/`project_number`/`package_name`, fake `mobilesdk_app_id`/`api_key`) is committed so CI compiles. Real values live only in the working tree via `git update-index --skip-worktree` — **never** unmark + `git add` without first stashing the real file.
- Two Firebase projects: `bomp-prod` (release, `com.github.barriosnahuel.vossosunboton`) + `bomp-debug` (debug, `...vossosunboton.debug`). Google Services plugin auto-resolves per-variant JSON by `package_name`.
- Release signing requires `nahuelbarrios.keystore-appbundle.pkcs12` + `secure.properties` (`key.alias`, `key.password`, `store.password`) at project root — not committed. Debug uses the included debug keystore.
- Bundled debug audio (`model/src/debug/res/raw/*.{mp3,ogg}`) not committed — without them Explore tab is empty.

Setup procedures (fresh-clone swap, primary-worktree copy, safe edit sequence) in CONTRIBUTING.md § *Firebase config file*.

## Android resources naming

Every resource name must start with the `resourcePrefix` from the module's `build.gradle`:

| Module | Prefix |
|---|---|
| `app` | `app_` |
| `commons_android` | `commons_android_` |
| `commons_file` | `commons_file_` |
| `model` | `model_` |

Sub-areas inside `:app` (e.g. `feature/addbutton/`) use a secondary prefix: `app_addbutton_*`, `app_about_*`. Cluster by feature. Android Lint enforces this (`ResourceName`) — violations fail the build.

## Copyright headers

Every `.kt` source file must start with the AGPLv3 copyright block (enforced by Spotless; `./gradlew spotlessApply` to auto-fix):

```
/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
```

**Do not remove or hide the About screen.** It's the "Appropriate Legal Notices" mechanism required by AGPLv3 §0 (paired with the headers above). Entry: TopAppBar overflow menu in `LandingScreen.kt`.

## Bug fixes — TDD workflow

Bug fixes follow TDD: failing test reproducing the bug → minimum production fix → `./gradlew test`. Skip only when the bug is exclusively in UI rendering or platform wiring not exercisable by unit / Robolectric tests; note why in the PR. For production bugs (not local-repro), scope frequency, versions, OS, recurring stack frames in Crashlytics + BigQuery first. Full procedure + BigQuery patterns: CONTRIBUTING.md § *Testing → Bug fixes — TDD procedure* + § *BigQuery export*.

## Features — test coverage workflow

Before writing production code, agree on minimum scenarios: happy path, failure modes at boundaries (audio I/O, MediaPlayer errors, permissions denied, Play feature delivery), smoke test (§ *Activity smoke tests*). Implement tests **alongside** the feature, not after — anything not listed up-front is out of scope; note in the PR. Skip a scenario only when it's exclusively platform wiring not exercisable by tests; note why. Full procedure: CONTRIBUTING.md § *Testing → Features — test coverage procedure*.

## Test naming convention

Test names are descriptive sentences, never opaque identifiers — reports list them verbatim.

- **JVM tests** (`src/test/`): backtick-quoted strings, sentence-case, no trailing period.
  ```kotlin
  @Test fun `searchResults emits empty list when query is blank`() { ... }
  ```
- **Instrumented tests** (`src/androidTest/`): camelCase. Backticks need DEX 040, not emitted by AGP/D8 at `minSdk` 23 — migrate to backticks when that boundary moves.
  ```kotlin
  @Test fun swipeRightPinsACustomSound() { ... }
  ```

## Test assertions

No bare `kotlin.assert(...)` in test sources — see [ADR 0006](docs/adr/0006-no-kotlin-assert-in-tests.md). Grep-enforced by `scripts/check-adr-invariants.sh` + CircleCI `test-assertion-guard`. Use Truth `assertThat(...)`, JUnit `assertEquals`/`assertTrue`/`assertNotNull`, or Compose UI Test API (`assertCountEquals`, `assertIsDisplayed`). Local check command: CONTRIBUTING.md § *Testing → Test assertions*.

## Activity smoke tests

Every `Activity` in `app` must have a smoke test in `app/src/test/` (extending `AbstractRobolectricTest`) that reaches `Lifecycle.State.RESUMED` without crashing:

```kotlin
ActivityScenario.launch(MyActivity::class.java).use { scenario ->
    assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)
}
```

Mock singleton factories (e.g. `PlayerControllerFactory`) that crash under Robolectric — canonical: `LandingActivityTest`. Full-screen Composables with business logic (PackageManager calls, raw resources, significant state) need a `createComposeRule()` smoke test — canonical: `AboutScreenTest`.

## Local UI test suite

Instrumented UI/functional tests live under `app/src/androidTest/`. CircleCI intentionally does not run them — see [ADR 0001](docs/adr/0001-local-ui-test-suite.md). When to run, setup, run commands, report paths: CONTRIBUTING.md § *Testing → Local UI test suite*.

### Synchronization (avoid bare `waitForIdle()` for state-dependent nodes)

`waitForIdle()` only flushes Compose recompositions — not the `DataStore → StateFlow → render` chain (canonical race, PR #1111). For nodes whose existence depends on ViewModel/DataStore state, use `awaitNode*` helpers in `app/src/androidTest/.../ComposeTestExtensions.kt` (rationale in KDoc):

```kotlin
composeRule.awaitNodeWithContentDescription(pinLabel()).performClick()
composeRule.awaitNodeWithText(homeTabLabel()).assertIsDisplayed()
composeRule.awaitNode(hasSetTextAction()).performTextInput(name)
```

Bare `waitForIdle()` / `waitUntil { onAllNodes(...).isNotEmpty() }` is still correct after deterministic actions (`performClick`, `pressBack`), before negative assertions (`assertCountEquals(0)`), before `.onFirst()` chains, and when the matcher would multi-match (the helpers' terminal `onNode*` throws on multi-match).

## Pre-PR and pre-push checklists

Full checklists: CONTRIBUTING.md § *Testing → Pre-PR checklist* / *Pre-push checklist*. Key write-time invariants here:

- Smoke tests required: new `Activity` (§ *Activity smoke tests*); new full-screen Composable with business logic (`createComposeRule()`); Composables with durable state need at least one `scenario.recreate()` test (§ *Stateful Composables*).
- Run `./gradlew check -x test && ./gradlew test` before pushing — same failures CI reports. Detekt max line length: **150**.
- **Functional changes also require the local UI test suite** (§ *Local UI test suite*). Touching Composables, ViewModels, intents, navigation, deep links, or persistence → run the instrumented suite on an emulator. CircleCI does not execute it. Cosmetic-only changes (CHANGELOG, copy, README, comments) are exempt.

## Analytics events

Firebase Analytics goes through the `AnalyticsTracker` wrapper at `commons_android/.../analytics/`. Three sibling files are the catalogue: `AnalyticsEvent` (sealed class, one subclass per custom event), `CanonicalScreenName` (every `screen_view` literal), `AnalyticsUserProperty` (user property names + lifetime counter keys).

Hard rules:

- Never call `FirebaseAnalytics.getInstance(...)` or `.logEvent(...)` outside the wrapper — the `analytics-wrapper-guard` CI job fails the build.
- Auto `screen_view` is disabled via manifest meta-data; every screen emits `tracker.logScreen(CanonicalScreenName.X)` manually with a canonical literal.
- The `first_*` variant is emitted by the wrapper when the event declares `hasFirstVariant = true` — call-sites never reference `first_*` directly.
- In tests: substitute with `AnalyticsTrackerProvider.setForTest(FakeAnalyticsTracker())`, assert with `fake.assertEmitted(...)` / `fake.assertScreenView(...)`. Never mock `AnalyticsTracker` directly. Fake lives in `:commons_android` test fixtures.

Naming rules, regression-test matrix, and DebugView / `adb logcat -s FA FA-SVC` verification in CONTRIBUTING.md § *Analytics events 📊*. Don't skip the manual smoke before merging — aggregated Reports have a 24–48 h delay.

## Error tracking (non-fatals)

Non-fatal exceptions go to Firebase Crashlytics through the `Tracker` wrapper at `commons_android/.../error/Trackable.kt`. Two methods, very different effects:

| Method | Effect | When to use |
|---|---|---|
| `Tracker.track(throwable)` | `recordException(...)` — full stack trace; **non-fatal in dashboard** | Any caught exception you want ops to see |
| `Tracker.log(message)` | `log(...)` — **breadcrumb only**, attached to the next crash/non-fatal; invisible until then | Right before `Tracker.track(...)` to attach context — not standalone |

Hard rules:

- **`Timber.e(throwable, msg)` does NOT surface a non-fatal.** `ErrorTrackerTree` forwards only the formatted message via `Tracker.log(...)`; the throwable is silently dropped. `Timber.e` / `Timber.w` are fine for logcat-only dev diagnostics.
- **Wrapper message MUST be static.** Dynamic interpolation makes Crashlytics titles flicker between events and breaks BigQuery searches by message. Attach per-event context as a breadcrumb via `Tracker.log("module.field=value")` **immediately before** `Tracker.track(...)`. Module = stable feature/surface concept matching `CanonicalScreenName` when one exists (`addbutton` ↔ `ADD_SOUND`, `about` ↔ `ABOUT`, `search` ↔ `SEARCH_SOUND`) — not a source directory if it's a shifting layout grouping (`SearchOverlay.kt` lives under `ui/home/` but its module is `search`). Multiple breadcrumbs allowed, one `Tracker.log` per key.
- **Don't say "button" for a Sound/Bomp in error or log messages.** Use neutral "audio" so the brand doesn't leak into ops/BigQuery. Framework/code identifiers (`addbutton/`, `AddButtonFeature`, Material `Button`) are out of scope.
- For caught exceptions, follow the established pattern (see `PlayerControllerImpl.kt`): wrap the cause in a `RuntimeException` with a **static** message describing the operation, then `Tracker.track(...)`. The wrapper message becomes the Crashlytics title; original throwable preserved as `cause`.
  ```kotlin
  } catch (e: ActivityNotFoundException) {
      Tracker.log("about.url=$url")
      Tracker.track(RuntimeException("Could not launch ACTION_VIEW", e))
      // ...recovery UI (snackbar, fallback) goes here
  }
  ```
- Expected and recoverable exceptions (e.g. user dismissed a chooser) don't need `Tracker.track`. Reserve it for things you want to investigate.
- In tests, `AbstractRobolectricTest` mocks `Tracker.track`/`Tracker.log` to no-ops in `@Before` — **don't re-mock**. Subclasses just `verify(exactly = 1) { Tracker.track(any()) }` (and `verify(atLeast = 1) { Tracker.log(any()) }` for breadcrumb sites). Don't assert exact breadcrumb text (overspecification). Override the global stub only to capture the throwable: `every { Tracker.track(capture(slot)) } answers { nothing }`.

SQL post-mortem on crash history: CONTRIBUTING.md § *BigQuery export*. Releases-only — `bomp-prod` exports Crashlytics/Analytics/Performance to BigQuery (`us`, daily). `bomp-debug` does not export.

## StrictMode debug audit

Source of truth: `app/src/debug/.../StrictModeConfigurator.kt` (debug-only, never reaches release). Unknown violations crash debug + instrumented runs by design — `Tracker.track(StrictModeException(violation))` with wrapper `"StrictMode: <ViolationClassName>"`. Filter logcat: `adb logcat | grep StrictMode` (operator workflow: CONTRIBUTING.md § *Terminal: StrictMode violations*).

When a new violation surfaces, choose in this order:

1. **Top app-code frame is ours** (`com.github.barriosnahuel.vossosunboton.*`): fix the production code. Don't filter.
2. **Scopable to a known-OK call-site we own** (e.g. SDK init that legitimately reads disk on first call): wrap with `StrictMode.allowThreadDiskReads()` + `try/finally` at the call-site. Canonical: `AnalyticsTrackerProvider.createTracker`. Don't add a matcher.
3. **Third-party class running its own code** (Compose, Espresso, GMS, framework finalizers): add a `KnownThirdPartyViolation` with a comment naming the library + upstream issue (when public). Use `methodNameContains` when the class prefix would over-match (`android.os.StrictMode` itself does); use `fileNameContains` when classes are obfuscated and unstable (GMS Dynamite ships as `m7.*` etc.).

## Security boundaries

Concrete rules for input/output validation and component exposure.

### Inbound URI validation

Inbound `Uri` via `Intent.EXTRA_STREAM`/`ACTION_SEND` (today only `AddButtonActivity`) — validate before opening the stream:

- **Scheme allowlist:** only `content` and `file` pass; reject everything else (`http`, `javascript`, `data`).
- **MIME type:** `ContentResolver.getType(uri)` must start with `audio/`. If null, reject.
- **Size cap:** reject inputs over 50 MB (≈4× a 5-min MP3 at 320 kbps). Resolve via `ContentResolver.openAssetFileDescriptor(uri, "r")?.length` or `OpenableColumns.SIZE`. Unknown size also rejects.
- **Failure mode:** surface a typed feedback string-res to the caller (same channel as `app_feedback_generic_error_contact_support`). Never throw raw.

Canonical implementation: `AddButtonFeature.saveNewButtonAsync`. Any future inbound-URI surface must call the same validator.

### Deep link path allowlist

`push-me://open<path>` routes against a closed allowlist in `LandingActivity.handleDeeplink`. Today: `/home` → `MY_SOUNDS`, `/explore` → `EXPLORE_SOUNDS`. **Unknown paths fall back to `MY_SOUNDS`** (the safe default) — never silently route to Explore or any other tab. New destinations require an explicit branch in the `when`; the `else` stays `MY_SOUNDS`.

### Backup hygiene

Before adding a DataStore preference file or persistent path with sensitive data (auth tokens, account IDs, private content), add `<exclude>` entries to both `app/src/main/res/xml/app_backup_rules.xml` and `app_data_extraction_rules.xml`. Today nothing sensitive is stored — rules `<include>` the `Music` external directory and the DataStore files. When that changes, exclusion ships in the same commit.

### Exported components default to false

New `Activity`/`Service`/`Receiver` in `AndroidManifest.xml` defaults `android:exported="false"`. Set `true` only with an `<intent-filter>` for external callers; document the intents in a comment above. Today's exported activities: `LandingActivity` (LAUNCHER + `push-me://open` deep link) and `AddButtonActivity` (share sheet `ACTION_SEND` with `audio/*`).

## Accessibility (WCAG 2.2 AA)

All UI and generated assets (store listing, What's New, changelogs) target **WCAG 2.2 Level AA**. Key requirements:

- **Contrast – text (1.4.3):** ≥ 4.5:1 for normal text; ≥ 3:1 for large text (≥ 18 sp or ≥ 14 sp bold)
- **Contrast – non-text (1.4.11):** ≥ 3:1 for interactive UI components (icon-only buttons, input borders, focus indicators)
- **Color not sole indicator (1.4.1):** never use color alone — pair with an icon, label, or pattern
- **Content descriptions (1.1.1):** every `Icon`/`Image` conveying information needs a non-null `contentDescription`; purely decorative assets use `contentDescription = null`
- **Touch targets (2.5.8):** minimum 24 × 24 dp; prefer 48 × 48 dp for primary actions
- **Labels match names (2.5.3):** visible button/field labels match the accessible name used by screen readers

Verify contrast when changing colors ([WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/), Material Theme Builder). The `AppTheme.kt` palette meets AA across all roles. **Critical role pairs auto-verified by `AppThemeContrastTest`** — palette change + test fail means fix the theme, not the test.

## Design system

Neo-Club palette (ink × acid). Source of truth: `AppTheme.kt` (hex values + role mappings). Tokens: `Ink1000`, `Ink900`, `Ink800`, `Ink50`, `Paper`, `Acid400`, `AcidDark`, `Blood600`.

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

Sibling backlog repo `../push-me-backlog/` holds product specs, brand language, canonical naming. Consult for user-facing strings, micro-copy, feature/level naming, gamification, social-layer behavior:

- [`docs/brand-dna.md`](../push-me-backlog/docs/brand-dna.md) — canonical terminology (Bomp/Bomper/Bompear, Richter levels Bompín → Bompión, Inmortal cloud-state descriptor)
- [`CLAUDE.md`](../push-me-backlog/CLAUDE.md) — Product Language glossary and spec conventions
- [`backlog/`](../push-me-backlog/backlog/) — pending feature specs (the "why")

Skip for refactors, dep bumps, build config, platform-wiring fixes. Sibling path absent → in-repo strings are authoritative; surface the gap.

## Repo writing language

Contributor-facing files (README, CONTRIBUTING, CHANGELOG, CODE_OF_CONDUCT, ADRs, `.github/` templates, CLAUDE.md, code comments, commit messages, PR descriptions, handoff notes) are written in **English**. Embedded examples of user-facing copy (❌/✓ snippets, `strings.xml` quotes) stay in their target locale. User-facing surfaces (in-app strings, store listings, push notifs, "What's New", marketing emails) follow § *Copy & localization*.

## Copy & localization

User-facing copy in any locale (in-app strings, store listings, push notifs, changelogs, emails) must read **native to the target locale**, not as a literal translation — and must not contradict brand DNA or published legal policies.

Sources of truth: `../push-me-backlog/docs/brand-dna.md` (canonical terms, anti-positioning bans), `../push-me-backlog/CLAUDE.md` (Product Language glossary + reserved terms), `../push-me-ghpages/privacy-policy.html`, `../push-me-ghpages/data-safety.html`. Missing path → flag the gap and proceed with in-repo strings as authoritative.

**Default locale.** `app/src/main/res/values/strings.xml` is the **English** master; Spanish-AR copy lives in `values-es/strings.xml`.

Hard rules:

- **No calque; locale-aware register.** Phrases natural in source can be wrong in target. US English marketing → contractions, short sentences, imperatives, concrete nouns. Spanish-AR → voseo and warmth. Verify with a native speaker or current idiom reference, not Google Translate. Calque examples + en-US listing post-mortem: CONTRIBUTING.md § *Copy guide*.
- **ASO awareness for store-listing copy.** Integrate high-volume target-market queries organically, without breaching brand-DNA bans (`soundboard`, `audio sticker`, `panel`, `viralizá`, `share with friends/followers` as CTA). Those are positioning bans, not vocabulary bans — `voice notes` is fine because it names the input, not the brand position.
- **Brand-DNA invariants.** Proper nouns Bomp / Bomper / Bompear / Bompeable NEVER translate. The manifesto closing ("Un audio de los tuyos no es un mensaje, es un abrazo que se escucha." or locale-equivalent preserving meaning) is invariant across surfaces and locales.
- **Reserved-term check.** Verify no term from `brand-dna.md` / Product Language glossary is reserved for a non-shipped feature or specific state. Reserved today: `Inmortal`/`immortal` (Saved-Games-synced Bompión — Pro, **not shipped**); `Bompardo` and `Bompión` (Escala Richter levels 4/5, gated by share milestones — not for a generic Bomp); `Bomptástico` (internal telemetry only). ❌ "Tus Bomps son inmortales" overstates Auto Backup. ✓ "Tus Bomps quedan respaldados en tu Google Drive".
- **Policy-contradiction check (no overclaims).** Before any absolute claim (`imborrable`, `permanent`, `forever`, `never lost`, `100% private`, `always`, `nunca se pierde`, `we never see your data`) open `privacy-policy.html` and `data-safety.html` and check the claim doesn't contradict a published statement or strip a user right. Invariants: user can delete Bomps one-by-one or by uninstall (ARCO §05); Auto Backup is OS-controllable (PP §02); Firebase collects pseudonymous crash logs, performance, aggregated interactions (DS §01) — copy can't claim "no data ever leaves the device".
- **Read-aloud check.** Read aloud as a native speaker before ship. Stumbles, false friends, weird tense block submission.
- **Cross-surface consistency.** Headers and body copy use the same verbs ("give it a name", not "give it a label"). Cross-reference title, short, full, screenshots, feature graphic, video script before shipping.

## Store listing asset generation

Store listing PNGs (icon, feature graphic) render from SVG masters under `store-listing/`. Canonical pipeline: `rsvg-convert` (`brew install librsvg`); brand font Inter must be installed system-wide. Tooling tradeoffs, install command, exact export commands, and screenshot capture all live in `CONTRIBUTING.md` § *Store listing*. For copy in screenshots / feature graphic taglines, see § *Copy & localization*.

## Labels and milestone

Apply exactly **one type label** (`a:*` or `an:*`) + **zero or more concern labels** (`c:*`) to every PR before merging. Don't call `gh label list` — use the tables below.

For the milestone, read the `## [unreleased]` line in `CHANGELOG.md` — the version in parentheses is the milestone (e.g. `(v2.0.0)` → milestone `v2.0.0`). Don't call `gh api repos/.../milestones`.

### Type — user-facing (appear under `### Added/Changed/Fixed/Removed` in CHANGELOG)

| Label | When to use |
|---|---|
| `a:feature` | Adds new user-facing functionality |
| `a:fix` | Corrects a user-visible bug |
| `an:enhancement` | Improves existing user-facing functionality (UX polish, copy, behavior) — strictly user-facing, not a catch-all |

### Type — internal (appear under `### For nerds 🤓` or omitted)

| Label | When to use |
|---|---|
| `a:refactor` | Restructures code without changing observable behavior |
| `a:test` | Test infrastructure, flaky-test fixes, new test suites or helpers |
| `a:build` | CI, Gradle, linters, repo tooling, dependency bumps |
| `a:docs` | Contributor-facing docs: CLAUDE.md, ADRs, README, CONTRIBUTING, `.github/` templates |

### Concern — cross-cutting (orthogonal, stackable, also apply to issues)

| Label | When to use |
|---|---|
| `c:accessibility` | WCAG: contrast, content descriptions, touch targets, screen reader |
| `c:performance` | User-perceivable performance (cold start, scroll, app size, frame budget) |
| `c:security` | Security boundaries: input validation, exported components, backup hygiene |
| `c:i18n` | Localization: translations, store listings per locale, locale-aware copy |
| `c:observability` | Analytics instrumentation, Crashlytics, logging, BigQuery |
| `c:dependencies` | Library / plugin / Gradle-wrapper version bumps (auto-applied by Dependabot with `a:build`) |

### Issues and lifecycle

| Label | When to use |
|---|---|
| `a:bug` | Issue reporting something broken |
| `a:feature-request` | Issue requesting functionality not yet implemented |
| `stale` | No recent activity — candidate for closing |

Worked examples of valid combinations: CONTRIBUTING.md § *Labels & milestone examples*.

## Third-party notices

`app/src/main/res/raw/app_third_party_notices.txt` lists all runtime dependencies with their license attribution. **Update this file whenever you add or remove a runtime dependency** (`implementation`, not `testImplementation` or `debugImplementation`). Entry format is in `CONTRIBUTING.md` § *Third-party notices*.

## Changelog

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/):
- Sections `Added` / `Changed` / `Fixed` / `Removed` under `## [unreleased]`. Each entry: single sentence, capital, no trailing period.
- Dependency bumps → one line for the overall bump (`"Bumped all dependencies to latest stable"`), not one per library.
- Update `[unreleased]` per commit when the change is user-visible or architecturally significant.
- Don't add `Fixed` for a bug introduced in the same `[unreleased]` cycle — git history is the traceability.
- **User-facing first; technical under "For nerds":** user-facing → `### Added/Changed/Fixed/Removed`. Technical (build/CI, deps, refactors, test infra, docs, analytics) → `### For nerds 🤓` with `#### Added/Changed/Fixed/Removed` sub-headings (omit empty). `[unreleased]` only; released versions stay as written.

## Handoff notes & issue tracking

GitHub Issues are open for external feature requests and bug reports. Out-of-scope work found during development goes in the PR description, not as a tracking issue.

`handoff/` holds session handoff documents (decisions, key paths, pending work). Gitignored. Only read these when the user references them to continue a previous topic.
