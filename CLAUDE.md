# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What goes in this file

Loaded into every agent's context window. Hard limit **40K chars** — Claude Code degrades above it (CI-enforced by `scripts/check-adr-invariants.sh`); target ≤ 35K for headroom. Before adding content, ask: **does the agent need this while typing the next line of code?**

- **Yes** — invariants, hard nos, project-specific overrides on platform docs → here.
- **No, a procedure or run command** → `CONTRIBUTING.md`.
- **No, a decision + rationale + revisit criteria** → `docs/adr/*.md`.

Small canonical snippets (≤ 5 lines) may live in both files when needed at write-time *and* reference-time; bash blocks, setup procedures, and operator workflows must not — link to CONTRIBUTING.md instead. Run `/claude-md-audit` when this file approaches 40K or feels bloated.

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

Push Me is an Android soundboard app with 4 Gradle modules (per-module detail in CONTRIBUTING.md § *Directory structure*):

- **`app`** — Activities, Fragments, feature layer (playback, share, permissions, add-button). Entry: `LandingActivity`; Add Button flow: `feature/addbutton/`.
- **`model`** — business logic: `Sound`, data managers, persistence. No Android UI dependencies.
- **`commons_android`** — foundation: Firebase init, Timber setup, annotations.
- **`commons_file`** — file handling (audio I/O).

Dependency direction: `app` → `model`, `commons_android`, `commons_file`. No dynamic features today.

## Sources of truth for Android / Kotlin / Compose decisions

For non-trivial decisions, consult the authoritative source first (WebFetch or linked skill) and cite it. Don't answer from training memory in version-sensitive areas — the platform moves. Unreachable source → say so, mark answer as heuristic.

The **area → authoritative source** routing table — Compose; coroutines/Flow/dispatchers; lifecycle; app architecture; DataStore; background work; permissions; accessibility; Kotlin idioms, plus the linked skills `navigation-3`, `migrate-xml-views-to-jetpack-compose`, `edge-to-edge`, `agp-9-upgrade`, `r8-analyzer`, `play-billing-library-version-upgrade` — is in CONTRIBUTING.md § *Sources of truth for platform decisions*. **Project-specific architectural decisions: `docs/adr/*.md` — read the relevant ADR before changing that area.**

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

`LaunchedEffect` calling `FocusRequester.requestFocus()` on first composition must wait for the first frame (`withFrameNanos { }`) then guard the call with `runCatching { … }` (track failures via `Tracker`) — a bare call no-ops silently if the node isn't attached yet and the user loses the IME on the primary input. Canonical incantation: `AddButtonScreen.kt`, `SearchOverlay.kt`.

## Persistence

Use **Jetpack DataStore Preferences** for new persistent key-value storage. Pattern: `model/.../SoundsRepository.kt` (top-level `Context.bompsStore` via `preferencesDataStore(...)` + `ReplaceFileCorruptionHandler`). Reference impls: `WelcomeStickerStore`, `DataStoreFirstFlagStore`, `DataStoreCounterStore`.

`SharedPreferences` is **forbidden** — grep `getSharedPreferences|EncryptedSharedPreferences` must return zero hits in `src/main` across all modules.

For synchronous reads on top of DataStore (e.g. analytics before nav to a chooser), use the in-memory cache + async write-back pattern from `DataStoreFirstFlagStore.kt` / `DataStoreCounterStore.kt`. Only documented exception to the no-`runBlocking`-in-production rule — see [ADR 0004](docs/adr/0004-datastore-sync-api-cache-prime.md). Test fixtures (`clearForTest()` on every store) documented in CONTRIBUTING.md § *Testing → Test fixtures*.

## Worktree setup

Two write-time invariants below; the full procedure (Firebase projects, signing files, bundled audio, fresh-clone swap, safe dummy-edit sequence) is in CONTRIBUTING.md § *Firebase config file* / § *Creating a new worktree*.

