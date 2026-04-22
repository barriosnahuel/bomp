/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import com.github.barriosnahuel.vossosunboton.model.Sound

internal object PlayerControllerFactory {
    internal var instance: PlayerController = PlayerControllerImpl()
}

internal interface PlayerController {
    fun startPlayingSound(
        context: Context,
        sound: Sound,
    )

    fun stopPlayingSound()

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
     */
    fun onPlayerStop(sound: Sound)

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
