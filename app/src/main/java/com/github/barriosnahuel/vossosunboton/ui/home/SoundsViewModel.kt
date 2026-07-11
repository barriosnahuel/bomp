/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tracing.Trace
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsScope
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsUserProperty
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.feature.collections.MySoundsFilterStore
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerListener
import com.github.barriosnahuel.vossosunboton.feature.share.ShareFeature
import com.github.barriosnahuel.vossosunboton.feature.share.ShareIntentOutcome
import com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore
import com.github.barriosnahuel.vossosunboton.feature.welcome.isWelcomeSticker
import com.github.barriosnahuel.vossosunboton.feature.welcome.welcomeSticker
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.CollectionsRepository
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

enum class AppTab { MY_SOUNDS, VAULT, EXPLORE_SOUNDS }

/**
 * Request payload for the create/rename collection bottom sheet. A single sealed shape so the
 * sheet host can match-on-type to derive title, placeholder, and submit semantics.
 */
sealed interface CollectionSheetRequest {
    data class Create(
        val access: CollectionAccess,
        // Analytics surface that opened the create sheet — flows into collection_create.source so
        // product can tell which entry point drives organization. See AnalyticsEvent.CollectionCreate.
        val source: String,
    ) : CollectionSheetRequest

    data class Rename(
        val id: String,
        val currentName: String,
    ) : CollectionSheetRequest
}

data class DeletedSoundEvent(
    val sound: Sound,
    val position: Int,
)

/**
 * Emitted when the user closes the assign sheet having turned OFF "Visible en Mis Sonidos" for an
 * audio that still lives in a private collection (ADR 0012). Drives the "Lo moviste al Baúl"
 * snackbar; [audioId] + [name] let the Undo action re-enable visibility via
 * [SoundsViewModel.setAudioVisibleInMySounds].
 */
data class MovedToVaultEvent(
    val audioId: String,
    val name: String,
)

/**
 * Anti-orphan predicate (ADR 0012): an audio may be hidden from My Bomps only if it is reachable
 * **elsewhere** — i.e. it belongs to at least one collection, **public OR private**. A public
 * member is reachable via that collection's filter chip; a Vault member via the Vault. With zero
 * collections, hiding it would strand it (not in "Todo", not in any chip, not in the Vault), so the
 * switch can't be turned off (UI gate) and `applyAssignment` coerces it back to visible (safety net).
 */
internal fun isReachableOutsideMySounds(
    publicCollectionIds: Set<String>,
    privateCollectionIds: Set<String>,
): Boolean = publicCollectionIds.isNotEmpty() || privateCollectionIds.isNotEmpty()

/**
 * Optimistic My Sounds "Todo" reprojection for a visibility toggle (ADR 0012), factored out as a
 * pure function so the synchronous list update is unit-testable without the DataStore round-trip
 * (`SoundsViewModel.setAudioVisibleInMySounds` applies the flag to the cache, then reprojects the
 * visible list through this). [current] is the visible list, [catalog] the full cache already
 * carrying the just-applied flag, [order] the canonical [Sound] sort. Hiding drops [audioId];
 * showing inserts it from [catalog] re-sorted by [order] — preserving the welcome sticker and any
 * pins — and is a no-op when it is already present or absent from the catalog. `loadSounds` later
 * reprojects to the same list, so this only governs the transient window before the write lands.
 */
internal fun reprojectVisibilityToggle(
    current: List<Sound>,
    audioId: String,
    visible: Boolean,
    catalog: List<Sound>,
    order: Comparator<Sound>,
): List<Sound> =
    if (!visible) {
        current.filterNot { it.id == audioId }
    } else if (current.any { it.id == audioId }) {
        current
    } else {
        catalog.firstOrNull { it.id == audioId }?.let { (current + it).sortedWith(order) } ?: current
    }

data class PlaybackProgress(
    val positionMs: Int,
    val durationMs: Int,
) {
    val fraction: Float get() = if (durationMs > 0) positionMs / durationMs.toFloat() else 0f
}

