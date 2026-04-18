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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

enum class AppTab { HOME, FAVORITES, EXPLORE }

data class DeletedSoundEvent(
    val sound: Sound,
    val position: Int,
)

class SoundsViewModel(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application),
    PlayerControllerListener {
    private val _selectedTab = MutableStateFlow(AppTab.HOME)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _sounds = MutableStateFlow<List<Sound>>(emptyList())
    val sounds: StateFlow<List<Sound>> = _sounds.asStateFlow()

    private val _playingSound = MutableStateFlow<Sound?>(null)
    val playingSound: StateFlow<Sound?> = _playingSound.asStateFlow()

    private val _deletedSoundEvent = MutableStateFlow<DeletedSoundEvent?>(null)
    val deletedSoundEvent: StateFlow<DeletedSoundEvent?> = _deletedSoundEvent.asStateFlow()

    private val _buttonSavedEvent = Channel<Unit>(Channel.BUFFERED)
    val buttonSavedEvent: Flow<Unit> = _buttonSavedEvent.receiveAsFlow()

    init {
        PlayerControllerFactory.instance.setOnStartStopListener(this)
        viewModelScope.launch(ioDispatcher) { loadSounds() }
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
        viewModelScope.launch(ioDispatcher) { loadSounds() }
    }

    fun toggleFavorite(sound: Sound) {
        val updated = sound.copy(isFavorite = !sound.isFavorite)
        _sounds.update { list -> list.map { if (it.name == sound.name) updated else it } }
        viewModelScope.launch(ioDispatcher) {
            SoundDao().saveFavorite(getApplication(), sound.name, updated.isFavorite)
        }
    }

    fun playOrStop(sound: Sound) {
        if (sound.isPlaying) {
            PlayerControllerFactory.instance.stopPlayingSound()
        } else {
            PlayerControllerFactory.instance.startPlayingSound(getApplication(), sound)
        }
    }

    fun deleteSound(sound: Sound) {
        val currentSounds = _sounds.value.toMutableList()
        val position = currentSounds.indexOf(sound)
        if (position == -1) return

        if (sound.isPlaying) {
            PlayerControllerFactory.instance.stopPlayingSound()
        }

        currentSounds.removeAt(position)
        _sounds.value = currentSounds
        _deletedSoundEvent.value = DeletedSoundEvent(sound.copy(isPlaying = false), position)
    }

    fun restoreSound() {
        val event = _deletedSoundEvent.value ?: return
        val currentSounds = _sounds.value.toMutableList()
        val insertPosition = event.position.coerceAtMost(currentSounds.size)
        currentSounds.add(insertPosition, event.sound)
        _sounds.value = currentSounds
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

    fun onButtonSaved() {
        selectTab(AppTab.HOME)
        _buttonSavedEvent.trySend(Unit)
    }

    private suspend fun loadSounds() {
        val allSounds = SoundDao().find(getApplication<Application>()).sortedBy { it.name.lowercase() }
        _sounds.update {
            when (_selectedTab.value) {
                AppTab.HOME -> allSounds.filter { !it.isBundled() }
                AppTab.FAVORITES -> allSounds.filter { it.isFavorite }
                AppTab.EXPLORE -> allSounds.filter { it.isBundled() }
            }
        }
    }

    override fun onPlayerStart(sound: Sound) {
        val playingSound = sound.copy(isPlaying = true)
        _playingSound.value = playingSound
        _sounds.update { list -> list.map { if (it.name == sound.name) playingSound else it } }
    }

    override fun onPlayerStop(sound: Sound) {
        val stoppedSound = sound.copy(isPlaying = false)
        _playingSound.value = null
        _sounds.update { list -> list.map { if (it.name == sound.name) stoppedSound else it } }
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
