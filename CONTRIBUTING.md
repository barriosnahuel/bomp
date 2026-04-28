# Welcome! 🙌

*Here are some useful notes when making changes on this project.*

But, before going deeper I suggest you to take a look to the [opensource.guide](https://opensource.guide/), there are many things to learn from there! 😃

## Table of contents 📋
- [Local setup](#local-setup-)
- [Directory structure](#directory-structure-)
- [Debugging tools](#debugging-tools-)
- [Continuous integration](#continuous-integration-)
- [Gradle upgrade](#gradle-upgrade)
- [Firebase config file](#firebase-config-file-)
- [Backup & restore testing](#backup--restore-testing-)
- [Logcat](#logcat-)
- [Resources](#resources-)
- [Signing](#signing-)
- [Bundled audio files](#bundled-audio-files-)
- [Store listing](#store-listing-)
- [Analytics events](#analytics-events-)

## Local setup ⚙

1. Clone/Fork this repo.
2. Replace the `app/google-services.json` file with the one from Firebase console. You *won't* be able to commit changes on this file.
3. Check how to prevent modifications at #Firebase config file
4. Run:
    > ./gradlew check

    It must return **`BUILD SUCCESS`**.

## Directory structure 🎄
- [app/](/app) Android application module which depends on all other submodules to be the great app you're building. The Add Button flow lives at `app/src/main/java/com/github/barriosnahuel/vossosunboton/feature/addbutton/`.
- [commons_android/](/commons_android) Android library module for Android-related foundation staff.
- [commons_file/](/commons_file) Android library module for File handling staff.
- [config/](/config) contains code analyzers configuration files.
- [gradle/wrapper/](/gradle/wrapper) contains Gradle's binary in order to be able to run this project everywhere.
- [model/](/model) Android library module containing our business logic.
- [store-listing/](/store-listing) contains all listing related files, like GIMP files to edit screenshots.

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
The instrumented suite under `app/src/androidTest/` is **intentionally not run on CircleCI**. It needs a booted emulator and is meant to replace manual end-to-end QA on the contributor's machine. Run it before pushing functional changes — see the *Local UI test suite* section in `CLAUDE.md` and [ADR 0001](docs/adr/0001-local-ui-test-suite.md) for the rationale.

## Platform upgrades
### API Level
⚠️ Remember to change not only the compile/target API levels but the tests config too. Check [`AbstractRobolectricTest`](/app/src/test/java/com/github/barriosnahuel/vossosunboton/AbstractRobolectricTest.kt).

### Gradle upgrade
As described at [Gradle docs#Adding wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:adding_wrapper) you must run:

    > ./gradlew wrapper --gradle-version ${desiredVersion}

## Firebase config file ⚙️

To prevent future modifications on `app/google-services.json` I run:

    >  git update-index --skip-worktree app/google-services.json

    To revert this just:

    > git update-index --no-skip-worktree app/google-services.json

## Backup & restore testing 💾

To manually verify Auto Backup saves and restores custom sound metadata, use `bmgr` via `adb`:

1. **Trigger a backup:**

       adb shell bmgr backupnow com.github.barriosnahuel.vossosunboton.debug

2. **List available backup sets** (to find the restore token):

       adb shell bmgr list sets

3. **Restore from a backup set:**

       adb shell bmgr restore <token> com.github.barriosnahuel.vossosunboton.debug

> Requires a device or emulator with Google Mobile Services. Does not work on stock AOSP emulators.

## Logcat 😿

### Android Studio: Remove all dev tools (*a.k.a. !Dev Tools*)

| Field     | REGEXP |
| --        | -- |
| TAG       | `^(?!(?:FirebasePerformance|FA|LeakCanary|FirebaseRemoteConfig|zygote|Choreographer|OpenGLRenderer|Adreno|vndksupport|SoLoader|ApkSoSource)$).*$` |
| Package   | `com.github.barriosnahuel.vossosunboton` |

### Terminal: Only Firebase Performance Monitoring 💯

You can filter logcat messages by:

> adb logcat -s FirebasePerformance

## Resources 🎨
- Colors selected using: [Material Design palette generator](https://material.io/design/color/the-color-system.html#tools-for-picking-colors)
- Icon generated using: [romannurik.github.io/AndroidAssetStudio/icons-launcher](http://romannurik.github.io/AndroidAssetStudio/icons-launcher.html)
- In-App icons using: [Material Design resources](https://material.io/resources/icons/?style=round)

## Signing 🔑

The following files must be located into the root dir:
- `nahuelbarrios.keystore-appbundle.pkcs12`
- `secure.properties`

## Bundled audio files 🔊

The following directory must be populated manually after cloning — it is not version-controlled:

```
model/src/debug/res/raw/
```

Place the bundled `.mp3` and `.ogg` files there. Without them the debug build still compiles and runs, but the **Explore** tab will be empty. Release builds are unaffected.

## Store listing 📄

As mentioned before, under [store-listing/](/store-listing) there are the assets for the store listing and the original GIMP files to edit those assets.

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

## License 📄

This project is licensed under the **GNU Affero General Public License v3.0 (AGPLv3)**.
By submitting a pull request you agree that your contribution will be distributed under
the same AGPLv3 terms. See [LICENSE](LICENSE) for the full text.

All `.kt` source files must include the AGPLv3 copyright header. Run `./gradlew spotlessApply`
to apply it automatically to any new files you add — CI will reject files without it.

When adding a runtime dependency, also update `app/src/main/res/raw/app_third_party_notices.txt`
with the library name, copyright holder, license, and project URL.