@Suppress("TooManyFunctions", "LargeClass")
class SoundsViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val searchDebounceMs: Long = 200L,
    private val welcomeStore: WelcomeStickerStore = WelcomeStickerStore(application),
    private val shareFeature: ShareFeature = ShareFeature.instance,
    private val collectionsRepo: CollectionsRepository = CollectionsRepository(application, onError = Tracker::track),
    private val mySoundsFilterStore: MySoundsFilterStore = MySoundsFilterStore(application),
    private val vaultFilterStore: com.github.barriosnahuel.vossosunboton.feature.vault.VaultFilterStore =
        com.github.barriosnahuel.vossosunboton.feature.vault
            .VaultFilterStore(application),
    private val dualHomeCoachStore: com.github.barriosnahuel.vossosunboton.feature.collections.DualHomeCoachStore =
        com.github.barriosnahuel.vossosunboton.feature.collections
            .DualHomeCoachStore(application),
    private val draftStore: com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderDraftStore =
        com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderDraftStoreProvider
            .get(application),
) : AndroidViewModel(application),
    PlayerControllerListener {
    /**
     * The pending recording draft, or null (ADR 0019 § Draft recovery). A cold flow so the Landing
     * collector re-validates the temp file's existence on every resume — self-healing if the OS evicted
     * the cached clip while the user was away.
     */
    val pendingDraft: kotlinx.coroutines.flow.Flow<com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderDraft?> =
        draftStore.draft

    /** Banner "Descartar": delete the draft clip and forget it. */
    fun discardDraft() {
        viewModelScope.launch(ioDispatcher) {
            draftStore.current()?.file?.delete()
            draftStore.clear()
        }
    }

    private val repo =
        SoundsRepository(
            application,
            onError = Tracker::track,
            // Pre-stable-id data was healed on read (ADR 0018). Gate to one event per install so the
            // per-read recovery doesn't flood; lets the legacy population be counted for retirement.
            onLegacyRecovery = {
                if (tracker.markFiredOnce("legacy_sounds_recovered")) {
                    tracker.log(AnalyticsEvent.LegacySoundsRecovered)
                }
            },
        )

    // Default `false` — flipped to `true` asynchronously by the IO coroutine in `init` after
    // reading the suspend `welcomeStore.isActive()`. Sub-frame in practice; same accepted
    // limitation as `_sounds = emptyList()` during cold-start.
    private val _welcomeStickerVisible = MutableStateFlow(false)
    val welcomeStickerVisible: StateFlow<Boolean> = _welcomeStickerVisible.asStateFlow()

    // `true` while the one-time swipe-hint nudge on the welcome card is still eligible to run. The
    // UI consumes it, runs the peek animation once, and calls [markWelcomeHintShown].
    private val _welcomeHintPending = MutableStateFlow(false)
    val welcomeHintPending: StateFlow<Boolean> = _welcomeHintPending.asStateFlow()

    // In-process latch closing the race between [markWelcomeHintShown] (flips the flag on the main
    // thread, persists async) and a `loadSounds` landing before that write commits — without it, the
    // recompute below would observe a stale `isHintShown()` and replay the nudge.
    @Volatile
    private var welcomeHintConsumed = false

    // SavedStateHandle-backed so the selected tab survives process death: the Nav3 back stack
    // restores itself (rememberNavBackStack) and the VM->graph sync in LandingScreen would
    // otherwise see the default MY_SOUNDS and snap the restored graph back to Home.
    val selectedTab: StateFlow<AppTab> = savedStateHandle.getStateFlow(KEY_SELECTED_TAB, AppTab.MY_SOUNDS)

    private val _sounds = MutableStateFlow<List<Sound>>(emptyList())
    val sounds: StateFlow<List<Sound>> = _sounds.asStateFlow()

    private val _hasBundledSounds = MutableStateFlow(false)
    val hasBundledSounds: StateFlow<Boolean> = _hasBundledSounds.asStateFlow()

    /** All collections (public + private) — drives the Vault tab list and the Add/Edit tag chips. */
    private val _collections = MutableStateFlow<List<Collection>>(emptyList())
    val collections: StateFlow<List<Collection>> = _collections.asStateFlow()

    /**
     * Reverse index: `audioId → list of collection ids this audio belongs to`. IDs (not names)
     * so the UI layer can resolve the user-facing label against `_collections` and substitute
     * the locale-aware string resource for system collections. Derived from [_collections];
     * rebuilt on every emission.
     */
    private val _audioCollectionsIndex = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val audioCollectionsIndex: StateFlow<Map<String, List<String>>> = _audioCollectionsIndex.asStateFlow()

    /** Persisted last-chosen filter on My Sounds. `null` = "All" (unfiltered). */
    private val _activeMySoundsFilter = MutableStateFlow<String?>(null)
    val activeMySoundsFilter: StateFlow<String?> = _activeMySoundsFilter.asStateFlow()

    /** Persisted last-chosen filter on the Vault tab. `null` = "All private audios" (unfiltered). */
    private val _activeVaultFilter = MutableStateFlow<String?>(null)
    val activeVaultFilter: StateFlow<String?> = _activeVaultFilter.asStateFlow()

    /**
     * Audios visible in the Vault tab — the union of all audios tagged to any private collection,
     * optionally narrowed to the active [activeVaultFilter] chip. Drives the Vault list directly
     * (no separate collection grid); rendering identical to `sounds` (same `SoundItem` cards).
     */
    private val _vaultAudios = MutableStateFlow<List<Sound>>(emptyList())
    val vaultAudios: StateFlow<List<Sound>> = _vaultAudios.asStateFlow()

    /**
     * Active "create or rename collection" request. `null` when no sheet is open. The single
     * StateFlow is enough because only one create/rename sheet can be visible at a time across
     * the whole screen graph.
     */
    private val _activeCollectionSheet = MutableStateFlow<CollectionSheetRequest?>(null)
    val activeCollectionSheet: StateFlow<CollectionSheetRequest?> = _activeCollectionSheet.asStateFlow()

    /**
     * Pending delete-confirmation prompt for a non-system collection. Null when no dialog is
     * showing. System collections are guarded earlier (the UI does not offer Delete for them).
     */
    private val _pendingCollectionDelete = MutableStateFlow<String?>(null)
    val pendingCollectionDelete: StateFlow<String?> = _pendingCollectionDelete.asStateFlow()

    /**
     * `audioId` of the sound the long-press → "Add to collection" sheet is currently editing.
     * `null` when the sheet is closed. Only one such sheet is reachable at a time (the long-press
     * menu collapses on selection), so a single Optional is sufficient.
     */
    private val _activeAssignAudioId = MutableStateFlow<String?>(null)
    val activeAssignAudioId: StateFlow<String?> = _activeAssignAudioId.asStateFlow()

    private val mutableInitialLoadComplete = MutableStateFlow(false)

    // Two-input gate for the flag below: the projections loadSounds writes read `_collections`
    // (chip filters, vault membership), and collections hydrate on an independent coroutine — a
    // flag opened on loadSounds alone could still front a collections-blind projection. @Volatile:
    // each input is marked from its own dispatcher thread.
    @Volatile
    private var initialSoundsLoaded = false

    @Volatile
    private var initialCollectionsLoaded = false

    private fun maybeCompleteInitialLoad() {
        if (initialSoundsLoaded && initialCollectionsLoaded) mutableInitialLoadComplete.value = true
    }

    // Serializes every loadSounds pass. Concurrent triggers (init, the reactive repo collector,
    // collections emissions, tab/filter changes) each read-then-write the projections; without
    // the lock, a pass that read an older snapshot can finish last and clobber a fresher
    // projection (stale-writer-wins — surfaced as a hidden audio flashing back into My Sounds).
    private val loadSoundsMutex = Mutex()

    /**
     * Flips to `true` only AFTER both cold-start inputs landed: the first [loadSounds] pass
     * finished writing every list it projects (`_sounds`, `_vaultAudios`, caches) AND the first
     * collections snapshot was applied (those projections read `_collections`). Fail-open: an init
     * failure flips it regardless so empty states are never gated shut forever. That write order
     * is a contract: UI consumers gate their empty states on this flag and MUST read it BEFORE the
     * list flows (release/acquire pairing), so a `true` flag is never paired with a stale empty
     * list during the first composition pass (cold-start ZRP flash). Tests also use it to await
     * the async DataStore reads started in `init` before mutating state via reflection.
     *
     * Avoids the conventional `_isInitialLoadComplete`/`isInitialLoadComplete` backing-field
     * pair because ktlint's `backing-property-naming` rule requires both to be `private`,
     * which would defeat the coordination purpose.
     */
    internal val isInitialLoadComplete: StateFlow<Boolean> = mutableInitialLoadComplete.asStateFlow()

    private val allSoundsCache = MutableStateFlow<List<Sound>>(emptyList())

    /**
     * Snapshot of the full catalog (custom + bundled) regardless of the currently selected tab.
     * Distinct from [sounds], which is the tab-filtered list that powers the visible LazyColumn —
     * on the Vault tab `sounds` collapses to an empty list (the Vault renders private collection
     * cards instead of audios). [library] keeps the full set available so detail screens like
     * `ImmersiveListenScreen` can resolve a collection's `audioIds` to real Sound objects without
     * caring about which tab the user happens to be on.
     */
    val library: StateFlow<List<Sound>> = allSoundsCache.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Sound>>(emptyList())
    val searchResults: StateFlow<List<Sound>> = _searchResults.asStateFlow()

    private val _isSearchPending = MutableStateFlow(false)
    val isSearchPending: StateFlow<Boolean> = _isSearchPending.asStateFlow()

    private var searchDebounceJob: Job? = null

    private val _playingSound = MutableStateFlow<Sound?>(null)
    val playingSound: StateFlow<Sound?> = _playingSound.asStateFlow()

    private val _playbackProgress = MutableStateFlow<PlaybackProgress?>(null)
    val playbackProgress: StateFlow<PlaybackProgress?> = _playbackProgress.asStateFlow()

    // Position of every sound that is paused (not the currently-playing one). Keyed by sound id.
    // Lets the UI keep a paused sound's progress bar at its position instead of collapsing it to
    // zero, so a pause reads as a pause rather than a reset. Cleared when the sound starts again
    // or is definitively stopped. In-memory only — mirrors the controller's saved-position cache.
    private val _pausedProgress = MutableStateFlow<Map<String, PlaybackProgress>>(emptyMap())
    val pausedProgress: StateFlow<Map<String, PlaybackProgress>> = _pausedProgress.asStateFlow()

    private val _soundDurations = MutableStateFlow<Map<String, Int>>(emptyMap())
    val soundDurations: StateFlow<Map<String, Int>> = _soundDurations.asStateFlow()

    private val _deletedSoundEvent = MutableStateFlow<DeletedSoundEvent?>(null)
    val deletedSoundEvent: StateFlow<DeletedSoundEvent?> = _deletedSoundEvent.asStateFlow()

    private val _scrollToTopEvent = Channel<Unit>(Channel.BUFFERED)
    val scrollToTopEvent: Flow<Unit> = _scrollToTopEvent.receiveAsFlow()

    // One-shot informative snackbar shown the first time the welcome audio plays to completion —
    // "it's yours, delete it whenever". Replaces the old self-destruct-on-completion flow (ADR 0003
    // Channel for one-shot UI events).
    private val _welcomeInfoEvent = Channel<Unit>(Channel.BUFFERED)
    val welcomeInfoEvent: Flow<Unit> = _welcomeInfoEvent.receiveAsFlow()

    private val _playbackErrorEvent = Channel<Unit>(Channel.BUFFERED)
    val playbackErrorEvent: Flow<Unit> = _playbackErrorEvent.receiveAsFlow()

    // CONFLATED (vs the BUFFERED siblings above): a tap-spamming user does not need every share-error
    // queued — only the latest matters, and stacking duplicate snackbars would just hide the message.
    private val _shareIntentEvent = Channel<ShareIntentOutcome.Success>(Channel.CONFLATED)
    val shareIntentEvent: Flow<ShareIntentOutcome.Success> = _shareIntentEvent.receiveAsFlow()

    private val _shareErrorEvent = Channel<Int>(Channel.CONFLATED)
    val shareErrorEvent: Flow<Int> = _shareErrorEvent.receiveAsFlow()

    // ADR 0012 — dual-home coach + "moved to Vault" undo. CONFLATED: only the latest close matters;
    // stacking duplicate coach/undo snackbars would just hide the message behind itself.
    private val _dualHomeCoachEvent = Channel<Unit>(Channel.CONFLATED)
    val dualHomeCoachEvent: Flow<Unit> = _dualHomeCoachEvent.receiveAsFlow()

    private val _movedToVaultEvent = Channel<MovedToVaultEvent>(Channel.CONFLATED)
    val movedToVaultEvent: Flow<MovedToVaultEvent> = _movedToVaultEvent.receiveAsFlow()

    /**
     * Detach from the process-singleton [PlayerControllerFactory] this registers with in [init].
     * Without it, a cleared ViewModel — with its caches and collectors — stays reachable through
     * `PlayerControllerImpl.listener` for the whole process (heap-confirmed leak).
     */
    override fun onCleared() {
        PlayerControllerFactory.instance.removeOnStartStopListener(this)
        super.onCleared()
    }

    init {
        PlayerControllerFactory.instance.setOnStartStopListener(this)
        // Single coroutine: read welcome-sticker visibility BEFORE the first loadSounds so the
        // prepend logic sees the right state on the first paint. selectTab cannot fire before VM
        // init returns, so the first loadSounds always observes a stable
        // `_welcomeStickerVisible` snapshot.
        //
        // The `finally` marks the gate's sounds input no matter where this block fails — otherwise
        // tests that await `isInitialLoadComplete.first { it }` would deadlock. Only THIS coroutine
        // marks it (not loadSounds itself): it is the one sequenced after migrateVisibilityIfNeeded,
        // so the gate can never open over a pre-migration projection written by a concurrent pass.
        viewModelScope.launch(ioDispatcher) {
            try {
                // One-time migration from the old "ephemeral, self-destruct on completion" model:
                // resurfaces the welcome for installs that consumed it under the old behavior. Runs
                // BEFORE isActive() so the reset takes effect on first paint. Idempotent: a manual
                // delete under the new model still sticks.
                welcomeStore.migrateToPersistentIfNeeded()
                val active = welcomeStore.isActive()
                _welcomeStickerVisible.value = active
                if (active && tracker.markFiredOnce("welcome_sticker_shown")) {
                    tracker.log(AnalyticsEvent.WelcomeStickerShown)
                }
                // ADR 0012: one-time seed of `isVisibleInMySounds` for audios created before the
                // flag existed. Runs before the first `loadSounds` so a pre-existing private-only
                // audio (which decodes with the `true` default) is flipped to hidden before the new
                // explicit rule projects the list — otherwise it would flash into My Sounds once.
                repo.migrateVisibilityIfNeeded(privateOnlyAudioIds())
                loadSounds()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                Tracker.track(RuntimeException("SoundsViewModel init failed", e))
                // Fail-open: a broken init must not gate the empty states shut forever.
                mutableInitialLoadComplete.value = true
            } finally {
                initialSoundsLoaded = true
                maybeCompleteInitialLoad()
            }
        }
        // Reactive trigger: any change to the persisted sound list (save / rename / pin /
        // duration / delete) re-runs `loadSounds` so the visible list catches up. Without it,
        // `loadSounds` would only run on cold start and tab switch, so an AddButton save/rename
        // never propagated to Home.
        //
        // `drop(1)` skips this collector's own initial snapshot — the `loadSounds()` above has
        // already consumed it. `repo.sounds` is `distinctUntilChanged` upstream, so a write
        // that re-encodes to the same JSON does not redundantly re-trigger `loadSounds`.
        //
        // `CancellationException` is rethrown so structured concurrency keeps working when
        // `viewModelScope` is cancelled — important in tests where each VM is cancelled in
        // `@After` to stop the collector outliving the test.
        viewModelScope.launch(ioDispatcher) {
            try {
                repo.sounds
                    .drop(1)
                    .collect { loadSounds() }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                Tracker.track(RuntimeException("SoundsViewModel repo observation failed", e))
            }
        }
        // Hydrate the last-chosen filter chips. Both filters persist per spec § 7 ("persist last
        // filter, favors single-context users").
        viewModelScope.launch(ioDispatcher) {
            try {
                val mine = mySoundsFilterStore.get()
                _activeMySoundsFilter.value = mine.takeIf { it != MySoundsFilterStore.ALL_SENTINEL }
                val vault = vaultFilterStore.get()
                _activeVaultFilter.value =
                    vault.takeIf {
                        it !=
                            com.github.barriosnahuel.vossosunboton.feature.vault.VaultFilterStore.ALL_SENTINEL
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                Tracker.track(RuntimeException("SoundsViewModel filter prime failed", e))
            }
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                // The first emission is the cold-start snapshot — `loadSounds()` from the init
                // launch above already read this same snapshot via `privateOnlyAudioIds()`, so
                // a second redundant `loadSounds()` here would just race with that work (and,
                // in tests, with reflection-based `injectSounds(...)` calls scheduled after
                // `isInitialLoadComplete`). The non-loadSounds bookkeeping (audio index, stale-
                // filter cleanup, vault recompute, user property sync) still has to fire on the
                // first emission because those fields start empty and depend on the snapshot.
                var firstEmission = true
                collectionsRepo.collections.collect { list ->
                    _collections.value = list
                    _audioCollectionsIndex.value = buildAudioCollectionsIndex(list)
                    // Drop stale active filters when the underlying collection is deleted so the
                    // user does not end up looking at a "filtered" but empty list with no chip
                    // highlighted to clear.
                    val activeMine = _activeMySoundsFilter.value
                    if (activeMine != null && list.none { it.id == activeMine }) {
                        _activeMySoundsFilter.value = null
                        runCatching { mySoundsFilterStore.set(MySoundsFilterStore.ALL_SENTINEL) }
                    }
                    val activeVault = _activeVaultFilter.value
                    if (activeVault != null && list.none { it.id == activeVault }) {
                        _activeVaultFilter.value = null
                        runCatching {
                            vaultFilterStore.set(
                                com.github.barriosnahuel.vossosunboton.feature.vault.VaultFilterStore.ALL_SENTINEL,
                            )
                        }
                    }
                    recomputeVaultAudios()
                    syncCollectionsUserProperties(list)
                    if (firstEmission) {
                        // The init loadSounds may have projected before this snapshot landed
                        // (chip filters and vault membership read `_collections`): re-project
                        // synchronously from the cache so the gate never opens over a
                        // collections-blind list. No-op while the cache is still empty —
                        // loadSounds hasn't run yet and will project with collections present.
                        applyTabFilterFromCache(selectedTab.value)
                        initialCollectionsLoaded = true
                        maybeCompleteInitialLoad()
                    } else {
                        loadSounds()
                    }
                    firstEmission = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                Tracker.track(RuntimeException("SoundsViewModel collections observation failed", e))
                // Fail-open: never hold the empty-state gate shut forever on a dead collector.
                mutableInitialLoadComplete.value = true
            }
        }
        // React to the Vault unlock/lock session flag. Search results are the only consumer whose
        // visibility set DEPENDS on the session — My Sounds is "public by definition" and Vault is
        // gated by its own screen. `drop(1)` skips the initial `false` emission; `recomputeSearch`
        // already runs implicitly via `onSearchQueryChange` so we don't need to seed it here. When
        // the user taps the "Unlock to search the Vault" CTA and authenticates, this collector
        // refreshes `_searchResults` so previously-hidden private matches appear without the user
        // having to re-type the query.
        viewModelScope.launch {
            try {
                com.github.barriosnahuel.vossosunboton.feature.vault.security.VaultSessionState
                    .flow
                    .drop(1)
                    .collect { recomputeSearchResults() }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                Tracker.track(RuntimeException("SoundsViewModel vault-session observation failed", e))
            }
        }
    }

    /**
     * Recomputes [_vaultAudios] = (audios in any private collection) optionally narrowed to the
     * active Vault filter chip. Called from the collections observer (so tag changes propagate
     * automatically) and explicitly after [selectVaultFilter] / loadSounds (so a freshly loaded
     * catalog also rebuilds the Vault list).
     */
    private fun buildAudioCollectionsIndex(collections: List<Collection>): Map<String, List<String>> {
        val index = mutableMapOf<String, MutableList<String>>()
        for (collection in collections) {
            for (audioId in collection.audioIds) {
                index.getOrPut(audioId) { mutableListOf() } += collection.id
            }
        }
        return index.mapValues { it.value.toList() }
    }

    /**
     * Re-emits the three "current_collections_*" user properties whenever the canonical collections
     * list mutates (create/rename/delete + every membership toggle, since [CollectionsRepository]
     * snapshots on every write). Excludes system collections so the dashboards reflect *user*
     * intent — the seeded Baúl row is always there.
     */
    private fun syncCollectionsUserProperties(collections: List<Collection>) {
        val publicCount = collections.count { it.isPublic && !it.isSystem }
        val privateCount = collections.count { it.isPrivate && !it.isSystem }
        val audiosInCollections = collections.flatMap { it.audioIds }.toSet().size
        tracker.setUserProperty(AnalyticsUserProperty.CURRENT_COLLECTIONS_PUBLIC, publicCount.toString())
        tracker.setUserProperty(AnalyticsUserProperty.CURRENT_COLLECTIONS_PRIVATE, privateCount.toString())
        tracker.setUserProperty(AnalyticsUserProperty.CURRENT_AUDIOS_IN_COLLECTIONS, audiosInCollections.toString())
        syncAudioBuckets(collections)
    }

    /**
     * Classifies every user audio into one of four mutually-exclusive buckets that sum to the total
     * user-created (non-bundled) audio count. Membership comes from [collections]; the audio
     * universe from [allSoundsCache]. Cross-tagged audios (public AND private) count as public — the
     * public surface preserves visibility (spec § 3.1), so the `when` checks public first.
     *
     * Called from BOTH the collections observer (tag changes) and [loadSounds] (audio create/delete)
     * because the buckets depend on the audio × collection product — either side mutating must
     * refresh the snapshot.
     */
    private fun syncAudioBuckets(collections: List<Collection>) {
        val inPublic = collections.filter { it.isPublic }.flatMap { it.audioIds }.toSet()
        val inPrivate = collections.filter { it.isPrivate }.flatMap { it.audioIds }.toSet()
        val inCustomPrivate = collections.filter { it.isPrivate && !it.isSystem }.flatMap { it.audioIds }.toSet()
        var publicDefault = 0
        var publicCustom = 0
        var vaultDefault = 0
        var vaultCustom = 0
        for (sound in allSoundsCache.value) {
            if (sound.isBundled()) continue
            when {
                sound.id in inPublic -> publicCustom++
                sound.id !in inPrivate -> publicDefault++
                sound.id in inCustomPrivate -> vaultCustom++
                else -> vaultDefault++
            }
        }
        tracker.setUserProperty(AnalyticsUserProperty.CURRENT_PUBLIC_DEFAULT, publicDefault.toString())
        tracker.setUserProperty(AnalyticsUserProperty.CURRENT_PUBLIC_CUSTOM, publicCustom.toString())
        tracker.setUserProperty(AnalyticsUserProperty.CURRENT_VAULT_DEFAULT, vaultDefault.toString())
        tracker.setUserProperty(AnalyticsUserProperty.CURRENT_VAULT_CUSTOM, vaultCustom.toString())
    }

    private fun recomputeVaultAudios() {
        val collections = _collections.value
        val activeFilter = _activeVaultFilter.value
        val privateCollections = collections.filter { it.isPrivate }
        val targetIds =
            if (activeFilter != null) {
                privateCollections
                    .firstOrNull { it.id == activeFilter }
                    ?.audioIds
                    ?.toSet()
                    .orEmpty()
            } else {
                privateCollections.flatMap { it.audioIds }.toSet()
            }
        if (targetIds.isEmpty()) {
            _vaultAudios.value = emptyList()
            return
        }
        val library = allSoundsCache.value
        _vaultAudios.value =
            library
                .filter { it.id in targetIds }
                .sortedWith(
                    compareByDescending<Sound> { it.isPinned }
                        .thenByDescending { it.dateAdded ?: Long.MIN_VALUE }
                        .thenBy { it.name.lowercase() },
                )
    }

    private val tracker get() = AnalyticsTrackerProvider.get(getApplication())

    /**
     * Surface label for events fired from this ViewModel. Mirrors the screen_view emitted by `LandingScreen` so
     * `surface` and the most recent `screen_name` always agree.
     *
     * The About screen is intentionally NOT modelled here: when `isAboutVisible == true`, `LandingScreen` early
     * returns and the FAB / sound list / search overlay are not rendered, so neither `playOrStop` nor `share` can
     * fire from the UI. The regression for that invariant lives in
     * `LandingScreenAnalyticsTest.no play or share UI is reachable while About is open`.
     */
    private val currentSurface: String
        get() =
            when {
                _isSearchVisible.value -> CanonicalScreenName.SEARCH_SOUND
                selectedTab.value == AppTab.MY_SOUNDS -> CanonicalScreenName.MY_SOUNDS
                selectedTab.value == AppTab.VAULT -> CanonicalScreenName.VAULT
                else -> CanonicalScreenName.EXPLORE_SOUNDS
            }

    fun showSearch() {
        _isSearchVisible.value = true
    }

    fun hideSearch() {
        searchDebounceJob?.cancel()
        _isSearchVisible.value = false
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearchPending.value = false
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchDebounceJob?.cancel()
        if (searchDebounceMs == 0L || query.isBlank()) {
            _isSearchPending.value = false
            recomputeSearchResults()
        } else {
            _isSearchPending.value = true
            searchDebounceJob =
                viewModelScope.launch {
                    delay(searchDebounceMs)
                    recomputeSearchResults()
                    _isSearchPending.value = false
                }
        }
    }

    private fun recomputeSearchResults() {
        val query = _searchQuery.value
        // Privacy gate: while the Vault session is locked, audios tagged exclusively to a
        // private collection must NOT surface on the search overlay (it's reachable from public
        // tabs only — My Sounds + Explore). Cross-tagged (public AND private) audios are excluded
        // from this filter on purpose, per spec § 3.1 "Restricción de cross-preset": the public
        // scope preserves visibility. Once the user authenticates the Vault during this process
        // (`VaultSessionState.markVaultOpen`), the filter relaxes and search merges both scopes.
        val privateOnlyIds =
            if (com.github.barriosnahuel.vossosunboton.feature.vault.security.VaultSessionState
                    .isVaultOpen()
            ) {
                emptySet()
            } else {
                collectionsAudioIdsSnapshot()
            }
        _searchResults.value =
            if (query.isBlank()) {
                emptyList()
            } else {
                allSoundsCache.value
                    .filter { it.id !in privateOnlyIds }
                    .filter { it.name.contains(query, ignoreCase = true) }
                    .sortedWith(compareByDescending<Sound> { it.isPinned }.thenBy { it.name.lowercase() })
            }
        if (query.isNotBlank() && _searchResults.value.isEmpty()) {
            tracker.log(AnalyticsEvent.SearchZeroResults(queryLength = query.length))
        }
    }

    fun selectTab(tab: AppTab) {
        savedStateHandle[KEY_SELECTED_TAB] = tab
        // Optimistically rebuild `_sounds` from the in-memory caches before kicking off the
        // async loadSounds. Without this, the freshly selected tab renders an empty list (the
        // previous tab's filtered view) until the IO chain settles — the user sees the empty
        // state flash on every tab swap. The tab is passed explicitly: reading it back through
        // the SavedStateHandle flow here observed the PREVIOUS value, which re-emptied the list
        // when leaving the Vault (whose projection is empty by design).
        applyTabFilterFromCache(tab)
        viewModelScope.launch(ioDispatcher) { loadSounds() }
    }

    /**
     * Synchronous projection of the currently-cached `allSoundsCache` to `_sounds` based on the
     * active tab. Used to bridge the visual gap between [selectTab] and the loadSounds emission —
     * keeps the body composable rendering the right list instead of bouncing through the empty
     * state. Returns whatever `applyCollectionFilter` produces for the active tab + chip.
     */
    private fun applyTabFilterFromCache(tab: AppTab) {
        val snapshot = allSoundsCache.value
        if (snapshot.isEmpty()) return
        // Mirrors the byTab branch in loadSounds so the tab swap doesn't flash an empty list.
        _sounds.value =
            when (tab) {
                AppTab.MY_SOUNDS -> mySoundsProjection(snapshot)
                AppTab.EXPLORE_SOUNDS -> snapshot.filter { it.isBundled() }
                // VaultScreen renders its own list and never reads `_sounds`; project the HOME
                // list here because (a) emptying flashed the outgoing tab's empty state mid-swap
                // and (b) a predictive-back preview from the Vault base always composes Home
                // (exit-through-home), which must show its own rows, not the previous tab's.
                AppTab.VAULT -> mySoundsProjection(snapshot)
            }
    }

    private fun collectionsAudioIdsSnapshot(): Set<String> {
        val list = _collections.value
        val inPrivate = list.filter { it.isPrivate }.flatMap { it.audioIds }.toSet()
        if (inPrivate.isEmpty()) return emptySet()
        val inPublic = list.filter { it.isPublic }.flatMap { it.audioIds }.toSet()
        return inPrivate - inPublic
    }

    /**
     * Activates the Vault filter chip to [collectionId], or to `null` for the "All private audios"
     * pseudo-chip. Idempotent. Persists the choice so the next cold start lands on the same chip.
     */
    fun selectVaultFilter(collectionId: String?) {
        if (_activeVaultFilter.value == collectionId) return
        _activeVaultFilter.value = collectionId
        recomputeVaultAudios()
        val storeValue =
            collectionId ?: com.github.barriosnahuel.vossosunboton.feature.vault.VaultFilterStore.ALL_SENTINEL
        viewModelScope.launch(ioDispatcher) {
            runCatching { vaultFilterStore.set(storeValue) }
                .onFailure {
                    Tracker.log("vault.filter_persist_failed=${it.javaClass.simpleName}")
                    Tracker.track(RuntimeException("Vault filter persist failed", it))
                }
        }
        if (collectionId != null) {
            tracker.log(AnalyticsEvent.CollectionFilterApply(matches = _vaultAudios.value.size, scope = AnalyticsScope.PRIVATE))
        }
    }

    /**
     * Activates the My Sounds public-collection filter to [collectionId], or to `null` for the
     * "All" pseudo-chip. Idempotent. Persists the choice so the next cold start lands on the
     * same chip — spec § 7 (Preguntas Abiertas → "persist last filter, favorece monocontexto").
     */
    fun selectMySoundsFilter(collectionId: String?) {
        if (_activeMySoundsFilter.value == collectionId) return
        _activeMySoundsFilter.value = collectionId
        val storeValue = collectionId ?: MySoundsFilterStore.ALL_SENTINEL
        viewModelScope.launch(ioDispatcher) {
            runCatching { mySoundsFilterStore.set(storeValue) }
                .onFailure {
                    Tracker.log("collections.filter_persist_failed=${it.javaClass.simpleName}")
                    Tracker.track(RuntimeException("My Sounds filter persist failed", it))
                }
            loadSounds()
        }
        if (collectionId != null) {
            val matches = audiosIn(collectionId).size
            tracker.log(AnalyticsEvent.CollectionFilterApply(matches = matches, scope = AnalyticsScope.PUBLIC))
        }
    }

    private fun audiosIn(collectionId: String): List<Sound> {
        val target = _collections.value.firstOrNull { it.id == collectionId } ?: return emptyList()
        val ids = target.audioIds.toSet()
        return allSoundsCache.value.filter { it.id in ids }
    }

    /**
     * Records that the user opened a collection for viewing from the Manage Collections overflow
     * ("View collection"). Kept in the VM so all collection analytics live in one place. [isPublic]
     * maps to the event's `scope` param. Navigation (selectTab + filter) happens at the call-site.
     */
    fun trackCollectionView(isPublic: Boolean) {
        tracker.log(AnalyticsEvent.CollectionView(scope = AnalyticsScope.of(isPublic)))
    }

    /**
     * Returns the set of audio ids that belong to at least one PRIVATE collection AND zero PUBLIC
     * collections — the **pre-flag** "Vault-only" rule (`inPrivate - inPublic`). My Sounds
     * visibility no longer derives from this (ADR 0012 made it the explicit [Sound.isVisibleInMySounds]
     * flag); this now feeds **only** the one-time [SoundsRepository.migrateVisibilityIfNeeded] seed,
     * which flips exactly these audios to `isVisibleInMySounds = false` so the post-update list
     * matches the old derived list. The search/Vault privacy gate uses the membership-based
     * [collectionsAudioIdsSnapshot] instead.
     */
    private suspend fun privateOnlyAudioIds(): Set<String> {
        // Read directly from the repo's freshest snapshot instead of `_collections.value` — the
        // VM's two init coroutines (welcome-sticker hydration + collections observation) race
        // against each other and the welcome path can call loadSounds before the collections
        // observer has populated `_collections`. Asking the repo here makes the filter robust
        // against that ordering: each loadSounds always sees the post-seed, post-tagging state.
        val collectionsSnapshot = collectionsRepo.collections.first()
        val inPrivate = collectionsSnapshot.filter { it.isPrivate }.flatMap { it.audioIds }.toSet()
        if (inPrivate.isEmpty()) return emptySet()
        val inPublic = collectionsSnapshot.filter { it.isPublic }.flatMap { it.audioIds }.toSet()
        return inPrivate - inPublic
    }

    /**
     * Projects [library] to the My Sounds list (ADR 0012).
     *
     * - **A public chip is active** → that collection's members, by membership only. A chip
     *   surfaces a member even when its [Sound.isVisibleInMySounds] is `false` — "Todo" and the
     *   chips are independent axes, so an audio never disappears from its own collection's filter.
     *   When the active filter id points to a just-deleted collection (delete↔tab-swap race) the
     *   set is empty; the init observer clears the stale id before the next `loadSounds`.
     * - **No chip ("Todo")** → every non-bundled audio whose [Sound.isVisibleInMySounds] is `true`.
     *
     * The Vault-privacy hide rule does NOT live here — that is the membership-based search/Vault
     * gate ([collectionsAudioIdsSnapshot]), which the explicit flag deliberately does not touch.
     */
    private fun mySoundsProjection(library: List<Sound>): List<Sound> {
        val custom = library.filter { !it.isBundled() }
        val activeChip = _activeMySoundsFilter.value.takeIf { selectedTab.value == AppTab.MY_SOUNDS }
        return if (activeChip != null) {
            val ids =
                _collections.value
                    .firstOrNull { it.id == activeChip }
                    ?.audioIds
                    .orEmpty()
                    .toSet()
            custom.filter { it.id in ids }
        } else {
            custom.filter { it.isVisibleInMySounds }
        }
    }

    fun togglePin(sound: Sound) {
        val nowPinned = !sound.isPinned
        val sortedList = { list: List<Sound> ->
            list
                .map { if (it.id == sound.id) it.copy(isPinned = nowPinned) else it }
                .sortedWith(
                    compareByDescending<Sound> { it.isPinned }
                        .thenByDescending { it.dateAdded ?: Long.MIN_VALUE }
                        .thenBy { it.name.lowercase() },
                )
        }
        _sounds.update(sortedList)
        allSoundsCache.update { list -> list.map { if (it.id == sound.id) it.copy(isPinned = nowPinned) else it } }
        recomputeSearchResults()
        recomputeVaultAudios()
        if (nowPinned) _scrollToTopEvent.trySend(Unit)
        viewModelScope.launch(ioDispatcher) {
            repo.savePin(sound.id, sound.name, nowPinned)
        }
        tracker.log(AnalyticsEvent.PinToggle(pinned = nowPinned))
        tracker.setUserProperty(
            AnalyticsUserProperty.CURRENT_PINNED,
            allSoundsCache.value.count { it.isPinned }.toString(),
        )
    }

    fun playOrStop(sound: Sound) {
        if (sound.isPlaying) {
            // Tap-while-playing pauses rather than stops. The controller fires onPlayerPause (not
            // onPlayerStop): the play icon returns because the sound is no longer the active one,
            // but its position is retained in _pausedProgress so the progress bar stays put.
            PlayerControllerFactory.instance.pause()
        } else {
            beginTapToSoundSpan()
            PlayerControllerFactory.instance.startPlayingSound(getApplication(), sound)
            if (isWelcomeSticker(sound)) {
                tracker.log(AnalyticsEvent.WelcomeStickerPlay)
            } else {
                tracker.log(AnalyticsEvent.SoundPlay(surface = currentSurface))
                val newCount = tracker.incrementCounter(AnalyticsUserProperty.LIFETIME_PLAYS)
                tracker.setUserProperty(AnalyticsUserProperty.LIFETIME_PLAYS, newCount.toString())
            }
        }
    }

    /**
     * Starts [sound] as an immersive listen session on the long-form engine — always from 0, no
     * cross-open position retention (ADR 0022). Same play analytics as a list tap; playback events
     * arrive through the same [PlayerControllerListener] callbacks, so the UI state
     * ([playingSound], [playbackProgress], [pausedProgress]) works unchanged. In-session toggles
     * keep routing through [playOrStop] — the controller resolves the engine.
     */
    fun startListenSession(sound: Sound) {
        PlayerControllerFactory.instance.startListenSession(getApplication(), sound)
        tracker.log(AnalyticsEvent.SoundPlay(surface = currentSurface))
        val newCount = tracker.incrementCounter(AnalyticsUserProperty.LIFETIME_PLAYS)
        tracker.setUserProperty(AnalyticsUserProperty.LIFETIME_PLAYS, newCount.toString())
    }

    /**
     * Definitive stop of whatever is playing on either engine (position discarded). The immersive's
     * back gesture uses this — a session must not survive its surface (ADR 0022: no retention).
     */
    fun stopPlayback() {
        PlayerControllerFactory.instance.stopPlayingSound()
    }

    fun seekTo(positionMs: Int) {
        PlayerControllerFactory.instance.seekTo(positionMs)
        _playbackProgress.update { it?.copy(positionMs = positionMs) }
    }

    /**
     * Seek used by the immersive listen player (waveform scrub + "back to start"). Unlike the
     * single-arg [seekTo] — which only the playing card uses — this also keeps a *paused* audio's
     * retained position in sync ([pausedProgress]) so the wave and time reflect the new spot. The
     * MediaPlayer head moves either way, so a later resume (or preempt) continues from there.
     */
    fun seekTo(
        positionMs: Int,
        soundId: String,
    ) {
        PlayerControllerFactory.instance.seekTo(positionMs)
        _playbackProgress.update { it?.copy(positionMs = positionMs) }
        _pausedProgress.update { current ->
            current[soundId]?.let { current + (soundId to it.copy(positionMs = positionMs)) } ?: current
        }
    }

    fun share(sound: Sound) {
        viewModelScope.launch {
            // applicationContext (via getApplication()) is enough: file resolution + FileProvider
            // are activity-agnostic. Activity context only re-enters at launchChooser, on the UI side.
            when (val outcome = shareFeature.prepareShareIntent(getApplication(), sound, currentSurface)) {
                is ShareIntentOutcome.Success -> _shareIntentEvent.send(outcome)
                is ShareIntentOutcome.Failure -> _shareErrorEvent.send(outcome.feedback)
            }
        }
    }

    fun deleteSound(sound: Sound) {
        val isWelcome = isWelcomeSticker(sound)
        // `_sounds.value` only contains the audios visible on the *currently-selected* tab. On
        // the Vault tab it is empty by design (VaultScreen reads `_vaultAudios` directly), so a
        // swipe-to-delete from the Vault would have early-returned here and left the dismissed
        // card in zombie state — the SwipeToDismissBox stuck at EndToStart, snackbar never
        // firing. Look the sound up in `allSoundsCache` instead so deletion works regardless of
        // which tab triggered it, and treat the visible-list update as optional bookkeeping.
        val sourceList = if (isWelcome) _sounds.value else allSoundsCache.value
        val resolvedSound = sourceList.firstOrNull { it.id == sound.id } ?: return
        val sourcePosition = sourceList.indexOf(resolvedSound)

        // Always ask the controller to forget this sound: it cleans up the saved-position cache
        // and, if this sound is the currently-loaded MediaPlayer target (playing OR paused),
        // stops and resets it. Going through _playingSound here would miss the paused case (after
        // the play/pause unification, `_playingSound` is null while the sound is paused but the
        // controller still holds the data source).
        PlayerControllerFactory.instance.forgetSound(sound)

        // Drop from the visible list when present. On Vault tab the audio is not in `_sounds` —
        // `recomputeVaultAudios()` below handles the equivalent update for `_vaultAudios`.
        val visibleIndex = _sounds.value.indexOfFirst { it.id == sound.id }
        if (visibleIndex >= 0) {
            _sounds.value = _sounds.value.toMutableList().also { it.removeAt(visibleIndex) }
        }
        if (!isWelcome) {
            // Welcome sticker is never in `allSoundsCache` (kept out of search) — skip the update.
            allSoundsCache.update { list -> list.filter { it.id != sound.id } }
            recomputeSearchResults()
            recomputeVaultAudios()
        }
        // A new deletion supersedes the previous undo window (that snackbar is already gone), so
        // commit the previous pending deletion now. Without this, rapid back-to-back deletes overwrite
        // the single pending slot and only the LAST one is ever persisted; the earlier sounds are
        // dropped from the in-memory list but left on disk, and the next repo re-emit resurrects them
        // (the "delete several Bomps → they reappear when the last snackbar fades" bug).
        if (_deletedSoundEvent.value != null) {
            confirmDelete()
        }
        _deletedSoundEvent.value = DeletedSoundEvent(resolvedSound.copy(isPlaying = false), sourcePosition)
        if (isWelcome) {
            _welcomeStickerVisible.value = false
            tracker.log(AnalyticsEvent.WelcomeStickerDismissed)
        }
    }

    fun restoreSound() {
        val event = _deletedSoundEvent.value ?: return
        val isWelcome = isWelcomeSticker(event.sound)
        // Mirror the symmetric guard in `deleteSound`: on the Vault tab the audio is never
        // directly in `_sounds`, so mutating that list would push a private-only audio into the
        // My Sounds projection until the next loadSounds wipes it. Keep `_sounds` untouched on
        // Vault and let `recomputeVaultAudios()` do the user-visible work.
        val shouldUpdateVisibleSounds = isWelcome || selectedTab.value != AppTab.VAULT
        if (shouldUpdateVisibleSounds) {
            val currentSounds = _sounds.value.toMutableList()
            if (isWelcome) {
                // Re-insert the welcome and sort it back into place by date — it behaves like any
                // other audio. `event.sound` already carries its `dateAdded`.
                currentSounds.add(event.sound)
                currentSounds.sortWith(SOUND_ORDER)
            } else {
                val insertPosition = event.position.coerceAtMost(currentSounds.size)
                currentSounds.add(insertPosition, event.sound)
            }
            _sounds.value = currentSounds
        }
        if (!isWelcome) {
            val insertPosition = event.position.coerceAtMost(allSoundsCache.value.size)
            allSoundsCache.update { list ->
                val allInsertPosition = insertPosition.coerceAtMost(list.size)
                list.toMutableList().also { it.add(allInsertPosition, event.sound) }
            }
            recomputeSearchResults()
            recomputeVaultAudios()
        }
        _deletedSoundEvent.value = null
        if (isWelcome) {
            // Update in-memory state synchronously so the UI flips immediately; persist the
            // restore fire-and-forget on IO so the snackbar interaction stays responsive.
            _welcomeStickerVisible.value = true
            restoreWelcomeAsync()
        }
        // Welcome is just-another-audio now, so its Undo logs the generic `sound_delete_undone` like
        // any other (the welcome-specific `welcome_sticker_undone` was pruned).
        tracker.log(AnalyticsEvent.SoundDeleteUndone)
    }

    fun confirmDelete() {
        val event = _deletedSoundEvent.value
        _deletedSoundEvent.value = null
        when {
            event == null -> Unit
            isWelcomeSticker(event.sound) -> consumeWelcomeAsync()
            event.sound.isBundled() -> Unit
            else -> {
                deletePersistedSoundAsync(event.sound)
                forgetAudioFromCollectionsAsync(event.sound.id)
            }
        }
    }

    private fun forgetAudioFromCollectionsAsync(audioId: String) {
        viewModelScope.launch(ioDispatcher) {
            runCatching { collectionsRepo.forgetAudio(audioId) }
                .onFailure {
                    Tracker.log("collections.forget_audio_failed=${it.javaClass.simpleName}")
                    Tracker.track(RuntimeException("Failed to sweep audio from collections", it))
                }
        }
    }

    /**
     * Creates a new collection of [access] scope. Emits a [AnalyticsEvent.CollectionCreate] event;
     * caller is responsible for any UI feedback (snackbar, sheet dismissal). Failures (e.g.
     * duplicate name) surface as [Result.failure] — caller maps to the right field error.
     */
    suspend fun createCollection(
        name: String,
        access: CollectionAccess,
        source: String,
    ): Result<Collection> =
        runCatching {
            val profile =
                when (access) {
                    CollectionAccess.PUBLIC -> com.github.barriosnahuel.vossosunboton.model.CollectionProfile.GENERIC_PUBLIC
                    CollectionAccess.PRIVATE -> com.github.barriosnahuel.vossosunboton.model.CollectionProfile.VAULT
                }
            collectionsRepo.create(name = name, profile = profile).also { _ ->
                tracker.log(
                    AnalyticsEvent.CollectionCreate(
                        scope = AnalyticsScope.of(access == CollectionAccess.PUBLIC),
                        audios = 0,
                        source = source,
                    ),
                )
                val newCount = tracker.incrementCounter(AnalyticsUserProperty.LIFETIME_COLLECTION_CREATES)
                tracker.setUserProperty(AnalyticsUserProperty.LIFETIME_COLLECTION_CREATES, newCount.toString())
            }
        }

    suspend fun renameCollection(
        id: String,
        newName: String,
    ): Result<Unit> =
        runCatching {
            val before = _collections.value.firstOrNull { it.id == id }
            collectionsRepo.rename(id, newName)
            before?.let {
                tracker.log(
                    AnalyticsEvent.CollectionRename(
                        scope = AnalyticsScope.of(it.isPublic),
                    ),
                )
                val newCount = tracker.incrementCounter(AnalyticsUserProperty.LIFETIME_COLLECTION_RENAMES)
                tracker.setUserProperty(AnalyticsUserProperty.LIFETIME_COLLECTION_RENAMES, newCount.toString())
            }
        }

    /**
     * Deletes a collection. Refuses to delete system collections (the UI must hide the action,
     * but we double-check here per CLAUDE.md error-tracking conventions). Audios are NOT deleted.
     */
    suspend fun deleteCollection(id: String): Result<Unit> =
        runCatching {
            val target =
                _collections.value.firstOrNull { it.id == id }
                    ?: return@runCatching
            require(!target.isSystem) { "Refusing to delete system collection $id" }
            val priorCount = target.audioIds.size
            collectionsRepo.delete(id)
            tracker.log(
                AnalyticsEvent.CollectionDelete(
                    scope = AnalyticsScope.of(target.isPublic),
                    audios = priorCount,
                ),
            )
            val newCount = tracker.incrementCounter(AnalyticsUserProperty.LIFETIME_COLLECTION_DELETES)
            tracker.setUserProperty(AnalyticsUserProperty.LIFETIME_COLLECTION_DELETES, newCount.toString())
        }

    /**
     * Replaces the set of [collectionIds] this audio belongs to inside [scope]. The other access
     * scope (e.g. private when committing public chips) is untouched — the UI commits public and
     * private chip groups via two independent calls.
     */
    suspend fun setAudioCollections(
        audioId: String,
        collectionIds: Set<String>,
        scope: CollectionAccess,
    ): Result<Unit> =
        runCatching {
            collectionsRepo.setAudioCollections(audioId, collectionIds, scope)
        }

    /** Removes a single [audioId] from [collectionId] without touching its other tags. */
    fun removeAudioFromCollection(
        collectionId: String,
        audioId: String,
    ) {
        viewModelScope.launch(ioDispatcher) {
            runCatching { collectionsRepo.removeAudio(collectionId, audioId) }
                .onFailure {
                    Tracker.log("collections.remove_audio_failed=${it.javaClass.simpleName}")
                    Tracker.track(RuntimeException("Failed to remove audio from collection", it))
                }
        }
    }

    /**
     * Opens the create-collection sheet for [access] scope. [source] is the analytics surface that
     * triggered it (flows into collection_create.source). Triggered from filter row, Vault FAB,
     * and Manage.
     */
    fun requestCreateCollection(
        access: CollectionAccess,
        source: String,
    ) {
        _activeCollectionSheet.value = CollectionSheetRequest.Create(access, source)
    }

    /** Opens the long-press → "Add to collection" sheet for [audioId]. */
    fun requestAssignCollections(audioId: String) {
        _activeAssignAudioId.value = audioId
    }

    /**
     * Cancels the assign sheet: the sheet is transactional (ADR 0012), so backing out **discards**
     * the staged collection + visibility edits. Nothing is persisted and no coach/undo fires —
     * those belong to [applyAssignment] ("Listo").
     */
    fun dismissAssignCollections() {
        _activeAssignAudioId.value = null
    }

    /**
     * Commits the assign sheet's staged state in one transaction ("Listo"), then fires the close
     * effects against the final state (ADR 0012). Replaces the audio's membership per scope, applies
     * the visibility delta, and emits the same per-delta `collection_audio_toggle` analytics the old
     * live-commit chips did. Backing out of the sheet does NOT call this — staged edits are dropped.
     */
    fun applyAssignment(
        audioId: String,
        name: String,
        publicCollectionIds: Set<String>,
        privateCollectionIds: Set<String>,
        isVisibleInMySounds: Boolean,
    ) {
        _activeAssignAudioId.value = null
        viewModelScope.launch(ioDispatcher) {
            val before = collectionsRepo.collections.first()
            val currentPublic = before.filter { it.isPublic && audioId in it.audioIds }.map { it.id }.toSet()
            val currentPrivate = before.filter { it.isPrivate && audioId in it.audioIds }.map { it.id }.toSet()

            runCatching {
                collectionsRepo.setAudioCollections(audioId, publicCollectionIds, CollectionAccess.PUBLIC)
                collectionsRepo.setAudioCollections(audioId, privateCollectionIds, CollectionAccess.PRIVATE)
            }.onSuccess {
                logAssignmentDeltas(currentPublic, publicCollectionIds, isPublic = true)
                logAssignmentDeltas(currentPrivate, privateCollectionIds, isPublic = false)
            }.onFailure {
                Tracker.log("collections.apply_assignment_failed=${it.javaClass.simpleName}")
                Tracker.track(RuntimeException("Failed to apply collection assignment", it))
            }

            // Anti-orphan safety net: never persist hidden for an audio that lives in NO collection
            // (public or private) — it'd be reachable from nowhere. The UI gate prevents reaching
            // this, but the invariant is enforced here too.
            val safeVisible = isVisibleInMySounds || !isReachableOutsideMySounds(publicCollectionIds, privateCollectionIds)
            val currentVisible = allSoundsCache.value.firstOrNull { it.id == audioId }?.isVisibleInMySounds ?: true
            if (safeVisible != currentVisible) {
                // Updates the cache synchronously + persists + logs visibility_toggle. The sync cache
                // update is what evaluateAssignCloseEffects below reads for the final visibility.
                setAudioVisibleInMySounds(audioId, name, safeVisible)
            }
            evaluateAssignCloseEffects(audioId)
        }
    }

    private fun logAssignmentDeltas(
        current: Set<String>,
        next: Set<String>,
        isPublic: Boolean,
    ) {
        val scope = AnalyticsScope.of(isPublic)
        (next - current).forEach { tracker.log(AnalyticsEvent.CollectionAudioToggle(assigned = true, scope = scope)) }
        (current - next).forEach { tracker.log(AnalyticsEvent.CollectionAudioToggle(assigned = false, scope = scope)) }
        var newCount = 0L
        repeat((next - current).size) { newCount = tracker.incrementCounter(AnalyticsUserProperty.LIFETIME_COLLECTION_ASSIGNS) }
        if ((next - current).isNotEmpty()) {
            tracker.setUserProperty(AnalyticsUserProperty.LIFETIME_COLLECTION_ASSIGNS, newCount.toString())
        }
    }

    /**
     * Persists [Sound.isVisibleInMySounds] for [audioId] and reflects it instantly in BOTH the catalog
     * cache and the visible "Todo" list (ADR 0012). The DataStore write re-triggers `loadSounds`
     * through the repo observer, which reprojects the visible list; updating [allSoundsCache] and
     * [_sounds] here keeps the sheet's switch and the list consistent in the meantime — a full mirror
     * of [togglePin]'s optimistic shape, so the toggle never has to wait for the DataStore round-trip.
     */
    fun setAudioVisibleInMySounds(
        audioId: String,
        name: String,
        visible: Boolean,
    ) {
        allSoundsCache.update { list ->
            list.map { if (it.id == audioId) it.copy(isVisibleInMySounds = visible) else it }
        }
        // Optimistically reproject the visible list so "Todo" updates synchronously instead of only
        // after the DataStore write round-trips back through `loadSounds` (the source of the earlier
        // flaky timeout under CI load). Only the unfiltered My Sounds view is visibility-driven: a
        // public chip surfaces members by membership (ADR 0012) and other tabs don't project by
        // visibility, so leave those to the reactive `loadSounds`, which reprojects to the same list.
        if (selectedTab.value == AppTab.MY_SOUNDS && _activeMySoundsFilter.value == null) {
            _sounds.update { current -> reprojectVisibilityToggle(current, audioId, visible, allSoundsCache.value, SOUND_ORDER) }
        }
        viewModelScope.launch(ioDispatcher) {
            runCatching { repo.saveVisibility(audioId, name, visible) }
                .onFailure {
                    Tracker.log("my_sounds.visibility_persist_failed=${it.javaClass.simpleName}")
                    Tracker.track(RuntimeException("My Sounds visibility persist failed", it))
                }
        }
        tracker.log(AnalyticsEvent.VisibilityToggle(visible = visible))
    }

    /**
     * On assign-sheet close, fires at most one teaching/feedback snackbar (ADR 0012), evaluated
     * against the audio's final state:
     * - hidden from My Sounds AND still in a private collection → "Lo moviste al Baúl" + Undo.
     * - visible in My Sounds AND in a private collection, coach not yet seen → the one-time
     *   dual-home coach (then mark it seen so it never repeats).
     *
     * Membership is read from the freshest collections snapshot, not `_collections.value`, so a
     * just-committed tag toggle is reflected even if the StateFlow has not re-emitted yet.
     */
    private suspend fun evaluateAssignCloseEffects(audioId: String) {
        val sound = allSoundsCache.value.firstOrNull { it.id == audioId } ?: return
        val inPrivate =
            collectionsRepo.collections.first().any { it.isPrivate && audioId in it.audioIds }
        if (!inPrivate) return
        when {
            !sound.isVisibleInMySounds ->
                _movedToVaultEvent.trySend(MovedToVaultEvent(audioId = audioId, name = sound.name))
            !dualHomeCoachStore.hasSeen() -> {
                _dualHomeCoachEvent.trySend(Unit)
                dualHomeCoachStore.markSeen()
            }
        }
    }

    /**
     * Toggles whether [audioId] belongs to [collectionId]. Implemented as a single mutation so
     * the sheet can be optimistic and let the DataStore round-trip catch up. The reverse-index
     * `audioCollectionNames` re-emits automatically when [collectionsRepo.collections] does.
     */
    fun toggleAudioInCollection(
        audioId: String,
        collectionId: String,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val current = collectionsRepo.collections.first()
            val target = current.firstOrNull { it.id == collectionId } ?: return@launch
            val alreadyIn = audioId in target.audioIds
            val outcome =
                runCatching {
                    if (alreadyIn) {
                        collectionsRepo.removeAudio(collectionId, audioId)
                    } else {
                        collectionsRepo.addAudio(collectionId, audioId)
                    }
                }
            outcome.onFailure {
                Tracker.log("collections.toggle_audio_failed=${it.javaClass.simpleName}")
                Tracker.track(RuntimeException("Failed to toggle audio in collection", it))
            }
            outcome.onSuccess {
                tracker.log(
                    AnalyticsEvent.CollectionAudioToggle(
                        assigned = !alreadyIn,
                        scope = AnalyticsScope.of(target.isPublic),
                    ),
                )
                val newCount = tracker.incrementCounter(AnalyticsUserProperty.LIFETIME_COLLECTION_ASSIGNS)
                tracker.setUserProperty(AnalyticsUserProperty.LIFETIME_COLLECTION_ASSIGNS, newCount.toString())
            }
        }
    }

    /** Opens the rename sheet pre-filled with the existing collection. No-op for unknown id. */
    fun requestRenameCollection(id: String) {
        val target = _collections.value.firstOrNull { it.id == id } ?: return
        _activeCollectionSheet.value = CollectionSheetRequest.Rename(id = id, currentName = target.name)
    }

    fun dismissCollectionSheet() {
        _activeCollectionSheet.value = null
    }

    fun requestDeleteConfirmation(collectionId: String) {
        val target = _collections.value.firstOrNull { it.id == collectionId } ?: return
        if (target.isSystem) return
        _pendingCollectionDelete.value = collectionId
    }

    fun dismissDeleteConfirmation() {
        _pendingCollectionDelete.value = null
    }

    fun confirmCollectionDelete() {
        val target = _pendingCollectionDelete.value ?: return
        _pendingCollectionDelete.value = null
        viewModelScope.launch(ioDispatcher) { deleteCollection(target) }
    }

    /**
     * Resolves where the welcome sticker belongs in the visible MY_SOUNDS list. Returns [filtered]
     * unchanged for non-MY_SOUNDS tabs, when the sticker is consumed, or while the snackbar window
     * is hiding it. Otherwise the welcome is added to the list and sorted by [SOUND_ORDER] like any
     * other audio: its [installTs] is its `dateAdded`, so on a fresh install it lands at the top
     * (newest) and naturally sinks as the Bomper adds their own audios.
     *
     * [welcomeIsPendingDismissal] and [installTs] are snapshotted at the top of `loadSounds` so this
     * stays a pure function — easier to test, and avoids issuing extra DataStore reads inside the
     * `_sounds.update` lambda.
     */
    private fun positionWelcomeIn(
        filtered: List<Sound>,
        welcomeIsPendingDismissal: Boolean,
        installTs: Long,
    ): List<Sound> {
        val shouldShowWelcome =
            selectedTab.value == AppTab.MY_SOUNDS &&
                _welcomeStickerVisible.value &&
                !welcomeIsPendingDismissal
        if (!shouldShowWelcome) return filtered
        val welcome = welcomeSticker(getApplication(), dateAdded = installTs)
        return (filtered + welcome).sortedWith(SOUND_ORDER)
    }

    private fun deletePersistedSoundAsync(sound: Sound) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repo.delete(sound)
                tracker.log(AnalyticsEvent.SoundDelete)
                tracker.setUserProperty(
                    AnalyticsUserProperty.CURRENT_SOUNDS,
                    allSoundsCache.value.count { !it.isBundled() }.toString(),
                )
            } catch (e: IllegalStateException) {
                Timber.w(e, "Sound has no file on disk, skipping delete")
            }
        }
    }

    private fun consumeWelcomeAsync() {
        viewModelScope.launch(ioDispatcher) {
            try {
                welcomeStore.consume()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                // The in-memory cache flip in `welcomeStore.consume()` already happened, so the
                // current process behaves correctly. Surface the persistence failure so we can see
                // why the welcome reappears on the next cold start.
                Tracker.track(RuntimeException("Welcome sticker consume persistence failed", e))
            }
        }
    }

    private fun restoreWelcomeAsync() {
        viewModelScope.launch(ioDispatcher) {
            try {
                welcomeStore.restore()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                // Same shape as `consumeWelcomeAsync` — the in-memory state is already correct; we
                // only lose the disk persistence of the re-enabled flag.
                Tracker.track(RuntimeException("Welcome sticker restore persistence failed", e))
            }
        }
    }

    /**
     * Called by the UI once the one-time swipe-hint nudge has run on the welcome card. Flips the
     * in-memory pending flag immediately and persists it fire-and-forget so the nudge never repeats.
     */
    fun markWelcomeHintShown() {
        if (!_welcomeHintPending.value) return
        welcomeHintConsumed = true
        _welcomeHintPending.value = false
        viewModelScope.launch(ioDispatcher) {
            try {
                welcomeStore.markHintShown()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                Tracker.track(RuntimeException("Welcome sticker hint persistence failed", e))
            }
        }
    }

    fun clearDeleteEvent() {
        _deletedSoundEvent.value = null
    }

    private suspend fun loadSounds() {
        loadSoundsMutex.withLock {
            val allSounds = repo.sounds.first().sortedWith(SOUND_ORDER)
            val cachedDurations = repo.durations.first()
            // Read once per loadSounds — pure function over a snapshotted value, easier to test. The
            // welcome's install timestamp is its `dateAdded` so it sorts by date with user audios.
            val welcomeInstallTs = welcomeStore.installTimestamp()
            // The swipe-hint nudge is eligible while the welcome is showing and the nudge hasn't run.
            _welcomeHintPending.value =
                !welcomeHintConsumed &&
                _welcomeStickerVisible.value &&
                !welcomeStore.isHintShown()
            _soundDurations.update { current -> cachedDurations + current }
            _hasBundledSounds.value = allSounds.any { it.isBundled() }
            val playingId = _playingSound.value?.id
            val deletedSound = _deletedSoundEvent.value?.sound
            val deletedId = deletedSound?.id
            val welcomeIsPendingDismissal = deletedSound != null && isWelcomeSticker(deletedSound)
            // Exclude the pending soft-deletion so a repo re-emit during the undo window can't
            // resurrect it in the cache-derived views (search, Vault). `_sounds` already filters
            // `deletedId` below; this keeps `allSoundsCache` consistent with it. `filterNot` over a
            // possibly-null id is a no-op when nothing is pending (ids are never null).
            val undeleted = allSounds.filterNot { it.id == deletedId }
            allSoundsCache.value =
                if (playingId == null) {
                    undeleted
                } else {
                    undeleted.map { if (it.id == playingId) it.copy(isPlaying = true) else it }
                }
            recomputeSearchResults()
            recomputeVaultAudios()
            _sounds.update {
                val byTab =
                    when (selectedTab.value) {
                        // ADR 0012: "Todo" shows audios with isVisibleInMySounds == true; an active
                        // public chip surfaces that collection's members by membership. Both live in
                        // mySoundsProjection. The Vault-privacy hide rule is NOT applied here.
                        AppTab.MY_SOUNDS -> mySoundsProjection(allSounds)
                        AppTab.EXPLORE_SOUNDS -> allSounds.filter { it.isBundled() }
                        // The Vault tab renders its own dedicated list (VaultScreen reads
                        // `_vaultAudios`, never this projection). Project HOME here: emptying
                        // flashed the outgoing tab's empty state mid-swap, and the predictive-back
                        // preview from the Vault base always reveals Home (exit-through-home).
                        AppTab.VAULT -> mySoundsProjection(allSounds)
                    }.filter { it.id != deletedId }
                val withWelcome = positionWelcomeIn(byTab, welcomeIsPendingDismissal, welcomeInstallTs)
                if (playingId == null) {
                    withWelcome
                } else {
                    withWelcome.map { if (it.id == playingId) it.copy(isPlaying = true) else it }
                }
            }
            val userCreatedCount = allSounds.count { !it.isBundled() }
            tracker.setUserProperty(AnalyticsUserProperty.CURRENT_SOUNDS, userCreatedCount.toString())
            tracker.setUserProperty(AnalyticsUserProperty.CURRENT_PINNED, allSounds.count { it.isPinned }.toString())
            // Refresh the audio-bucket snapshot: a created/deleted audio shifts the public_default
            // count even when no collection changed. allSoundsCache was updated earlier in this
            // function, so the buckets read the fresh universe.
            syncAudioBuckets(_collections.value)
            AUDIO_MILESTONES.forEach { threshold ->
                if (userCreatedCount >= threshold && tracker.markFiredOnce("milestone_sounds_$threshold")) {
                    tracker.log(AnalyticsEvent.MilestoneAudios(threshold))
                }
            }
        }
    }

    override fun onPlayerStart(
        sound: Sound,
        durationMs: Int,
        positionMs: Int,
    ) {
        endTapToSoundSpan()
        val playingSound = sound.copy(isPlaying = true)
        _playingSound.value = playingSound
        // positionMs is non-zero on resume from a paused state; initialising _playbackProgress with
        // it (rather than 0) prevents a ~100 ms slider flicker before the first progressRunnable
        // tick reports the real position.
        _playbackProgress.value = PlaybackProgress(positionMs = positionMs, durationMs = durationMs)
        // The sound is playing now, not paused — drop any retained paused position for it.
        _pausedProgress.update { it - sound.id }
        _soundDurations.update { it + (sound.id to durationMs) }
        viewModelScope.launch(ioDispatcher) {
            repo.saveDuration(sound.id, sound.name, durationMs)
        }
        _sounds.update { list -> list.map { if (it.id == sound.id) playingSound else it } }
        allSoundsCache.update { list -> list.map { if (it.id == sound.id) playingSound else it } }
        recomputeSearchResults()
        recomputeVaultAudios()
    }

    override fun onPlayerStop(
        sound: Sound,
        completed: Boolean,
    ) {
        // A stop/delete can land while a tap's prepare is still in flight — close the span so the
        // next tap doesn't double-begin with the same cookie (guarded no-op otherwise).
        endTapToSoundSpan()
        val stoppedSound = sound.copy(isPlaying = false)
        _playingSound.value = null
        _playbackProgress.value = null
        // A definitive stop discards the position — drop any retained paused progress for it.
        _pausedProgress.update { it - sound.id }
        _sounds.update { list -> list.map { if (it.id == sound.id) stoppedSound else it } }
        allSoundsCache.update { list -> list.map { if (it.id == sound.id) stoppedSound else it } }
        recomputeSearchResults()
        recomputeVaultAudios()

        if (completed && isWelcomeSticker(sound)) {
            // The welcome no longer self-destructs — it stays in the list as a
            // persistent audio. Only the FIRST completion does anything: log `welcome_sticker_completed`
            // (gated so unlimited replays don't inflate it — replays are covered by `welcome_sticker_play`),
            // show the one-shot informative snackbar, and mark the welcome acknowledged. Later
            // completions are silent.
            viewModelScope.launch(ioDispatcher) {
                try {
                    if (!welcomeStore.isAcknowledged()) {
                        welcomeStore.markAcknowledged()
                        tracker.log(AnalyticsEvent.WelcomeStickerCompleted)
                        _welcomeInfoEvent.trySend(Unit)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Throwable,
                ) {
                    // viewModelScope has no CoroutineExceptionHandler, so an uncaught throw here would
                    // crash the app. Same defensive shape as consumeWelcomeAsync / markWelcomeHintShown.
                    Tracker.track(RuntimeException("Welcome sticker acknowledge failed", e))
                }
            }
        }
    }

    override fun onPlayerPause(
        sound: Sound,
        positionMs: Int,
        durationMs: Int,
    ) {
        // Paused (toggle) or preempted (another playback started). The sound is no longer the
        // active one, so `_playingSound` / `_playbackProgress` clear — but unlike a stop, we
        // retain its position in `_pausedProgress` so the UI keeps the slider where it was.
        val pausedSound = sound.copy(isPlaying = false)
        if (_playingSound.value?.id == sound.id) {
            _playingSound.value = null
            _playbackProgress.value = null
        }
        _pausedProgress.update { it + (sound.id to PlaybackProgress(positionMs = positionMs, durationMs = durationMs)) }
        _sounds.update { list -> list.map { if (it.id == sound.id) pausedSound else it } }
        allSoundsCache.update { list -> list.map { if (it.id == sound.id) pausedSound else it } }
        recomputeSearchResults()
        recomputeVaultAudios()
    }

    override fun onProgressUpdate(positionMs: Int) {
        _playbackProgress.update { it?.copy(positionMs = positionMs) }
    }

    override fun onPlayerError(sound: Sound) {
        endTapToSoundSpan()
        _playbackErrorEvent.trySend(Unit)
    }

    /** Whether a [TAP_TO_SOUND_TRACE] span is open. See [beginTapToSoundSpan]; main-thread only. */
    private var tapToSoundSpanInFlight = false

    /**
     * Opens the [TAP_TO_SOUND_TRACE] async span. At most one span is in flight: a re-tap that
     * supersedes a pending start closes the stale span first (its start never completed — the new
     * tap preempts it), otherwise a same-cookie double-begin corrupts the trace. Main-thread only
     * (like every player callback here), so the flag needs no synchronization.
     */
    private fun beginTapToSoundSpan() {
        if (tapToSoundSpanInFlight) {
            Trace.endAsyncSection(TAP_TO_SOUND_TRACE, TAP_TO_SOUND_COOKIE)
        }
        tapToSoundSpanInFlight = true
        Trace.beginAsyncSection(TAP_TO_SOUND_TRACE, TAP_TO_SOUND_COOKIE)
    }

    /** Closes the span if one is in flight. Guarded: resume paths fire [onPlayerStart] with no tap span. */
    private fun endTapToSoundSpan() {
        if (!tapToSoundSpanInFlight) return
        tapToSoundSpanInFlight = false
        Trace.endAsyncSection(TAP_TO_SOUND_TRACE, TAP_TO_SOUND_COOKIE)
    }

    companion object {
        private val AUDIO_MILESTONES = listOf(3, 5, 10, 25)

        /**
         * Trace section spanning tap → playback started (the ≤100 ms tap-to-sound budget). Begins in
         * [playOrStop], ends in [onPlayerStart]/[onPlayerError] — deliberately OUTSIDE the player
         * engine so the span survives engine swaps and stays comparable across them. Measured by the
         * :macrobenchmark `TapLatencyBenchmark` (keep the literal in sync with `BenchmarkTargets.kt`).
         */
        private const val TAP_TO_SOUND_TRACE = "BompTapToSound"
        private const val TAP_TO_SOUND_COOKIE = 0

        /**
         * Canonical My Sounds ordering: pinned first, then most-recent by `dateAdded`, then name.
         * Shared between the initial repo sort and [positionWelcomeIn] so the persistent welcome
         * sticker sorts by its `dateAdded` exactly like a user-created audio.
         */
        private val SOUND_ORDER: Comparator<Sound> =
            compareByDescending<Sound> { it.isPinned }
                .thenByDescending { it.dateAdded ?: Long.MIN_VALUE }
                .thenBy { it.name.lowercase() }

        private const val KEY_SELECTED_TAB = "selected_tab"

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SoundsViewModel(
                        application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
