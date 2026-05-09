/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.model.Sound
import java.io.IOException

internal class PlayerControllerImpl(
    private val mediaPlayer: MediaPlayer = MediaPlayer(),
) : PlayerController {
    private var listener: PlayerControllerListener? = null
    private var currentSound: Sound? = null

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable =
        object : Runnable {
            override fun run() {
                if (mediaPlayer.isPlaying) {
                    listener?.onProgressUpdate(mediaPlayer.currentPosition)
                    handler.postDelayed(this, PROGRESS_INTERVAL_MS)
                }
            }
        }

    override fun startPlayingSound(
        context: Context,
        sound: Sound,
    ) {
        if (mediaPlayer.isPlaying) {
            // User clicked on a new button while still listening an audio, then we should turn that running button off.
            mediaPlayer.stop()
            listener?.onPlayerStop(currentSound!!, completed = false)
        }

        handler.removeCallbacks(progressRunnable)
        mediaPlayer.reset()

        if (!prepareForPlayback(context, sound)) {
            listener?.onPlayerError(sound)
            return
        }

        val durationMs = mediaPlayer.duration
        mediaPlayer.setOnCompletionListener {
            handler.removeCallbacks(progressRunnable)
            listener?.onPlayerStop(sound, completed = true)
        }

        currentSound = sound
        try {
            mediaPlayer.start()
        } catch (e: IllegalStateException) {
            Tracker.track(RuntimeException("Media player can't be started for playback.", e))
            listener?.onPlayerError(sound)
            return
        }
        // onPlayerStart fires AFTER start() succeeds so the UI never flips to "playing" when start
        // is going to throw. Both happen on Main, so the listener still updates before the first
        // progressRunnable post lands.
        listener?.onPlayerStart(sound, durationMs)
        handler.post(progressRunnable)
    }

    private fun prepareForPlayback(
        context: Context,
        sound: Sound,
    ): Boolean {
        if (!setupSoundSource(context, sound)) return false
        return try {
            mediaPlayer.prepare()
            true
        } catch (e: IOException) {
            Tracker.track(RuntimeException("Media player can't be prepared for playback.", e))
            false
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
                Tracker.track(java.lang.RuntimeException("User custom button is not playable", e))
                false
            }
        } else {
            try {
                MediaPlayerHelper.setupSoundSource(context, mediaPlayer, sound.file!!)
            } catch (e: IOException) {
                Tracker.track(java.lang.RuntimeException("Bundled button is not playable", e))
                false
            }
        }

    override fun stopPlayingSound() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            handler.removeCallbacks(progressRunnable)
            listener?.onPlayerStop(currentSound!!, completed = false)
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
