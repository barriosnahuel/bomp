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
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTracker
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
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
    private val draftStore: RecorderDraftStore = RecorderDraftStoreProvider.get(application),
    private val analytics: AnalyticsTracker = AnalyticsTrackerProvider.get(application),
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
    private var entered = false

    // Amplitude samples captured live during recording (one per poll tick) and the envelope built from
    // them on stop — so the review wave renders instantly instead of re-decoding the file (ADR 0019). A
    // restored draft has no live samples, leaving [capturedEnvelope] null so the host decodes instead.
    private val amplitudes = mutableListOf<Float>()

    /**
     * The review envelope: live-built on stop, or backfilled (via [cacheDecodedEnvelope]) by the host's
     * decode for a restored draft. Null until known — the host then decodes the file.
     */
    var capturedEnvelope: FloatArray? = null
        private set

    /**
     * Backfills the envelope a restored draft decoded from the file, so a later config recreate reuses
     * it instead of re-decoding (and re-flashing the placeholder). No-op on null / for a fresh recording
     * (which already built one live); reset by [onRecordTapped] / [onReRecord].
     */
    fun cacheDecodedEnvelope(envelope: FloatArray?) {
        if (envelope != null) capturedEnvelope = envelope
    }

    // Guards the start ↔ stop transition across its suspension points so a rapid double-tap (or a stop
    // racing onHostStopped) can't run the transition twice — leaking a second MediaRecorder or deleting
    // a valid clip down the discard branch.
    private var transitioning = false

    init {
        engine.onMaxDurationReached = { viewModelScope.launch { finishRecording(preserve = true) } }
        engine.onInterrupted = { viewModelScope.launch { finishRecording(preserve = true) } }
    }

    /**
     * Called once by the host on first creation (ADR 0019 § Draft recovery). [resumeDraft] true — the
     * Landing draft banner launched us — restores a persisted clip into `Review`, sparing its file from
     * the purge; false starts fresh and purges every leftover capture. Guarded so a config-change
     * recreate (which keeps this VM) does not re-run it.
     */
    fun onEnter(resumeDraft: Boolean) {
        if (entered) return
        entered = true
        viewModelScope.launch {
            val draft = draftStore.current()
            // [resumeDraft] only decides whether to *restore into Review*; the draft's file is ALWAYS
            // spared from the purge. A fresh entry (resumeDraft=false) with a pending draft must not
            // delete the clip out from under the Landing banner — restore and preserve are independent.
            if (resumeDraft && draft != null) {
                val uri = withContext(ioDispatcher) { uriProvider(draft.file) }
                tempFile = draft.file
                mutableState.value = RecorderState.Review(uri = uri, durationMs = draft.durationMs)
            }
            // Off the main thread — listing/deleting cache files is disk I/O (StrictMode).
            withContext(ioDispatcher) { RecorderTempFiles.purge(getApplication(), keep = draft?.file) }
        }
    }

    /** Hero button in `Ready`: begin capturing (after the disk + mic guards). */
    fun onRecordTapped() {
        if (mutableState.value != RecorderState.Ready || transitioning) return
        transitioning = true
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                // usableSpace is a blocking statvfs syscall and newTempFile does mkdirs() — both disk I/O,
                // kept off the main thread (StrictMode crashes the debug build on a main-thread read).
                val file =
                    withContext(ioDispatcher) {
                        if (context.cacheDir.usableSpace < MIN_FREE_BYTES) null else RecorderTempFiles.newTempFile(context)
                    }
                if (file == null) {
                    emit(RecorderEvent.Message(R.string.app_recorder_feedback_no_space))
                    return@launch
                }
                val started =
                    runCatching { withContext(ioDispatcher) { engine.start(file) } }
                        .onFailure {
                            Tracker.log("recorder.start_failed=${it.javaClass.simpleName}")
                            Tracker.track(RuntimeException("Recorder could not start capturing audio", it))
                        }.isSuccess
                if (!started) {
                    withContext(ioDispatcher) { file.delete() }
                    emit(RecorderEvent.Message(R.string.app_recorder_feedback_mic_busy))
                    return@launch
                }
                tempFile = file
                amplitudes.clear()
                capturedEnvelope = null
                mutableState.value = RecorderState.Recording(elapsedMs = 0L, amplitude = 0f)
                startPolling()
            } finally {
                transitioning = false
            }
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
        draftStore.clear()
        amplitudes.clear()
        capturedEnvelope = null
        mutableState.value = RecorderState.Ready
    }

    /** Review → "Use this": hand the clip to the save flow. */
    fun onUseClip() {
        val review = mutableState.value as? RecorderState.Review ?: return
        // Deliberately NOT clearing the draft here — the save pipeline clears it once the Sound is
        // actually persisted (RecorderDraftSaveCleanup). Backing out of the save screen therefore leaves
        // the clip recoverable from the Landing banner instead of silently lost.
        emit(RecorderEvent.Handoff(review.uri))
    }

    /** Back while a clip exists (recording or review): drop it and return to ready. */
    fun onDiscard() {
        val wasRecording = mutableState.value is RecorderState.Recording
        pollJob?.cancel()
        val file = tempFile
        tempFile = null
        draftStore.clear()
        mutableState.value = RecorderState.Ready
        // Stop (if recording) THEN delete, in one ordered coroutine, so engine.stop() finalizing the
        // output file can't race the delete and leave an orphan (or throw on a vanished path).
        viewModelScope.launch(ioDispatcher) {
            if (wasRecording) runCatching { engine.stop() }
            file?.delete()
        }
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
        if (transitioning) return
        val recording = mutableState.value as? RecorderState.Recording ?: return
        transitioning = true
        try {
            pollJob?.cancel()
            val elapsed = recording.elapsedMs
            val produced = withContext(ioDispatcher) { engine.stop() }
            val file = tempFile
            if (!produced || file == null || elapsed < MIN_DURATION_MS) {
                discardTemp()
                draftStore.clear()
                amplitudes.clear()
                mutableState.value = RecorderState.Ready
                // A deliberate too-short tap is worth a nudge; an interruption-preserve under 1 s isn't.
                if (!preserve) emit(RecorderEvent.Message(R.string.app_recorder_feedback_too_short))
            } else {
                val uri = withContext(ioDispatcher) { uriProvider(file) }
                // Build the review envelope from the live samples BEFORE emitting Review, so the host
                // reads it on the resulting recompose and skips the decode (instant, no placeholder gap).
                capturedEnvelope = buildRecorderEnvelope(amplitudes, RECORDER_WAVEFORM_BARS)
                amplitudes.clear() // the 48-bar envelope is all we keep through the Review session.
                // Persist as a recoverable draft so a background / launcher re-entry / process death can
                // resume this clip instead of losing it (ADR 0019 § Draft recovery).
                draftStore.save(file, elapsed)
                mutableState.value = RecorderState.Review(uri = uri, durationMs = elapsed)
                analytics.log(AnalyticsEvent.RecordingCompleted)
            }
        } finally {
            transitioning = false
        }
    }

    private fun startPolling() {
        pollJob =
            viewModelScope.launch {
                var elapsed = 0L
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    elapsed += POLL_INTERVAL_MS
                    // One read per tick (maxAmplitude resets the peak) — reuse it for the live meter AND
                    // the accumulating envelope so review can render it instantly on stop.
                    val amplitude = engine.maxAmplitude()
                    amplitudes.add(amplitude)
                    mutableState.value = RecorderState.Recording(elapsedMs = elapsed, amplitude = amplitude)
                }
            }
    }

    private fun discardTemp() {
        val file = tempFile ?: return
        tempFile = null
        // File.delete is disk I/O — keep it off the main thread (StrictMode forbids disk on main, and
        // the debug build crashes on it). Called from onCleared the scope is already cancelled, so this
        // no-ops and the leftover temp is reclaimed by the purge-on-entry the next time the recorder opens.
        viewModelScope.launch(ioDispatcher) { file.delete() }
    }

    private fun emit(event: RecorderEvent) {
        eventChannel.trySend(event)
    }

    override fun onCleared() {
        pollJob?.cancel()
        engine.release()
        // Deliberately NOT discarding the temp file: a clip in Review is a persisted draft that must
        // survive this Activity being destroyed (launcher clearTop, process death) so Landing can offer
        // to resume it (ADR 0019 § Draft recovery). Explicit discard/re-record already deleted it;
        // orphans (no draft metadata) are reclaimed by the purge-on-entry next time.
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
