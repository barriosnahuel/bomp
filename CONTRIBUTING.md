# Welcome! 🙌

*Here are some useful notes when making changes on this project.*

But, before going deeper I suggest you to take a look to the [opensource.guide](https://opensource.guide/), there are many things to learn from there! 😃

## Table of contents 📋
- [Local setup](#local-setup-)
- [Directory structure](#directory-structure-)
- [Debugging tools](#debugging-tools-)
- [Continuous integration](#continuous-integration-)
- [Sources of truth for platform decisions](#sources-of-truth-for-platform-decisions-)
- [Testing](#testing-)
- [Performance](#performance-)
- [Gradle upgrade](#gradle-upgrade)
- [Firebase config file](#firebase-config-file-)
- [Backup & restore testing](#backup--restore-testing-)
- [Logcat](#logcat-)
- [Resources](#resources-)
- [Signing](#signing-)
- [Release builds](#release-builds-)
- [Bundled audio files](#bundled-audio-files-)
- [Store listing](#store-listing-)
- [Copy guide](#copy-guide-)
- [Error tracking](#error-tracking-)
- [Analytics events](#analytics-events-)
- [BigQuery export](#bigquery-export-)
- [Labels & milestone examples](#labels--milestone-examples-)
- [Third-party notices](#third-party-notices-)

## Local setup ⚙

1. Clone/Fork this repo.
2. The repo commits scrubbed dummy `google-services.json` files at `app/src/debug/google-services.json` and `app/src/release/google-services.json` so the build works out of the box. To run the app against the maintainer's Firebase projects (`bomp-debug` for the `.debug` build, `bomp-prod` for release) or your own fork, replace those two files with the real ones downloaded from Firebase Console, then follow [Firebase config file](#firebase-config-file-) to keep your real keys out of git.
3. Run:
    > ./gradlew check

    It must return **`BUILD SUCCESS`**.
4. *(Optional)* To let Claude Code diagnose CI failures, set up the CircleCI MCP server — see [§ Continuous Integration](#continuous-integration-). An agent can wire it up; you provide the token and restart Claude Code.
5. *(Optional)* To let Claude Code read **deobfuscated** Crashlytics stacks, install the Firebase CLI (`npm install -g firebase-tools`) and wire the Firebase MCP — per-user, not committed (`claude mcp add -s local firebase -- firebase experimental:mcp --dir .`), then `firebase login` + restart Claude Code. An agent can wire it up; you provide the login. When to use it (deobfuscated traces) vs BQ (aggregation): [§ BigQuery export](#bigquery-export-).

## Directory structure 🎄
- [app/](/app) Android application module which depends on all other submodules to be the great app you're building. The Add Button flow lives at `app/src/main/java/com/github/barriosnahuel/vossosunboton/feature/addbutton/`.
- [commons_android/](/commons_android) Android library module for Android-related foundation staff.
- [commons_file/](/commons_file) Android library module for File handling staff.
- [config/](/config) contains code analyzers configuration files.
- [gradle/wrapper/](/gradle/wrapper) contains Gradle's binary in order to be able to run this project everywhere.
- [model/](/model) Android library module containing our business logic.
- [store-listing/](/store-listing) contains the Play Store listing — copy per locale, brand mark, fonts, and SVG sources for icon and feature graphic. See the [Store listing](#store-listing-) section for the export workflow.

## Debugging tools 🐛
We use some really useful tools like:
- [LeakCanary](https://square.github.io/leakcanary/)
- [Flipper](https://fbflipper.com/)

Please refer to their docs for setup & guidelines.

### Seeding sample My Sounds (debug only) 🌱
A clean debug install starts with an empty "My Sounds" board, so manually re-adding real audios after every reinstall gets tedious. `scripts/install-debug-seeded.sh` installs the debug build and launches it with a debug-only intent flag that copies a few of the bundled Explore samples into "My Sounds" as if you'd saved them yourself:

```bash
./scripts/install-debug-seeded.sh
```

Seeding is **idempotent** — re-running never duplicates entries. The audio source is the bundled debug audio (`model/src/debug/res/raw/`), so a checkout without those files seeds nothing. Debug-only by construction: the release `CustomBuildTypeApplication` seam is a no-op, so the flag is ignored entirely in release builds. Implementation: `DebugSoundSeeder` (`app/src/debug/`), reached from `LandingActivity` through the `CustomBuildTypeApplication` debug/release source-set swap — the same pattern used by `StrictModeConfigurator`.

## Continuous Integration ➿
We use Circle CI, so if you're gonna change the [config.yml](.circleci/config.yml) file you can check the config using the local CLI.
- https://circleci.com/docs/2.0/local-cli

> circleci config validate

### Diagnosing CI failures from Claude Code
Claude Code can read remote pipeline/job logs via the official [CircleCI MCP server](https://github.com/CircleCI-Public/mcp-server-circleci) (`@circleci/mcp-server-circleci`). Setup is **per-user, not committed**: a personal CircleCI API token in your env (carries full account access — treat as a secret) + `claude mcp add -s local …`. Follow the package README for the current commands, or just ask an agent to wire it up.

**Gotcha:** the token resolves at Claude Code launch. If you get `401`, fully quit and relaunch Claude Code from a shell that has the token exported — a `/mcp` reconnect reuses the old env and won't pick it up.

### Instrumented UI tests are local-only
The instrumented suite under `app/src/androidTest/` is **intentionally not run on CircleCI**. It needs a booted emulator and is meant to replace manual end-to-end QA on the contributor's machine. Setup, run commands, and synchronization helpers live in [Testing → Local UI test suite](#testing-); rationale lives in [ADR 0001](docs/adr/0001-local-ui-test-suite.md).

## Sources of truth for platform decisions 📚

CLAUDE.md § *Sources of truth for Android / Kotlin / Compose decisions* has the invariant — consult the authoritative source first and cite it, don't answer from training memory in version-sensitive areas, mark heuristic if the source is unreachable. The routing table:

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
| Accessibility in Compose (semantics, click labels, custom actions, large text) | https://developer.android.com/develop/ui/compose/accessibility — pairs with WCAG 2.2 AA (CLAUDE.md § Accessibility) |
| AGP 9 migration | Linked skill `agp-9-upgrade` |
| R8 keep rules audit | Linked skill `r8-analyzer` |
| Play Billing | Linked skill `play-billing-library-version-upgrade` |
| Kotlin idioms, conventions, KEEP proposals | https://kotlinlang.org/docs/coding-conventions.html + KEEP at https://github.com/Kotlin/KEEP |
| Project-specific architectural decisions | `docs/adr/*.md` — read the relevant ADR before changing that area |

## Testing 🧪

Testing rules and invariants live in `CLAUDE.md` (§ *Bug fixes — TDD workflow*, § *Features — test coverage workflow*, § *Test naming convention*, § *Activity smoke tests*, § *Pre-PR and pre-push checklists*). This section covers the *operational* side: setup, run commands, conventions you need at the keyboard — plus the full Pre-PR / Pre-push checklists themselves.

### Bug fixes — TDD procedure

The trigger and the "skip only when..." invariant live in CLAUDE.md § *Bug fixes — TDD workflow*. The full three-step procedure:

1. **Write a failing test first** that reproduces the bug. Run it to confirm it fails for the right reason.
2. **Fix the production code** with the minimum change to make it pass.
3. **Run the full test suite** (`./gradlew test`).

Skip TDD only when the bug lives exclusively in UI rendering or platform wiring that can't be exercised by unit / Robolectric tests (e.g. a pure layout glitch). Note in the PR description why TDD was skipped.

For **production bugs reported by users** (not local-repro), scope frequency, affected versions, OS distribution, and recurring stack frames in Crashlytics + BigQuery first — see § *BigQuery export* below.

### Features — test coverage procedure

The trigger and the three-axis gate live in CLAUDE.md § *Features — test coverage workflow*. This is the worked template — produce all three axes before writing production code. They cover the bug classes a pre-PR checklist structurally can't: state-model gaps and device-only gaps surface in manual testing precisely because they were never enumerated in the plan.

**(a) Platform surfaces touched.** For each Android surface the feature hits, name the known gotcha and read the relevant ADR/skill first (ADRs are read-on-demand — nothing auto-loads them):

| Surface | Gotcha | Source |
|---|---|---|
| `resolveActivity()` / implicit intent | API 30+ package visibility needs a manifest `<queries>` entry, or it returns null and the affordance silently never appears | — |
| `singleTask` / exported Activity | a new intent on the live instance needs `onNewIntent`, or the screen shows the previous intent's stale data | — |
| full-screen / immersive UI | content draws under the status/nav bars; system-bar icon legibility over your own background | `edge-to-edge` skill |
| new color role / control fill | must map to a semantic role from `AppTheme.kt`; contrast | § *Design system*, ADR 0010 |

**(b) State & transition model.** Enumerate every state a new user-facing control can be in and every transition between them, then derive one test per transition. The transitions that have bitten us:

- **Rapid-repeat of an action** — deleting several items before the undo snackbar fades; only the last stuck (#1174).
- **A new intent while the screen is open** — a share arriving over an open Edit screen showed stale data (#1165).
- **Resource state mid-transition** — preview audio still playing when the save-success morph appeared (#1183 / `bbaa669`).
- **Concurrent reactive re-emit** — a DataStore write re-ran `loadSounds` and repopulated a cache just mutated (#1174).
- **What's rendered/seekable behind a dismiss gesture** — predictive-back revealed a bare window because the base wasn't composed behind (#1171 / `5404b7a`).

Check each state against CLAUDE.md § *Stateful Composables* (durable state → `rememberSaveable`; the focus incantation). Agree the minimum scenarios up front (happy path + these boundaries + smoke test, § *Activity smoke tests*); implement tests **alongside** the feature, not after. Anything unlisted is out of scope — note in the PR.

**(c) Device-only verification — route to the existing guardrail first.** Don't hand-check what tooling already covers:

- **Palette contrast** is covered by `AppThemeContrastTest` (critical role pairs, both modes) + the `Color(0x…)` / magic-alpha grep in `check-adr-invariants.sh`. Adding a *new* designed role pair → add its assertion to `AppThemeContrastTest`; don't re-verify existing ones by hand.
- **Residue those can't see** → flag it: inset overlap, system-bar icon legibility over a gradient (forced-dark fix, #1183), animation/scrub visual behavior (the scrubber visually reset though seek worked, #1183), a control fill on a non-bar surface collapsing to invisible (dark-mode CTA, #1170 → ADR 0010). Convert to an instrumented assertion where feasible (#1183 added a status-bar-inset guard); else record a named manual check — interactive: a pre-merge checklist item; overnight: a *manual verification suggested* line in the PR's `### Code review tips`.

**Acceptance criteria are concrete** for any generated or derived content, so a plausible-looking stub doesn't pass self-review — e.g. the immersive waveform shipped synthetic before the real amplitude envelope was specified (#1183). Define "done" in the plan.

Skip an axis item only when it lives exclusively in platform wiring not exercisable by unit / Robolectric tests. Note why.

### Activity smoke tests

The rule (every `Activity` needs one; what to mock; when a full-screen Composable needs a `createComposeRule()` smoke test) lives in CLAUDE.md § *Activity smoke tests*. The Activity snippet:

```kotlin
ActivityScenario.launch(MyActivity::class.java).use { scenario ->
    assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)
}
```

Canonical: `LandingActivityTest` (mocks `PlayerControllerFactory`, which crashes under Robolectric), `AboutScreenTest` (full-screen Composable with PackageManager + raw-resource access).

### Pre-PR checklist

Before opening a PR for any feature or bug fix, verify:

- [ ] Happy path is covered by at least one test
- [ ] Failure modes at system/external boundaries have tests (file I/O, MediaPlayer, permissions, network, Play feature delivery)
- [ ] New `Activity` has a smoke test (CLAUDE.md § *Activity smoke tests*)
- [ ] New full-screen Composable with business logic has a `createComposeRule()` smoke test
- [ ] Composables with durable state (text input, opened overlays/sheets, sub-screens, in-flight error) have at least one `scenario.recreate()` test (CLAUDE.md § *Stateful Composables*)
- [ ] Any skipped scenario is explicitly noted with a reason
- [ ] Self code review: re-read every changed file as a reviewer, not author — logic gaps, missing edge cases, unclear naming

Then run the **Pre-push checklist** below.

### Pre-push checklist

CI linters that must pass:

- **KtLint** — style (part of `check`; auto-fix `ktlintFormat`)
- **Detekt** — static analysis (config `config/detekt/detekt-config.yml`; max line length 150)
- **Spotless** — AGPLv3 headers (part of `check`; auto-fix `spotlessApply`)
- **Android Lint** — rules in `config/android/android-lint.xml`

Run `./gradlew check -x test && ./gradlew test` before pushing — catches the same failures CI reports without waiting for a full CI run.

**Enforced locally by `.githooks/pre-push`** (installed by copy via `scripts/install-hooks.sh`, re-armed each session). On every `git push` it runs CI's cheap checks fail-fast, cheapest first — the pure grep guards (`check-adr-invariants.sh`, `check-security-test-count.sh`, `check-analytics-wrapper.sh`, `check-test-assertions.sh`; the latter two are the shared scripts the `analytics-wrapper-guard` / `test-assertion-guard` CI jobs also call), then `./gradlew ktlintCheck detekt spotlessCheck`. A failing check blocks the push and prints the auto-fix command (`ktlintFormat` / `spotlessApply`) where applicable.

- **Heavy path is opt-in:** `PREPUSH_FULL=1 git push` also runs `./gradlew test` + `:app:lintDebug :app:lintRelease`. Kept out of the default path so the hook stays fast; CI runs them regardless.
- **Escape hatch:** `git push --no-verify` skips the hook entirely — for cosmetic-only pushes (CHANGELOG, copy strings, README, comments).
- The instrumented UI suite is **not** part of the hook (it cold-boots an emulator) — run it via `./scripts/run-instrumented-tests.sh` for functional changes as above.
- **Claude Code sessions auto-format first:** a committed `PreToolUse` hook (`.claude/hooks/pre-check-ktlint-format.sh`, registered in `.claude/settings.json`) runs `ktlintFormat` before any Gradle check command, so the pre-push hook's `ktlintCheck` never trips on an auto-fixable violation. Best-effort (always exits 0, never blocks the command, no-ops in microseconds on non-check commands); it does not replace running `ktlintFormat` by hand in a plain terminal.

**Functional changes also require the local UI test suite** (see § *Local UI test suite → When to run* below). If the change touches Composables, ViewModels, intents, navigation, deep links, or persistence — run `./scripts/run-instrumented-tests.sh` before pushing (it cold-boots the emulator; never run the Gradle task directly against a warm AVD). CircleCI does not execute it. Cosmetic-only changes (CHANGELOG, copy strings, README, comments) are exempt.

### Test assertions

Bare `kotlin.assert(...)` is forbidden in test sources — it's a silent no-op without JVM `-ea`. Use Truth (`assertThat`), JUnit (`assertEquals` / `assertTrue` / `assertNotNull`), or the Compose UI Test API (`assertCountEquals`, `assertIsDisplayed`). Full rationale and the incident that prompted the rule (PR #1117) live in [ADR 0006](docs/adr/0006-no-kotlin-assert-in-tests.md). Enforced by the CircleCI `test-assertion-guard` job and by `scripts/check-adr-invariants.sh`. Run the same check locally before pushing:

```bash
grep -rnE '(^|[^[:alnum:]_])assert[[:space:]]*\(' --include='*.kt' \
    app/src/test app/src/androidTest \
    commons_android/src/test commons_file/src/test model/src/test
```

Empty output = clean. Any hit is a hard failure — fix the call-site, do not add an exclusion.

### Security test tagging (OWASP MASVS)

The write-time rule — which tests must carry an `OWASP MASVS-…` KDoc and how to pick the control — lives in CLAUDE.md § *Security boundaries → Security test tagging*. Guard mechanics:

- `scripts/check-security-test-count.sh` (CircleCI `security-test-count-guard`) counts the literal marker `OWASP MASVS-` across `app/src/test`, `app/src/androidTest`, `commons_android/src`, `commons_file/src`, `model/src`.
- It fails the build when the count drops below `EXPECTED_COUNT` — a drop signals an accidentally-removed security test. Restore it, or for a deliberate removal bump `EXPECTED_COUNT` in the script and explain why in the PR.

### Awaiting multiple async inputs

The write-time invariant — await *every* upstream a value is folded from, not just the signal you triggered — lives in CLAUDE.md § *JVM tests — await every async input*. Worked example: a user property folded from `library` + `collections`, asserted after awaiting only the signal the test fired, flakes because the reactive `loadSounds` populating `allSoundsCache` hasn't arrived on a loaded CI machine. Await each upstream before the triggering action — after `save`, `vm.library.first { it.has(id) }`; after a tag, `awaitAnalyticsEvent(...)` **and** `collections.first { it.contains(...) }`. Canonical: `SoundsViewModelAnalyticsTest`.

**Bound every `runBlocking` await with `withTimeout`.** An unbounded `runBlocking { … .first/.collect/.await/.single … }` that never resolves hangs the test until CircleCI's 10-min no-output timeout kills the whole job with a useless generic message (the PR #1186 hang). Wrap the await in `withTimeout(TIMEOUT_MS)` so it fails in seconds, by name. Pattern: `SoundsViewModelVisibilityTest`. A ratchet in `scripts/check-adr-invariants.sh` (job `adr-invariants`) enforces this for **new** test code via a grandfathered per-file baseline — a new file may not introduce one, and a baselined file may not grow past its count; the baseline only shrinks as the existing offenders are swept. Escape hatch for a genuinely-needed one-off: a trailing `// await-ok`. Multi-line `runBlocking {` openings are out of scope (most are legit setup).

### Test fixtures: `clearForTest()`

Every DataStore-backed store ships a `@VisibleForTesting(otherwise = NONE) suspend fun clearForTest()` so test setUp can reset state without poking the file system. Mirror the pattern when you add a new store. References: `WelcomeStickerStore`, `DataStoreFirstFlagStore`, `DataStoreCounterStore`.

For tracker substitution use `AnalyticsTrackerProvider.setForTest(FakeAnalyticsTracker())` — never mock `AnalyticsTracker` directly. The fake lives in `:commons_android` test fixtures, pulled into call-site tests via `testImplementation testFixtures(project(":commons_android"))`.

### Local UI test suite

Instrumented UI/functional tests live under `app/src/androidTest/`. They drive a real emulator using Compose UI Test + Espresso + UI Automator + Espresso Accessibility Checks. CircleCI does not run them — see [ADR 0001](docs/adr/0001-local-ui-test-suite.md) for the rationale.

#### When to run

- After any change to a Composable, ViewModel, intent flow, navigation, deep link, or persistence layer.
- Not required for: CHANGELOG, copy strings, README, comments, off-device tooling config.

#### Setup (one-time)

```bash
# Creates the AVD `Android_14_API_34` (idempotent, ~5 min the first time including system image download)
./scripts/setup-test-emulator.sh
```

#### Run the full suite

```bash
./scripts/run-instrumented-tests.sh
```

The wrapper cold-boots the AVD with wiped userdata, waits for it, then runs `./gradlew :app:connectedDebugAndroidTest` against that emulator only (it pins the serial, so a physical device attached at the same time is ignored). The cold boot launches with `-gpu host` and widened resources (`-cores 6 -memory 4096`) so the guest renders on the host GPU instead of in software — faster and less flaky than the AVD defaults. Override per run with `EMULATOR_GPU` (use `auto` if `host` misbehaves on your machine), `EMULATOR_CORES`, `EMULATOR_MEMORY`.

**Always go through the wrapper — do not run `./gradlew :app:connectedDebugAndroidTest` against an already-running emulator.** A warm emulator degrades across back-to-back runs (`system_server` watchdog ANRs, hundreds of skipped frames), which surfaces as `ComposeTimeoutException` / `ComposeNotIdleException` flakes or an outright `Process crashed`. A cold boot resets that — a clean run finishes in ~3 min; a degraded one takes 15+ min or never completes. Rationale: [ADR 0001 § *Cold boot per run*](docs/adr/0001-local-ui-test-suite.md).

To hunt flakes, run several cold-booted passes in a row:

```bash
RUNS=3 ./scripts/run-instrumented-tests.sh
```

HTML report: `app/build/reports/androidTests/connected/debug/index.html`. Raw XML: `app/build/outputs/androidTest-results/connected/debug/`.

#### Is the red the emulator, or a real bug?

Before re-running, classify by **determinism and timing** — not every red is the degraded emulator:

- **Emulator flake** — `ComposeTimeoutException`, `ComposeNotIdleException`, `Process crashed`. Non-deterministic, varies run to run, usually on a slow pass. A clean cold boot (or freeing host load) makes it pass. Re-run via the wrapper.
- **Deterministic bug** — `Failed to inject touch input. Reason: Expected exactly '1' node but found '2'`, or an `assertCountEquals` mismatch. Fails **instantly and identically every run**, cold boot included. The `inject touch` wording is misleading: Compose resolves the single target node *before* sending input, so this throws in the test process and the emulator is never invoked. It means matcher ambiguity — two real nodes in the tree (read the dump: two distinct rows) — i.e. a **test-isolation / seeding bug**, not the AVD. **Re-running never helps.** Fix the data the test renders. Rationale: [ADR 0001 § *Not every red is the emulator*](docs/adr/0001-local-ui-test-suite.md).

#### Run a single test class

Any extra arguments are passed straight through to Gradle:

```bash
./scripts/run-instrumented-tests.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=com.github.barriosnahuel.vossosunboton.ui.home.SearchOverlayTest
```

#### Synchronization

`waitForIdle()` only flushes Compose recompositions — not the `DataStore → StateFlow → render` chain (canonical race, PR #1111). For nodes whose existence depends on ViewModel/DataStore state, use the `awaitNode*` helpers in `app/src/androidTest/.../ComposeTestExtensions.kt` (rationale in KDoc):

```kotlin
composeRule.awaitNodeWithContentDescription(pinLabel()).performClick()
composeRule.awaitNodeWithText(homeTabLabel()).assertIsDisplayed()
composeRule.awaitNode(hasSetTextAction()).performTextInput(name)
```

Bare `waitForIdle()` / `waitUntil { onAllNodes(...).isNotEmpty() }` is still correct after deterministic actions (`performClick`, `pressBack`), before negative assertions (`assertCountEquals(0)`), before `.onFirst()` chains, and when the matcher would multi-match (the helpers' terminal `onNode*` throws on multi-match). The one-line rule also lives in `CLAUDE.md` § *Local UI test suite*.

## Performance 📈

Three complementary layers — reach for the one that matches the question:

| Layer | What it does | When to reach for it |
|---|---|---|
| **Baseline Profile** (`app/src/main/baseline-prof.txt`) | AOT-compiles the cold-start path so first launches are fast | Make startup faster; regenerate when the startup path changes (§ *Baseline Profile* below) |
| **Macrobenchmark** (`:macrobenchmark`) | Statistical on-device numbers for cold start + scroll — the **assertable regression gate** for slow-frame jank | Prove or measure a regression with numbers; validate the Baseline Profile |
| **Frozen-frame crash gate** (debug JankStats) | **Fails loud** in manual debug on a repeated main-thread block, like StrictMode | Catch an egregious block the moment you hit it by hand — no script, no watching logcat |

Rule of thumb: slow-frame jank is *measured* (Macrobenchmark); a *frozen* frame — a main-thread block — is *gated* (the crash gate). Complementary, not redundant. The first two are documented immediately below; the gate is § *Frozen-frame crash gate (JankStats)*.

The `:macrobenchmark` module (`com.android.test`, AndroidX Macrobenchmark) measures LandingActivity's cold start and sound-list scroll on a **real device or emulator** — the on-device half of the entry-screen jank investigation (Firebase Performance flags the regimes; these reproduce them with numbers). It targets the app's release-like `benchmark` build type (non-debuggable, minified, profileable), so the numbers track what users get — not the debug build. Architecture + rationale (why synchronous atomic seeding, why not real files, relationship to ADR 0004): [ADR 0015](docs/adr/0015-macrobenchmark-seeding-architecture.md).

### Run it

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

Needs a connected device/emulator (API 28+; metrics are most reliable on API 29+, where the build is profileable). Results land in `macrobenchmark/build/outputs/connected_android_test_additional_output/` (JSON + Perfetto traces), with a summary in the Gradle output. In Android Studio, run a benchmark class from the gutter. CircleCI does not run benchmarks (no device), same as the instrumented suite (§ *Local UI test suite*).

### What it measures

- **`StartupBenchmark`** — cold start across three `CompilationMode`s: `None` (no AOT — fresh install), `DEFAULT` (warmed), and `Partial(Require)` (the committed Baseline Profile). Brackets the AOT spread and validates the profile.
- **`ScrollBenchmark`** — `FrameTimingMetric` while flinging the sound list at **20 / 50 / 200** items, to expose whether scroll jank scales with list size (hypothesis H2 — Fase 3 found it does not). The list is seeded to exactly N synthetic sounds via a launch-intent extra handled by the benchmark build type's `CustomBuildTypeApplication` (a release-like override; never reaches release or debug). Seeding is synchronous and atomic so the measured scroll always traverses exactly N — see [ADR 0015](docs/adr/0015-macrobenchmark-seeding-architecture.md).

### Baseline Profile (manual, no plugin)

The cold-start hot path is AOT-compiled from `app/src/main/baseline-prof.txt`, which AGP bakes into the release/`benchmark` APK (`assets/dexopt/baseline.prof`). We **don't** use the `androidx.baselineprofile` plugin: it would auto-create variants that overlap our `benchmark` build type and rewire the `:macrobenchmark` module (consolidating onto the plugin is a deferred follow-up). Instead the profile is generated by hand and committed; regenerate it when the startup path changes meaningfully.

The profile must reflect the **real release startup path** with **real (non-obfuscated) names** (so AGP remaps them through R8 when it bakes the profile into the minified release), so generation runs against the **`nonMinifiedRelease`** build type — the real release code (its own `CustomBuildTypeApplication` in `src/nonMinifiedRelease` mirrors `src/release` — source sets resolve by build-type name, not `initWith`) but un-minified. (Generating against `debug` would capture debug-only code: StrictMode, the seeder, the debug `Application`; against the minified `benchmark` it would capture obfuscated names.) One command does it — install `nonMinifiedRelease`, run the `BaselineProfileGenerator`, pull into `app/src/main/baseline-prof.txt`:

```bash
./scripts/generate-baseline-profile.sh        # set ANDROID_SERIAL if several devices are attached
```

Any device or emulator works for **generation** (the profile is a code-path snapshot, not a timing). Review the diff and commit. Then **validate on a real device** (emulators report inverted AOT numbers, so they can't validate this): `StartupBenchmark.startupBaselineProfile` (`CompilationMode.Partial(Require)`) should land near `startupDefaultCompilation` and well under `startupNoCompilation`.

### Frozen-frame crash gate (JankStats)

Debug-only, the rendering sibling of StrictMode. `JankStatsLogger` installs JankStats per Activity (logs janky frames by screen to Logcat); `FrozenFrameGate` turns a **repeated frozen frame** into a process crash. Source of truth: `app/src/debug/.../FrozenFrameGate.kt` + `JankStatsLogger.kt`. Decision + full rationale (why frozen-only, why not `isJank`, why not total frame duration, why log-only under instrumentation): [ADR 0016](docs/adr/0016-jankstats-frozen-frame-crash-gate.md).

**What fires it** (two tiers, after each screen's 5 s startup settle and minus the allowlist): a **single egregious frame ≥ 1.5 s** crashes immediately (an unambiguous block, even one-shot on a screen you visit once); in the ambiguous **700 ms–1.5 s** band — a main-thread block, *not* GPU slowness — it takes the **2nd** frozen frame within a 5 s window (a lone one, or two far apart, is absorbed as an environmental hiccup). Crash message: `JankStats: frozen frame exceeded 700ms (debug jank gate)`.

**When it does NOT fire:** under instrumentation (`connectedAndroidTest` / the local UI suite) the crash is **suppressed** — the cold-boot emulator emits multi-second frozen frames from its own starvation that aren't real blocks (ADR 0016). Those runs keep the log; the line `frozen-frame crash gate log-only — under instrumentation` confirms it. The gate's home is **manual / real-device** debug use.

**Triage when it fires** (same order as the StrictMode tree, § *Terminal: StrictMode violations*):

1. **Top app-code frame is ours** (`com.github.barriosnahuel.vossosunboton.*`) → a real main-thread block on that screen. Fix the production code (move the work off the main thread). Don't allowlist.
2. **A known-legit heavy render we own** (e.g. the first paint of a deliberately huge list) → add a `KnownHeavyFrame` to the allowlist in `FrozenFrameGate.kt` (grep marker `frozen-frame-allowlist`), same spirit as StrictMode's `KnownThirdPartyViolation`.
3. **Suspected environment** (a thermally-throttled real device) → it already needs 2 frozen frames within 5 s; if it still misfires on real hardware, that's the signal to tune the constants — see ADR 0016.

## Platform upgrades
### API Level
⚠️ Remember to change not only the compile/target API levels but the tests config too. Check [`AbstractRobolectricTest`](/app/src/test/java/com/github/barriosnahuel/vossosunboton/AbstractRobolectricTest.kt).

### Gradle upgrade
As described at [Gradle docs#Adding wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:adding_wrapper) you must run:

    > ./gradlew wrapper --gradle-version ${desiredVersion}

## Firebase config file ⚙️

The repo commits **scrubbed dummies** at `app/src/debug/google-services.json` and `app/src/release/google-services.json` (real `project_id` / `project_number` / `package_name`, fake `mobilesdk_app_id` and `api_key`) so CI can compile and GitGuardian doesn't flag real Firebase API keys. Two Firebase projects back the build types: `bomp-prod` (release) and `bomp-debug` (debug). The Google Services Gradle plugin auto-resolves the per-variant JSON by `package_name`.

To run local builds against the real Firebase projects, swap the dummies for the files downloaded from Firebase Console, then mark them skip-worktree so accidental edits don't reach the index:

    > cp /path/to/real/google-services-release.json app/src/release/google-services.json
    > cp /path/to/real/google-services-debug.json   app/src/debug/google-services.json
    > git update-index --skip-worktree app/src/release/google-services.json
    > git update-index --skip-worktree app/src/debug/google-services.json

### Creating a new worktree

Worktrees live as **siblings of the primary worktree**, never nested under `.claude/` — a nested worktree gets indexed by the IDE as a separate project in the same window, cluttering navigation. Name them `push-me-<topic>`:

```bash
git worktree add ../push-me-<topic> -b <branch> origin/develop
```

A committed `WorktreeCreate` hook (`.claude/hooks/create-sibling-worktree.sh`, registered in `.claude/settings.json`) applies the same layout to worktrees the Claude Code harness or its subagents create — they land as `../push-me-<name>` instead of the harness default `.claude/worktrees/<name>`. The hook resolves the primary worktree root via `git rev-parse --git-common-dir`, so it works even when invoked from inside a linked worktree.

The dummies land checked-in on the new worktree but the real configs and the bundled audio files don't follow — the swap-and-skip-worktree state is per-worktree. The `WorktreeCreate` hook runs `./scripts/setup-worktree.sh` automatically when the Claude Code harness creates the worktree. For worktrees created by hand, run it once from the new worktree's root:

```bash
./scripts/setup-worktree.sh
```

The script is idempotent — re-running on an already-set-up worktree exits without changes. Equivalent manual sequence (for reference / when the script is unavailable):

```bash
cp "$(git rev-parse --git-common-dir)/../app/src/release/google-services.json" app/src/release/google-services.json
cp "$(git rev-parse --git-common-dir)/../app/src/debug/google-services.json"   app/src/debug/google-services.json
git update-index --skip-worktree app/src/release/google-services.json
git update-index --skip-worktree app/src/debug/google-services.json
mkdir -p model/src/debug/res/raw
cp "$(git rev-parse --git-common-dir)/../model/src/debug/res/raw/"*.mp3 model/src/debug/res/raw/ 2>/dev/null || true
cp "$(git rev-parse --git-common-dir)/../model/src/debug/res/raw/"*.ogg model/src/debug/res/raw/ 2>/dev/null || true
```

This is distinct from the swap-from-disk procedure above (used the first time you set up a fresh clone). The worktree-copy variant assumes the primary worktree already has the real files in place.

### Cleaning up merged worktrees

Worktrees created per task — by the `overnight-work` Claude Code skill (user-level, **not** in this repo), by harness subagents, or by hand — accumulate after their PR merges: `deleteBranchOnMerge` drops the *remote* branch, but the **local** worktree and branch linger. `scripts/cleanup-merged-worktrees.sh` removes them automatically. The full rationale (why keyed on the PR's merged commit and not git ancestry/branch name, why copy-install instead of `core.hooksPath`, how it relates to the harness's own cleanup) is in [ADR 0014](docs/adr/0014-worktree-lifecycle-sibling-layout-and-cleanup.md) — this section is the operator's how-to.

**Trigger.** The committed `.githooks/post-merge` hook runs the script on the `git pull` of `develop` that follows a merge (only on `develop`; always exits 0, so it can never block a pull).

**What it does.** For each linked worktree, it removes the worktree + its local branch **only if** a `MERGED` PR for that head landed this exact commit (`headRefOid` == the worktree's tip) and no PR is still `OPEN`; then `git fetch --prune`s dead `origin/*` refs. It never touches the primary worktree, protected branches (`develop`, `gh-pages`, `feat/gh-pages-*` — the `PROTECTED_BRANCHES` list at the top of the script), detached or dirty worktrees (kept with a warning, never `--force`), or orphan local branches with no worktree (e.g. `backup/*`). Any `gh` failure or tip mismatch ⇒ *keep* (fail-safe).

Preview without changing anything (side-effect-free):

```bash
./scripts/cleanup-merged-worktrees.sh --dry-run
```

**Tests.** `scripts/test-cleanup-merged-worktrees.sh` is a self-contained behaviour test (pure bash + git — no network/`gh`/extra tooling: it builds a throwaway repo with real worktrees and shims `gh`), mutation-tested to fail if the keep/remove decision regresses. Runs in CI as the `worktree-cleanup-test` job; run it locally the same way:

```bash
./scripts/test-cleanup-merged-worktrees.sh
```

**Installation.** The hook is installed by copy into `$(git rev-parse --git-common-dir)/hooks` via `scripts/install-hooks.sh`, re-armed every session by a committed `SessionStart` hook in `.claude/settings.json` (a fresh clone self-arms on its first session; the installed copy then persists for terminal `git pull`s). Arm it by hand for a clone you won't open in Claude Code — **not** via `core.hooksPath`, see [ADR 0014](docs/adr/0014-worktree-lifecycle-sibling-layout-and-cleanup.md):

```bash
./scripts/install-hooks.sh
```

### Editing the committed dummy

To **edit the committed dummy itself** (rare — only when adding fields the plugin needs, or when scrubbing differently). Naively running `--no-skip-worktree` then `git add` would stage the real file from the working tree and leak `AIza…` keys, so use this safe sequence per file:

    > # 1. Stash the real file outside the worktree
    > mv app/src/release/google-services.json /tmp/google-services-release-real.json
    > # 2. Drop skip-worktree and restore the dummy from HEAD
    > git update-index --no-skip-worktree app/src/release/google-services.json
    > git checkout HEAD -- app/src/release/google-services.json
    > # 3. Edit the dummy and stage it
    > $EDITOR app/src/release/google-services.json
    > git add app/src/release/google-services.json
    > # 4. Restore the real file and re-arm skip-worktree
    > mv /tmp/google-services-release-real.json app/src/release/google-services.json
    > git update-index --skip-worktree app/src/release/google-services.json

Repeat for `app/src/debug/google-services.json`. Before committing, verify with `git diff --cached app/src/release/google-services.json` (and the debug one) — if you see an `AIza…` key or a non-dummy `mobilesdk_app_id`, abort and restart the sequence.

## Backup & restore testing 💾

To manually verify Auto Backup saves and restores custom sound metadata, use `bmgr` via `adb`:

1. **Trigger a backup:**

       adb shell bmgr backupnow com.github.barriosnahuel.vossosunboton.debug

2. **List available backup sets** (to find the restore token):

       adb shell bmgr list sets

3. **Restore from a backup set:**

       adb shell bmgr restore <token> com.github.barriosnahuel.vossosunboton.debug

4. **Wipe the backup for this app from the active transport** (e.g. start over from scratch on Drive):

       adb shell bmgr list transports                                                # find the active (*) Drive transport
       adb shell bmgr wipe <transport> com.github.barriosnahuel.vossosunboton.debug

   Scoped to the package — other apps' Drive backups are untouched. Pair with `adb shell pm clear com.github.barriosnahuel.vossosunboton.debug` to also wipe local state, otherwise the next `backupnow` re-uploads what is still on the device.

> Requires a device or emulator with Google Mobile Services. Does not work on stock AOSP emulators.

### On-device data paths

When debugging what the app actually persists, these are the two files/directories to inspect (paths use the `.debug` suffix from `applicationIdSuffix`; release builds drop it):

| What | Path |
| -- | -- |
| Sound metadata (DataStore Preferences, JSON-encoded `StoredSound` list) | `/data/data/com.github.barriosnahuel.vossosunboton.debug/files/datastore/bomps.preferences_pb` |
| Custom audio files (the actual `.mp3` saved from the share sheet) | `/storage/emulated/0/Android/data/com.github.barriosnahuel.vossosunboton.debug/files/Music/` |

The DataStore file is binary Protobuf wrapping our `sounds_json` string — the JSON itself is plain text inside, so `strings` reveals the contents without needing protoc:

```bash
# List the datastore directory
adb shell run-as com.github.barriosnahuel.vossosunboton.debug ls -la files/datastore/

# Dump the JSON payload (readable)
adb shell run-as com.github.barriosnahuel.vossosunboton.debug cat files/datastore/bomps.preferences_pb | strings

# Pull a copy locally (for diffing across runs, attaching to a bug, etc.)
adb exec-out run-as com.github.barriosnahuel.vossosunboton.debug cat files/datastore/bomps.preferences_pb > bomps.preferences_pb

# List the audio files
adb shell ls -la /storage/emulated/0/Android/data/com.github.barriosnahuel.vossosunboton.debug/files/Music/
```

The audio directory is app-private external storage — no special permission needed for `adb shell ls`, but `run-as` is required for the internal `dataDir`.

## Logcat 😿

### Android Studio: Remove all dev tools (*a.k.a. !Dev Tools*)

| Field     | REGEXP |
| --        | -- |
| TAG       | `^(?!(?:FirebasePerformance|FA|LeakCanary|FirebaseRemoteConfig|zygote|Choreographer|OpenGLRenderer|Adreno|vndksupport|SoLoader|ApkSoSource)$).*$` |
| Package   | `com.github.barriosnahuel.vossosunboton` |

### Terminal: Only Firebase Performance Monitoring 💯

You can filter logcat messages by:

> adb logcat -s FirebasePerformance

### Terminal: StrictMode violations 🚨

The debug build's `StrictModeConfigurator` silences known third-party noise and routes every surviving violation through `Tracker.track`, so the logcat line uses the `Tracker` tag and the message starts with `StrictMode: <ViolationClass>`. The cleanest filter is to grep for the prefix:

```bash
adb logcat -d | grep StrictMode
```

If you also want to scope by tag (cuts unrelated `Tracker.track` non-fatals from MediaPlayer, etc.), combine:

```bash
adb logcat -d -s Tracker:E | grep StrictMode
```

Empty output means every detected violation matched a `KnownThirdPartyViolation` entry — none reached Crashlytics either. When one does show up, triage in this order (the order CLAUDE.md § *StrictMode debug audit* summarizes):

1. **Top app-code frame is ours** (`com.github.barriosnahuel.vossosunboton.*`): fix the production code. Don't filter.
2. **Scopable to a known-OK call-site we own** (e.g. SDK init that legitimately reads disk on first call): wrap with `StrictMode.allowThreadDiskReads()` + `try/finally` at the call-site. Canonical: `AnalyticsTrackerProvider.createTracker`. Don't add a matcher.
3. **Third-party class running its own code** (Compose, Espresso, GMS, framework finalizers): add a `KnownThirdPartyViolation` with a comment naming the library + upstream issue (when public). Use `methodNameContains` when the class prefix would over-match (`android.os.StrictMode` itself does); use `fileNameContains` when classes are obfuscated and unstable (GMS Dynamite ships as `m7.*` etc.).

## Resources 🎨
- **Color palette:** Neo-Club (ink × acid), a custom palette designed for Bomp. Single source of truth is [`app/src/main/java/com/github/barriosnahuel/vossosunboton/ui/theme/AppTheme.kt`](app/src/main/java/com/github/barriosnahuel/vossosunboton/ui/theme/AppTheme.kt) — see `CLAUDE.md` § "Design system" for the role mapping and contrast guarantees.
- **Launcher icon:** rendered from the SVG masters under [`store-listing/brand/`](store-listing/brand/) (`launcher-fallback.svg` for Android < 8; the adaptive vector at [`app/src/main/res/mipmap-anydpi-v26/app_ic_launcher.xml`](app/src/main/res/mipmap-anydpi-v26/app_ic_launcher.xml) for Android 8+). Export pipeline (`rsvg-convert`) is documented in `CLAUDE.md` § "Store listing asset generation".
- In-App icons using: [Material Symbols](https://fonts.google.com/icons)

**Semantic role → intent mapping** — the full table CLAUDE.md § *Design system* points to. `AppTheme.kt` (`LightColors` / `DarkColors`) is the source of truth for the per-mode token; this is the reach-for-it reference:

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

## Signing 🔑

The following files must be located into the root dir (neither is committed):
- `nahuelbarrios.keystore-appbundle.pkcs12`
- `secure.properties` — holds `key.alias`, `key.password`, `store.password`

Debug builds use the included debug keystore, so no signing setup is needed for them.

## Release builds 📦

Release-only Gradle commands (need the signing files above in the project root):

```bash
./gradlew app:lintVitalRelease   # Android lint, release variant — the release lint gate
./gradlew app:bundle             # Build the AAB for Play Store upload
```

### Pre-release checklist

A **seed** — expand it as the release process is formalized (store rollout steps aren't documented yet, so they're intentionally omitted rather than invented). Before cutting a release:

- [ ] **Regenerate the Baseline Profile** — `./scripts/generate-baseline-profile.sh`, then validate on a **real device** (`StartupBenchmark.startupBaselineProfile` near `DEFAULT`, well under `None`). It's a frozen snapshot of the cold-start path (§ *Baseline Profile*); refresh it so accumulated startup changes are precompiled.
- [ ] **Finalize `CHANGELOG.md`** — move `## [unreleased]` to the version + date (§ CLAUDE.md *Changelog*).
- [ ] **Bump `versionCode` + `versionName`** in `app/build.gradle` to match the milestone.
- [ ] **Release lint gate** — `./gradlew app:lintVitalRelease` green.
- [ ] **Build the AAB** — `./gradlew app:bundle`.
- [ ] **Write the store change notes** — `store-listing/{en-US,es-AR}/changelog-<versionCode>.txt`; the GitHub release notes are sourced from the en-US file (§ *Creating the GitHub release*).
- [ ] **Manual analytics smoke** — verify events in DebugView before merging (§ *Analytics events*); aggregated Reports lag 24–48 h.

### Creating the GitHub release

After the checklist above is green and the version bump is merged to `develop`:

1. **Build the release bundle** — `./gradlew app:bundleRelease` (or `app:bundle`). The Crashlytics Gradle plugin auto-wires `uploadCrashlyticsMappingFileRelease` into this task (verify: `./gradlew :app:bundleRelease --dry-run | grep Crashlytics`), so with the **real** `google-services.json` active it uploads the R8 mapping to Firebase as part of the build — no separate command. **Do NOT pass `-x uploadCrashlyticsMappingFileRelease`** when cutting a real release: that flag is **CI-only** (CI builds against the scrubbed dummy config, where the upload 400s). Passing it — or building without the real `google-services.json` — is the most likely cause of past releases shipping obfuscated: `injectCrashlyticsMappingFileIdRelease` still embeds the `r8-map-id` into the app, but the matching `mapping.txt` never lands in Firebase, so Crashlytics/MCP can't de-obfuscate (§ *BigQuery export* → *Stack frames come R8-obfuscated*).
2. **Create the release + tag from `develop`**, notes from the store change file, attaching **only** the R8 mapping as the single asset:

   ```bash
   gh release create vX.Y.Z --target develop --title "vX.Y.Z" \
     --notes-file store-listing/en-US/changelog-<versionCode>.txt \
     app/build/outputs/mapping/release/mapping.txt
   ```

   `<versionCode>` is the value in `app/build.gradle`. Add a footer line linking to the full `CHANGELOG.md` on `develop` if the notes file omits it.
3. **Attach nothing else.** The mapping is a backup for offline `retrace`; **never** attach the keystore, `secure.properties`, the real `google-services.json`, or the `.aab` (the `.aab` carries no mapping anyway).
4. **Verify the asset landed** — `gh release view vX.Y.Z --json assets -q '.assets[].name'` must list `mapping.txt`. Print an explicit line for the maintainer to confirm, e.g. `✅ mapping.txt attached to vX.Y.Z` (or ⚠️ + re-run `gh release upload vX.Y.Z app/build/outputs/mapping/release/mapping.txt` if missing).
5. **Verify Firebase actually got the mapping** — the build log must show `uploadCrashlyticsMappingFileRelease` *ran* (not skipped). Within minutes, a fresh crash on this version should de-obfuscate in the Console / via the Firebase MCP (real frames, **not** `r8-map-id-…`). This step exists because past releases shipped **without** the mapping (§ *BigQuery export* → *Stack frames come R8-obfuscated*) — so Crashlytics couldn't deobfuscate them either.

Why archive the mapping: even when Firebase holds it for Console/MCP de-obfuscation, a GitHub-release copy keyed to the tag is a durable offline `retrace` backup independent of Firebase retention — and the safety net for exactly the case above, where the Firebase upload didn't happen.

## Bundled audio files 🔊

The following directory must be populated manually after cloning — it is not version-controlled:

```
model/src/debug/res/raw/
```

Place the bundled `.mp3` and `.ogg` files there. Without them the debug build still compiles and runs, but the **Explore** tab will be empty. Release builds are unaffected.

## Store listing 📄

Everything that goes into the Google Play Store listing — copy, brand mark, feature graphic, icon, screenshots — lives under [`store-listing/`](/store-listing), organized by locale. The directory layout and the upload checklist are in [`store-listing/README.md`](/store-listing/README.md).

### How the assets are generated

The icon and feature graphic are rendered from **SVG masters** committed in the repo, not authored in a raster editor. This makes them reproducible: same command, same output. The `*.png` files under `store-listing/<locale>/images/` are derived artifacts — regenerate from source rather than editing pixels.

Pick a tool depending on what you're doing:

| Tool | Fidelity | Effort | When to use |
|---|---|---|---|
| **`rsvg-convert`** (librsvg) | High for shapes; text falls back to system sans if the declared font is missing | `brew install librsvg` (~10 MB, no GUI) | **Default**. Reproducible CLI, great for scripts and CI. |
| **Inkscape** | Maximum (kerning, complex text, filters) | `brew install --cask inkscape` (~250 MB, GUI + CLI) | Only when you need to tweak typography by hand before export. |
| **`qlmanage`** | Acceptable for simple shapes; text often breaks | Already installed on macOS | Last resort if you can't install anything else. Avoid for typography-heavy assets. |

### Before you export: install the brand fonts

The feature graphic SVG declares `Inter, Roboto, system-ui, sans-serif`. If **Inter** isn't installed system-wide, the renderer falls back to the next match — usually Helvetica/SF Pro on macOS — and the output drifts from what you see in the browser preview.

Inter is committed as a single zip at [`store-listing/brand/fonts/Inter.zip`](/store-listing/brand/fonts/Inter.zip) (SIL Open Font License 1.1; `OFL.txt` is inside the archive). We keep it zipped so the directory stays clean — we never read the TTFs from there at runtime, only install them once. On macOS:

```bash
unzip -j -o store-listing/brand/fonts/Inter.zip "*.ttf" -d ~/Library/Fonts/
```

`-j` (junk paths) flattens the nested `static/` subdirectory; `-o` overwrites silently. This installs all 56 TTFs (54 static + 2 variable) directly into your user font directory.

If you add a new font dependency for any future asset, drop its zipped distribution at `store-listing/brand/fonts/<Family>.zip` (license file inside) and update this section.

### Exporting the icon (512×512)

The canonical wrapper SVG is `store-listing/<locale>/briefs/icon-512.svg` — full-bleed Ink1000 background with the brand mark centered at ~80%, ~10% margin so Play's rounded mask doesn't bite.

```bash
rsvg-convert -w 512 -h 512 \
  store-listing/es-AR/briefs/icon-512.svg \
  -o store-listing/es-AR/images/icon-512-es-AR.png

open store-listing/es-AR/images/icon-512-es-AR.png
```

If you want to reevaluate the composition (e.g. a Paper-background variant), create a sibling wrapper (`icon-512-paper.svg`), generate both PNGs, compare in Preview, and collapse back to a single canonical wrapper after the decision.

### Exporting the feature graphic (1024×500)

```bash
rsvg-convert -w 1024 -h 500 \
  store-listing/es-AR/briefs/feature-graphic.svg \
  -o store-listing/es-AR/images/feature-graphic-1024x500-es-AR.png

open store-listing/es-AR/images/feature-graphic-1024x500-es-AR.png
```

Verify visually — fonts, alignment, safe zone (~15% margin).

### Screenshots — captured, not exported from SVG

The 4 phone + 2 tablet screenshots come from the running app, not from SVG. Workflow:

```bash
./gradlew app:installDebug
adb shell am start -n com.github.barriosnahuel.vossosunboton/.LandingActivity
# Navigate to the screen described in the screenshots brief, then:
adb exec-out screencap -p > shot.png
```

Then compose the header strip (Ink1000 background + headline in Paper) on top in Inkscape or GIMP per the layout in `store-listing/<locale>/briefs/screenshots.md`. Store the final PNGs in `store-listing/<locale>/images/{phone,tablet-7,tablet-10}/`.

### Verifying every export

```bash
file store-listing/<locale>/images/*.png   # confirms dimensions and color depth
ls -lh store-listing/<locale>/images/*.png # confirms weight (Play caps icon at 1024 KB)
```

## Copy guide ✍️

Hard rules and brand-DNA invariants for user-facing copy live in `CLAUDE.md` § *Copy & localization*. This section collects the **pedagogical examples** — concrete calques we hit (and fixed) during real listings — so contributors writing or reviewing copy can recognise the failure modes without re-deriving them.

### Calque examples (en-US listing post-mortem)

Phrases that read natural in the source but are wrong in the target:

- **"save a day"** — calque of *salvar un día*. Correct English idiom: `save the day`. Caught during the en-US listing review.
- **"on the other side"** — calque of *del otro lado*. In English this means *the afterlife*; the phone-call idiom is `on the other end`.
- **"your audios"** — calque of *tus audios*. `audio` is a mass noun in English; the natural plural for voice messages is `voice notes` / `voice clips` / `voice memos`. The category descriptor is fine for ASO; the literal plural is not.

The general failure mode: phrases natural in source can be wrong in target. Match the register the locale expects — US English marketing → contractions, short sentences, imperatives, concrete nouns; Spanish-AR → voseo and warmth. Verify with a native speaker or a current idiom reference, not Google Translate.

### Reserved-term reminders

Before using any term from `../push-me-backlog/docs/brand-dna.md` or the Product Language glossary, verify it isn't reserved for a non-shipped feature:

- `Inmortal` / `immortal` — state descriptor for a Bompión synced via Saved Games SDK (Pro, **not shipped yet**). ❌ "Tus Bomps son inmortales" overstates Auto Backup. ✓ "Tus Bomps quedan respaldados en tu Google Drive".
- `Bompardo`, `Bompión` — Escala Richter levels 4 and 5, gated by share milestones. Don't apply to a generic Bomp.
- `Bomptástico` — internal telemetry only, never in UI.

### Read-aloud check

Before shipping any locale, read every paragraph aloud as a native speaker. Stumbles, false friends, weird tense, "wait, what?" reactions are blockers — fix before submitting.

## Error tracking 📡

Non-fatal exceptions go to Crashlytics via the `Tracker` wrapper at
`commons_android/.../error/Trackable.kt`. The canonical rules and rationale
live in [`CLAUDE.md` § Error tracking (non-fatals)](CLAUDE.md#error-tracking-non-fatals);
TL;DR for human contributors:

- Wrap the cause: `Tracker.track(RuntimeException("Static description of the failure", e))`. Keep the message stable — it becomes the Crashlytics issue title.
- Attach dynamic per-event context as a breadcrumb on the line right above `Tracker.track(...)`:
  `Tracker.log("module.field=value")`. Module is the feature directory (`share`, `addbutton`, `playback`, `about`, …). Use as many breadcrumbs as needed — one per key.
- Don't say "button" in messages or comments. These are "audio" internally, "Bomp" in user-facing copy.
- Verify locally with `adb logcat | grep -E "Tracker|FirebaseCrashlytics"` — the `log(...)` call should appear immediately before the `recordException(...)` line. Crashlytics DebugView surfaces the breadcrumb under the event detail panel.
- In unit tests that exercise a site that emits a breadcrumb, mock both methods (`every { Tracker.log(any()) } answers { nothing }`) and assert the contract with `verify(atLeast = 1) { Tracker.log(any()) }` alongside the existing track assertion. Don't assert exact breadcrumb text (overspecification). To capture the throwable for inspection, override the global stub: `every { Tracker.track(capture(slot)) } answers { nothing }`.

## Analytics events 📊

The app emits Firebase Analytics through the `AnalyticsTracker` wrapper at
`commons_android/.../analytics/`. Three sibling files form the catalogue:
`AnalyticsEvent` (sealed class, one subclass per custom event),
`CanonicalScreenName` (every `screen_view` literal), and
`AnalyticsUserProperty` (user property names + lifetime counter keys). The
full contract with rationale per event lives at
[`plans/04-firebase-analytics-core-funnel.md`](https://github.com/barriosnahuel/bomp-backlog/blob/main/plans/04-firebase-analytics-core-funnel.md)
in the sibling backlog repo.

Firebase auto-tracking of `screen_view` is **disabled** in
`AndroidManifest.xml` so reports never get coupled to class names that break
on refactor — every screen calls `tracker.logScreen(CanonicalScreenName.X)`
manually.

### Naming rules

- `snake_case` lowercase. Reserved Firebase event names are forbidden — check
  <https://firebase.google.com/docs/analytics/events> before naming.
- **Length limits.** User property names ≤ **24** chars; event and param names
  ≤ 40. Firebase drops an over-length name silently (`E/FA Name is too long…`)
  so it never reaches BigQuery — invisible until someone reads logcat.
  `AnalyticsUserPropertyNameTest` enforces the user-property bound at build time.
- Describe the **product fact**, not the **UI mechanic**: `about_credits_open`,
  not `about_credits_expand`.
- `screen_view` for full destinations; custom events for discrete actions.
  Never create a custom event for "user opened screen X" when a `screen_view`
  covers it.
- No `_intent` / `_done` suffixes — `screen_view` IS the intent signal, the
  custom event firing IS the done signal.
- `_open` only for in-screen reveals without a dedicated destination
  (`about_credits_open`, `about_license_open`).
- `first_*` is emitted by the wrapper when the event declares
  `hasFirstVariant = true` — never reference it from a call-site.
- `milestone_*` for one-shot numeric thresholds (the call-site gates emission
  via `tracker.markFiredOnce(...)`).
- `surface` param on actions that fire from multiple UI entry points
  (`sound_play`, `share`). Values MUST match `CanonicalScreenName` — when a
  new surface ships, add it to both lists in lockstep.
- No PII in params. Lengths, counts, booleans derived from input are fine
  (`name_length`, `query_length`); the literal text is not.
- Prefer Firebase recommended events (`share`, `select_content`, …) over
  custom when the semantics match — gives access to pre-built reports and
  BigQuery schema compatibility.

### When adding a new tracked action

1. Decide: full screen → `tracker.logScreen(CanonicalScreenName.X)` and add
   the constant. Discrete action → add an `AnalyticsEvent` subclass; decide
   `hasFirstVariant`.
2. Update the event table in plan 04 §4.2 (sibling backlog repo).
3. Add a wrapper-level test in `commons_android/src/test/.../analytics/`.
4. Add the simple class name (or screen name) to
   `EVENTS_WITH_REGRESSION_TEST` / `SCREEN_VIEWS_WITH_REGRESSION_TEST` /
   `USER_PROPERTIES_WITH_REGRESSION_TEST` in `AnalyticsCoverageMatrixTest`.
   The meta-test fails until you do — that is the regression net working.
5. Add at least one call-site test that triggers the action and asserts via
   `FakeAnalyticsTracker.assertEmitted(...)` / `.assertScreenView(...)`.
   Tests pull the fake via `testImplementation testFixtures(project(":commons_android"))`.
6. **Manual smoke before merging** — required, see below. The aggregated
   `Reports → Engagement / Events / Realtime` dashboards are NOT a substitute
   (24–48 h delay, never confirm a single new event).

### Manual verification

Two complementary paths. DebugView for params + user properties; logcat for
"did the SDK fire it at all?". Run BOTH the first time you add a track.

```bash
# Firebase DebugView (per device, runtime-only — phone reboot resets it)
adb shell setprop debug.firebase.analytics.app com.github.barriosnahuel.vossosunboton.debug
adb shell am force-stop com.github.barriosnahuel.vossosunboton.debug
# Then exercise the track and watch Firebase Console → DebugView (10–30 s).

# Local logcat (immediate)
adb shell setprop log.tag.FA VERBOSE
adb shell setprop log.tag.FA-SVC VERBOSE
adb logcat -s FA FA-SVC
```

## BigQuery export 🗄️

`bomp-prod` exports Crashlytics, Analytics, and Performance to BigQuery (`us` multi-region, daily, no streaming) so we can query accumulated history with SQL — useful for post-mortem on crashes that no longer fit the 90-day Crashlytics retention, or for pivot tables across events that GA4 Explorer makes painful.

**Scope: release only.** `bomp-debug` does not export. Debug builds intentionally crash on StrictMode violations (see `CLAUDE.md` § *StrictMode debug audit*); exporting that noise to BQ would pollute queries and waste daily-export quota.

### Tooling install (one-time, macOS)

```bash
brew install --cask google-cloud-sdk
```

Then authenticate. The login opens a browser — run it via the `!` prefix in Claude Code so the output lands in the conversation:

    > ! gcloud auth login
    > gcloud config set project bomp-prod
    > bq ls --project_id=bomp-prod

### Expected datasets

After enabling the BQ export, the first daily run lands ~24 h later. `bq ls --project_id=bomp-prod` should then list:

| Dataset | Tables |
|---|---|
| `firebase_crashlytics` | `com_github_barriosnahuel_vossosunboton_ANDROID` (one row per non-fatal/fatal; `event_timestamp`, `issue_id`, stack trace, sessions when enabled) |
| `analytics_<GA4_PROPERTY_ID>` | `events_YYYYMMDD` (one row per event, params nested) — property ID is numeric, distinct from project ID, visible only after the first dataset materializes |
| `firebase_performance` | `com_github_barriosnahuel_vossosunboton_ANDROID` — one row per perf event; `event_type` ∈ {`DURATION_TRACE` (incl. `_app_start`), `SCREEN_TRACE` (`_st_<Activity>`, carries `screen_info.slow_frame_ratio` / `frozen_frame_ratio`), `TRACE_METRIC`, `NETWORK_REQUEST`}; segment by `app_build_version` (versionCode) + `device_name`. No cold/warm/hot dimension. |

### Sanity-check query

```sql
SELECT COUNT(*) AS issue_count
FROM `bomp-prod.firebase_crashlytics.com_github_barriosnahuel_vossosunboton_ANDROID`
WHERE event_timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY)
```

Returns `0` early on if no crashes — that's a feature, not a bug.

### When to use BQ instead of the Console

- The Crashlytics dashboard caps per-issue stack traces at the most recent N occurrences. BQ retains all of them.
- Cross-cutting questions (e.g. "of users who hit `StrictModeViolation`, how many later opened the share flow?") need a SQL JOIN across the Crashlytics + Analytics datasets — only possible because both datasets live in the same `us` region (cross-region JOINs incur egress).
- Performance regressions across releases — easier to diff trace medians via `WITH` clauses than to flip between Console screens. Before treating a weekly-report perf alarm as a code bug, run the **`/perf-report-triage`** skill (`.claude/skills/perf-report-triage/`). Past investigations: `docs/perf-investigations/`.

The Console is still right for: real-time DebugView, alert configuration, single-issue triage. BQ is for *retrospectives* across N events / users / days.

### Stack frames come R8-obfuscated — read the trace in Crashlytics, not BQ

Frames carry `blame_frame.file` = `r8-map-id-…` (a mapping UUID, not a source path) and R8-renamed symbols (`ki2.q`, `et2.n`). Crashlytics — Console **and** the Firebase MCP (§ *Local setup*) — de-obfuscate server-side, **but only if the R8 mapping for that build was uploaded to Firebase.**

**Today it usually isn't.** CI excludes the mapping upload (§ *Release builds*) and release builds haven't been reliably uploading it, so shipped crashes read `r8-map-id-…` frames **even in Crashlytics / the MCP** — verified live on the 2.1.0 `sounds_json` issue: `at x83.a(r8-map-id-…:42)`. Only the exception **message** is human-readable (a static `Tracker.track` wrapper message — which is the *only* reason that crash was diagnosable). So the fix is **not** "use the MCP instead of BQ" — it's making every release **upload + verify** its mapping (§ *Release builds* → *Creating the GitHub release*). Once the mapping is in Firebase, the Console/MCP show real frames; the BQ export never deobfuscates regardless.

Tooling division (once mappings are present): **reading a single crash's stack** → Console or Firebase MCP (the agent-accessible path); **aggregation / trends / JOINs** → BQ.

**Wiring the Firebase MCP (one-time, per-user — not committed):**

```bash
npm install -g firebase-tools
claude mcp add -s local firebase -- firebase experimental:mcp --dir .
firebase login          # interactive (browser) — run via the ! prefix in Claude Code
```

Restart Claude Code so it loads the server's tools. The MCP auto-detects the Android Crashlytics SDK and exposes read tools (`crashlytics_get_report`, `crashlytics_batch_get_events`, `crashlytics_get_issue`, `crashlytics_list_events`, …) that return frames **deobfuscated when the mapping is present** (else still `r8-map-id-…`). `firebase login` is per-user auth, like the CircleCI token (§ *Continuous Integration*).

Those tools need the **Firebase App Id** (not a secret — it ships inside the APK; the secret is the API key in `google-services.json`):

| Project | App Id | Package |
|---|---|---|
| `bomp-prod` (release) | `1:383291838647:android:8f96908ee8b49821da3d32` | `com.github.barriosnahuel.vossosunboton` |
| `bomp-debug` (debug) | `1:947596384148:android:1ecfa2fa66603e42e52181` | `com.github.barriosnahuel.vossosunboton.debug` |

`bomp-debug` does not export to BQ and rarely has real crashes; for crash triage use the `bomp-prod` App Id.

## Labels & milestone examples 🏷️

The *rule* (one type label + optional concern labels; how to derive the milestone from the CHANGELOG) and the bare label names live in CLAUDE.md § *Labels and milestone*. Full "when to use" per label, then worked combinations:

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
| `c:i18n` | Localization: translations, locale-aware copy, adding/maintaining a locale (a new locale's store listing is `c:i18n` + `c:aso`) |
| `c:aso` | App Store Optimization: store-listing copy, feature graphic, screenshots, keywords, custom listings — the store *presence* itself, in any locale |
| `c:observability` | Analytics instrumentation, Crashlytics, logging, BigQuery |
| `c:dependencies` | Library / plugin / Gradle-wrapper version bumps (auto-applied by Dependabot with `a:build`) |

### Issues and lifecycle

| Label | When to use |
|---|---|
| `a:bug` | Issue reporting something broken |
| `a:feature-request` | Issue requesting functionality not yet implemented |
| `stale` | No recent activity — candidate for closing |

### Worked combinations

- WCAG contrast fix: `a:fix` + `c:accessibility`
- New screen with localized copy: `a:feature` + `c:i18n`
- URI validation hardening: `a:fix` + `c:security`
- AAB size optimization: `an:enhancement` + `c:performance`
- Dependabot bump: `a:build` + `c:dependencies` (auto-applied)
- New Firebase Analytics event: `a:build` + `c:observability`
- Flaky test stabilization: `a:test`
- README rewrite: `a:docs`
- Seasonal custom store listing (existing locale): `a:docs` + `c:aso`
- New locale's full store listing: `a:docs` + `c:i18n` + `c:aso`

## Third-party notices 📜

`app/src/main/res/raw/app_third_party_notices.txt` lists all runtime dependencies with their license attribution. **Update it whenever you add or remove a runtime dependency** (`implementation`, not `testImplementation` or `debugImplementation`).

Each entry follows this format:

```
--------------------------------------------------------------------------------
Library Name
Copyright (C) Author
License Name
https://project-url
--------------------------------------------------------------------------------
```

## License 📄

This project is licensed under the **GNU Affero General Public License v3.0 (AGPLv3)**.
By submitting a pull request you agree that your contribution will be distributed under
the same AGPLv3 terms. See [LICENSE](LICENSE) for the full text.

All `.kt` source files must include the AGPLv3 copyright header. Run `./gradlew spotlessApply`
to apply it automatically to any new files you add — CI will reject files without it.

When adding a runtime dependency, also update `app/src/main/res/raw/app_third_party_notices.txt`
with the library name, copyright holder, license, and project URL.