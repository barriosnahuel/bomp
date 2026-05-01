# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog][], and this project adheres to [Semantic Versioning][].

## \[unreleased] (v2.0.0)
### Removed
- Bundled audio files removed from version control; bottom navigation bar hidden in release builds (Explore tab only appears in debug when audio files are manually placed)
- About screen pronunciation block (`/sohs oon boh-TOHN/`) removed alongside the rename to Bomp

### Changed
- Replaced the launcher icon with the Bomp brand: Acid (Neo-Club signal yellow-green) container with an Ink play triangle, plus adaptive and themed (Material You) icon support. The organic blob brand mark stays on web surfaces (favicon, wordmark, feature graphic) where there is no system mask
- Added a branded launch screen showing the Bomp brand mark on a theme-aware background (Paper in light mode, Ink in dark) — Android 12+ system splash plus core-splashscreen backport for API 23-30
- Renamed app to **Bomp**: launcher label, About screen heading and Play Store listing now align with the canonical brand. Old name `Sos Un Boton` retired
- Enabled Gradle configuration cache to speed up incremental builds and CI runs
- Promoted the Add Button flow from a `:feature_addbutton` dynamic feature module into the core `:app` module (creating buttons is core, not a freemium add-on). Eliminates the bundletool wrapper script and lets the local UI test suite run as plain `./gradlew app:connectedDebugAndroidTest`
- Deeplink `push-me://open/explore` now falls back to Home when no bundled sounds are available (release builds and debug checkouts without the bundled `raw/` directory), avoiding a blank Explore tab
- Search overlay's clear-query icon now announces "Clear search" (was "Close search", which collided with the back-arrow's label and confused screen readers)
- Swipe-to-delete haptic feedback on custom sounds changed from double-pulse (reject) to single-pulse (confirm), matching the pin gesture — a successful delete is a confirmed action
- Edit screen audio preview now shows total duration and date added, matching the home card layout
- Normalized top-bar-to-content spacing to 16 dp across all screens
- Replaced the old violet/rose/amber palette with the Neo-Club ink × acid design system across all screens (top bars, cards, FAB, search, swipe actions)
- Play button is now the primary visual action within each card: acid-filled circle with a halo when playing; share icon demoted to secondary weight
- Sound cards now have a subtle 1dp hairline border for visual separation on dark and light backgrounds

### Added
- Acknowledged the first-audios collaborators (Fede, Juli, Mati, Tincho) as the first card inside About's Third-party credits, replacing the previously empty Made with section
- Privacy Policy and Data Safety links added to the About screen's new "Legal & Privacy" section, opening the published pages with `?hl=` matched to the device locale (es-AR, es-419, es-ES, en, pt-BR)
- Initial Google Play Store listing assets in es-AR and en-US under `store-listing/`: title, short and full description (with zero-friction onboarding and Auto Backup to Drive featured as headline differentials, and a stewardship-framed close that respects Data Safety policy), what's new, brand mark SVG, design briefs for icon, feature graphic, five phone screenshots ordered for ASO impact (UI hero, brand manifesto, search, playing, emotional close) and preview video
- Firebase Analytics instrumentation: emits events for every active user flow (add, edit, delete, play, pin, search, share, about) plus 6 canonical `screen_view` names (`my_sounds`, `explore_sounds`, `about`, `search_sound`, `add_sound`, `edit_sound`) with auto-tracking disabled, one-shot `first_*` variants on first invocation, and user properties (`current_sounds`, `current_pinned`, `lifetime_shares`, `lifetime_plays`) that unlock cohort segmentation in Firebase Console
- Renamed the first bottom-nav tab from "Inicio"/"Home" to "Mis audios"/"My Sounds" so the visible label matches the canonical telemetry `screen_name`
- Bundled sounds (Explore tab) can now be pinned to the top via button tap or swipe right, with the pinned state persisting across app restarts
- Swiping left or long-pressing a bundled sound gives haptic rejection feedback (double-pulse), indicating those actions are not available
- Pinning any sound now automatically scrolls the list back to the top so the newly pinned item is immediately visible
- About screen redesigned as a brand manifesto: audio branding button (plays official push-me sound), AI co-pilot attribution (Gemini and Claude), conditional collaborators section, and full-width legal buttons — WCAG AAA accessible (18 sp body text, 56 dp touch targets, 7:1+ contrast across all text roles)
- About screen: version info, license viewer (AGPLv3), third-party credits, and source code link — accessible from the top-bar overflow menu
- AGPLv3 copyright header on all source files, enforced by Spotless
- Swipe right on any custom sound to pin/favorite it (PrimaryContainer background); swipe left to delete (ErrorContainer background) — replaces the old single-direction swipe
- Edit custom sounds: long-press any card to rename it; the existing audio stays, only the name changes
- Sorting: custom sounds now order by date added (newest first), with alphabetical fallback for bundled sounds
- Haptic feedback on swipe actions: CONFIRM pulse for pin, REJECT double-pulse for delete (API 30+)
- Delete animation: removed items scale and fade to zero before the list collapses ("Void Shrink")
- List reflow: remaining items animate smoothly into place after any removal (animateItem)
- Edit button screen: reuses the Add Button flow with pre-loaded name, audio preview, and "Save changes" CTA
- Search overlay: tap the new FAB to search across all tabs at once; results show the same play/favorite/share/delete actions as the main list plus a subtle origin badge
- Favorites: users can mark/unmark any custom button as favorite; a dedicated Favorites tab lists only marked buttons
- Deeplinks: the app responds to `push-me://open/home`, `push-me://open/favorites`, and `push-me://open/explore` to navigate directly to a tab
- Back navigation now follows the actual tab history instead of always returning to Explore
- Jetpack Compose UI replacing all Fragments, RecyclerViews and XML layouts.
- `SoundsViewModel` with `StateFlow` for reactive UI state management.
- Back press navigates from Home/Favorites to Explore instead of exiting the app.
- Tapping the already-selected bottom tab scrolls the list back to the top.
- Custom audio files and their metadata are backed up and restored via Android Auto Backup (cloud backup and device transfer on all supported API levels)
- Tests for `SoundsViewModel` covering playback state and delete/restore flows.

