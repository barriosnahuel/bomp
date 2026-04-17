package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import android.media.MediaPlayer
import com.github.barriosnahuel.vossosunboton.model.Sound
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import io.mockk.verifySequence
import org.junit.After
import org.junit.Test

internal class PlayerControllerTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `on stopPlayingSound when media player is playing should stop it`() {
        val mockedMediaPlayer = givenAMediaPlayerCurrentlyPlayingASound()

        whenCallingPlayerControllerStop(mockedMediaPlayer)

        thenItShouldStopMediaPlayer(mockedMediaPlayer)
    }

    @Test
    fun `on startPlayingSound when nothing is playing should reset and start`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        PlayerControllerImpl(mp).startPlayingSound(context, sound)

        verifyOrder {
            mp.reset()
            mp.prepare()
            mp.start()
        }
    }

    @Test
    fun `on startPlayingSound when a sound is already playing should stop it first`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = mockk<MediaPlayer>(relaxed = true)
        every { mp.isPlaying } returns true

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns true

        PlayerControllerImpl(mp).startPlayingSound(context, sound)

        verifyOrder {
            mp.stop()
            mp.reset()
            mp.start()
        }
    }

    @Test
    fun `on startPlayingSound when source setup fails should not start media player`() {
        val context = mockk<Context>(relaxed = true)
        val sound = Sound("test", rawRes = 1)
        val mp = givenAnIdleMediaPlayer()

        mockkStatic(MediaPlayerHelper::class)
        every { MediaPlayerHelper.setupSoundSource(any(), any(), any<Int>()) } returns false

        PlayerControllerImpl(mp).startPlayingSound(context, sound)

        verify(exactly = 0) { mp.start() }
    }

    private fun givenAMediaPlayerCurrentlyPlayingASound(): MediaPlayer {
        val mockedMediaPlayer = mockk<MediaPlayer>()
        every { mockedMediaPlayer.isPlaying } returns true
        every { mockedMediaPlayer.stop() } answers { nothing }
        return mockedMediaPlayer
    }

    private fun givenAnIdleMediaPlayer(): MediaPlayer =
        mockk<MediaPlayer>(relaxed = true).also {
            every { it.isPlaying } returns false
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
