/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
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
import timber.log.Timber

enum class AppTab { MY_SOUNDS, VAULT, EXPLORE_SOUNDS }

/**
 * Request payload for the create/rename collection bottom sheet. A single sealed shape so the
 * sheet host can match-on-type to derive title, placeholder, and submit semantics.
 */
sealed interface CollectionSheetRequest {
    data class Create(
        val access: CollectionAccess,
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

data class PlaybackProgress(
    val positionMs: Int,
    val durationMs: Int,
) {
    val fraction: Float get() = if (durationMs > 0) positionMs / durationMs.toFloat() else 0f
}

@Suppress("TooManyFunctions", "LargeClass")
class SoundsViewModel(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val searchDebounceMs: Long = 200L,
    private val welcomeStore: WelcomeStickerStore = WelcomeStickerStore(application),
    private val shareFeature: ShareFeature = ShareFeature.instance,
    private val collectionsRepo: CollectionsRepository = CollectionsRepository(application, onError = Tracker::track),
    private val mySoundsFilterStore: MySoundsFilterStore = MySoundsFilterStore(application),
    private val vaultFilterStore: com.github.barriosnahuel.vossosunboton.feature.vault.VaultFilterStore =
        com.github.barriosnahuel.vossosunboton.feature.vault
            .VaultFilterStore(application),
) : AndroidViewModel(application),
    PlayerControllerListener {
    private val repo = SoundsRepository(application, onError = Tracker::track)

    // Default `false` — flipped to `true` asynchronously by the IO coroutine in `init` after
    // reading the suspend `welcomeStore.isActive()`. Sub-frame in practice; same accepted
    // limitation as `_sounds = emptyList()` during cold-start.
    private val _welcomeStickerVisible = MutableStateFlow(false)
    val welcomeStickerVisible: StateFlow<Boolean> = _welcomeStickerVisible.asStateFlow()

    private val _selectedTab = MutableStateFlow(AppTab.MY_SOUNDS)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

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

    /**
     * Flips to `true` after the first [loadSounds] call returns (success OR failure).
     * Tests in this module use it to await the async DataStore read started in `init`
     * before mutating state via reflection (otherwise the injected value gets overwritten
     * by the in-flight load). Not part of the production API — `internal` so the wider
     * codebase cannot accidentally depend on it; `@VisibleForTesting(otherwise = NONE)`
     * so Lint warns if any non-test caller appears.
     *
     * Avoids the conventional `_isInitialLoadComplete`/`isInitialLoadComplete` backing-field
     * pair because ktlint's `backing-property-naming` rule requires both to be `private`,
     * which would defeat the test-coordination purpose.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
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

    private val _playbackErrorEvent = Channel<Unit>(Channel.BUFFERED)
    val playbackErrorEvent: Flow<Unit> = _playbackErrorEvent.receiveAsFlow()

    // CONFLATED (vs the BUFFERED siblings above): a tap-spamming user does not need every share-error
    // queued — only the latest matters, and stacking duplicate snackbars would just hide the message.
    private val _shareIntentEvent = Channel<ShareIntentOutcome.Success>(Channel.CONFLATED)
    val shareIntentEvent: Flow<ShareIntentOutcome.Success> = _shareIntentEvent.receiveAsFlow()

    private val _shareErrorEvent = Channel<Int>(Channel.CONFLATED)
    val shareErrorEvent: Flow<Int> = _shareErrorEvent.receiveAsFlow()

    init {
        PlayerControllerFactory.instance.setOnStartStopListener(this)
        // Single coroutine: read welcome-sticker visibility BEFORE the first loadSounds so the
        // prepend logic sees the right state on the first paint. selectTab cannot fire before VM
        // init returns, so the first loadSounds always observes a stable
        // `_welcomeStickerVisible` snapshot.
        //
        // The whole block is wrapped in try/catch so that any failure between `isActive()` and the
        // `try/finally` inside `loadSounds()` still flips `mutableInitialLoadComplete` — otherwise
        // tests that await `isInitialLoadComplete.first { it }` would deadlock.
        viewModelScope.launch(ioDispatcher) {
            try {
                val active = welcomeStore.isActive()
                _welcomeStickerVisible.value = active
                if (active && tracker.markFiredOnce("welcome_sticker_shown")) {
                    tracker.log(AnalyticsEvent.WelcomeStickerShown)
                }
                loadSounds()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                Tracker.track(RuntimeException("SoundsViewModel init failed", e))
                mutableInitialLoadComplete.value = true
            }
        }
        // Reactive trigger: any change to the persisted sound list (save / rename / pin /
        // duration / delete) re-runs `loadSounds` so the visible list catches up. This is the
        // fix for the post-PR-#1130 regression where AddButton save/rename never propagated to
        // Home — `loadSounds` only ran on cold start and tab switch.
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
                    if (!firstEmission) {
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
                _selectedTab.value == AppTab.MY_SOUNDS -> CanonicalScreenName.MY_SOUNDS
                _selectedTab.value == AppTab.VAULT -> CanonicalScreenName.VAULT
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
        _searchResults.value =
            if (query.isBlank()) {
                emptyList()
            } else {
                allSoundsCache.value
                    .filter { it.name.contains(query, ignoreCase = true) }
                    .sortedWith(compareByDescending<Sound> { it.isPinned }.thenBy { it.name.lowercase() })
            }
        if (query.isNotBlank() && _searchResults.value.isEmpty()) {
            tracker.log(AnalyticsEvent.SearchZeroResults(queryLength = query.length))
        }
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
        // Optimistically rebuild `_sounds` from the in-memory caches before kicking off the
        // async loadSounds. Without this, the freshly selected tab renders an empty list (the
        // previous tab's filtered view) until the IO chain settles — the user sees the empty
        // state flash for ~150ms on every tab swap.
        applyTabFilterFromCache()
        viewModelScope.launch(ioDispatcher) { loadSounds() }
    }

    /**
     * Synchronous projection of the currently-cached `allSoundsCache` to `_sounds` based on the
     * active tab. Used to bridge the visual gap between [selectTab] and the loadSounds emission —
     * keeps the body composable rendering the right list instead of bouncing through the empty
     * state. Returns whatever `applyCollectionFilter` produces for the active tab + chip.
     */
    private fun applyTabFilterFromCache() {
        val snapshot = allSoundsCache.value
        if (snapshot.isEmpty()) return
        // Mirrors the byTab branch in loadSounds. We intentionally avoid `privateOnlyAudioIds`
        // here (it would do a fresh DataStore read) — the projection is best-effort and the next
        // loadSounds correction lands within ~50ms.
        val privateIds = collectionsAudioIdsSnapshot()
        val byTab =
            when (_selectedTab.value) {
                AppTab.MY_SOUNDS -> snapshot.filter { !it.isBundled() && it.id !in privateIds }
                AppTab.EXPLORE_SOUNDS -> snapshot.filter { it.isBundled() }
                AppTab.VAULT -> emptyList()
            }
        val filtered = applyCollectionFilter(byTab)
        _sounds.value = filtered
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
            tracker.log(AnalyticsEvent.CollectionFilterApply(matches = _vaultAudios.value.size))
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
            tracker.log(AnalyticsEvent.CollectionFilterApply(matches = matches))
        }
    }

    private fun audiosIn(collectionId: String): List<Sound> {
        val target = _collections.value.firstOrNull { it.id == collectionId } ?: return emptyList()
        val ids = target.audioIds.toSet()
        return allSoundsCache.value.filter { it.id in ids }
    }

    /**
     * Returns the set of audio ids that belong to at least one PRIVATE collection AND zero PUBLIC
     * collections. Used to hide "Vault-only" audios from My Sounds (spec § 1: those audios are
     * "preserved inside the app and never come out"). The cross-tagged case (private + public)
     * is intentionally excluded — spec § 3.1's "Restricción de cross-preset" preserves visibility
     * from the public surface.
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
     * Filters [tabSounds] by the currently active My Sounds chip. Only applied when MY_SOUNDS is
     * the selected tab — the Explore and Vault tabs ignore the chip state entirely. When the
     * active filter id points to a deleted collection (race between deletion and a tab swap)
     * the predicate matches nothing; the observation coroutine in `init` clears the stale id
     * before the next `loadSounds` runs, so the empty intermediate state is brief.
     */
    private fun applyCollectionFilter(tabSounds: List<Sound>): List<Sound> {
        val activeId =
            _activeMySoundsFilter.value.takeIf { _selectedTab.value == AppTab.MY_SOUNDS }
                ?: return tabSounds
        val activeCollection = _collections.value.firstOrNull { it.id == activeId }
        val ids = activeCollection?.audioIds.orEmpty().toSet()
        return tabSounds.filter { it.id in ids }
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

    fun seekTo(positionMs: Int) {
        PlayerControllerFactory.instance.seekTo(positionMs)
        _playbackProgress.update { it?.copy(positionMs = positionMs) }
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
        val shouldUpdateVisibleSounds = isWelcome || _selectedTab.value != AppTab.VAULT
        if (shouldUpdateVisibleSounds) {
            val currentSounds = _sounds.value.toMutableList()
            if (isWelcome) {
                // Demote to the end of MY_SOUNDS — the prime row 0 spot belongs to the user once
                // they've shown they want this sticker back rather than letting it consume.
                currentSounds.add(event.sound)
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
            tracker.log(AnalyticsEvent.WelcomeStickerUndone)
        } else {
            tracker.log(AnalyticsEvent.SoundDeleteUndone)
        }
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
                        scope = if (access == CollectionAccess.PUBLIC) "public" else "private",
                        audios = 0,
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
                        scope = if (it.isPublic) "public" else "private",
                    ),
                )
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
                    scope = if (target.isPublic) "public" else "private",
                    audios = priorCount,
                ),
            )
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

