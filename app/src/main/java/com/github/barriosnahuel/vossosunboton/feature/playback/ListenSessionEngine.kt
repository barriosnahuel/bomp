/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.Sound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Long-form listen-session engine (Media3 ExoPlayer) : docs/adr/0022. Owns the lazily-created
 * session [Player] for the listening surfaces (Vault immersive, recorder review) and reports
 * through the SAME channels as the MediaPlayer engine — [PlayerControllerListener] for Sound
 * targets, [playbackState] for Uri targets — so consumers render sessions identically.
 *
 * Sessions have NO cross-open position retention: starts are always from 0 (or the explicit
 * [startUri] offset) and preemption is a definitive stop, never a pause. In-session pause/resume
 * keeps the player's own head. [PlayerControllerImpl] is the only caller and enforces the
 * one-active-playback invariant across both engines. Main-thread only, like the whole controller.
 */
internal class ListenSessionEngine(
    private val playerProvider: (Context) -> Player,
    private val handler: Handler,
    private val listenerProvider: () -> PlayerControllerListener?,
    private val playbackState: MutableStateFlow<PlaybackState?>,
) {
    private sealed class SessionTarget {
        data class SoundTarget(
            val sound: Sound,
        ) : SessionTarget()

        data class UriTarget(
            val uri: Uri,
        ) : SessionTarget()
    }

    private var player: Player? = null
    private var target: SessionTarget? = null

    /** Set by start calls; consumed by the first STATE_READY so re-buffers don't re-emit start events. */
    private var startPending = false

    val isActive: Boolean get() = target != null

    /** [Sound.id] of the active session target, or null when idle / playing a Uri. */
    val targetSoundId: String? get() = (target as? SessionTarget.SoundTarget)?.sound.let { it?.id }

    private val progressRunnable =
        object : Runnable {
            override fun run() {
                val p = player ?: return
                if (p.isPlaying) {
                    val position = p.currentPosition.toInt()
                    listenerProvider()?.onProgressUpdate(position)
                    playbackState.update { it?.copy(positionMs = position) }
                    handler.postDelayed(this, PROGRESS_INTERVAL_MS)
                }
            }
        }

    private val playerListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> onReady()
                    Player.STATE_ENDED -> onEnded()
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                this@ListenSessionEngine.onError(error)
            }
        }

    /**
     * Starts [sound] as a session. Fresh sessions always start from 0 (no saved-position lookup,
     * ADR 0022); when [sound] is the current PAUSED session target this is the in-session play
     * toggle and resumes in place. After natural completion the target is cleared, so a replay is
     * a fresh from-0 start — never a MediaPlayer fallback.
     */
    fun startSound(
        context: Context,
        sound: Sound,
    ) {
        val current = target
        if (current is SessionTarget.SoundTarget && current.sound.id == sound.id && player?.isPlaying == false) {
            resume()
            return
        }
        stop()
        val uri =
            if (sound.isBundled()) {
                Uri.parse("android.resource://${context.packageName}/${sound.rawRes}")
            } else {
                Uri.fromFile(getFile(context, sound.file!!))
            }
        target = SessionTarget.SoundTarget(sound)
        load(context, uri, startPositionMs = 0)
    }

    /** Starts [uri] as a session, honoring [startPositionMs] (recorder review's scrub offset). */
    fun startUri(
        context: Context,
        uri: Uri,
        startPositionMs: Int,
    ) {
        val current = target
        if (current is SessionTarget.UriTarget && current.uri == uri && player?.isPlaying == false) {
            // Same uri, paused → resume in place (mirrors startPlayingUri's resume shortcut).
            resume()
            return
        }
        stop()
        target = SessionTarget.UriTarget(uri)
        load(context, uri, startPositionMs)
    }

    private fun load(
        context: Context,
        uri: Uri,
        startPositionMs: Int,
    ) {
        val p = obtainPlayer(context)
        startPending = true
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        if (startPositionMs > 0) p.seekTo(startPositionMs.toLong())
        p.play()
    }

    /**
     * Stops and clears the session, emitting the definitive stop for its target — preemption of a
     * session is a stop, never a pause (no retention). No-op when idle.
     */
    fun stop() {
        val t = target ?: return
        handler.removeCallbacks(progressRunnable)
        startPending = false
        player?.let { runCatching { it.stop() } }
        target = null
        when (t) {
            is SessionTarget.SoundTarget -> listenerProvider()?.onPlayerStop(t.sound, completed = false)
            is SessionTarget.UriTarget -> playbackState.value = null
        }
    }

    /**
     * Pauses the session in place. Returns false when idle (caller falls through to MediaPlayer).
     * Unconditional even mid-prepare — `Player.pause()` is just `playWhenReady = false`, so a tap
     * that pauses before STATE_READY cancels the pending start instead of letting audio pop later
     * (also consumes [startPending] so READY won't emit a stale start event).
     */
    fun pause(): Boolean {
        val t = target ?: return false
        val p = player
        if (p != null) {
            runCatching { p.pause() }
            handler.removeCallbacks(progressRunnable)
            startPending = false
            val position = p.currentPosition.toInt()
            when (t) {
                is SessionTarget.SoundTarget ->
                    listenerProvider()?.onPlayerPause(t.sound, positionMs = position, durationMs = p.durationMsOrZero())
                is SessionTarget.UriTarget ->
                    playbackState.update { it?.copy(positionMs = position, isPlaying = false) }
            }
        }
        return true
    }

    /** Resumes the paused session in place, re-emitting the start event. Returns false when idle. */
    fun resume(): Boolean {
        val t = target ?: return false
        val p = player
        if (p != null && !p.isPlaying) {
            p.play()
            val position = p.currentPosition.toInt()
            when (t) {
                is SessionTarget.SoundTarget ->
                    listenerProvider()?.onPlayerStart(t.sound, durationMs = p.durationMsOrZero(), positionMs = position)
                is SessionTarget.UriTarget ->
                    playbackState.update { it?.copy(positionMs = position, isPlaying = true) }
            }
            handler.post(progressRunnable)
        }
        return true
    }

    /** Moves the session head. Returns false when idle (caller falls through to MediaPlayer). */
    fun seekTo(positionMs: Int): Boolean {
        if (target == null) return false
        player?.let { runCatching { it.seekTo(positionMs.toLong()) } }
        playbackState.update { it?.copy(positionMs = positionMs) }
        return true
    }

    private fun obtainPlayer(context: Context): Player =
        player ?: playerProvider(context.applicationContext).also {
            it.addListener(playerListener)
            player = it
        }

    private fun onReady() {
        if (!startPending) return
        startPending = false
        val p = player ?: return
        val duration = p.durationMsOrZero()
        val position = p.currentPosition.toInt()
        when (val t = target) {
            is SessionTarget.SoundTarget ->
                listenerProvider()?.onPlayerStart(t.sound, durationMs = duration, positionMs = position)
            is SessionTarget.UriTarget ->
                playbackState.value = PlaybackState(uri = t.uri, positionMs = position, durationMs = duration, isPlaying = true)
            null -> Unit
        }
        handler.post(progressRunnable)
    }

    private fun onEnded() {
        handler.removeCallbacks(progressRunnable)
        when (val t = target) {
            is SessionTarget.SoundTarget -> listenerProvider()?.onPlayerStop(t.sound, completed = true)
            is SessionTarget.UriTarget -> playbackState.value = null
            null -> Unit
        }
        target = null
    }

    private fun onError(error: PlaybackException) {
        handler.removeCallbacks(progressRunnable)
        val t = target
        target = null
        startPending = false
        when (t) {
            is SessionTarget.SoundTarget -> {
                Tracker.log("playback.sound=${t.sound.name}")
                Tracker.track(RuntimeException("Listen session can't play audio", error))
                listenerProvider()?.onPlayerError(t.sound)
            }
            is SessionTarget.UriTarget -> {
                Tracker.log("playback.uri=${t.uri}")
                Tracker.track(RuntimeException("Listen session can't play preview", error))
                playbackState.value = null
            }
            null -> Unit
        }
    }

    private fun Player.durationMsOrZero(): Int {
        val d = duration
        return if (d == C.TIME_UNSET) 0 else d.toInt()
    }

    private companion object {
        private const val PROGRESS_INTERVAL_MS = 100L
    }
}
