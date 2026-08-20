/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTransport
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
 *
 * Sessions are also published to the system through [bridge] (media notification, lock screen,
 * media keys). External surfaces command the [Player] DIRECTLY — not through this engine's
 * [pause]/[resume] — so the [playerListener] reconciles player-initiated transitions back into
 * the same consumer events, keyed off [publishedPlaying] to avoid double emission.
 */
internal class ListenSessionEngine(
    private val playerProvider: (Context) -> Player,
    private val handler: Handler,
    private val listenerProvider: () -> PlayerControllerListener?,
    private val playbackState: MutableStateFlow<PlaybackState?>,
    private val bridge: MediaSessionBridge,
    /**
     * Reports that a system surface (media notification, lock screen, media key) drove the
     * playback, with the action that happened. Kept as a callback so this engine stays free of
     * analytics and tests can assert the origin split without a tracker.
     */
    private val onSystemTransport: (Context, String) -> Unit = { _, _ -> },
    /** Injectable clock: the system-seek coalescing window below is time-based. */
    private val nowMs: () -> Long = { SystemClock.uptimeMillis() },
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
    private var appContext: Context? = null

    /** Set by start calls; consumed by the first STATE_READY so re-buffers don't re-emit start events. */
    private var startPending = false

    /**
     * The play/pause state last emitted to consumers. When a player callback reports a state we
     * already emitted (because the transition came from [pause]/[resume]) the reconciliation in
     * [playerListener] skips it; a mismatch means the player was commanded externally (media
     * notification, media key, audio-focus loss) and the event must be emitted from the callback.
     */
    private var publishedPlaying = false

    /**
     * Last reason Media3 gave for a playWhenReady flip. An automatic pause (audio-focus loss, the
     * headphones coming out) is not the Bomper touching a control, so it must not be reported as
     * transport use. Media3 delivers this alongside the isPlaying callback that reconciles state.
     */
    private var lastPlayWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST

    /**
     * True while this engine is itself commanding the player. Media3 can deliver the state callback
     * SYNCHRONOUSLY inside `player.pause()` / `player.play()`, i.e. before [publishedPlaying] has been
     * updated — so the reconciliation would see a mismatch and report our own pause as a system one.
     * Measured on device: a screen pause emitted both origins 5 ms apart.
     */
    private var selfCommandPending = false

    /** Set by [seekTo] so the discontinuity it causes is not mistaken for a lock-screen scrub. */
    private var selfSeekPending = false

    /**
     * When the last system seek was reported. Dragging the lock-screen seekbar emits a
     * discontinuity per movement, so on device three gestures reported six events — which inflates a
     * system scrub against the same gesture on our own waveform (that one reports once, on release).
     * Reports inside [SYSTEM_SEEK_WINDOW_MS] of the previous one are the same gesture and collapse
     * into it.
     */
    private var lastSystemSeekAtMs: Long? = null

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

            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                lastPlayWhenReadyReason = reason
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (startPending) return // The initial start is STATE_READY's to emit, whatever the callback order.
                val t = target
                val p = player
                when {
                    isPlaying == publishedPlaying ->
                        // Regaining after a transient suppression (audio-focus loss, re-buffer) is not a
                        // state change to re-emit, but the self-terminating progress ticker must be re-armed.
                        if (isPlaying) {
                            handler.removeCallbacks(progressRunnable)
                            handler.post(progressRunnable)
                        }
                    t == null || p == null -> Unit
                    isPlaying -> {
                        publishPlaying(t, p)
                        reportSystemTransport(AnalyticsTransport.PLAY)
                    }
                    // ENDED is onEnded's stop; playWhenReady still true is a transient re-buffer.
                    p.playbackState != Player.STATE_ENDED && !p.playWhenReady -> {
                        publishPaused(t, p)
                        reportSystemTransport(AnalyticsTransport.PAUSE)
                    }
                    else -> Unit
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                // External seeks (lock-screen seekbar) move the head without passing through seekTo;
                // publish the new position so a paused UI doesn't keep the stale one.
                if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                val wasOurs = selfSeekPending
                selfSeekPending = false
                if (!wasOurs) reportSystemTransport(AnalyticsTransport.SEEK)
                val position = newPosition.positionMs.toInt()
                when (target) {
                    is SessionTarget.SoundTarget -> listenerProvider()?.onProgressUpdate(position)
                    is SessionTarget.UriTarget -> playbackState.update { it?.copy(positionMs = position) }
                    null -> Unit
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
        load(context, uri, startPositionMs = 0, title = sound.name)
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
        load(context, uri, startPositionMs, title = context.getString(R.string.app_playback_recording_session_title))
    }

    private fun load(
        context: Context,
        uri: Uri,
        startPositionMs: Int,
        title: String,
    ) {
        val p = obtainPlayer(context)
        startPending = true
        p.setMediaItem(
            MediaItem
                .Builder()
                .setUri(uri)
                // Title feeds the system media surfaces (notification, lock screen).
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build(),
        )
        p.prepare()
        if (startPositionMs > 0) p.seekTo(startPositionMs.toLong())
        p.play()
        bridge.onSessionStarted(context.applicationContext)
    }

    /**
     * Stops and clears the session, emitting the definitive stop for its target — preemption of a
     * session is a stop, never a pause (no retention). No-op when idle.
     */
    fun stop() {
        val t = target ?: return
        handler.removeCallbacks(progressRunnable)
        startPending = false
        publishedPlaying = false
        // clearMediaItems drops the notification content and turns a later stray media-key press
        // into a no-op — a dead session must not be resurrectable from the system surface.
        player?.let {
            runCatching { it.stop() }
            runCatching { it.clearMediaItems() }
        }
        target = null
        appContext?.let { bridge.onSessionEnded(it) }
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
            selfCommandPending = true
            runCatching { p.pause() }
            startPending = false
            publishPaused(t, p)
            selfCommandPending = false
        }
        return true
    }

    /** Resumes the paused session in place, re-emitting the start event. Returns false when idle. */
    fun resume(): Boolean {
        val t = target ?: return false
        val p = player
        if (p != null && !p.isPlaying) {
            selfCommandPending = true
            p.play()
            publishPlaying(t, p)
            selfCommandPending = false
            // Re-publish to the system: after onTaskRemoved the service is gone while the paused
            // target survives, so an in-place resume must restore the notification (start is idempotent).
            appContext?.let { bridge.onSessionStarted(it) }
        }
        return true
    }

    /** Moves the session head. Returns false when idle (caller falls through to MediaPlayer). */
    fun seekTo(positionMs: Int): Boolean {
        if (target == null) return false
        selfSeekPending = true
        player?.let { runCatching { it.seekTo(positionMs.toLong()) } }
        playbackState.update { it?.copy(positionMs = positionMs) }
        return true
    }

    private fun obtainPlayer(context: Context): Player =
        player ?: run {
            val app = context.applicationContext
            appContext = app
            playerProvider(app).also {
                it.addListener(playerListener)
                bridge.onSessionPlayerCreated(app, it)
                player = it
            }
        }

    /**
     * Reports a transport action that did NOT come from this engine's own API — by
     * [publishedPlaying]'s contract, that means a system surface drove it. Automatic pauses are
     * filtered out: they are the OS reacting, not the Bomper acting.
     */
    private val reportSystemTransport: (String) -> Unit = { action ->
        val isSeek = action == AnalyticsTransport.SEEK
        // Our own command, reported back to us before we published the new state.
        val ours = selfCommandPending && !isSeek
        // The OS reacting (focus lost, headphones out), not the Bomper touching anything.
        val automatic =
            !isSeek &&
                (
                    lastPlayWhenReadyReason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                        lastPlayWhenReadyReason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                )
        // Null, not 0: a zero sentinel would swallow the first seek whenever uptime is still low.
        val withinGesture = isSeek && lastSystemSeekAtMs?.let { nowMs() - it < SYSTEM_SEEK_WINDOW_MS } == true

        if (!ours && !automatic && !withinGesture) {
            if (isSeek) lastSystemSeekAtMs = nowMs()
            appContext?.let { onSystemTransport(it, action) }
        }
    }

    /** Emits the paused state for [t] and stops progress ticking. Shared by [pause] and reconciliation. */
    private fun publishPaused(
        t: SessionTarget,
        p: Player,
    ) {
        handler.removeCallbacks(progressRunnable)
        publishedPlaying = false
        val position = p.currentPosition.toInt()
        when (t) {
            is SessionTarget.SoundTarget ->
                listenerProvider()?.onPlayerPause(t.sound, positionMs = position, durationMs = p.durationMsOrZero())
            is SessionTarget.UriTarget ->
                playbackState.update { it?.copy(positionMs = position, isPlaying = false) }
        }
    }

    /** Emits the playing state for [t] and (re)starts progress ticking. Shared by [resume] and reconciliation. */
    private fun publishPlaying(
        t: SessionTarget,
        p: Player,
    ) {
        publishedPlaying = true
        val position = p.currentPosition.toInt()
        when (t) {
            is SessionTarget.SoundTarget ->
                listenerProvider()?.onPlayerStart(t.sound, durationMs = p.durationMsOrZero(), positionMs = position)
            is SessionTarget.UriTarget ->
                playbackState.update { it?.copy(positionMs = position, isPlaying = true) }
        }
        handler.post(progressRunnable)
    }

    private fun onReady() {
        if (!startPending) return
        startPending = false
        val p = player ?: return
        val duration = p.durationMsOrZero()
        val position = p.currentPosition.toInt()
        publishedPlaying = true
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
        val t = target ?: return
        handler.removeCallbacks(progressRunnable)
        publishedPlaying = false
        player?.let { runCatching { it.clearMediaItems() } }
        target = null
        appContext?.let { bridge.onSessionEnded(it) }
        when (t) {
            is SessionTarget.SoundTarget -> listenerProvider()?.onPlayerStop(t.sound, completed = true)
            is SessionTarget.UriTarget -> playbackState.value = null
        }
    }

    private fun onError(error: PlaybackException) {
        handler.removeCallbacks(progressRunnable)
        val t = target
        target = null
        startPending = false
        publishedPlaying = false
        appContext?.let { bridge.onSessionEnded(it) }
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
        /**
         * One drag of a system seekbar fires several discontinuities; this window folds them into one
         * report. Calibrated on device: the bursts inside a single gesture spanned 17 ms and 96 ms, while
         * two deliberate drags came 976 ms apart — so 300 ms clears the noise with room to spare and still
         * counts two quick gestures as two.
         */
        const val SYSTEM_SEEK_WINDOW_MS = 300L

        private const val PROGRESS_INTERVAL_MS = 100L
    }
}
