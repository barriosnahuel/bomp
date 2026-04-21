package com.github.barriosnahuel.vossosunboton.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerListener
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

enum class AppTab { HOME, EXPLORE }

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
) : AndroidViewModel(application),
    PlayerControllerListener {
    private val _selectedTab = MutableStateFlow(AppTab.HOME)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _sounds = MutableStateFlow<List<Sound>>(emptyList())
    val sounds: StateFlow<List<Sound>> = _sounds.asStateFlow()

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

    private val _deletedSoundEvent = MutableStateFlow<DeletedSoundEvent?>(null)
    val deletedSoundEvent: StateFlow<DeletedSoundEvent?> = _deletedSoundEvent.asStateFlow()

    private val _buttonSavedEvent = Channel<String>(Channel.BUFFERED)
    val buttonSavedEvent: Flow<String> = _buttonSavedEvent.receiveAsFlow()

    private val _buttonRenamedEvent = Channel<String>(Channel.BUFFERED)
    val buttonRenamedEvent: Flow<String> = _buttonRenamedEvent.receiveAsFlow()

    private val _playbackErrorEvent = Channel<Unit>(Channel.BUFFERED)
    val playbackErrorEvent: Flow<Unit> = _playbackErrorEvent.receiveAsFlow()

    init {
        PlayerControllerFactory.instance.setOnStartStopListener(this)
        viewModelScope.launch(ioDispatcher) { loadSounds() }
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
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
        viewModelScope.launch(ioDispatcher) { loadSounds() }
    }

    fun togglePin(sound: Sound) {
        if (sound.isBundled()) return
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
        viewModelScope.launch(ioDispatcher) {
            SoundDao().savePin(getApplication(), sound.name, nowPinned)
        }
    }

    fun playOrStop(sound: Sound) {
        if (sound.isPlaying) {
            PlayerControllerFactory.instance.stopPlayingSound()
        } else {
            PlayerControllerFactory.instance.startPlayingSound(getApplication(), sound)
        }
    }

    fun seekTo(positionMs: Int) {
        PlayerControllerFactory.instance.seekTo(positionMs)
        _playbackProgress.update { it?.copy(positionMs = positionMs) }
    }

    fun deleteSound(sound: Sound) {
        val currentSounds = _sounds.value.toMutableList()
        val currentSound = currentSounds.find { it.name == sound.name } ?: return
        val position = currentSounds.indexOf(currentSound)

        if (_playingSound.value?.name == sound.name) {
            PlayerControllerFactory.instance.stopPlayingSound()
        }

        currentSounds.removeAt(position)
        _sounds.value = currentSounds
        allSoundsCache.update { list -> list.filter { it.name != sound.name } }
        recomputeSearchResults()
        _deletedSoundEvent.value = DeletedSoundEvent(currentSound.copy(isPlaying = false), position)
    }

    fun restoreSound() {
        val event = _deletedSoundEvent.value ?: return
        val currentSounds = _sounds.value.toMutableList()
        val insertPosition = event.position.coerceAtMost(currentSounds.size)
        currentSounds.add(insertPosition, event.sound)
        _sounds.value = currentSounds
        allSoundsCache.update { list ->
            val allInsertPosition = insertPosition.coerceAtMost(list.size)
            list.toMutableList().also { it.add(allInsertPosition, event.sound) }
        }
        recomputeSearchResults()
        _deletedSoundEvent.value = null
    }

    fun confirmDelete(context: android.content.Context) {
        val event = _deletedSoundEvent.value ?: return
        _deletedSoundEvent.value = null

        if (!event.sound.isBundled()) {
            try {
                SoundDao().delete(context, event.sound)
            } catch (e: IllegalStateException) {
                Timber.w(e, "Sound has no file on disk, skipping delete")
            }
        }
    }

    fun clearDeleteEvent() {
        _deletedSoundEvent.value = null
    }

    fun onButtonSaved(name: String) {
        selectTab(AppTab.HOME)
        _buttonSavedEvent.trySend(name)
    }

    fun onButtonRenamed(name: String) {
        selectTab(AppTab.HOME)
        _buttonRenamedEvent.trySend(name)
    }

    private suspend fun loadSounds() {
        val allSounds =
            SoundDao()
                .find(getApplication<Application>())
                .sortedWith(
                    compareByDescending<Sound> { it.isPinned }
                        .thenByDescending { it.dateAdded ?: Long.MIN_VALUE }
                        .thenBy { it.name.lowercase() },
                )
        val playingName = _playingSound.value?.name
        val deletedName = _deletedSoundEvent.value?.sound?.name
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
                    AppTab.HOME -> allSounds.filter { !it.isBundled() }
                    AppTab.EXPLORE -> allSounds.filter { it.isBundled() }
                }.filter { it.name != deletedName }
            if (playingName == null) {
                filtered
            } else {
                filtered.map { if (it.name == playingName) it.copy(isPlaying = true) else it }
            }
        }
    }

    override fun onPlayerStart(
        sound: Sound,
        durationMs: Int,
    ) {
        val playingSound = sound.copy(isPlaying = true)
        _playingSound.value = playingSound
        _playbackProgress.value = PlaybackProgress(positionMs = 0, durationMs = durationMs)
        _sounds.update { list -> list.map { if (it.name == sound.name) playingSound else it } }
        allSoundsCache.update { list -> list.map { if (it.name == sound.name) playingSound else it } }
        recomputeSearchResults()
    }

    override fun onPlayerStop(sound: Sound) {
        val stoppedSound = sound.copy(isPlaying = false)
        _playingSound.value = null
        _playbackProgress.value = null
        _sounds.update { list -> list.map { if (it.name == sound.name) stoppedSound else it } }
        allSoundsCache.update { list -> list.map { if (it.name == sound.name) stoppedSound else it } }
        recomputeSearchResults()
    }

    override fun onProgressUpdate(positionMs: Int) {
        _playbackProgress.update { it?.copy(positionMs = positionMs) }
    }

    override fun onPlayerError(sound: Sound) {
        _playbackErrorEvent.trySend(Unit)
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SoundsViewModel(application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!)
                }
            }
    }
}
