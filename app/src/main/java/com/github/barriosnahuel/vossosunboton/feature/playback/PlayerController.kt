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
 * this StateFlow — see ADR 0007 for the bridging rationale and revisit criteria.
 */
internal data class PlaybackState(
    val uri: Uri,
    val positionMs: Int,
    val durationMs: Int,
    val isPlaying: Boolean,
)

@Suppress("TooManyFunctions") // Single-facade contract by design (ADR 0005): every playback surface routes here.
internal interface PlayerController {
    /**
     * Emits a non-null value while a Uri-bound (preview) playback is active. Consumers filter by
     * matching [PlaybackState.uri] against their own source.
     */
    val playbackState: StateFlow<PlaybackState?>

    /**
     * Starts (or resumes) playback for [sound]. Behavior:
     * - If [sound] is the currently-loaded target and the MediaPlayer is paused → resume in place
     *   via `MediaPlayer.start()` (no reset, no re-prepare, position preserved).
     * - Otherwise → pause any other target while preserving its position, reset, prepare the new
     *   sound, seek to its previously-saved position (if any), and start.
     *
     * Saved positions live in-process and survive Activity recreation; they are lost on app/OS kill.
     * They are also cleared when the sound finishes naturally (OnCompletionListener) or when
     * [stopPlayingSound]/[forgetSound] is called for that sound.
     */
    fun startPlayingSound(
        context: Context,
        sound: Sound,
    )

    /**
     * Starts playback of an arbitrary [uri]. If another target (Sound or Uri) is currently playing
     * or paused it is preempted: its position is saved (Sound) or its state cleared (Uri), and the
     * registered [PlayerControllerListener] sees `onPlayerPause(currentSound, ...)` for the Sound
     * case (preemption preserves position — it is a pause, not a stop). Progress for the new
     * preview is reported via [playbackState] only — listener events are not fired (no Sound to pass).
     *
     * [startPositionMs] resumes a fresh start from that offset (e.g. the recorder review resuming a
     * scrubbed position); 0 starts from the beginning. A resume of the already-loaded paused uri
     * continues from its own head and ignores this.
     */
    fun startPlayingUri(
        context: Context,
        uri: Uri,
        startPositionMs: Int = 0,
    )

    /**
     * Starts [sound] as a LISTEN SESSION on the long-form engine (Media3 ExoPlayer) — the Vault
     * immersive surface. Fresh sessions ALWAYS start from 0: session sounds deliberately have no
     * cross-open position retention, unlike [startPlayingSound]'s saved-position semantics
     * (ADR 0022). When [sound] is the current paused session target this resumes in place — it is
     * the immersive's play toggle, valid mid-clip and after natural completion alike. Any current
     * playback on either engine is preempted ([startPlayingSound] targets pause with position; a
     * previous session stops). Events flow through the same [PlayerControllerListener] as list
     * playback, so consumers render sessions identically.
     */
    fun startListenSession(
        context: Context,
        sound: Sound,
    )

    /**
     * Starts [uri] as a listen session on the long-form engine — the recorder-review surface.
     * [startPositionMs] preserves review's resume-from-offset semantics (ADR 0022). Progress is
     * reported via [playbackState] only, exactly like [startPlayingUri] (no Sound to pass to the
     * listener).
     */
    fun startUriListenSession(
        context: Context,
        uri: Uri,
        startPositionMs: Int = 0,
    )

    /**
     * Fully stops the current target (if any), clears its saved position, and tears down the
     * progress callbacks. Use this for definitive stop scenarios (e.g. AudioPreview disposal,
     * deleting the currently-loaded sound). For "user tapped the playing sound to pause it" use
     * [pause] instead — that preserves position.
     */
    fun stopPlayingSound()

    /**
     * Pauses the currently-playing target (Sound or Uri), preserving its position so a later
     * [resume] or [startPlayingSound] of the same target picks up where it left off. No-op if
     * nothing is playing.
     */
    fun pause()

    /**
     * Resumes a paused target from its last position. No-op if nothing is paused or the current
     * target is already playing.
     */
    fun resume()

    fun seekTo(positionMs: Int)

    /**
     * Forgets all controller state associated with [sound]: removes its saved position from the
     * cache, and if it is the currently-loaded MediaPlayer target, stops and resets. Use when a
     * sound is being deleted so its in-memory playback state does not outlive the entity.
     */
    fun forgetSound(sound: Sound)

    /**
     * @param listener the listener that will handle all play/stop callbacks for all buttons.
     */
    fun setOnStartStopListener(listener: PlayerControllerListener)

    /**
     * Detaches [listener] only if it is the currently-registered one (a no-op once a newer listener
     * has replaced it). Call from the consumer's lifecycle teardown (e.g. `ViewModel.onCleared`): this
     * controller is a process-singleton, so a consumer that never detaches is retained for the whole
     * process — a leak (see [PlayerControllerFactory]).
     */
    fun removeOnStartStopListener(listener: PlayerControllerListener)
}

internal interface PlayerControllerListener {
    /**
     * Perform any action you want after the player has definitively stopped — the playback
     * position is gone.
     * @param sound The sound that was playing before.
     * @param completed `true` when the audio reached natural end-of-stream
     *   (`MediaPlayer.OnCompletionListener`); `false` when [PlayerController.stopPlayingSound] or
     *   [PlayerController.forgetSound] was called explicitly. The Sticker Cero auto-destruct branch
     *   in `SoundsViewModel` relies on `completed` to distinguish a finished welcome message from
     *   an explicit stop. NOTE: a user pause or a preemption is NOT a stop — see [onPlayerPause].
     */
    fun onPlayerStop(
        sound: Sound,
        completed: Boolean,
    )

    /**
     * The given [sound] was paused with its position retained — either by a user toggle or by
     * being preempted when another playback started. A later resume continues from [positionMs].
     * Distinct from [onPlayerStop]: the consumer should keep the sound visible as "paused at
     * [positionMs]" (e.g. the progress bar stays where it was) rather than collapsing it to a
     * fresh "stopped" state.
     * @param sound The sound that was paused.
     * @param positionMs Playback position preserved for resume, in milliseconds.
     * @param durationMs Total duration of the audio in milliseconds.
     */
    fun onPlayerPause(
        sound: Sound,
        positionMs: Int,
        durationMs: Int,
    )

    /**
     * Perform any action you want right after the given sound started (or resumed) playback.
     * @param sound The sound that has started to play.
     * @param durationMs Total duration of the audio in milliseconds.
     * @param positionMs Current playback position in milliseconds. Non-zero on resume from a
     *   paused state or when starting a sound that had a saved position from a previous session
     *   in this process; zero on a fresh start.
     */
    fun onPlayerStart(
        sound: Sound,
        durationMs: Int,
        positionMs: Int = 0,
    )

    /** Called approximately every 100 ms while audio is playing. */
    fun onProgressUpdate(positionMs: Int)

    /** Called when the audio source could not be prepared for playback. */
    fun onPlayerError(sound: Sound)
}