    /** Opens the create-collection sheet for [access] scope. Triggered from filter row and Vault FAB. */
    fun requestCreateCollection(access: CollectionAccess) {
        _activeCollectionSheet.value = CollectionSheetRequest.Create(access)
    }

    /** Opens the long-press → "Add to collection" sheet for [audioId]. */
    fun requestAssignCollections(audioId: String) {
        _activeAssignAudioId.value = audioId
    }

    fun dismissAssignCollections() {
        _activeAssignAudioId.value = null
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
                        scope = if (target.isPublic) "public" else "private",
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
     * is hiding it. Otherwise prepends on a fresh install or appends once the user has undone the
     * dismissal at least once (the demoted state).
     *
     * [welcomeIsPendingDismissal] and [wasRestored] are snapshotted at the top of `loadSounds` so
     * this stays a pure function — easier to test, and avoids issuing extra DataStore reads inside
     * the `_sounds.update` lambda.
     */
    private fun positionWelcomeIn(
        filtered: List<Sound>,
        welcomeIsPendingDismissal: Boolean,
        wasRestored: Boolean,
    ): List<Sound> {
        val shouldShowWelcome =
            _selectedTab.value == AppTab.MY_SOUNDS &&
                _welcomeStickerVisible.value &&
                !welcomeIsPendingDismissal
        if (!shouldShowWelcome) return filtered
        val welcome = welcomeSticker(getApplication())
        return if (wasRestored) filtered + welcome else listOf(welcome) + filtered
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
                // Same shape as `consumeWelcomeAsync` — the in-memory state is already correct;
                // we only lose the disk persistence of the demoted-position flag.
                Tracker.track(RuntimeException("Welcome sticker restore persistence failed", e))
            }
        }
    }

    fun clearDeleteEvent() {
        _deletedSoundEvent.value = null
    }

    private suspend fun loadSounds() {
        try {
            val allSounds =
                repo.sounds
                    .first()
                    .sortedWith(
                        compareByDescending<Sound> { it.isPinned }
                            .thenByDescending { it.dateAdded ?: Long.MIN_VALUE }
                            .thenBy { it.name.lowercase() },
                    )
            val cachedDurations = repo.durations.first()
            // Read once per loadSounds — pure function over a snapshotted boolean, easier to test.
            val welcomeWasRestored = welcomeStore.wasRestored()
            _soundDurations.update { current -> cachedDurations + current }
            _hasBundledSounds.value = allSounds.any { it.isBundled() }
            val playingId = _playingSound.value?.id
            val deletedSound = _deletedSoundEvent.value?.sound
            val deletedId = deletedSound?.id
            val welcomeIsPendingDismissal = deletedSound != null && isWelcomeSticker(deletedSound)
            allSoundsCache.value =
                if (playingId == null) {
                    allSounds
                } else {
                    allSounds.map { if (it.id == playingId) it.copy(isPlaying = true) else it }
                }
            recomputeSearchResults()
            recomputeVaultAudios()
            // Snapshot of private-only ids: tagged to at least one private collection and to
            // ZERO public collections. Spec § 3.1 + § 1 — those audios are meant to stay inside
            // the Vault and never surface on My Sounds. Cross-tagged (private + public) audios
            // remain visible on My Sounds because the public surface preserves visibility
            // (the "Restricción de cross-preset" carve-out). Computed OUTSIDE the _sounds.update
            // lambda because the helper now reads collectionsRepo.collections.first() (suspend).
            val privateOnlyIds = privateOnlyAudioIds()
            _sounds.update {
                val byTab =
                    when (_selectedTab.value) {
                        AppTab.MY_SOUNDS ->
                            allSounds.filter { !it.isBundled() && it.id !in privateOnlyIds }
                        AppTab.EXPLORE_SOUNDS -> allSounds.filter { it.isBundled() }
                        // The Vault tab is rendered by its own dedicated screen (VaultScreen) — the
                        // primary sound list is empty here so the bottom-bar tab swap leaves the
                        // screen blank while VaultScreen takes over via the overlay path in
                        // LandingScreen. Returning an empty list also keeps the sound list's empty
                        // state composable from rendering its "no sounds yet" body for the wrong tab.
                        AppTab.VAULT -> emptyList()
                    }.filter { it.id != deletedId }
                val filtered = applyCollectionFilter(byTab)
                val withWelcome = positionWelcomeIn(filtered, welcomeIsPendingDismissal, welcomeWasRestored)
                if (playingId == null) {
                    withWelcome
                } else {
                    withWelcome.map { if (it.id == playingId) it.copy(isPlaying = true) else it }
                }
            }
            val userCreatedCount = allSounds.count { !it.isBundled() }
            tracker.setUserProperty(AnalyticsUserProperty.CURRENT_SOUNDS, userCreatedCount.toString())
            tracker.setUserProperty(AnalyticsUserProperty.CURRENT_PINNED, allSounds.count { it.isPinned }.toString())
            AUDIO_MILESTONES.forEach { threshold ->
                if (userCreatedCount >= threshold && tracker.markFiredOnce("milestone_sounds_$threshold")) {
                    tracker.log(AnalyticsEvent.MilestoneAudios(threshold))
                }
            }
        } finally {
            // Always flag completion so test waits do not hang on DataStore failures.
            mutableInitialLoadComplete.value = true
        }
    }

    override fun onPlayerStart(
        sound: Sound,
        durationMs: Int,
        positionMs: Int,
    ) {
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
            val position = _sounds.value.indexOfFirst { it.id == sound.id }.coerceAtLeast(0)
            _sounds.update { list -> list.filter { it.id != sound.id } }
            _welcomeStickerVisible.value = false
            _deletedSoundEvent.value = DeletedSoundEvent(stoppedSound, position)
            tracker.log(AnalyticsEvent.WelcomeStickerCompleted)
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
        _playbackErrorEvent.trySend(Unit)
    }

    companion object {
        private val AUDIO_MILESTONES = listOf(3, 5, 10, 25)

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SoundsViewModel(application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!)
                }
            }
    }
}
