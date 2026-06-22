/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Immersive recorder UI state (ADR 0019). Permission priming/denial is owned by the Composable. */
sealed interface RecorderState {
    data object Ready : RecorderState

    data class Recording(
        val elapsedMs: Long,
        val amplitude: Float,
    ) : RecorderState

    /** A captured clip awaiting review. The content [uri] is resolved off the main thread when the
     *  clip is produced (FileProvider's first `getUriForFile` reads the paths XML from disk), so the
     *  host never touches FileProvider on the UI thread — see ADR 0019 / the stop-crash fix. */
    data class Review(
        val uri: Uri,
        val durationMs: Long,
    ) : RecorderState
}

/** One-shot effects (ADR 0003: `Channel` + `receiveAsFlow`, never `SharedFlow`). */
sealed interface RecorderEvent {
    /** Transient feedback (too short, no space, mic busy). */
    data class Message(
        @StringRes val messageRes: Int,
    ) : RecorderEvent

    /** The user kept the clip: hand its content [uri] to the save flow and finish. */
    data class Handoff(
        val uri: Uri,
    ) : RecorderEvent
}

/**
 * Drives the tap-to-toggle recorder: `Ready → Recording → Review` (no manual pause — ADR 0019). Owns
 * the capture temp file, the elapsed/amplitude poll, and the min/max/disk guards; delegates the mic to
 * [RecorderEngine] (off the main thread via [ioDispatcher]). Releases the engine in [onCleared].
 */
class RecorderViewModel(
    application: Application,
    private val engine: RecorderEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // Resolves the captured file to a shareable content URI. Default uses FileProvider; injected in
    // tests to avoid FileProvider's disk/manifest dependency under Robolectric. Always invoked off-main.
    private val uriProvider: (File) -> Uri = { RecorderTempFiles.contentUriFor(application, it) },
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow<RecorderState>(RecorderState.Ready)
    val state: StateFlow<RecorderState> = mutableState.asStateFlow()

    private val eventChannel = Channel<RecorderEvent>(Channel.BUFFERED)
    val events: Flow<RecorderEvent> = eventChannel.receiveAsFlow()

    private var tempFile: File? = null
    private var pollJob: Job? = null
    private var handedOff = false

    init {
        // Clear clips a prior session handed off (already copied by the save pipeline) or abandoned.
        RecorderTempFiles.purge(application)
        engine.onMaxDurationReached = { viewModelScope.launch { finishRecording(preserve = true) } }
        engine.onInterrupted = { viewModelScope.launch { finishRecording(preserve = true) } }
    }

    /** Hero button in `Ready`: begin capturing (after the disk + mic guards). */
    fun onRecordTapped() {
        if (mutableState.value != RecorderState.Ready) return
        val context = getApplication<Application>()
        if (context.cacheDir.usableSpace < MIN_FREE_BYTES) {
            emit(RecorderEvent.Message(R.string.app_recorder_feedback_no_space))
            return
        }
        viewModelScope.launch {
            val file = RecorderTempFiles.newTempFile(context)
            val started =
                runCatching { withContext(ioDispatcher) { engine.start(file) } }
                    .onFailure {
                        Tracker.log("recorder.start_failed=${it.javaClass.simpleName}")
                        Tracker.track(RuntimeException("Recorder could not start capturing audio", it))
                    }.isSuccess
            if (!started) {
                file.delete()
                emit(RecorderEvent.Message(R.string.app_recorder_feedback_mic_busy))
                return@launch
            }
            tempFile = file
            mutableState.value = RecorderState.Recording(elapsedMs = 0L, amplitude = 0f)
            startPolling()
        }
    }

    /** Hero button in `Recording`: stop and (if long enough) move to review. */
    fun onStopTapped() {
        if (mutableState.value !is RecorderState.Recording) return
        viewModelScope.launch { finishRecording(preserve = false) }
    }

    /** Review → "Re-record": discard the clip and return to ready. */
    fun onReRecord() {
        if (mutableState.value !is RecorderState.Review) return
        discardTemp()
        mutableState.value = RecorderState.Ready
    }

    /** Review → "Use this": hand the clip to the save flow. */
    fun onUseClip() {
        val review = mutableState.value as? RecorderState.Review ?: return
        handedOff = true
        emit(RecorderEvent.Handoff(review.uri))
    }

    /** Back while a clip exists (recording or review): drop it and return to ready. */
    fun onDiscard() {
        if (mutableState.value is RecorderState.Recording) {
            pollJob?.cancel()
            viewModelScope.launch { withContext(ioDispatcher) { engine.stop() } }
        }
        discardTemp()
        mutableState.value = RecorderState.Ready
    }

    /** True when there is unsaved captured audio — the Activity gates its back discard-confirm on this. */
    fun hasUnsavedClip(): Boolean = mutableState.value !is RecorderState.Ready

    /**
     * The host went to the background. Recording is foreground-only (ADR 0019 § Out of scope): auto-stop
     * and preserve so the mic is freed, mirroring an audio-focus loss. A no-op outside `Recording`.
     */
    fun onHostStopped() {
        if (mutableState.value is RecorderState.Recording) {
            viewModelScope.launch { finishRecording(preserve = true) }
        }
    }

    private suspend fun finishRecording(preserve: Boolean) {
        val recording = mutableState.value as? RecorderState.Recording ?: return
        pollJob?.cancel()
        val elapsed = recording.elapsedMs
        val produced = withContext(ioDispatcher) { engine.stop() }
        val file = tempFile
        if (!produced || file == null || elapsed < MIN_DURATION_MS) {
            discardTemp()
            mutableState.value = RecorderState.Ready
            // A deliberate too-short tap is worth a nudge; an interruption-preserve under 1 s isn't.
            if (!preserve) emit(RecorderEvent.Message(R.string.app_recorder_feedback_too_short))
            return
        }
        val uri = withContext(ioDispatcher) { uriProvider(file) }
        mutableState.value = RecorderState.Review(uri = uri, durationMs = elapsed)
    }

    private fun startPolling() {
        pollJob =
            viewModelScope.launch {
                var elapsed = 0L
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    elapsed += POLL_INTERVAL_MS
                    mutableState.value =
                        RecorderState.Recording(elapsedMs = elapsed, amplitude = engine.maxAmplitude())
                }
            }
    }

    private fun discardTemp() {
        tempFile?.delete()
        tempFile = null
    }

    private fun emit(event: RecorderEvent) {
        eventChannel.trySend(event)
    }

    override fun onCleared() {
        pollJob?.cancel()
        engine.release()
        if (!handedOff) discardTemp()
    }

    @androidx.annotation.VisibleForTesting
    internal fun clearForTest() = onCleared()

    companion object {
        /** A clip shorter than this is discarded (a Bomp is a sticker, not a misfire). */
        const val MIN_DURATION_MS = 1_000L
        private const val POLL_INTERVAL_MS = 80L

        /** Refuse to start with less than this free in cache — a 60 s clip is ≈0.5 MB; 5 MB is ample. */
        private const val MIN_FREE_BYTES = 5L * 1024 * 1024

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                    RecorderViewModel(app, RecorderEngineProvider.get(app))
                }
            }
    }
}
