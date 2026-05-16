# Welcome! 🙌

*Here are some useful notes when making changes on this project.*

But, before going deeper I suggest you to take a look to the [opensource.guide](https://opensource.guide/), there are many things to learn from there! 😃

## Table of contents 📋
- [Local setup](#local-setup-)
- [Directory structure](#directory-structure-)
- [Debugging tools](#debugging-tools-)
- [Continuous integration](#continuous-integration-)
- [Testing](#testing-)
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

## Continuous Integration ➿
We use Circle CI, so if you're gonna change the [config.yml](.circleci/config.yml) file you can check the config using the local CLI.
- https://circleci.com/docs/2.0/local-cli

> circleci config validate

### Instrumented UI tests are local-only
The instrumented suite under `app/src/androidTest/` is **intentionally not run on CircleCI**. It needs a booted emulator and is meant to replace manual end-to-end QA on the contributor's machine. Setup, run commands, and synchronization helpers live in [Testing → Local UI test suite](#testing-); rationale lives in [ADR 0001](docs/adr/0001-local-ui-test-suite.md).

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

The trigger lives in CLAUDE.md § *Features — test coverage workflow*. The minimum scenario set you agree on before writing production code:

1. **Happy path** — works as intended under normal conditions.
2. **Failure modes at system boundaries** — audio I/O, MediaPlayer errors, permissions denied, Play feature delivery failures.
3. **Smoke test** — see CLAUDE.md § *Activity smoke tests*.

Implement tests **alongside** the feature, not after. Any scenario not listed before starting is out of scope for the current PR — note in the PR description.

Skip a scenario only when it lives exclusively in platform wiring not exercisable by unit / Robolectric tests. Note why.

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

**Functional changes also require the local UI test suite** (see § *Local UI test suite → When to run* below). If the change touches Composables, ViewModels, intents, navigation, deep links, or persistence — run `./scripts/run-instrumented-tests.sh` before pushing (it cold-boots the emulator; never run the Gradle task directly against a warm AVD). CircleCI does not execute it. Cosmetic-only changes (CHANGELOG, copy strings, README, comments) are exempt.

### Test assertions

Bare `kotlin.assert(...)` is forbidden in test sources — it's a silent no-op without JVM `-ea`. Use Truth (`assertThat`), JUnit (`assertEquals` / `assertTrue` / `assertNotNull`), or the Compose UI Test API (`assertCountEquals`, `assertIsDisplayed`). Full rationale and the incident that prompted the rule (PR #1117) live in [ADR 0006](docs/adr/0006-no-kotlin-assert-in-tests.md). Enforced by the CircleCI `test-assertion-guard` job and by `scripts/check-adr-invariants.sh`. Run the same check locally before pushing:

```bash
grep -rnE '(^|[^[:alnum:]_])assert[[:space:]]*\(' --include='*.kt' \
    app/src/test app/src/androidTest \
    commons_android/src/test commons_file/src/test model/src/test
```

Empty output = clean. Any hit is a hard failure — fix the call-site, do not add an exclusion.

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

The wrapper cold-boots the AVD with wiped userdata, waits for it, then runs `./gradlew :app:connectedDebugAndroidTest` against that emulator only (it pins the serial, so a physical device attached at the same time is ignored).

**Always go through the wrapper — do not run `./gradlew :app:connectedDebugAndroidTest` against an already-running emulator.** A warm emulator degrades across back-to-back runs (`system_server` watchdog ANRs, hundreds of skipped frames), which surfaces as `ComposeTimeoutException` / `ComposeNotIdleException` flakes or an outright `Process crashed`. A cold boot resets that — a clean run finishes in ~3 min; a degraded one takes 15+ min or never completes. Rationale: [ADR 0001 § *Cold boot per run*](docs/adr/0001-local-ui-test-suite.md).

To hunt flakes, run several cold-booted passes in a row:

```bash
RUNS=3 ./scripts/run-instrumented-tests.sh
```

HTML report: `app/build/reports/androidTests/connected/debug/index.html`. Raw XML: `app/build/outputs/androidTest-results/connected/debug/`.

#### Run a single test class

Any extra arguments are passed straight through to Gradle:

```bash
./scripts/run-instrumented-tests.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=com.github.barriosnahuel.vossosunboton.ui.home.SearchOverlayTest
```

#### Synchronization

The synchronization rule (when to use `awaitNode*` helpers vs. bare `waitForIdle()`) lives in `CLAUDE.md` § *Local UI test suite*. The `awaitNode*` helpers themselves live in `app/src/androidTest/.../ComposeTestExtensions.kt` (rationale in KDoc).

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

Empty output means every detected violation matched a `KnownThirdPartyViolation` entry — none reached Crashlytics either. Anything that does show up either has a top frame in our package (`com.github.barriosnahuel.vossosunboton.*`) and needs fixing, or comes from a new SDK / framework version that should be added to the matcher list. The decision tree is in `CLAUDE.md` § "StrictMode debug audit".

## Resources 🎨
- **Color palette:** Neo-Club (ink × acid), a custom palette designed for Bomp. Single source of truth is [`app/src/main/java/com/github/barriosnahuel/vossosunboton/ui/theme/AppTheme.kt`](app/src/main/java/com/github/barriosnahuel/vossosunboton/ui/theme/AppTheme.kt) — see `CLAUDE.md` § "Design system" for the role mapping and contrast guarantees.
- **Launcher icon:** rendered from the SVG masters under [`store-listing/brand/`](store-listing/brand/) (`launcher-fallback.svg` for Android < 8; the adaptive vector at [`app/src/main/res/mipmap-anydpi-v26/app_ic_launcher.xml`](app/src/main/res/mipmap-anydpi-v26/app_ic_launcher.xml) for Android 8+). Export pipeline (`rsvg-convert`) is documented in `CLAUDE.md` § "Store listing asset generation".
- In-App icons using: [Material Symbols](https://fonts.google.com/icons)

## Signing 🔑

The following files must be located into the root dir:
- `nahuelbarrios.keystore-appbundle.pkcs12`
- `secure.properties`

## Release builds 📦

Release-only Gradle commands (need the signing files above in the project root):

```bash
./gradlew app:lintVitalRelease   # Android lint, release variant — the release lint gate
./gradlew app:bundle             # Build the AAB for Play Store upload
```

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
- In unit tests that exercise a site that emits a breadcrumb, mock both methods (`every { Tracker.log(any()) } answers { nothing }`) and assert the contract with `verify(atLeast = 1) { Tracker.log(any()) }` alongside the existing track assertion.

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
| `firebase_performance` | exact tables to confirm post-export |

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
- Performance regressions across releases — easier to diff trace medians via `WITH` clauses than to flip between Console screens.

The Console is still right for: real-time DebugView, alert configuration, single-issue triage. BQ is for *retrospectives* across N events / users / days.

## Labels & milestone examples 🏷️

The label / milestone *rules* (which label means what; how to derive the milestone from the CHANGELOG) live in CLAUDE.md § *Labels and milestone*. The combinations below illustrate the rules in practice:

- WCAG contrast fix: `a:fix` + `c:accessibility`
- New screen with localized copy: `a:feature` + `c:i18n`
- URI validation hardening: `a:fix` + `c:security`
- AAB size optimization: `an:enhancement` + `c:performance`
- Dependabot bump: `a:build` + `c:dependencies` (auto-applied)
- New Firebase Analytics event: `a:build` + `c:observability`
- Flaky test stabilization: `a:test`
- README rewrite: `a:docs`

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