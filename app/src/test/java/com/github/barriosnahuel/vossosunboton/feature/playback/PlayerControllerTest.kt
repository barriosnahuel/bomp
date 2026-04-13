package com.github.barriosnahuel.vossosunboton.feature.playback

import android.media.MediaPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verifySequence
import org.junit.Test

internal class PlayerControllerTest {
    @Test
    fun `on stopPlayingSound when media player is playing should stop it`() {
        val mockedMediaPlayer = givenAMediaPlayerCurrentlyPlayingASound()

        whenCallingPlayerControllerStop(mockedMediaPlayer)

        thenItShouldStopMediaPlayer(mockedMediaPlayer)
    }

    private fun givenAMediaPlayerCurrentlyPlayingASound(): MediaPlayer {
        val mockedMediaPlayer = mockk<MediaPlayer>()
        every { mockedMediaPlayer.isPlaying } returns true
        every { mockedMediaPlayer.stop() } answers { nothing }

        return mockedMediaPlayer
    }

    private fun whenCallingPlayerControllerStop(mockedMediaPlayer: MediaPlayer) {
        mockkObject(PlayerControllerFactory)

        every { PlayerControllerFactory.instance } returns PlayerControllerImpl(mockedMediaPlayer)
        PlayerControllerFactory.instance.stopPlayingSound()
    }

    private fun thenItShouldStopMediaPlayer(mockedMediaPlayer: MediaPlayer) {
        verifySequence {
            mockedMediaPlayer.isPlaying
            mockedMediaPlayer.stop()
        }
    }
}
