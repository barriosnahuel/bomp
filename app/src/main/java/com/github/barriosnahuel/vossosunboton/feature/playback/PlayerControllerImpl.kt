package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import android.media.MediaPlayer
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.model.Sound
import java.io.IOException

internal class PlayerControllerImpl(
    private val mediaPlayer: MediaPlayer = MediaPlayer(),
) : PlayerController {
    private var listener: PlayerControllerListener? = null
    private var currentSound: Sound? = null

    override fun startPlayingSound(
        context: Context,
        sound: Sound,
    ) {
        if (mediaPlayer.isPlaying) {
            // User clicked on a new button while still listening an audio, then we should turn that running button off.
            mediaPlayer.stop()
            listener?.onPlayerStop(currentSound!!)
        }

        mediaPlayer.reset()

        if (setupSoundSource(context, sound)) {
            try {
                mediaPlayer.prepare()
            } catch (e: IOException) {
                Tracker.track(RuntimeException("Media player can't be prepared for playback.", e))
            }

            mediaPlayer.setOnCompletionListener { listener?.onPlayerStop(sound) }
            mediaPlayer.setOnSeekCompleteListener { it.pause() }

            listener?.onPlayerStart(sound)
            currentSound = sound
            mediaPlayer.start()
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
        // Here sound is on so we have to stop it.

        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            listener?.onPlayerStop(currentSound!!)
        }
    }

    override fun setOnStartStopListener(listener: PlayerControllerListener) {
        this.listener = listener
    }
}
