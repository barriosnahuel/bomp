# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog][], and this project adheres to [Semantic Versioning][].

## \[v2.1.0] - 2026-05-25

### Added
- New **Vault** tab in the bottom navigation, grouping your private collections behind your fingerprint or screen lock. The "Baúl" lives there by default and you can create more private collections for specific people, moments, or memories. Audios tagged to a private collection only play from there: tapping play opens an immersive, full-screen listen mode — waveform, the date and name, a single play/pause control, and no share button
- On devices with no screen lock set, the Vault stays usable and its unlock screen now offers a one-tap shortcut to Android's screen-lock setup so you can protect it
- Public **collections** for organizing your Bomps by context (Family, Work, Recipes…). A filter chip row at the top of My Sounds lets you narrow the list to one collection at a time — the last chip you used persists across cold starts
- New "Assign to collections" section in New Bomp and Rename Bomp. Public collections show up as multi-select chips; private collections appear behind a "Show private collections (requires unlock)" CTA that asks for your fingerprint before revealing them
- A Bomp can now live in **My Bomps and the Vault at the same time**. The assign sheet (now titled with the Bomp's name) has a "Visible en Mis Bomps" switch: adding a Bomp to a private collection no longer drops it from My Bomps. Edits are confirmed with "Listo" and discarded if you back out. Turn the switch off — only allowed once the Bomp is in the Vault — to move it out of My Bomps entirely, with an Undo. The first time you keep a Bomp in both places, a one-time tip explains it. Selecting a public collection's filter chip always shows that collection's Bomps, even any you've hidden from the main list
- Terms of Service link in the About screen's "Legal & Privacy" section, opening the published page with `?hl=` matched to the device locale (es-AR, es-419, es-ES, en, pt-BR)
- New Bomp screen now shows the audio preview card with play/pause and seek above the name field, so you can listen to the incoming audio before saving — matching the card already present when renaming a Bomp
- New Bomp screen now flags when you already have a Bomp with the same name and lets you tap an inline play/stop toggle to hear that one before deciding — case-insensitive, trimmed; tap again while playing to stop and reset. Non-blocking: you can still save the duplicate (two Bomps can legitimately share a name)

### Fixed
- The two gratitude buttons in the About screen now center their label when it wraps to a second line (e.g. "Invitame un cafecito", or any label at larger font sizes), instead of left-aligning the wrapped line
- Audio preview card in Rename Bomp now advances the seek bar in real time during playback (was static)
- Starting a preview while a Bomp from the Home list was playing would leave both audio sources playing at once; now starting a new preview cleanly stops the list playback (and vice versa)
- Sharing a Bomp whose audio path can't be resolved by the FileProvider no longer crashes the app; the failure is reported to the user with a Snackbar and tracked as a non-fatal so it can be investigated
- Sharing a Bomp no longer crashes the app when no installed app handles audio sharing, when external storage can't be written, or when the Sound has corrupt data — each case shows a tailored Snackbar and is tracked as a non-fatal
- Tapping play on a Bomp no longer crashes the app if MediaPlayer rejects the start call; the playback error Snackbar is shown instead and a non-fatal is tracked
- Tapping play on a Bomp whose audio source can't be opened now shows the playback error Snackbar instead of doing nothing
- Saving a new Bomp from a revoked or unreadable inbound URI now shows a tailored "couldn't read the audio" Snackbar instead of failing silently; the underlying ContentResolver failure is tracked as a non-fatal
- Saving a new Bomp now keeps the user on the form with a clear error Snackbar when the underlying file copy fails, instead of silently navigating away as if the save had succeeded
- Rotating the device while typing a name in the Add Button or Edit Bomp screen no longer wipes the user's draft — the field now restores what was typed (and any visible "name is required" error) after recreate
- Rotating the device while the About screen is open no longer bounces the user back to the sound list; About stays open
- Rotating the device while the License sheet is open in About no longer collapses the sheet; the sheet stays open
- Optimized app size by filtering AAB locales to `en` and `es` only via AGP 9 `androidResources.localeFilters`; transitive dependencies (Material, AndroidX, Firebase, Play Services) no longer ship ~80 unused translations in the bundle
- Restored ripple contrast on the empty Home in light mode so the illustration reads clearly from the first launch
- Audio preview no longer bleeds into the success confirmation when you save a new Bomp (or save a rename) while the preview is playing — the audio now stops the moment the confirmation appears
- Sharing an audio into Bomp while a rename was left open in the background no longer drops you back into that unsaved rename screen with the wrong audio's data; the share now correctly opens the new-Bomp form for the incoming audio (anything you had typed in the unsaved rename is discarded since it was never saved)
- Deleting several Bomps in a row (in My Sounds or the Vault) now removes all of them; previously only the most recent deletion stuck and the rest reappeared when the last undo snackbar faded or the list reloaded
- Search results now carry an origin tag — the Bomp's collection, your Vault, or Explore for the bundled catalog — so matches that look alike across tabs are finally distinguishable (the tag had been announced but never appeared)
- A Bomp's private collection name no longer appears on My Sounds or in search while the Vault is locked — private collection tags stay hidden until you unlock the Vault


### Changed
- The Search action is now hidden until you have enough Bomps for it to be useful, so a fresh install isn't cluttered with a control that can't find anything
- The unlocked Vault now offers the same Search action as the other tabs, replacing its dedicated "new private collection" button — create one from the chip row's "+ Nueva" instead
- Shortened the welcome-dismissal snackbar from 10 s to 4 s so the feedback no longer lingers; user-deleted sounds keep the longer Undo window
- Saving a new Bomp or renaming an existing one now confirms with a brand "voice bubble" overlay that briefly fills the screen with the Bomp's name (inflates with a spring, holds, slides out — metaphor for "your Bomp is on its way") and returns the user to wherever they came from, instead of a long snackbar that lingered after the form. Honors the system "Remove animations" setting with an equivalent static confirmation
- The "name your Bomp" field now opens with focus and the keyboard ready, saving one tap
- The New Bomp name field now hints with real example names instead of a joke placeholder, so the suggestions echo the Bomps you saw on the store screenshots ("Risa de mi vieja, La frase del jefe…" / "Mom's laugh, The boss's catchphrase…")
- Tapping Save on the new-Bomp form without a name now plays a "reject" haptic alongside the existing error message, matching the tactile feedback used elsewhere for rejected actions
- Search empty-state hint moved away from teaching the obvious "type to filter" toward a warmer brand-voice line ("That gem of yours is in here. Go find it." / "Esa reliquia tuya está acá. Encontrala.")
- Tapping a Bomp while it plays now pauses it (and resumes from the same position on the next tap) instead of restarting from the beginning, matching the Add Audio preview behavior. The progress bar stays at the paused position instead of snapping to zero, so a pause reads as a pause. Positions survive rotation; they are reset when a Bomp finishes naturally, is deleted, or after the app is killed
- The back gesture now previews where it leads on the Search, About, and Manage collections screens: as you swipe back the screen slides and fades to reveal what is behind, and you can release mid-swipe to cancel and stay — honoring the system "Remove animations" setting, with the instant back kept on older devices

### For nerds 🤓

#### Added
- Enforce ADR 0010 button typology via `check-adr-invariants.sh` (CI job `adr-invariants`): a name-ban on `OutlinedButton`/`ElevatedButton`/`FilledTonalButton` in component code — they default to `secondary*` roles that collapse to ~1:1 contrast on `surface` (the dark-mode CTA bug, #1170). `GratitudeSection` migrated off its recolored `FilledTonalButton` to `Button` (same `primaryContainer` colors), clearing the one exception that previously blocked enforcement; `ui/theme/` exempt, `// button-ok` escape hatch
- A **planning gate** for feature work — CLAUDE.md § *Features — test coverage workflow* now requires enumerating three axes before writing code: platform surfaces touched (+ the ADR/skill to read), the state/transition model (one test per transition), and device-only checks routed to existing guardrails first. Codifies the prevention for the bug classes that kept surfacing only in manual testing — stale-intent, rapid-repeat deletions, mid-transition playback, inset/legibility — across both ad-hoc and `/overnight-work` sessions; the `/claude-md-audit` skill now protects the section from being trimmed
- `CollectionsRepository` + `Collection` model in `:model` (DataStore Preferences, JSON-encoded, in-memory cache, `ReplaceFileCorruptionHandler`). Mirrors `SoundsRepository` shape so the audit guards already in place (`adr-invariants`) keep applying. The `collections.preferences_pb`, `my-sounds-filter.preferences_pb` and `vault-filter.preferences_pb` stores are included in `app_backup_rules.xml` and `app_data_extraction_rules.xml` so the user's whole archive (Collections + Vault) survives an Auto Backup or device transfer; a new `BackupRulesTest` asserts the inclusion to catch silent regressions
- Explicit `Sound.isVisibleInMySounds` flag ([ADR 0012](docs/adr/0012-explicit-my-sounds-visibility.md)) replaces the derived `inPrivate - inPublic` rule as the sole input to the My Sounds "Todo" projection (`SoundsViewModel.mySoundsProjection`). The search-with-Vault-locked gate and the Vault tab stay membership-based — visibility is "shown in the botonera", not "is private", so a cross-tagged Bomp never leaks into locked search. One-time guarded migration (`migrateVisibilityIfNeeded`, `encodeDefaults = false`) seeds existing Vault-only audios to hidden. New `DualHomeCoachStore` (backed up, asserted by `BackupRulesTest`) + `visibility_toggle` analytics event
- `androidx.biometric:biometric` 1.2.0-alpha05 + `BiometricGate` wrapper isolating `BiometricPrompt` from UI code; uses `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` so the screen-lock PIN is a valid fallback. `LandingActivity` and `AddButtonActivity` migrate from `ComponentActivity` to `FragmentActivity` (the parent class needed by `BiometricPrompt`) — no other functional change
- Analytics events: `collection_create`, `collection_delete`, `collection_rename`, `collection_filter_apply`, `vault_unlock`, `vault_enter_immersive`, `vault_unprotected_warning_shown`. New `CanonicalScreenName` entries `vault`, `vault_unlock`, `vault_collection_listen`, `collection_create`
- `scripts/check-security-test-count.sh` + CircleCI `security-test-count-guard` job — strict-equality count guard for OWASP MASVS-tagged tests under all test source roots. Fails the build if a tagged test is removed (regression) or added without bumping `EXPECTED_COUNT` (visibility); the bump is a deliberate one-line edit that ships in the same PR as the test change
- Analytics event `sound_add_abandoned_after_error` fires when the user leaves the Add Button screen with an unresolved save error (lifecycle-driven via `ON_STOP` and explicit Snackbar dismiss); best-effort — process death drops the signal
- Analytics events `duplicate_name_hint_shown` (gated per-match by the matched sound's id so keystroke churn doesn't multiply emissions) and `duplicate_name_hint_play` (inline play tapped) for the New Bomp duplicate-name hint
- Enforce ADR 0005 audio engine invariant via `check-adr-invariants.sh` and the CircleCI `adr-invariants` job — fails the build if a `MediaPlayer()` constructor appears in `src/main` outside `PlayerControllerImpl.kt`
- Added `scripts/run-instrumented-tests.sh`, a wrapper that cold-boots the test emulator (wiped userdata, pinned serial) before each `connectedDebugAndroidTest` run — a warm AVD degrades across back-to-back runs (`system_server` watchdog ANRs) and produces misleading `ComposeTimeout`/`Process crashed` flakes; ADR 0001 and CONTRIBUTING now document the cold-boot requirement
- Three-layer system to keep CLAUDE.md within budget: new top-of-file routing rule that codifies write-time vs reference-time intent, a 40K-char hard limit enforced by `check-adr-invariants.sh`, and a versioned `/claude-md-audit` skill at `.claude/skills/claude-md-audit/SKILL.md` that mechanizes the audit procedure; first run trimmed CLAUDE.md from 41K to ~35K by moving procedures, checklists, and examples to CONTRIBUTING.md while keeping write-time invariants in place
- Design-system guard in `check-adr-invariants.sh` (CI job `adr-invariants`) fails the build on a raw `Color(0x…)` hex or a magic `.copy(alpha = N)` in `feature/`/`ui/` component code — closing the hard-coded-color / invented-alpha pattern that recurred across three handoffs; `ui/theme/` is exempt (it defines the palette and shared named alphas) and a trailing `// alpha-ok` justifies a one-off
- `WorktreeCreate` hook (`.claude/hooks/create-sibling-worktree.sh`, registered in a now-committed `.claude/settings.json`) places git worktrees the agent harness or its subagents create as siblings of the primary worktree (`../push-me-<name>`) instead of nesting them under `.claude/worktrees/`, where the IDE indexes them as extra projects in the same window; CLAUDE.md and CONTRIBUTING.md document the sibling-worktree convention
- `scripts/setup-worktree.sh` automates the per-worktree setup (copies real `google-services.json` debug + release and bundled debug audio from the primary worktree, re-arms `skip-worktree`) and the `WorktreeCreate` hook invokes it after `git worktree add` so harness-created worktrees come up operational; replaces the prose recipe in CLAUDE.md § Worktree setup with an executable instruction
- Third-party StrictMode matcher for `java.util.zip.Inflater.finalize` (Firebase Crashlytics settings fetch via Android platform OkHttp's `GzipSource`) — unblocks `connectedDebugAndroidTest` runs which were dying with `LeakedClosableViolation` before any `@Test` could execute
- Instrumented-test FileProvider infrastructure: debug-variant `AndroidTestFileProvider` (authority `${applicationId}.androidtest.fileprovider`) exposes `cacheDir/preview-audio/` and a new `TestData.seedPreviewAudio(context)` helper mints playable `content://` URIs from the bundled `test_sound.mp3` asset, unlocking end-to-end coverage of `AddButtonActivity`'s share-sheet flow
- `AddButtonPreviewFlowTest` exercises the AudioPreview card end-to-end on a real `content://` URI: card mounts after MediaMetadataRetriever resolves the duration, tapping play enables the slider and advances progress, tapping pause/play holds and resumes from the paused position — protects the pause/resume paths introduced in PR #1128
- `AddButtonPreviewRecreateTest` covers the CLAUDE.md § Stateful Composables obligation for AddButton Create mode: typed draft name and blank-name error survive `scenario.recreate()`, and the AudioPreview card re-renders after the duration metadata is re-extracted
- `PlayerConcurrencyFlowTest` exercises ADR 0005's Stream↔Sound preempt invariant end-to-end against a real `MediaPlayer`: starting a preview while a Bomp is playing pauses the Bomp and its tile reverts to play, and the inverse (starting a Bomp while a preview is playing) clears the preview's `playbackState` and the Bomp's tile shows pause
- `InboundUriValidationFlowTest` covers the security boundary documented in CLAUDE.md § Inbound URI validation: invalid `EXTRA_STREAM` URIs (disallowed scheme, non-audio MIME, sparse file over the 50 MB cap) trigger the rejection Snackbar without crashing the app, exercising `AddButtonFeature.validateAudioUri` end-to-end against real `ContentResolver` / `FileProvider` traffic
- OWASP MASVS / CWE KDoc tags on the 16 existing security boundary tests (5 in `AddButtonFeatureTest`, 3 in `InboundUriValidationFlowTest`, 1 in `DeepLinkTest`, 6 in `BackupRulesTest`, 1 in `ShareFeatureTest`), and a new CLAUDE.md § Security boundaries → *Security test tagging* sub-section mandating the same KDoc on future security tests — gives the codebase a greppable, audit-friendly trail from test → control → CWE without renaming the tests
- `scripts/install-debug-seeded.sh` plus a debug-only `DebugSoundSeeder` seam (`app/src/debug/`, reached from `LandingActivity` via the `CustomBuildTypeApplication` source-set swap) seeds a few bundled Explore samples into My Sounds on a clean debug install, so contributors don't re-add real audios after every reinstall; idempotent, and a structural no-op in release

#### Changed
- Right-sized the Robolectric SDK matrix ([ADR 0013](docs/adr/0013-single-sdk-default-robolectric.md)): the `AbstractRobolectricTest` base classes and the standalone DataStore/Collections tests collapse from a 3-SDK matrix (`M`, `TIRAMISU`, `VANILLA_ICE_CREAM`) to single `VANILLA_ICE_CREAM` (35) — none of that code branches on `SDK_INT`, so the matrix only tripled CI time. New `// sdk-boundary:`-tagged tests (`HapticsTest`, `AddButtonActivitySdkBoundaryTest`) cover the two branches that *do* vary by SDK (haptic constants on `>= R`, `getParcelableExtra` on `>= TIRAMISU`), and a new `adr-invariants` guard fails any multi-SDK matrix lacking a documented `// sdk-boundary:` reason
- Trimmed CLAUDE.md to ~34K (from ~40K, near the hard limit) by relocating reference-time procedures, tables, and examples to CONTRIBUTING.md — sources-of-truth and design-system/label tables, StrictMode triage tree, security-test-tag mechanics, worktree/synchronization/smoke-test detail — while keeping write-time invariants in CLAUDE.md; the `adr-invariants` size guard stays green
- Extracted the inline alpha literals in `SoundItem`, `AudioPreview`, `SearchOverlay` and `ManageCollectionsScreen` into named constants (shared `ui/theme/Alpha.kt`: `PLAYING_TINT_ALPHA`, `DISABLED_TRACK_ALPHA`, `MUTED_TEXT_ALPHA`; file-local `private const val` for one-offs like the search scrim and deep-link highlight) — identical values, zero visual change, and the new design-system guard keeps them named
- CircleCI PR workflow parallelizes `lint`, `test`, and `bundle` after the cheap linters (`detekt`, `ktlint`, `spotless`) instead of serializing them; `lint` job drops the redundant `app:lintRelease` re-invocation and `app:lintVitalRelease` (a fatal-only filter over the same checks); `bundle` job drops `bundleDebug` (the debug AAB was never consumed in CI). Critical path drops from ~14m39s to an estimated ~6-7min without losing coverage
- Replaced the unmaintained `androidx.compose.material:material-icons-core` dependency with bundled Material Symbols vector drawables loaded via `painterResource`: the 15 icons in use become `app_ic_*` drawables (filled/outlined matched to the originals, `autoMirrored` preserved for the back arrow and chevron); `AppIcons` local vectors are untouched

#### Fixed
- Fixed a `SoundsViewModel` leak — it registered as the process-singleton `PlayerController`'s playback listener in `init` but never detached, so each cleared ViewModel stayed reachable for the life of the process; it now detaches in `onCleared`. Confirmed by heap analysis and guarded by `SoundsViewModelLifecycleTest`

#### Removed
- Dead post-save plumbing: `EXTRA_BUTTON_SAVED` / `EXTRA_BUTTON_RENAMED` / `EXTRA_BUTTON_NAME` Intent extras, `SoundsViewModel.buttonSavedEvent` / `buttonRenamedEvent` channels and their `onButtonSaved` / `onButtonRenamed` setters, the `LandingScreen` `LaunchedEffect`s that consumed them, and `AddButtonActivity.navigateBackSaved` / `navigateBackRenamed`
- `bundledNames(context)` and the two `require(sound.name !in bundledNames(...))` guards in `SoundsRepository.save`/`rename` — with stable `Sound.id` (ADR 0008) a custom sound legitimately sharing a name with a bundled one is no longer an identity collision
- Four separate `EXTRA_EDIT_SOUND_NAME`/`_FILE`/`_FAVORITE`/`_DATE_ADDED` Intent extras for the edit flow — replaced by a single `EXTRA_EDIT_SOUND` Parcelable carrying the full `Sound`

#### Changed
- Error-tracking non-fatals now use stable wrapper messages with per-event context attached as Crashlytics breadcrumbs (`Tracker.log("module.field=value")`) instead of interpolating dynamic data into the wrapper message; improves Crashlytics issue titles and BigQuery searchability
- Retired "button" from internal error and log messages in favor of "audio" — user-facing copy already uses the brand name "Bomp"; internal messages stay neutral so the brand doesn't leak into operations
- Migrated Firebase to per-build-type projects (bomp-prod for release, bomp-debug for debug); added BigQuery export documentation
- About screen's external Legal & Privacy links now open extensionless URLs (`/privacy-policy`, `/data-safety`, `/terms-of-service`) — GitHub Pages serves the same content under both forms, so this is a cosmetic cleanup with no destination or behavior change
- Routed the audio share flow through `SoundsViewModel` with Channel-based one-shot events, aligning ADR 0002 (constructor injection) and ADR 0003 (Channel for one-shot events); `ShareFeature` is split into `prepareShareIntent` (VM-side I/O) and `launchChooser` (UI-side)
- Banned bare `kotlin.assert(...)` in test sources via a new CircleCI `test-assertion-guard` job; tests must use Truth's `assertThat`, JUnit's `assertEquals`, or the Compose UI Test API. `kotlin.assert` is a no-op without JVM `-ea` and silently masked an `EXTERNAL_LEGAL_ITEMS = 3` count that was stale once Terms of Service shipped. Migrated the existing offenders and bumped the constant to 4
- Unified the audio playback engine across the Home/Explore list and the AddButton preview card so a single `MediaPlayer` instance owns concurrency, progress polling, and lifecycle (ADR 0005); the AddButton preview no longer instantiates its own player; setDataSource/prepare moved off the main thread via constructor-injected `ioDispatcher`
- Unified Sound playback semantics under pause/resume (ADR 0007 supersedes the "tap-again-to-stop" decision in ADR 0005): `PlayerControllerImpl` now caches per-sound positions in-process and routes re-taps through `MediaPlayer.start()` when the data source is still loaded; cross-sound switching preempts the previous target by saving its position before `reset()` and seeks back on return. `PlayerControllerListener` gained `onPlayerStart`'s `positionMs` parameter and a new `onPlayerPause(sound, positionMs, durationMs)` callback — distinct from `onPlayerStop` so the UI can keep a paused sound's progress bar in place; `SoundsViewModel` exposes `pausedProgress` (a `Map<String, PlaybackProgress>`) consumed by Home/Explore/Search. `forgetSound(sound)` is the new explicit lifecycle hook used by `SoundsViewModel.deleteSound`
- Refined the GitHub label taxonomy: split the catch-all `an:enhancement` into explicit internal type labels (`a:refactor`, `a:test`, `a:build`, `a:docs`) and added stackable cross-cutting concern labels (`c:accessibility`, `c:performance`, `c:security`, `c:i18n`, `c:observability`, `c:dependencies`); every PR now carries one type label plus zero or more concerns, and Dependabot auto-applies `a:build` + `c:dependencies`
- Refactored `Sound` identity to a stable internal `id` (ADR 0008): `"bundled:$rawRes"` for packaged sounds (cross-install stable), `UUID.randomUUID()` for user-created ones minted at first save in `AddButtonFeature`. Persistence upsert (`SoundsRepository.save/savePin/saveDuration/rename/delete`), the playback position cache (`PlayerControllerImpl.savedSoundPositions`), the paused-progress and duration caches in `SoundsViewModel`, and Compose `LazyColumn` keys all migrate from `Sound.name` to `Sound.id`; renaming a Bomp now preserves its cached duration and same-name coexistence at the data layer is legal (precondition for the upcoming duplicate-name hint). `Sound` becomes `@Parcelize` and the edit-flow Intent collapses four separate extras (`EXTRA_EDIT_SOUND_NAME`/`_FILE`/`_FAVORITE`/`_DATE_ADDED`) into one `EXTRA_EDIT_SOUND` Parcelable. `StoredSound` gains a required `id` JSON field — no migration ships; old-format JSON degrades to an empty list via `decodeSafely` (acceptable in the no-users window). `Sound.equals`/`hashCode` stay structural so `StateFlow` re-emissions of the same logical sound with flipped `isPlaying`/`isPinned` still propagate. `:model` adopts the `kotlin-parcelize` plugin. `ShareFeature` bundled cache filename migrates from `<name>.mp3` to `bundled_<rawRes>.mp3`. New ADR 0008 invariants enforced by `check-adr-invariants.sh`: `StoredSound` declares `id`; `SoundsRepository` does not use name-keyed identity idioms (`associateBy { it.name`, `filterNot { it.name ==`, `firstOrNull { it.name ==`)

#### Fixed
- Debug builds now show a gray launcher icon background again, restoring the visual distinction from release builds that was lost when the icon was modernized to an adaptive icon
- Failures while extracting duration metadata for a newly imported audio are now tracked as non-fatals (Crashlytics) for visibility; the save still succeeds
- Stopped a duplicate startup log breadcrumb fired from both CustomBuildTypeApplication and MainApplication; renamed the surviving log to "Starting <build-type> application"
- De-flaked three instrumented tests by adding animation-settle `waitForIdle()` between deterministic actions and the next assertion: `SearchOverlayTest.systemBackClosesOverlay` (post `Espresso.pressBack()`), `SearchOverlayTest.searchOverlayExposesA11yContentDescriptions` (defensive close at end so the overlay/IME doesn't leak to the next test) and `HomeTabFlowTest.swipeLeftToDeleteThenUndoRestoresSound` (between snackbar appearing and undo click so the M3 enter animation is settled)
- Bumped stale `AboutScreenFlowTest.EXTERNAL_LEGAL_ITEMS` constant from 3 to 4 to match the Terms of Service link added in the same release
- Save-flow state in the Add Button screen now survives Activity recreate (`rememberSaveable` with a custom `Saver` for the `SaveOutcome` sealed interface); on rotation mid-Success the confirmation overlay is restored with the saved name instead of disappearing. In-flight `Loading` collapses to `Idle` on restore because the save coroutine is composition-scoped — the user re-taps Save in the sub-second window
- `FocusRequester.requestFocus()` failures on first-frame attach are now recorded as Crashlytics non-fatals (with a Mode breadcrumb) instead of swallowed by `runCatching`, so a silent focus break on the screen's primary action surfaces in the dashboard if it ever spikes
- Extended `rememberSaveable` coverage to the rest of the durable state in the touched surfaces: Add Button `name` (`TextFieldValue.Saver`) and `nameError`, `LandingScreen.isAboutVisible`, `AboutScreen.isLicenseSheetVisible` — closes the gaps audited app-wide alongside the original PR
- Search overlay's auto-focus now mirrors the Add Button pattern: `withFrameNanos` wait + `runCatching { requestFocus() }` with `Tracker.track` on failure (static wrapper title, breadcrumb `searchoverlay.field=search`), so a first-frame focus break on the search input is no longer silent
- New CLAUDE.md § *Stateful Composables — `rememberSaveable` is the default for durable state* documents the rule, references `SaveOutcomeSaver` as the Saver template, and adds a Pre-PR checklist line requiring `scenario.recreate()` tests for screens with durable state
- `AboutScreenFlowTest` back-navigation tests now use the always-present overflow-menu icon as the Landing sentinel instead of the Search FAB, which #1143 gated behind a minimum sound count
- `FakeAnalyticsTracker.firedFlags` is now backed by a `ConcurrentHashMap` (via `Collections.newSetFromMap`) so its `add` mirrors production `DataStoreFirstFlagStore.consumeFirstTime` (atomic per-key); fixes a flaky `SoundsViewModelAnalyticsTest > loadSounds does not re-emit milestone_sounds_3 once already fired[33]` where two concurrent IO-dispatched coroutines could both observe the flag as absent on the plain `mutableSetOf()` and double-emit the milestone
- `LandingScreenAnalyticsTest` now joins each `SoundsViewModel`'s scope in `@After` (same pattern as the other four `ui/home` test classes since PR #1130); the missing cleanup let its leaked `repo.sounds.drop(1)` collectors outlive the class and re-emit `milestone_sounds_3` into a later test's `FakeAnalyticsTracker`, surfacing as the residual flake in `SoundsViewModelAnalyticsTest > loadSounds does not re-emit milestone_sounds_3 once already fired[33]` after the `firedFlags` thread-safety fix

## \[v2.0.0] - 2026-05-07

### Added
- About now includes a heart-first gratitude frame inviting users to buy a virtual coffee via Ko-fi (all locales) and Cafecito (visible only on es-AR devices)
- Search overlay: tap the new FAB to search across all tabs at once; results show the same play/favorite/share/delete actions as the main list plus a subtle origin badge
- Favorites: mark/unmark any custom button as favorite; a dedicated Favorites tab lists only marked buttons
- Edit custom sounds: long-press any card to rename it (the audio stays, only the name changes); the Edit screen reuses the Add Button flow with pre-loaded name, audio preview, and "Save changes" CTA
- Swipe right on any custom sound to pin/favorite it (PrimaryContainer background); swipe left to delete (ErrorContainer background) — replaces the old single-direction swipe
- Bundled sounds (Explore tab) can now be pinned to the top via button tap or swipe right, with the pinned state persisting across app restarts
- Pinning any sound automatically scrolls the list back to the top so the newly pinned item is immediately visible
- Haptic feedback on swipe actions: CONFIRM pulse for pin, REJECT double-pulse for delete (API 30+); swiping left or long-pressing a bundled sound also gives haptic rejection, indicating those actions are not available
- Delete animation: removed items scale and fade to zero before the list collapses ("Void Shrink"); remaining items animate smoothly into place after any removal (animateItem)
- Deeplinks: the app responds to `push-me://open/home`, `push-me://open/favorites`, and `push-me://open/explore` to navigate directly to a tab
- Back navigation now follows the actual tab history instead of always returning to Explore; back press from Home/Favorites returns to Explore instead of exiting the app
- Tapping the already-selected bottom tab scrolls the list back to the top
- Custom audio files and their metadata are backed up and restored via Android Auto Backup (cloud backup and device transfer on all supported API levels)
- Custom sounds now sort by date added (newest first), with alphabetical fallback for bundled sounds
- About screen rebuilt as a brand manifesto with audio branding button (plays the official push-me sound), AI co-pilot attribution (Gemini and Claude), conditional collaborators section, and full-width legal buttons — WCAG AAA accessible (18 sp body text, 56 dp touch targets, 7:1+ contrast across all text roles); includes version info, license viewer (AGPLv3), third-party credits, and source code link, accessible from the top-bar overflow menu
- Privacy Policy and Data Safety links in the About screen's "Legal & Privacy" section, opening the published pages with `?hl=` matched to the device locale (es-AR, es-419, es-ES, en, pt-BR)
- Acknowledged the first-audios collaborators (Fede, Juli, Mati, Tincho) as the first card inside About's Third-party credits, replacing the previously empty Made with section
- Added a personal "Caro ❤️ y Bob 🐶" attribution at the top of About's Third-party credits
- Welcome card "Un saludo de Nahu" appears at the top of My Sounds on first launch — auto-removed after the audio finishes, with an Undo option to bring it back for a replay
- My Sounds shows a heart-first empty state with concentric acid ripples and copy "Acá viven las voces que te importan" / "This is where the voices live" when the welcome has been consumed and no Bomp has been added yet

### Changed
- Renamed Add Button flow user-facing strings to align with brand DNA: TopAppBar reads "New Bomp"/"Nuevo Bomp" when creating and "Rename Bomp"/"Renombrar Bomp" when editing (the edit screen currently only renames); name-field hint references "your new Bomp"/"tu Bomp"; share-sheet subtitle is now just "Save"/"Guardar" (was "Save button" — and "Save Bomp" stuttered under the "Bomp" app name)
- Renamed app to **Bomp**: launcher label, About screen heading and Play Store listing now align with the canonical brand; old name `Sos Un Boton` retired
- Replaced launcher icon: brand container is now Acid (Neo-Club signal yellow-green) with an Ink play triangle, with adaptive and themed (Material You) icon support; the organic blob brand mark remains on web surfaces (favicon, wordmark, feature graphic) where there is no system mask
- Added a branded launch screen showing the Bomp brand mark on a theme-aware background (Paper in light mode, Ink in dark) — Android 12+ system splash plus core-splashscreen backport for API 23-30
- Renamed the first bottom-nav tab from "Inicio"/"Home" to "Mis audios"/"My Sounds"
- Replaced the old palette with the Neo-Club ink × acid design system (WCAG 2.2 AA–compliant) across all screens (top bars, cards, FAB, search, swipe actions)
- Play button is now the primary visual action within each card: acid-filled circle with a halo when playing; share icon demoted to secondary weight
- Sound cards now have a subtle 1dp hairline border for visual separation on dark and light backgrounds
- Normalized top-bar-to-content spacing to 16 dp across all screens
- Edit screen audio preview now shows total duration and date added, matching the home card layout
- Swipe-to-delete haptic feedback on custom sounds changed from double-pulse (reject) to single-pulse (confirm), matching the pin gesture — a successful delete is a confirmed action
- Search overlay's clear-query icon now announces "Clear search" (was "Close search", which collided with the back-arrow's label and confused screen readers)
- Deeplink `push-me://open/explore` now falls back to Home when no bundled sounds are available, avoiding a blank Explore tab
- Share sheet filename for bundled sounds now shows the button name instead of an internal prefix
- Custom button audio files are now named after the user-provided sound name (non-alphanumeric chars replaced by underscores) instead of the generic `Button-custom-` prefix
- Snackbar after deleting a custom sound now reads "Audio deleted"/"Audio borrado" (was "Button deleted"/"Botón borrado") so the copy matches how users describe what they removed
- Welcome card can be dismissed by swiping left, or long-pressing to open the actions menu and tapping Delete — no need to listen all the way through. Tapping Undo within 5s brings it back, but it now lives at the bottom of My Sounds instead of pinning to the top
- Restoring the app via Auto Backup or device-transfer now preserves your dismissal of the welcome card and your lifetime usage counters (number of plays, number of shares) so a restored device picks up where the previous one left off. First-time-event flags still reset per-install to align with Firebase's per-install lifecycle
- Saving a button via the share intent now confirms with a "Saved!" Snackbar and navigates to the Home tab, instead of silently returning to the previous app

### Fixed
- Custom sound stops playing immediately when deleted instead of continuing until the track ends
- Bundled sounds no longer offer a swipe-to-delete gesture; the action is simply not available
- Crash when switching tabs while a sound is playing
- Ghost-playing state when scrolling the list

### Removed
- About screen pronunciation block (`/sohs oon boh-TOHN/`) removed alongside the rename to Bomp
- `WRITE_EXTERNAL_STORAGE` permission (no longer required with scoped storage)

### For nerds 🤓

#### Added
- About screen shows the Gradle root-dir name on a second line (debug builds only) so contributors juggling multiple worktrees can tell which build is installed at a glance
- Initial Google Play Store listing assets in es-AR and en-US under `store-listing/`: title, short and full description (with zero-friction onboarding and Auto Backup to Drive featured as headline differentials, and a stewardship-framed close that respects Data Safety policy), what's new, brand mark SVG, design briefs for icon, feature graphic, five phone screenshots ordered for ASO impact (UI hero, brand manifesto, search, playing, emotional close) and preview video
- Firebase Analytics instrumentation: emits events for every active user flow (add, edit, delete, play, pin, search, share, about) plus 6 canonical `screen_view` names (`my_sounds`, `explore_sounds`, `about`, `search_sound`, `add_sound`, `edit_sound`) with auto-tracking disabled, one-shot `first_*` variants on first invocation, and user properties (`current_sounds`, `current_pinned`, `lifetime_shares`, `lifetime_plays`) that unlock cohort segmentation in Firebase Console
- Jetpack Compose UI replacing all Fragments, RecyclerViews and XML layouts
- `SoundsViewModel` with `StateFlow` for reactive UI state management
- Tests for `SoundsViewModel` covering playback state and delete/restore flows
- AGPLv3 copyright header on all source files, enforced by Spotless

#### Changed
- `PlayerControllerListener.onPlayerStop` now takes a `completed: Boolean` argument so callers can distinguish natural end-of-stream (`MediaPlayer.OnCompletionListener`) from user-initiated stops; required for the welcome-sticker auto-destruct path that fires only on natural completion
- `WelcomeStickerStore.wasRestored` flag persists "user has undone at least once" so the next render demotes the welcome from row 0 to the end of My Sounds; sticky once set
- Migrated `WelcomeStickerStore` and the analytics flag/counter store from `SharedPreferences` to Jetpack DataStore Preferences. SharedPreferences is no longer used anywhere in the project
- Split the analytics flag/counter store into two DataStore files (`analytics-flags` and `analytics-counters`) so they can have opposing backup postures: counters are backed up (lifetime user properties must persist across devices), flags are not (each install emits its own `first_*` series)
- Analytics store keeps its synchronous `FirstFlagStore` / `CounterStore` API by layering an in-memory cache + async write-back over DataStore — mirrors Firebase Analytics' own sync-API + internal-buffer design so events fired right before navigating away (share chooser, browser intent) keep their durability guarantee
- `MainApplication.onCreate` warm-up dispatches the `AnalyticsTrackerProvider.get(...)` cache prime onto a process-owned `applicationScope` so the `runBlocking(IO)` backstop in each store's constructor rarely blocks main in practice
- Migrated sound metadata persistence from SharedPreferences to Jetpack DataStore Preferences with a single JSON-encoded payload, exposed as a reactive `Flow<List<Sound>>` from the new `SoundsRepository`; safer concurrent writes, no main-thread IO, and corrupted-payload recovery via Crashlytics-reported fallback to an empty list
- StrictMode debug audit: switched both ThreadPolicy and VmPolicy to `detectAll()` (forward-compat for new detectors) keeping `detectNonSdkApiUsage()` explicit; route every surviving violation through `Tracker.track` so each one prints a single line under the `Tracker` tag with `"StrictMode: <ViolationClass>"` in the message (greppable via `adb logcat | grep StrictMode`); known noise (Firebase Analytics / Crashlytics / Datatransport CCT, Compose `dispatchOnScrollChanged`, Espresso reflection, framework `SurfaceControl` finalize, framework Activity-destroy GC) is silenced in both logcat and Crashlytics with one decision; unknown violations now crash the debug process so they cannot slip past unnoticed; Firebase init disk reads wrapped at the call-site via scoped `allowThreadDiskReads` in `AnalyticsTrackerProvider`
- Rewrote `README.md` to reflect v2.0 reality: refreshed brand to Bomp, refreshed version/API/CI badges, replaced feature list with current capabilities aligned to brand-DNA vocabulary, added Google Play badge and GitHub Pages link, removed obsolete Codacy badge and "What's next" section
- Enabled Gradle configuration cache to speed up incremental builds and CI runs
- Promoted the Add Button flow from a `:feature_addbutton` dynamic feature module into the core `:app` module (creating buttons is core, not a freemium add-on); eliminates the bundletool wrapper script and lets the local UI test suite run as plain `./gradlew app:connectedDebugAndroidTest`
- Migrated from `material-icons-extended` to `material-icons-core` following Material 3 1.4.0; icons absent from core (`Pause`, `ViewComfyAlt`) are now local `ImageVector` definitions copied verbatim from the extended sources to preserve visual consistency
- `SoundsViewModel` now loads sounds on a background thread (`Dispatchers.IO`) to avoid blocking the main thread on startup
- Centralised all dependency and plugin versions in `gradle/libs.versions.toml` (Gradle version catalog)
- Re-enabled resource shrinking (`shrinkResources true`) now that AGP 9 resolves the long-standing incompatibility with dynamic feature modules
- Upgraded stack: AGP 9.1.1, Gradle 9.3.1, compileSdk android-37.0, targetSdk 37, Java 21; migrated to AGP built-in Kotlin compilation (removed `kotlin-android` plugin)
- Bumped all dependencies to latest stable: Firebase BOM 34.12.0, Compose BOM 2026.03.01, lifecycle 2.10.0, material 1.13.0, Robolectric 4.16.1, MockK 1.14.9, Firebase perf-plugin 2.0.2, ktlint-gradle 14.2.0, KTLint 1.6.0, and more
- `minSdk` raised to 23 (Android 6.0); devices on Android 5.x no longer supported
- KTLint migrated to JLLeitschuh plugin with KTLint 1.5.0
- Resolved all Kotlin compiler and Gradle DSL deprecation warnings for a clean build log
- Bundled audio files removed from version control; bottom navigation bar hidden in release builds (Explore tab only appears in debug when audio files are manually placed)
- Inbound audio URIs from share intents are now validated by scheme, MIME type, and size before importing

#### Fixed
- `ShareFeature.share` now runs file-system access (`getFile` + first-time `copy` of bundled raw resources) on `Dispatchers.IO`, eliminating a long-standing main-thread disk write that could cause jank the first time a bundled sound was shared

#### Removed
- Removed `techstack.md` and its companion `techstack.yml` (StackShare.io config that broke after the repo rename to `bomp` and was no longer maintained — last meaningful update predated the v2.0 Compose rewrite)
- `MediaPlayerHelper` dead code path using the deprecated `MediaStore.Audio.Media.DATA` column
- Flipper and LeakCanary debug tools
- `dexcount` Gradle plugin
- All Fragment/RecyclerView/ViewBinding UI code
- Multidex support (not needed above minSdk 23)

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
