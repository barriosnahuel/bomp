/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.model.Sound
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

internal class PlayerControllerImpl(
    private val mediaPlayer: MediaPlayer = MediaPlayer(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
) : PlayerController {
    private var listener: PlayerControllerListener? = null
    private var currentSound: Sound? = null

    private val _playbackState = MutableStateFlow<PlaybackState?>(null)
    override val playbackState: StateFlow<PlaybackState?> = _playbackState.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable =
        object : Runnable {
            override fun run() {
                if (mediaPlayer.isPlaying) {
                    val position = mediaPlayer.currentPosition
                    listener?.onProgressUpdate(position)
                    _playbackState.update { it?.copy(positionMs = position) }
                    handler.postDelayed(this, PROGRESS_INTERVAL_MS)
                }
            }
        }

    /**
     * Tracks the in-flight setDataSource+prepare coroutine so a new start*() call can cancel the
     * previous one. `mediaPlayer.reset()` at the top of each new launch interrupts whatever the
     * previous IO block was doing; the cancelled coroutine bails after withContext returns via the
     * `isActive` guard so it never fires Tracker / listener events for work that was superseded.
     */
    private var prepareJob: Job? = null

    override fun startPlayingSound(
        context: Context,
        sound: Sound,
    ) {
        prepareJob?.cancel()
        prepareJob =
            scope.launch {
                preempt()
                handler.removeCallbacks(progressRunnable)
                mediaPlayer.reset()

                val prepared =
                    withContext(ioDispatcher) {
                        runCatching {
                            if (!setupSoundSource(context, sound)) return@runCatching null
                            mediaPlayer.prepare()
                            mediaPlayer.duration
                        }
                    }
                if (!isActive) return@launch

                prepared
                    .onSuccess { durationMs ->
                        if (durationMs == null) {
                            listener?.onPlayerError(sound)
                            return@onSuccess
                        }
                        completeSoundStart(sound, durationMs)
                    }.onFailure { e ->
                        if (e is IOException) {
                            Tracker.log("playback.sound=${sound.name}")
                            Tracker.track(RuntimeException("Media player can't be prepared for playback.", e))
                        }
                        listener?.onPlayerError(sound)
                    }
            }
    }

    private fun completeSoundStart(
        sound: Sound,
        durationMs: Int,
    ) {
        mediaPlayer.setOnCompletionListener {
            handler.removeCallbacks(progressRunnable)
            listener?.onPlayerStop(sound, completed = true)
        }
        currentSound = sound
        val started = runCatching { mediaPlayer.start() }
        started
            .onSuccess {
                // onPlayerStart fires AFTER start() succeeds so the UI never flips to "playing" when
                // start is going to throw. Both happen on Main, so the listener still updates before
                // the first progressRunnable post lands.
                listener?.onPlayerStart(sound, durationMs)
                handler.post(progressRunnable)
            }.onFailure { e ->
                if (e is IllegalStateException) {
                    Tracker.log("playback.sound=${sound.name}")
                    Tracker.track(RuntimeException("Media player can't be started for playback.", e))
                    listener?.onPlayerError(sound)
                }
            }
    }

    override fun startPlayingUri(
        context: Context,
        uri: Uri,
    ) {
        prepareJob?.cancel()
        prepareJob =
            scope.launch {
                preempt()
                handler.removeCallbacks(progressRunnable)
                mediaPlayer.reset()

                val prepared =
                    withContext(ioDispatcher) {
                        runCatching {
                            mediaPlayer.setDataSource(context, uri)
                            mediaPlayer.prepare()
                            mediaPlayer.duration
                        }
                    }
                if (!isActive) return@launch

                prepared
                    .onSuccess { durationMs -> completeUriStart(uri, durationMs) }
                    .onFailure { e -> trackUriPrepareFailure(uri, e) }
            }
    }

    private fun completeUriStart(
        uri: Uri,
        durationMs: Int,
    ) {
        mediaPlayer.setOnCompletionListener {
            handler.removeCallbacks(progressRunnable)
            _playbackState.value = null
        }
        currentSound = null
        val started = runCatching { mediaPlayer.start() }
        started
            .onSuccess {
                _playbackState.value =
                    PlaybackState(uri = uri, positionMs = 0, durationMs = durationMs, isPlaying = true)
                handler.post(progressRunnable)
            }.onFailure { e ->
                if (e is IllegalStateException) {
                    Tracker.log("playback.uri=$uri")
                    Tracker.track(RuntimeException("MediaPlayer can't be started for preview", e))
                }
            }
    }

    private fun trackUriPrepareFailure(
        uri: Uri,
        e: Throwable,
    ) {
        when (e) {
            is IOException -> {
                Tracker.log("playback.uri=$uri")
                Tracker.track(RuntimeException("Could not prepare preview", e))
            }
            is IllegalArgumentException -> {
                Tracker.log("playback.uri=$uri")
                Tracker.track(RuntimeException("Malformed preview uri", e))
            }
            is SecurityException -> {
                Tracker.log("playback.uri=$uri")
                Tracker.track(RuntimeException("No read permission for preview", e))
            }
            else -> throw e
        }
    }

    /**
     * Stops whatever was playing (Sound or Stream) so a new playback can take over. Fires the
     * Sound-bound listener if a Sound was playing; clears [_playbackState] if a Stream was playing.
     */
    private fun preempt() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            currentSound?.let { listener?.onPlayerStop(it, completed = false) }
            _playbackState.value = null
        }
    }

    private fun setupSoundSource(
        context: Context,
        sound: Sound,
    ): Boolean =
        if (sound.isBundled()) {
            try {
                MediaPlayerHelper.setupSoundSource(context, mediaPlayer, sound.rawRes)
            } catch (e: IOException) {
                Tracker.log("playback.sound=${sound.name}")
                Tracker.track(RuntimeException("User custom audio is not playable", e))
                false
            }
        } else {
            try {
                MediaPlayerHelper.setupSoundSource(context, mediaPlayer, sound.file!!)
            } catch (e: IOException) {
                Tracker.log("playback.sound=${sound.name}")
                Tracker.track(RuntimeException("Bundled audio is not playable", e))
                false
            }
        }

    override fun stopPlayingSound() {
        val isPlayingNow = mediaPlayer.isPlaying
        val hasStreamState = _playbackState.value != null
        if (isPlayingNow || hasStreamState) {
            if (isPlayingNow) {
                mediaPlayer.stop()
            }
            handler.removeCallbacks(progressRunnable)
            currentSound?.let { listener?.onPlayerStop(it, completed = false) }
            _playbackState.value = null
        }
    }

    override fun pause() {
        if (mediaPlayer.isPlaying && _playbackState.value != null) {
            mediaPlayer.pause()
            handler.removeCallbacks(progressRunnable)
            _playbackState.update { it?.copy(isPlaying = false) }
        }
    }

    override fun resume() {
        val state = _playbackState.value
        if (state != null && !state.isPlaying) {
            val resumed = runCatching { mediaPlayer.start() }
            resumed
                .onSuccess {
                    _playbackState.update { it?.copy(isPlaying = true) }
                    handler.post(progressRunnable)
                }.onFailure { e ->
                    if (e is IllegalStateException) {
                        Tracker.log("playback.uri=${state.uri}")
                        Tracker.track(RuntimeException("MediaPlayer can't resume preview", e))
                    }
                }
        }
    }

    override fun seekTo(positionMs: Int) {
        mediaPlayer.seekTo(positionMs)
    }

    override fun setOnStartStopListener(listener: PlayerControllerListener) {
        this.listener = listener
    }

    companion object {
        private const val PROGRESS_INTERVAL_MS = 100L
    }
}
