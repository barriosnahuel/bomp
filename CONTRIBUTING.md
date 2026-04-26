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
- [Store listing](#store-listing-)

## Local setup ⚙

1. Clone/Fork this repo.
2. Replace the `app/google-services.json` file with the one from Firebase console. You *won't* be able to commit changes on this file.
3. Check how to prevent modifications at #Firebase config file
4. Run:
    > ./gradlew check

    It must return **`BUILD SUCCESS`**.

## Directory structure 🎄
- [app/](/app) Android application module which depends on all other submodules to be the great app you're building.
- [commons_android/](/commons_android) Android library module for Android-related foundation staff.
- [commons_file/](/commons_file) Android library module for File handling staff.
- [config/](/config) contains code analyzers configuration files.
- [feature_addbutton/](/feature_addbutton) Android Feature module containing all code and resources required in order to let users add a new
button.
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

## License 📄

This project is licensed under the **GNU Affero General Public License v3.0 (AGPLv3)**.
By submitting a pull request you agree that your contribution will be distributed under
the same AGPLv3 terms. See [LICENSE](LICENSE) for the full text.

All `.kt` source files must include the AGPLv3 copyright header. Run `./gradlew spotlessApply`
to apply it automatically to any new files you add — CI will reject files without it.

When adding a runtime dependency, also update `app/src/main/res/raw/app_third_party_notices.txt`
with the library name, copyright holder, license, and project URL.