- Worktrees live as **siblings of the primary worktree** (`../push-me-<topic>`), never nested under `.claude/` — a nested worktree gets indexed by the IDE as a separate project, cluttering navigation. The committed `WorktreeCreate` hook (`.claude/hooks/create-sibling-worktree.sh`) applies this layout to harness/subagent worktrees automatically. Full lifecycle (layout + merged-worktree cleanup) in [ADR 0014](docs/adr/0014-worktree-lifecycle-sibling-layout-and-cleanup.md).
- Real `google-services.json` lives only in the working tree via `git update-index --skip-worktree` (the committed copies are scrubbed dummies so CI compiles) — **never** unmark + `git add` without first stashing the real file, or you leak real keys.
- The `post-merge` hook that auto-cleans merged worktrees is installed by **copy** into `.git/hooks/` (via `scripts/install-hooks.sh`, re-armed by the committed `SessionStart` hook) — **never** rewire it through `git config core.hooksPath` (it breaks the remote env's commit-signing; see [ADR 0014](docs/adr/0014-worktree-lifecycle-sibling-layout-and-cleanup.md)).

After creating a worktree by hand, run `./scripts/setup-worktree.sh` from its root (idempotent; the `WorktreeCreate` hook invokes it automatically) — it copies the real google-services configs + bundled debug audio and re-arms skip-worktree.

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

Every `.kt` source file must start with the AGPLv3 copyright block (`SPDX-License-Identifier: AGPL-3.0-only`, © 2016-2026 Nahuel Barrios). Enforced by Spotless — `./gradlew spotlessApply` applies the exact header to new files (or copy it from any existing `.kt`).

**Do not remove or hide the About screen.** It's the "Appropriate Legal Notices" mechanism required by AGPLv3 §0 (paired with the headers above). Entry: TopAppBar overflow menu in `LandingScreen.kt`.

## Comments & KDoc

**KDoc = contract.** Document what the code can't say itself: the contract (what/params/returns), invariants & gotchas you'd break unknowingly (thread-safety, main-looper post, ordering, reflection probe), and grep-anchor markers. **Decision rationale — why this design, rejected alternatives, revisit criteria — goes in `docs/adr/*.md`**, referenced by a one-line pointer (e.g. `… : docs/adr/00XX-name.md`). Re-deriving an ADR's rationale in a comment is two sources of truth that drift; the worked trim is [ADR 0016](docs/adr/0016-jankstats-frozen-frame-crash-gate.md) ↔ `FrozenFrameGate.kt`. **"How" comments stay ≤ 2-3 lines** — no truth is truer than the code itself. **No traceability breadcrumbs** in comments (PR/issue/feedback refs) — that lives in git/CHANGELOG/ADR/PR.

Audit case-by-case, never blind-delete: a long block that is *local justification* with no ADR home (a multi-key contract, the `StrictModeConfigurator` matchers) stays. A single comment block over **26 lines** is flagged by `scripts/check-adr-invariants.sh` (an awk line-count over a contiguous comment run); trim it to a pointer, or acknowledge a genuinely-legit long block with a `long-comment-ok` marker **on its own line inside the block** — kin to the `// alpha-ok` / `// button-ok` hatches, but placed in the comment itself rather than trailing a code line (there is no offending code line to trail).

## Bug fixes — TDD workflow

Bug fixes follow TDD: failing test reproducing the bug → minimum production fix → `./gradlew test`. Skip only when the bug is exclusively in UI rendering or platform wiring not exercisable by unit / Robolectric tests; note why in the PR. For production bugs (not local-repro), scope frequency, versions, OS, recurring stack frames in Crashlytics + BigQuery first. Full procedure + BigQuery patterns: CONTRIBUTING.md § *Testing → Bug fixes — TDD procedure* + § *BigQuery export*.

## Features — test coverage workflow

Before code, the plan enumerates three axes — skip it and bugs leak to manual testing. Worked template + the bug each prevents: CONTRIBUTING.md § *Testing → Features — test coverage procedure*.

- **(a) Platform surfaces + gotcha.** List each Android surface touched; read its ADR/skill first (ADRs are read-on-demand — § *Sources of truth*). E.g. `resolveActivity`→manifest `<queries>` (API 30+); `singleTask`/exported→`onNewIntent`; full-screen→`edge-to-edge` skill.
- **(b) State & transition model.** Enumerate every state/transition of a new control — rapid-repeat, new intent over an open screen, playback state mid-transition, what's behind a dismiss gesture. Check each against § *Stateful Composables*. **One test per transition**; agree scenarios up front (+ smoke test, § *Activity smoke tests*), implement **alongside** the feature; unlisted = out of scope.
- **(c) Device-only checks — route to the existing guardrail first.** Palette contrast is covered (`AppThemeContrastTest` + the color/alpha grep, § *Design system*) — don't re-check by hand. Flag only the residue: inset overlap, system-bar legibility, animation/scrub visuals, a fill on a non-bar surface. Make it an instrumented assertion if feasible, else a named manual check.

**Acceptance is concrete** for generated/derived content (e.g. a real waveform, not a synthetic stub). Skip an axis item only when it's pure platform wiring not exercisable by tests; note why.

## Test naming convention

Test names are descriptive sentences, never opaque identifiers — reports list them verbatim.

- **JVM tests** (`src/test/`): backtick-quoted, sentence-case, no trailing period — `` @Test fun `searchResults emits empty list when query is blank`() ``.
- **Instrumented tests** (`src/androidTest/`): camelCase — `@Test fun swipeRightPinsACustomSound()`. Backticks need DEX 040, not emitted by AGP/D8 at `minSdk` 23; migrate when that boundary moves.

## Robolectric SDK config

Robolectric tests run at a **single SDK by default** — the `AbstractRobolectricTest` base classes pin it; subclasses inherit. A multi-SDK matrix (`@Config(sdk = [a, b, …])`) is **only** for a test guarding a real `Build.VERSION.SDK_INT` branch and **must** carry a trailing `// sdk-boundary: <branch>` comment (grep-enforced by `scripts/check-adr-invariants.sh`). Straddle the boundary (one level below, one at/above). Canonical: `HapticsTest` (`[M, R]`), `AddButtonActivitySdkBoundaryTest` (`[M, TIRAMISU]`). See [ADR 0013](docs/adr/0013-single-sdk-default-robolectric.md).

## Test assertions

No bare `kotlin.assert(...)` in test sources — see [ADR 0006](docs/adr/0006-no-kotlin-assert-in-tests.md). Grep-enforced by `scripts/check-adr-invariants.sh` + CircleCI `test-assertion-guard`. Use Truth `assertThat(...)`, JUnit `assertEquals`/`assertTrue`/`assertNotNull`, or Compose UI Test API (`assertCountEquals`, `assertIsDisplayed`). Local check command: CONTRIBUTING.md § *Testing → Test assertions*.

## JVM tests — await every async input, not just the one you triggered

For a value aggregated from **multiple** flows, await **each** upstream before the triggering action — not just the signal the test fired. The other input (typically the reactive `loadSounds` populating `allSoundsCache`) hasn't arrived on a loaded CI machine, so the derived value is computed against empty state and the assertion flakes. Worked example + canonical `SoundsViewModelAnalyticsTest`: CONTRIBUTING.md § *Testing → Awaiting multiple async inputs*.

## Activity smoke tests

Every `Activity` in `app` must have a smoke test in `app/src/test/` (extending `AbstractRobolectricTest`) that reaches `Lifecycle.State.RESUMED` without crashing — mock singleton factories (e.g. `PlayerControllerFactory`) that crash under Robolectric. Full-screen Composables with business logic (PackageManager calls, raw resources, significant state) need a `createComposeRule()` smoke test. The `ActivityScenario.launch(...).use { … }` snippet + canonical `LandingActivityTest` / `AboutScreenTest`: CONTRIBUTING.md § *Testing → Activity smoke tests*.

## Local UI test suite

Instrumented UI/functional tests live under `app/src/androidTest/`. CircleCI intentionally does not run them — see [ADR 0001](docs/adr/0001-local-ui-test-suite.md). When to run, setup, run commands, report paths: CONTRIBUTING.md § *Testing → Local UI test suite*.

**Always run the suite via `./scripts/run-instrumented-tests.sh`** (cold-boots the AVD), never `./gradlew :app:connectedDebugAndroidTest` against a warm emulator — a degraded AVD makes flakes masquerade as `ComposeTimeoutException` / `Process crashed`. If the suite flakes, re-run via the wrapper before suspecting a test or production bug. Rationale: [ADR 0001 § *Cold boot per run*](docs/adr/0001-local-ui-test-suite.md).

### Synchronization (avoid bare `waitForIdle()` for state-dependent nodes)

`waitForIdle()` only flushes Compose recompositions — not the `DataStore → StateFlow → render` chain (canonical race, PR #1111). For nodes whose existence depends on ViewModel/DataStore state, use the `awaitNode*` helpers in `app/src/androidTest/.../ComposeTestExtensions.kt`. Bare `waitForIdle()` / `waitUntil { … }` stays correct after deterministic actions (`performClick`, `pressBack`), before negative assertions, before `.onFirst()` chains, and when a matcher would multi-match. Examples + the full when-which list: CONTRIBUTING.md § *Testing → Local UI test suite → Synchronization*.

## Pre-PR and pre-push checklists

Full checklists: CONTRIBUTING.md § *Testing → Pre-PR checklist* / *Pre-push checklist*. Key write-time invariants here:

- Smoke tests required: new `Activity` (§ *Activity smoke tests*); new full-screen Composable with business logic (`createComposeRule()`); Composables with durable state need at least one `scenario.recreate()` test (§ *Stateful Composables*).
- Run `./gradlew check -x test && ./gradlew test` before pushing — same failures CI reports. Detekt max line length: **150**.
- **`.githooks/pre-push` enforces the cheap CI checks locally** (ADR/security/analytics/assertion grep guards, then `ktlintCheck detekt spotlessCheck`) so style/format/guard failures block the push instead of round-tripping CircleCI. Heavy unit-test + Android-lint are opt-in via `PREPUSH_FULL=1`. Escape hatch for cosmetic-only pushes: `git push --no-verify`. Auto-fix: `ktlintFormat` / `spotlessApply`.
- **Functional changes also require the local UI test suite** (§ *Local UI test suite*). Touching Composables, ViewModels, intents, navigation, deep links, or persistence → run `./scripts/run-instrumented-tests.sh` (cold-boots the emulator; never run the Gradle task against a warm AVD). CircleCI does not execute it. Cosmetic-only changes (CHANGELOG, copy, README, comments) are exempt.
- **Changing the app startup path** (Application/Activity `onCreate`, first-frame Composables, startup-time deps): regenerate the committed Baseline Profile — procedure + when in CONTRIBUTING.md § *Baseline Profile*. Stale = slower first launches, never broken.

## Analytics events

Firebase Analytics goes through the `AnalyticsTracker` wrapper (`commons_android/.../analytics/`); the catalogue is three sibling files — `AnalyticsEvent` (one subclass per custom event), `CanonicalScreenName` (every `screen_view` literal), `AnalyticsUserProperty` (user-property names + lifetime counter keys).

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
- For caught exceptions, follow the established pattern (see `PlayerControllerImpl.kt`): `Tracker.log("module.field=value")` then `Tracker.track(RuntimeException("static operation description", e))` — the static wrapper message becomes the Crashlytics title, the original throwable is preserved as `cause`, recovery UI (snackbar, fallback) follows.
- Expected and recoverable exceptions (e.g. user dismissed a chooser) don't need `Tracker.track`. Reserve it for things you want to investigate.
- In tests, `AbstractRobolectricTest` already mocks `Tracker.track`/`Tracker.log` to no-ops — **don't re-mock**; subclasses just `verify { Tracker.track(any()) }`. Capturing the throwable / breadcrumb-assertion detail: CONTRIBUTING.md § *Error tracking*.

SQL post-mortem on crash history: CONTRIBUTING.md § *BigQuery export*. Releases-only — `bomp-prod` exports Crashlytics/Analytics/Performance to BigQuery (`us`, daily). `bomp-debug` does not export.

**Reading a specific crash's stack:** BQ frames are R8-obfuscated (`r8-map-id-…`, `ki2.q`); read the stack in **Crashlytics** (Console / Firebase MCP), which deobfuscates only when the build's R8 mapping was uploaded — often missing today (§ *Release builds*). BQ for aggregation/counts/JOINs only — detail in CONTRIBUTING.md § *BigQuery export*.

## StrictMode debug audit

Source of truth: `app/src/debug/.../StrictModeConfigurator.kt` (debug-only, never reaches release). Unknown violations crash debug + instrumented runs by design — `Tracker.track(StrictModeException(violation))` with a static wrapper `"StrictMode: <ViolationClassName>"`.

When a new violation surfaces, the fix order is: **(1)** top app-code frame is ours (`com.github.barriosnahuel.vossosunboton.*`) → fix the production code, don't filter; **(2)** scopable to a known-OK call-site we own → wrap with `StrictMode.allowThreadDiskReads()` + `try/finally` at the call-site (canonical: `AnalyticsTrackerProvider.createTracker`), don't add a matcher; **(3)** third-party class running its own code → add a `KnownThirdPartyViolation`. Logcat filter, and the full triage tree with `methodNameContains` vs `fileNameContains` guidance: CONTRIBUTING.md § *Terminal: StrictMode violations*.

## JankStats frozen-frame gate

Debug-only rendering sibling of StrictMode: `app/src/debug/.../FrozenFrameGate.kt` + `JankStatsLogger.kt`. A **repeated frozen frame** (UI-thread duration ≥ 700 ms — a main-thread block, *not* GPU slowness) crashes the process via `Tracker.track(FrozenFrameException)` + a main-looper throw, static message `"JankStats: frozen frame…"`. Unlike StrictMode it is **manual / real-device debug only** — the crash is armed off under instrumentation (the cold-boot emulator emits multi-second frozen frames from its own starvation; a real device produces none). Slow-frame jank stays log-only; its regression gate is the Macrobenchmark, not this. Don't gate on `isJank` (too frequent) or total frame duration (GPU isn't a block). Two tiers: a single **egregious** frame (≥ 1.5 s) crashes at once; the ambiguous 700 ms–1.5 s band needs a 2nd frozen frame within 5 s. Calibration (per-screen startup window, the two tiers, allowlist) + the test-harness-disable rationale: [ADR 0016](docs/adr/0016-jankstats-frozen-frame-crash-gate.md). Triage when it fires (StrictMode-style fix order) + the perf-tool trio: CONTRIBUTING.md § *Performance*.

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

### Security test tagging (OWASP MASVS / CWE)

Tests that exercise a security boundary defined in this section **MUST** cite their OWASP MASVS control and (when applicable) CWE in a one-line KDoc directly above `@Test`. Map to the **primary** boundary the production code enforces (secondary controls comma-separated in the same KDoc, e.g. `MASVS-CODE-4, MASVS-RESILIENCE-2`); boundary verifications only, not implementation-detail tests (ordering, performance).

```kotlin
/** OWASP MASVS-PLATFORM-1 / CWE-441 (Confused Deputy — untrusted IPC URI scheme). */
@Test
fun httpSchemeUriShowsRejectionSnackbar() { ... }
```

The literal marker `OWASP MASVS-` is counted by `scripts/check-security-test-count.sh` (CircleCI `security-test-count-guard`); dropping below `EXPECTED_COUNT` fails the build. Count scope + how to adjust `EXPECTED_COUNT` on a deliberate removal: CONTRIBUTING.md § *Security test tagging*.

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

`AppTheme.kt` (`LightColors` / `DarkColors`) is the source of truth for the per-mode token behind each M3 role. Quick map for picking a role: `primaryContainer` → FAB/buttons/swipe-pin/search accent; `primary` → nav text & active labels; `secondary`/`onSecondary` → always-dark top bars; `surfaceVariant`/`onSurfaceVariant` → cards & bottom-nav; `inverseSurface`/`inversePrimary` → snackbar; `error`/`errorContainer` → swipe-delete & error states. Full role × Light × Dark table: CONTRIBUTING.md § *Resources*.

### Rules for component authors

- **Never add `isSystemInDarkTheme()` / `isDark` in component files.** If the same semantic role needs to look different per mode, the role mapping in `AppTheme.kt` is wrong — fix the theme, not the component.
- **Never hardcode a color literal in a component** (e.g. `Color(0xFF2E7D32)`), and never inline a magic `.copy(alpha = 0.NN)`. Use the closest semantic role from `AppTheme.kt`; for a genuinely-needed translucent overlay, use a named alpha (shared in `ui/theme/Alpha.kt`, or a `private const val` next to the Composable). Both are grep-enforced by `scripts/check-adr-invariants.sh` (escape hatch: a justified trailing `// alpha-ok`).
- **Components inside always-dark bars** (TopAppBar using `secondary`): use `primaryContainer` (= Acid400 in both modes) for accent elements like cursor, underline, icons — not `primary`, which is AcidDark in light mode and nearly invisible on a dark bar.
- **Adding a new color:** add the constant to `AppTheme.kt`, map it to an M3 role in both `LightColors` and `DarkColors`, then add a contrast assertion for the relevant pair in `AppThemeContrastTest`.

### Button typology ([ADR 0010](docs/adr/0010-button-typology.md))

Use the smallest tier that fits the action's hierarchy. **Never** `OutlinedButton`, `ElevatedButton`, or stock-colored `FilledTonalButton`.

| Tier | Composable | Color | When |
|---|---|---|---|
| Filled primary | `Button` / FAB family | `primaryContainer` / `onPrimaryContainer` | The one main action (Save, screen FAB, Unlock the Vault) |
| Text | `TextButton` | `primary` (inherited — don't override) | Secondary: "+ New" in lists, Cancel, CTAs. Pair with an 18 dp leading icon |
| Chip | `FilterChip` / `AssistChip` | chip defaults | Filters; "+ New" only *inside a chip row* (vertical list → Text tier) |

**`secondary`-on-`surface` trap:** `secondary` / `secondaryContainer` = Ink (always-dark bars only). As a control fill/accent on `surface` they collapse to ~1.1–1.3:1 — invisible. Stock `FilledTonalButton` / `OutlinedButton` default to these roles; that's why they're banned. When a text button needs separation from scrolling content, give it a container (`Surface` + `HorizontalDivider` action bar), not a border. Canonical: `SectionCreateButton` (`ManageCollectionsScreen.kt`), `VaultUnlockCta` (`SearchOverlay.kt`).

## Product & brand context (when relevant)

Sibling backlog repo `../push-me-backlog/` holds product specs, brand language, canonical naming. Consult for user-facing strings, micro-copy, feature/level naming, gamification, social-layer behavior:

- [`docs/brand-dna.md`](../push-me-backlog/docs/brand-dna.md) — canonical terminology (Bomp/Bomper/Bompear, Richter levels Bompín → Bompión, Inmortal cloud-state descriptor)
- [`CLAUDE.md`](../push-me-backlog/CLAUDE.md) — Product Language glossary and spec conventions
- [`backlog/`](../push-me-backlog/backlog/) — pending feature specs (the "why")

Skip for refactors, dep bumps, build config, platform-wiring fixes. Sibling path absent → in-repo strings are authoritative; surface the gap.

## Repo writing language

Contributor-facing files (README, CONTRIBUTING, CHANGELOG, CODE_OF_CONDUCT, ADRs, `.github/` templates, CLAUDE.md, code comments, commit messages, PR descriptions, handoff notes) are written in **English**. Embedded examples of user-facing copy (❌/✓ snippets, `strings.xml` quotes) stay in their target locale. User-facing surfaces (in-app strings, store listings, push notifs, "What's New", marketing emails) follow § *Copy & localization*.

## Copy & localization

User-facing copy in any locale (in-app strings, store listings, push notifs, changelogs, emails) must read **native to the target locale**, not a literal translation — and must not contradict brand DNA or published legal policies.

Sources of truth: `../push-me-backlog/docs/brand-dna.md` (canonical terms, anti-positioning bans), `../push-me-backlog/CLAUDE.md` (Product Language glossary + reserved terms), `../push-me-ghpages/privacy-policy.html`, `../push-me-ghpages/data-safety.html`. Missing path → flag the gap, proceed with in-repo strings as authoritative. **Default locale:** `values/strings.xml` is the **English** master; Spanish-AR lives in `values-es/strings.xml`.

Hard rules (calque examples, reserved-term list with ✓/❌, read-aloud detail → CONTRIBUTING.md § *Copy guide*):

- **No calque; locale-aware register.** US-EN marketing → contractions, short imperatives, concrete nouns; es-AR → voseo + warmth. Verify with a native speaker, not Google Translate.
- **ASO without breaching brand-DNA positioning bans** (`soundboard`, `audio sticker`, `panel`, `viralizá`, `share with friends/followers` as CTA). Positioning bans, not vocabulary bans — `voice notes` is fine (names the input, not the position).
- **Brand-DNA invariants.** Proper nouns Bomp / Bomper / Bompear / Bompeable NEVER translate. The manifesto closing ("Un audio de los tuyos no es un mensaje, es un abrazo que se escucha." or a meaning-preserving locale equivalent) is invariant across surfaces and locales.
- **Reserved-term check.** Don't use a term reserved for a non-shipped feature/state — `Inmortal`/`immortal` (Pro, not shipped), `Bompardo` / `Bompión` (Escala Richter levels 4/5), `Bomptástico` (telemetry only).
- **Policy-contradiction check.** Before any absolute claim (`imborrable`, `permanent`, `forever`, `never lost`, `100% private`, `nunca se pierde`, `we never see your data`) check `privacy-policy.html` + `data-safety.html`: the claim can't strip a user right or contradict a published statement — Bomps are deletable (ARCO §05), Auto Backup is OS-controllable (PP §02), Firebase collects pseudonymous data (DS §01).
- **Read aloud as a native speaker before ship; keep verbs consistent across title / short / full / screenshots.**

## Store listing asset generation

Store listing PNGs (icon, feature graphic) render from SVG masters under `store-listing/`. Canonical pipeline: `rsvg-convert` (`brew install librsvg`); brand font Inter must be installed system-wide. Tooling tradeoffs, install command, exact export commands, and screenshot capture all live in `CONTRIBUTING.md` § *Store listing*. For copy in screenshots / feature graphic taglines, see § *Copy & localization*.

## Labels and milestone

Apply exactly **one type label** (`a:*` or `an:*`) + **zero or more concern labels** (`c:*`) to every PR before merging. Don't call `gh label list`. Milestone: assign the current month's `vYYYY.MM.N` **at PR creation**, creating it if missing; a no-release month renames it to the next month. See [ADR 0023](docs/adr/0023-monthly-sequential-release-tags.md).

- **Type — user-facing** (appear in CHANGELOG `### Added/Changed/Fixed/Removed`): `a:feature`, `a:fix`, `an:enhancement`.
- **Type — internal** (under `### For nerds 🤓` or omitted): `a:refactor`, `a:test`, `a:build`, `a:docs`.
- **Concern — cross-cutting** (stackable): `c:accessibility`, `c:performance`, `c:security`, `c:i18n`, `c:aso`, `c:observability`, `c:dependencies`.
- **Issues/lifecycle:** `a:bug`, `a:feature-request`, `stale`.

Full "when to use" per label + worked combinations: CONTRIBUTING.md § *Labels & milestone examples*.

## Third-party notices

`app/src/main/res/raw/app_third_party_notices.txt` lists all runtime dependencies with their license attribution. **Update this file whenever you add or remove a runtime dependency** (`implementation`, not `testImplementation` or `debugImplementation`). Entry format is in `CONTRIBUTING.md` § *Third-party notices*.

## Changelog

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/); released headers are CalVer `## [vYYYY.MM.N]` (CONTRIBUTING § *Versioning*):
- Sections `Added` / `Changed` / `Fixed` / `Removed` under `## [unreleased]`. Each entry: single sentence, capital, no trailing period.
- Dependency bumps → one line for the overall bump (`"Bumped all dependencies to latest stable"`), not one per library.
- Update `[unreleased]` per commit when the change is user-visible or architecturally significant.
- Don't add `Fixed` for a bug introduced in the same `[unreleased]` cycle — git history is the traceability.
- **User-facing first; technical under "For nerds":** user-facing → `### Added/Changed/Fixed/Removed`. Technical (build/CI, deps, refactors, test infra, docs, analytics) → `### For nerds 🤓` with `#### Added/Changed/Fixed/Removed` sub-headings (omit empty). `[unreleased]` only; released versions stay as written.

## Handoff notes & issue tracking

GitHub Issues are open for external feature requests and bug reports. Out-of-scope work found during development goes in the PR description, not as a tracking issue.

`handoff/` holds session handoff documents (decisions, key paths, pending work). Gitignored. Only read these when the user references them to continue a previous topic.
