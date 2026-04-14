# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog][], and this project adheres to [Semantic Versioning][].

## \[unreleased]
### Added
- Jetpack Compose UI replacing all Fragments, RecyclerViews and XML layouts.
- `SoundsViewModel` with `StateFlow` for reactive UI state management.
- Back press navigates from Home/Favorites to Explore instead of exiting the app.
- Tapping the already-selected bottom tab scrolls the list back to the top.
- Explicit backup rules for Android 12+: user audio files are now included in cloud backup and device transfer.
- Tests for `SoundsViewModel` covering playback state and delete/restore flows.

### Changed
- Replaced placeholder color palette with a full WCAG 2.2 AA–compliant brand identity (Deep Violet / Vivid Rose / Amber) covering all Material3 color roles for light and dark modes.
- TopAppBar on the home screen and Add Button screen now shows the brand primary color instead of the default surface color.
- NavigationBar and sound cards now use explicit brand-palette colors instead of derived dark defaults, restoring visual distinction in both light and dark modes.
- Fixed swipe-to-delete background bleeding through card padding when no swipe is in progress.
- Upgraded stack: AGP 8.13, Kotlin 2.2.21, Gradle 8.13, compileSdk/targetSdk 37, Java 21.
- Bumped all dependencies to latest stable: Firebase BOM 34.12.0, Compose BOM 2026.03.01, lifecycle 2.10.0, material 1.13.0, and more.
- `minSdk` raised to 23 (Android 6.0).
- KTLint migrated to JLLeitschuh plugin with KTLint 1.5.0.
- Sound list is now sorted alphabetically across all tabs.
- Share sheet filename for bundled sounds now shows the button name instead of an internal prefix.

### Fixed
- Bundled sounds no longer offer a swipe-to-delete gesture; the action is simply not available.
- Scrolling-induced ghost playing state fixed by migrating to Compose state-driven rendering.
- Crash when switching tabs mid-playback fixed by ViewModel-owned player state.
- `MediaPlayerHelper` dead code path using the deprecated `MediaStore.Audio.Media.DATA` column removed.

### Removed
- Flipper and LeakCanary debug tools.
- `dexcount` Gradle plugin.
- All Fragment/RecyclerView/ViewBinding UI code.
- `WRITE_EXTERNAL_STORAGE` permission (no longer needed with scoped storage).
- Multidex support (not needed above minSdk 21).

## \[v1.1.0] - 2017-08-16

### Added
- **Share saved audios after long press.** ![bitmoji](https://render.bitstrips.com/v2/cpanel/8363918-196115675_6-s4-v1.png?transparent=1&palette=1&width=246)
- Support for Android O (API level 26).
- Static code checks to improve code quality using SCA.
- Setup release signing certificate.
- Test tools for the debug version: Stetho, different applicatonId and icon, Leak Canary.

### Fixed
- Performance when scrolling.

### Changed
- Migrated to Gradle wrapper v4.1 as well as Android build tools.
- Refactor.

## \[v1.0.1] - 2017-08-16

### Fixed
- Support for Content URI format when creating new buttons.

## \[v1.0.0] - 2017-08-16
### Added
- Predefined audio messages.
- Save audio messages directly from WhatsApp by just sharing them.

[keep a changelog]: https://keepachangelog.com/en/1.0.0/
[semantic versioning]: https://semver.org/spec/v2.0.0.html
