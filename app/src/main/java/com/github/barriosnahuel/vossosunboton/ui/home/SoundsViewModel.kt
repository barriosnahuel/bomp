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
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerListener
import com.github.barriosnahuel.vossosunboton.feature.share.ShareFeature
import com.github.barriosnahuel.vossosunboton.feature.share.ShareIntentOutcome
import com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore
import com.github.barriosnahuel.vossosunboton.feature.welcome.isWelcomeSticker
import com.github.barriosnahuel.vossosunboton.feature.welcome.welcomeSticker
import com.github.barriosnahuel.vossosunboton.model.Sound
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

enum class AppTab { MY_SOUNDS, EXPLORE_SOUNDS }

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

@Suppress("TooManyFunctions")
class SoundsViewModel(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val searchDebounceMs: Long = 200L,
    private val welcomeStore: WelcomeStickerStore = WelcomeStickerStore(application),
    private val shareFeature: ShareFeature = ShareFeature.instance,
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
        viewModelScope.launch(ioDispatcher) { loadSounds() }
    }

    fun togglePin(sound: Sound) {
        val nowPinned = !sound.isPinned
        val sortedList = { list: List<Sound> ->
            list
                .map { if (it.name == sound.name) it.copy(isPinned = nowPinned) else it }
                .sortedWith(
                    compareByDescending<Sound> { it.isPinned }
                        .thenByDescending { it.dateAdded ?: Long.MIN_VALUE }
                        .thenBy { it.name.lowercase() },
                )
        }
        _sounds.update(sortedList)
        allSoundsCache.update { list -> list.map { if (it.name == sound.name) it.copy(isPinned = nowPinned) else it } }
        recomputeSearchResults()
        if (nowPinned) _scrollToTopEvent.trySend(Unit)
        viewModelScope.launch(ioDispatcher) {
            repo.savePin(sound.name, nowPinned)
        }
        tracker.log(AnalyticsEvent.PinToggle(pinned = nowPinned))
        tracker.setUserProperty(
            AnalyticsUserProperty.CURRENT_PINNED,
            allSoundsCache.value.count { it.isPinned }.toString(),
        )
    }

    fun playOrStop(sound: Sound) {
        if (sound.isPlaying) {
            // Tap-while-playing now pauses (preserving position via the controller's in-process
            // cache) rather than stopping. The icon and slider still collapse to "not playing"
            // because _playingSound goes null on onPlayerStop — the saved position is invisible to
            // the VM/UI and only surfaces when the user re-taps and the controller resumes.
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
        val currentSounds = _sounds.value.toMutableList()
        val currentSound = currentSounds.find { it.name == sound.name } ?: return
        val position = currentSounds.indexOf(currentSound)
        val isWelcome = isWelcomeSticker(sound)

        // Always ask the controller to forget this sound: it cleans up the saved-position cache
        // and, if this sound is the currently-loaded MediaPlayer target (playing OR paused),
        // stops and resets it. Going through _playingSound here would miss the paused case (after
        // the play/pause unification, `_playingSound` is null while the sound is paused but the
        // controller still holds the data source).
        PlayerControllerFactory.instance.forgetSound(sound)

        currentSounds.removeAt(position)
        _sounds.value = currentSounds
        if (!isWelcome) {
            // Welcome sticker is never in `allSoundsCache` (kept out of search) — skip the update.
            allSoundsCache.update { list -> list.filter { it.name != sound.name } }
            recomputeSearchResults()
        }
        _deletedSoundEvent.value = DeletedSoundEvent(currentSound.copy(isPlaying = false), position)
        if (isWelcome) {
            _welcomeStickerVisible.value = false
            tracker.log(AnalyticsEvent.WelcomeStickerDismissed)
        }
    }

    fun restoreSound() {
        val event = _deletedSoundEvent.value ?: return
        val isWelcome = isWelcomeSticker(event.sound)
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
        if (!isWelcome) {
            val insertPosition = event.position.coerceAtMost(_sounds.value.size)
            allSoundsCache.update { list ->
                val allInsertPosition = insertPosition.coerceAtMost(list.size)
                list.toMutableList().also { it.add(allInsertPosition, event.sound) }
            }
            recomputeSearchResults()
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
            else -> deletePersistedSoundAsync(event.sound)
        }
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
            val playingName = _playingSound.value?.name
            val deletedSound = _deletedSoundEvent.value?.sound
            val deletedName = deletedSound?.name
            val welcomeIsPendingDismissal = deletedSound != null && isWelcomeSticker(deletedSound)
            allSoundsCache.value =
                if (playingName == null) {
                    allSounds
                } else {
                    allSounds.map { if (it.name == playingName) it.copy(isPlaying = true) else it }
                }
            recomputeSearchResults()
            _sounds.update {
                val filtered =
                    when (_selectedTab.value) {
                        AppTab.MY_SOUNDS -> allSounds.filter { !it.isBundled() }
                        AppTab.EXPLORE_SOUNDS -> allSounds.filter { it.isBundled() }
                    }.filter { it.name != deletedName }
                val withWelcome = positionWelcomeIn(filtered, welcomeIsPendingDismissal, welcomeWasRestored)
                if (playingName == null) {
                    withWelcome
                } else {
                    withWelcome.map { if (it.name == playingName) it.copy(isPlaying = true) else it }
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
        _soundDurations.update { it + (sound.name to durationMs) }
        viewModelScope.launch(ioDispatcher) {
            repo.saveDuration(sound.name, durationMs)
        }
        _sounds.update { list -> list.map { if (it.name == sound.name) playingSound else it } }
        allSoundsCache.update { list -> list.map { if (it.name == sound.name) playingSound else it } }
        recomputeSearchResults()
    }

    override fun onPlayerStop(
        sound: Sound,
        completed: Boolean,
    ) {
        val stoppedSound = sound.copy(isPlaying = false)
        _playingSound.value = null
        _playbackProgress.value = null
        _sounds.update { list -> list.map { if (it.name == sound.name) stoppedSound else it } }
        allSoundsCache.update { list -> list.map { if (it.name == sound.name) stoppedSound else it } }
        recomputeSearchResults()

        if (completed && isWelcomeSticker(sound)) {
            val position = _sounds.value.indexOfFirst { it.name == sound.name }.coerceAtLeast(0)
            _sounds.update { list -> list.filter { it.name != sound.name } }
            _welcomeStickerVisible.value = false
            _deletedSoundEvent.value = DeletedSoundEvent(stoppedSound, position)
            tracker.log(AnalyticsEvent.WelcomeStickerCompleted)
        }
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
