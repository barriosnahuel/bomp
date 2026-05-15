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

    /**
     * What data source is currently loaded into [mediaPlayer]. `null` means IDLE / no source.
     * When non-null the player is in STARTED (mp.isPlaying == true) or PAUSED (mp.isPlaying ==
     * false but the data source is intact) state.
     */
    private var target: PlaybackTarget? = null

    /**
     * Per-sound position cache for sounds that are NOT the currently-loaded target. When the user
     * returns to one of these we [MediaPlayer.seekTo] the cached value before [MediaPlayer.start].
     * In-memory only — process death loses it. Cleared on natural completion, [stopPlayingSound],
     * or [forgetSound]. Keyed by [Sound.id], the stable internal identity (see ADR 0008).
     */
    private val savedSoundPositions = mutableMapOf<String, Int>()

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

    private sealed class PlaybackTarget {
        data class SoundTarget(
            val sound: Sound,
        ) : PlaybackTarget()

        data class UriTarget(
            val uri: Uri,
        ) : PlaybackTarget()
    }

    override fun startPlayingSound(
        context: Context,
        sound: Sound,
    ) {
        val currentTarget = target
        if (currentTarget is PlaybackTarget.SoundTarget && currentTarget.sound.id == sound.id && !mediaPlayer.isPlaying) {
            // Same sound, paused → resume in place. MediaPlayer kept the data source and the
            // position; no reset/prepare/seek needed. Cancel any superseded prepare just in case.
            prepareJob?.cancel()
            resumeCurrentTarget()
            return
        }

        prepareJob?.cancel()
        prepareJob =
            scope.launch {
                preemptCurrentTargetPreservingPosition()
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
            savedSoundPositions.remove(sound.id)
            target = null
            listener?.onPlayerStop(sound, completed = true)
        }
        val seekPos = savedSoundPositions[sound.id]
        if (seekPos != null) {
            // seekTo is valid in PREPARED state; the next start() picks up from the seek position.
            runCatching { mediaPlayer.seekTo(seekPos) }
        }
        val started = runCatching { mediaPlayer.start() }
        started
            .onSuccess {
                target = PlaybackTarget.SoundTarget(sound)
                // onPlayerStart fires AFTER start() succeeds so the UI never flips to "playing" when
                // start is going to throw. Both happen on Main, so the listener still updates before
                // the first progressRunnable post lands.
                val pos = runCatching { mediaPlayer.currentPosition }.getOrDefault(0)
                listener?.onPlayerStart(sound, durationMs = durationMs, positionMs = pos)
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
        val currentTarget = target
        if (currentTarget is PlaybackTarget.UriTarget && currentTarget.uri == uri && !mediaPlayer.isPlaying) {
            prepareJob?.cancel()
            resumeCurrentTarget()
            return
        }

        prepareJob?.cancel()
        prepareJob =
            scope.launch {
                preemptCurrentTargetPreservingPosition()
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
            target = null
            _playbackState.value = null
        }
        val started = runCatching { mediaPlayer.start() }
        started
            .onSuccess {
                target = PlaybackTarget.UriTarget(uri)
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
     * Save the current target's playback position (if any) and emit the corresponding stop event
     * so the consumer can update its UI. The caller is about to `mediaPlayer.reset()` for a new
     * data source, so we capture position here while it is still valid. We do NOT call
     * `mediaPlayer.stop()` first — `reset()` subsumes it from any state, and an extra `stop()` is
     * wasted work that also moves the player through STOPPED, an extra state transition we don't
     * need.
     */
    private fun preemptCurrentTargetPreservingPosition() {
        val t = target ?: return
        val pos = runCatching { mediaPlayer.currentPosition }.getOrDefault(0)
        when (t) {
            is PlaybackTarget.SoundTarget -> {
                savedSoundPositions[t.sound.id] = pos
                // Preemption preserves position → it is a pause, not a stop. The consumer keeps the
                // sound visible at `pos`. `mediaPlayer.duration` is still valid here because the
                // caller resets the player only AFTER this method returns.
                val dur = runCatching { mediaPlayer.duration }.getOrDefault(0)
                listener?.onPlayerPause(t.sound, positionMs = pos, durationMs = dur)
            }
            is PlaybackTarget.UriTarget -> {
                // Uri previews don't get a cross-screen position cache: the AudioPreview composable
                // already clears state on dispose via stopPlayingSound, so a saved Uri position
                // would never be read. If a future surface wants Uri resume across navigation,
                // introduce a savedUriPositions map symmetric to savedSoundPositions.
                _playbackState.value = null
            }
        }
        target = null
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
        val hasUriState = _playbackState.value != null
        val t = target
        if (!isPlayingNow && !hasUriState && t == null) return

        if (isPlayingNow) {
            mediaPlayer.stop()
        }
        handler.removeCallbacks(progressRunnable)
        when (t) {
            is PlaybackTarget.SoundTarget -> {
                savedSoundPositions.remove(t.sound.id)
                listener?.onPlayerStop(t.sound, completed = false)
            }
            is PlaybackTarget.UriTarget -> {
                _playbackState.value = null
            }
            null -> {
                // Orphan Uri state without a tracked target (e.g. after a failed start path).
                _playbackState.value = null
            }
        }
        target = null
    }

    override fun pause() {
        val t = target
        if (t == null || !mediaPlayer.isPlaying) return
        val paused = runCatching { mediaPlayer.pause() }
        paused.onFailure { e ->
            if (e is IllegalStateException) {
                Tracker.log("playback.target=${describeTarget(t)}")
                Tracker.track(RuntimeException("MediaPlayer can't pause", e))
            }
        }
        if (paused.isFailure) return
        handler.removeCallbacks(progressRunnable)
        val pos = runCatching { mediaPlayer.currentPosition }.getOrDefault(0)
        when (t) {
            is PlaybackTarget.SoundTarget -> {
                savedSoundPositions[t.sound.id] = pos
                val dur = runCatching { mediaPlayer.duration }.getOrDefault(0)
                listener?.onPlayerPause(t.sound, positionMs = pos, durationMs = dur)
            }
            is PlaybackTarget.UriTarget -> {
                _playbackState.update { it?.copy(positionMs = pos, isPlaying = false) }
            }
        }
    }

    override fun resume() {
        val t = target
        if (t == null || mediaPlayer.isPlaying) return
        val uriBlocked = t is PlaybackTarget.UriTarget && (_playbackState.value?.isPlaying != false)
        if (uriBlocked) return
        resumeCurrentTarget()
    }

    private fun resumeCurrentTarget() {
        val t = target ?: return
        val resumed = runCatching { mediaPlayer.start() }
        resumed
            .onSuccess {
                val pos = runCatching { mediaPlayer.currentPosition }.getOrDefault(0)
                when (t) {
                    is PlaybackTarget.SoundTarget -> {
                        val duration = runCatching { mediaPlayer.duration }.getOrDefault(0)
                        listener?.onPlayerStart(t.sound, durationMs = duration, positionMs = pos)
                        handler.post(progressRunnable)
                    }
                    is PlaybackTarget.UriTarget -> {
                        _playbackState.update { it?.copy(positionMs = pos, isPlaying = true) }
                        handler.post(progressRunnable)
                    }
                }
            }.onFailure { e ->
                if (e is IllegalStateException) {
                    when (t) {
                        is PlaybackTarget.SoundTarget -> {
                            Tracker.log("playback.sound=${t.sound.name}")
                            Tracker.track(RuntimeException("MediaPlayer can't resume sound", e))
                            listener?.onPlayerError(t.sound)
                        }
                        is PlaybackTarget.UriTarget -> {
                            Tracker.log("playback.uri=${t.uri}")
                            Tracker.track(RuntimeException("MediaPlayer can't resume preview", e))
                        }
                    }
                }
            }
    }

    override fun seekTo(positionMs: Int) {
        mediaPlayer.seekTo(positionMs)
    }

    override fun forgetSound(sound: Sound) {
        savedSoundPositions.remove(sound.id)
        val t = target
        if (t is PlaybackTarget.SoundTarget && t.sound.id == sound.id) {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            handler.removeCallbacks(progressRunnable)
            mediaPlayer.reset()
            target = null
            listener?.onPlayerStop(t.sound, completed = false)
        }
    }

    override fun setOnStartStopListener(listener: PlayerControllerListener) {
        this.listener = listener
    }

    private fun describeTarget(t: PlaybackTarget): String =
        when (t) {
            is PlaybackTarget.SoundTarget -> "sound=${t.sound.name}"
            is PlaybackTarget.UriTarget -> "uri=${t.uri}"
        }

    companion object {
        private const val PROGRESS_INTERVAL_MS = 100L
    }
}
