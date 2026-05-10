/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import android.net.Uri
import com.github.barriosnahuel.vossosunboton.model.Sound
import kotlinx.coroutines.flow.StateFlow

internal object PlayerControllerFactory {
    internal var instance: PlayerController = PlayerControllerImpl()
}

/**
 * Snapshot of an in-progress Stream (preview) playback. Null when no Stream is playing.
 *
 * Sound (Home/Explore list) playback is reported through [PlayerControllerListener], not through
 * this StateFlow — see ADR 0005 for the bridging rationale and revisit criteria.
 */
internal data class PlaybackState(
    val uri: Uri,
    val positionMs: Int,
    val durationMs: Int,
    val isPlaying: Boolean,
)

internal interface PlayerController {
    /**
     * Emits a non-null value while a Uri-bound (preview) playback is active. Consumers filter by
     * matching [PlaybackState.uri] against their own source.
     */
    val playbackState: StateFlow<PlaybackState?>

    fun startPlayingSound(
        context: Context,
        sound: Sound,
    )

    /**
     * Starts playback of an arbitrary [uri]. If a Sound is currently playing it is stopped first
     * (the registered [PlayerControllerListener] sees `onPlayerStop(currentSound, completed = false)`).
     * Progress is reported via [playbackState] only — listener events are not fired (no Sound to
     * pass).
     */
    fun startPlayingUri(
        context: Context,
        uri: Uri,
    )

    fun stopPlayingSound()

    /**
     * Pauses an in-progress Stream playback. No-op for Sound playback (the Home UX is
     * "tap again to stop", not pause/resume).
     */
    fun pause()

    /**
     * Resumes a paused Stream playback from its last position.
     */
    fun resume()

    fun seekTo(positionMs: Int)

    /**
     * @param listener the listener that will handle all play/stop callbacks for all buttons.
     */
    fun setOnStartStopListener(listener: PlayerControllerListener)
}

internal interface PlayerControllerListener {
    /**
     * Perform any action you want after player has stopped.
     * @param sound The sound that was playing before.
     * @param completed `true` when the audio reached natural end-of-stream
     *   (`MediaPlayer.OnCompletionListener`); `false` when stopped by the user (or pre-empted by a
     *   new `startPlayingSound` call). The Sticker Cero auto-destruct branch in `SoundsViewModel`
     *   relies on this to distinguish a finished welcome message from a user-initiated pause.
     */
    fun onPlayerStop(
        sound: Sound,
        completed: Boolean,
    )

    /**
     * Perform any action you want right after the given sound started to play.
     * @param sound The sound that has started to play.
     * @param durationMs Total duration of the audio in milliseconds.
     */
    fun onPlayerStart(
        sound: Sound,
        durationMs: Int,
    )

    /** Called approximately every 100 ms while audio is playing. */
    fun onProgressUpdate(positionMs: Int)

    /** Called when the audio source could not be prepared for playback. */
    fun onPlayerError(sound: Sound)
}