### Changed
- Migrated from `material-icons-extended` to `material-icons-core` following Material 3 1.4.0; icons absent from core (`Pause`, `ViewComfyAlt`) are now local `ImageVector` definitions copied verbatim from the extended sources to preserve visual consistency
- Resolved all Kotlin compiler and Gradle DSL deprecation warnings for a clean build log
- `SoundsViewModel` now loads sounds on a background thread (`Dispatchers.IO`) to avoid blocking the main thread on startup.
- Centralised all dependency and plugin versions in `gradle/libs.versions.toml` (Gradle version catalog).
- Replaced placeholder color palette with a full WCAG 2.2 AA–compliant brand identity (Deep Violet / Vivid Rose / Amber) covering all Material3 color roles for light and dark modes.
- TopAppBar on the home screen and Add Button screen now shows the brand primary color instead of the default surface color.
- NavigationBar and sound cards now use explicit brand-palette colors instead of derived dark defaults, restoring visual distinction in both light and dark modes.
- Fixed swipe-to-delete background bleeding through card padding when no swipe is in progress.
- Re-enabled resource shrinking (`shrinkResources true`) now that AGP 9 resolves the long-standing incompatibility with dynamic feature modules.
- Upgraded stack: AGP 9.1.1, Gradle 9.3.1, compileSdk android-37.0, targetSdk 37, Java 21; migrated to AGP built-in Kotlin compilation (removed `kotlin-android` plugin).
- Bumped all dependencies to latest stable: Firebase BOM 34.12.0, Compose BOM 2026.03.01, lifecycle 2.10.0, material 1.13.0, Robolectric 4.16.1, MockK 1.14.9, Firebase perf-plugin 2.0.2, ktlint-gradle 14.2.0, KTLint 1.6.0, and more.
- `minSdk` raised to 23 (Android 6.0).
- KTLint migrated to JLLeitschuh plugin with KTLint 1.5.0.
- Sound list is now sorted alphabetically across all tabs.
- Share sheet filename for bundled sounds now shows the button name instead of an internal prefix.
- Custom button audio files are now named after the user-provided sound name (non-alphanumeric chars replaced by underscores) instead of the generic "Button-custom-" prefix.

### Fixed
- Validate inbound audio URIs by scheme, MIME type, and size before importing
- Restrict deep link routing to a known path allowlist with a safe fallback to My Sounds
- The app now opens the Home tab on launch instead of the Search tab
- After saving a button via the share intent, the app now navigates to the Home tab and shows a "Saved!" confirmation Snackbar instead of silently returning to the previous app
- The Add Button screen no longer appears in the recent apps tray after saving
- Custom sound stops playing immediately when deleted instead of continuing until the track ends
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
