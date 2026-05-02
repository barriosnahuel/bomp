/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

internal class SoundsViewModelTest : AbstractRobolectricTest() {
    @Before
    fun setUp() {
        runBlocking {
            SoundsRepository(ApplicationProvider.getApplicationContext()).clearForTest()
        }
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initial tab is MY_SOUNDS`() {
        val viewModel = givenAViewModel()

        assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.MY_SOUNDS)
    }

    @Test
    fun `selectTab updates the selected tab`() {
        val viewModel = givenAViewModel()

        viewModel.selectTab(AppTab.MY_SOUNDS)

        assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.MY_SOUNDS)
    }

    @Test
    fun `deleteSound removes sound from the list and stores a delete event`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", file = "test.mp3")
        viewModel.injectSounds(listOf(sound))

        viewModel.deleteSound(sound)

        assertThat(viewModel.sounds.value).doesNotContain(sound)
        assertThat(viewModel.deletedSoundEvent.value?.sound).isEqualTo(sound)
    }

    @Test
    fun `restoreSound puts the sound back and clears the delete event`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", file = "test.mp3")
        viewModel.injectSounds(listOf(sound))

        viewModel.deleteSound(sound)
        viewModel.restoreSound()

        assertThat(viewModel.sounds.value).contains(sound)
        assertThat(viewModel.deletedSoundEvent.value).isNull()
    }

    @Test
    fun `onPlayerStart sets playingSound to the given sound`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", rawRes = 1)

        viewModel.onPlayerStart(sound, durationMs = 1000)

        assertThat(viewModel.playingSound.value?.name).isEqualTo(sound.name)
        assertThat(viewModel.playingSound.value?.isPlaying).isTrue()
        assertThat(sound.isPlaying).isFalse()
    }

    @Test
    fun `onPlayerStop clears playingSound`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", rawRes = 1)
        viewModel.onPlayerStart(sound, durationMs = 1000)

        viewModel.onPlayerStop(sound)

        assertThat(viewModel.playingSound.value).isNull()
        assertThat(sound.isPlaying).isFalse()
    }

    @Test
    fun `onPlayerStart updates sounds list with isPlaying true without mutating original sound`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", rawRes = 1)
        viewModel.injectSounds(listOf(sound))

        viewModel.onPlayerStart(sound, durationMs = 1000)

        assertThat(
            viewModel.sounds.value
                .single { it.name == "test" }
                .isPlaying,
        ).isTrue()
        assertThat(sound.isPlaying).isFalse()
    }

    @Test
    fun `onPlayerStop updates sounds list with isPlaying false without mutating original sound`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", null, 1, isPlaying = true)
        viewModel.injectSounds(listOf(sound))

        viewModel.onPlayerStop(sound)

        assertThat(
            viewModel.sounds.value
                .single { it.name == "test" }
                .isPlaying,
        ).isFalse()
        assertThat(sound.isPlaying).isTrue()
    }

    @Test
    fun `playOrStop when sound is not playing calls startPlayingSound`() {
        every { PlayerControllerFactory.instance.startPlayingSound(any(), any()) } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = Sound("test", null, 1, false)

        viewModel.playOrStop(sound)

        verify { PlayerControllerFactory.instance.startPlayingSound(any(), sound) }
    }

    @Test
    fun `playOrStop when sound is playing calls stopPlayingSound`() {
        every { PlayerControllerFactory.instance.stopPlayingSound() } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = Sound("test", null, 1, isPlaying = true)

        viewModel.playOrStop(sound)

        verify { PlayerControllerFactory.instance.stopPlayingSound() }
    }

    @Test
    fun `deleteSound when sound is playing stops playback`() {
        every { PlayerControllerFactory.instance.stopPlayingSound() } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = Sound("custom", "custom.mp3", 0, isPlaying = false)
        viewModel.injectSounds(listOf(sound))
        viewModel.onPlayerStart(sound, durationMs = 1000) // establece _playingSound como fuente autoritativa

        viewModel.deleteSound(sound)

        verify { PlayerControllerFactory.instance.stopPlayingSound() }
    }

    @Test
    fun `restoreSound when deleted sound was playing restores it as not playing`() {
        every { PlayerControllerFactory.instance.stopPlayingSound() } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = Sound("custom", "custom.mp3", 0, isPlaying = false)
        viewModel.injectSounds(listOf(sound))
        viewModel.onPlayerStart(sound, durationMs = 1000) // establece _playingSound como fuente autoritativa

        viewModel.deleteSound(sound)
        viewModel.restoreSound()

        assertThat(
            viewModel.sounds.value
                .single { it.name == "custom" }
                .isPlaying,
        ).isFalse()
    }

    @Test
    fun `deleteSound when sound is not playing does not stop playback`() {
        val controller = PlayerControllerFactory.instance
        val viewModel = givenAViewModel()
        val sound = Sound("custom", "custom.mp3", 0, isPlaying = false)
        viewModel.injectSounds(listOf(sound))

        viewModel.deleteSound(sound)

        verify(exactly = 0) { controller.stopPlayingSound() }
    }

    @Test
    fun `clearDeleteEvent clears the delete event`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", file = "test.mp3")
        viewModel.injectSounds(listOf(sound))
        viewModel.deleteSound(sound)

        viewModel.clearDeleteEvent()

        assertThat(viewModel.deletedSoundEvent.value).isNull()
    }

    @Test
    fun `togglePin marks a sound as pinned`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", file = "test.mp3")
        viewModel.injectSounds(listOf(sound))

        viewModel.togglePin(sound)

        assertThat(
            viewModel.sounds.value
                .single { it.name == "test" }
                .isPinned,
        ).isTrue()
    }

    @Test
    fun `togglePin on a pinned sound unpins it`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", "test.mp3", 0, false, isPinned = true)
        viewModel.injectSounds(listOf(sound))

        viewModel.togglePin(sound)

        assertThat(
            viewModel.sounds.value
                .single { it.name == "test" }
                .isPinned,
        ).isFalse()
    }

    @Test
    fun `onButtonSaved selects MY_SOUNDS tab`() {
        val viewModel = givenAViewModel()

        viewModel.onButtonSaved("test")

        assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.MY_SOUNDS)
    }

    @Test
    fun `onButtonSaved emits buttonSavedEvent with the button name`() =
        runTest {
            val viewModel = givenAViewModel()

            viewModel.onButtonSaved("Juancho")

            assertThat(viewModel.buttonSavedEvent.first()).isEqualTo("Juancho")
        }

    @Test
    fun `togglePin moves pinned sound to top of list`() {
        val viewModel = givenAViewModel()
        val sound1 = Sound(name = "alpha", file = "a.mp3", rawRes = 0, isPlaying = false, dateAdded = 2000L)
        val sound2 = Sound(name = "beta", file = "b.mp3", rawRes = 0, isPlaying = false, dateAdded = 1000L)
        viewModel.injectSounds(listOf(sound1, sound2))

        viewModel.togglePin(sound2)

        assertThat(
            viewModel.sounds.value
                .first()
                .name,
        ).isEqualTo("beta")
    }

    @Test
    fun `togglePin emits scrollToTopEvent when sound is pinned`() =
        runTest {
            val viewModel = givenAViewModel()
            val sound = Sound("test", file = "test.mp3")
            viewModel.injectSounds(listOf(sound))

            viewModel.togglePin(sound)

            viewModel.scrollToTopEvent.first()
        }

    @Test
    fun `togglePin does not emit scrollToTopEvent when sound is unpinned`() =
        runTest {
            val viewModel = givenAViewModel()
            val sound = Sound("test", "test.mp3", 0, false, isPinned = true)
            viewModel.injectSounds(listOf(sound))

            viewModel.togglePin(sound)

            val received = withTimeoutOrNull(50) { viewModel.scrollToTopEvent.first() }
            assertThat(received).isNull()
        }

    @Test
    fun `togglePin on bundled sound updates isPinned to true`() {
        val viewModel = givenAViewModel()
        val sound = Sound("bundled", rawRes = 1)
        viewModel.injectSounds(listOf(sound))

        viewModel.togglePin(sound)

        assertThat(
            viewModel.sounds.value
                .single { it.name == "bundled" }
                .isPinned,
        ).isTrue()
    }

    @Test
    fun `togglePin twice returns sound to its original date-sorted position`() {
        val viewModel = givenAViewModel()
        val alpha = Sound(name = "alpha", file = "a.mp3", rawRes = 0, isPlaying = false, dateAdded = 2000L)
        val beta = Sound(name = "beta", file = "b.mp3", rawRes = 0, isPlaying = false, dateAdded = 1000L)
        viewModel.injectSounds(listOf(alpha, beta))

        viewModel.togglePin(beta)
        val pinnedBeta = viewModel.sounds.value.single { it.name == "beta" }
        viewModel.togglePin(pinnedBeta)

        assertThat(
            viewModel.sounds.value
                .first()
                .name,
        ).isEqualTo("alpha")
    }

    @Test
    fun `deleteSound stops playback and removes sound even when passed a stale copy with isPlaying false`() {
        every { PlayerControllerFactory.instance.stopPlayingSound() } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = Sound("custom", "custom.mp3", 0, isPlaying = false)
        viewModel.injectSounds(listOf(sound.copy(isPlaying = true)))
        viewModel.onPlayerStart(sound, durationMs = 1000)

        viewModel.deleteSound(sound)

        verify { PlayerControllerFactory.instance.stopPlayingSound() }
        assertThat(viewModel.sounds.value.none { it.name == sound.name }).isTrue()
    }

    @Test
    fun `loadSounds does not restore a soft-deleted sound while its delete is pending`() {
        val viewModel = givenAViewModelWithCustomSound()
        val sound = viewModel.sounds.value.first()

        viewModel.deleteSound(sound)
        viewModel.selectTab(AppTab.EXPLORE_SOUNDS)
        viewModel.selectTab(AppTab.MY_SOUNDS)

        assertThat(viewModel.sounds.value.none { it.name == sound.name }).isTrue()
    }

    @Test
    fun `sounds list preserves isPlaying state after switching tabs and returning`() =
        runTest {
            val viewModel = givenAViewModelWithCustomSound()
            val playingSound = viewModel.sounds.value.first()

            viewModel.onPlayerStart(playingSound, durationMs = 1000)
            viewModel.selectTab(AppTab.EXPLORE_SOUNDS)
            viewModel.selectTab(AppTab.MY_SOUNDS)

            assertThat(
                viewModel.sounds.value
                    .single { it.name == playingSound.name }
                    .isPlaying,
            ).isTrue()
        }

    @Test
    fun `onPlayerStart initialises playbackProgress with zero position and given duration`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", rawRes = 1)

        viewModel.onPlayerStart(sound, durationMs = 3000)

        assertThat(viewModel.playbackProgress.value?.positionMs).isEqualTo(0)
        assertThat(viewModel.playbackProgress.value?.durationMs).isEqualTo(3000)
    }

    @Test
    fun `onPlayerStop clears playbackProgress`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", rawRes = 1)
        viewModel.onPlayerStart(sound, durationMs = 3000)

        viewModel.onPlayerStop(sound)

        assertThat(viewModel.playbackProgress.value).isNull()
    }

    @Test
    fun `onProgressUpdate updates positionMs while keeping durationMs`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", rawRes = 1)
        viewModel.onPlayerStart(sound, durationMs = 3000)

        viewModel.onProgressUpdate(positionMs = 1500)

        assertThat(viewModel.playbackProgress.value?.positionMs).isEqualTo(1500)
        assertThat(viewModel.playbackProgress.value?.durationMs).isEqualTo(3000)
    }

    @Test
    fun `seekTo delegates to PlayerController and optimistically updates progress`() {
        every { PlayerControllerFactory.instance.seekTo(any()) } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = Sound("test", rawRes = 1)
        viewModel.onPlayerStart(sound, durationMs = 5000)

        viewModel.seekTo(2000)

        verify { PlayerControllerFactory.instance.seekTo(2000) }
        assertThat(viewModel.playbackProgress.value?.positionMs).isEqualTo(2000)
    }

    @Test
    fun `onPlayerError emits playbackErrorEvent`() =
        runTest {
            val viewModel = givenAViewModel()

            viewModel.onPlayerError(Sound("test", rawRes = 1))

            viewModel.playbackErrorEvent.first()
        }

    @Test
    fun `sounds are sorted alphabetically`() {
        val viewModel = givenAViewModel()
        val sounds = viewModel.sounds.value

        assumeTrue("At least 2 packaged sounds required to verify ordering", sounds.size >= 2)

        assertThat(sounds.map { it.name.lowercase() }).isInOrder()
    }

    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectSounds(sounds: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("_sounds")
            .also { it.isAccessible = true }
            // Safe: _sounds is always MutableStateFlow<List<Sound>> — type parameter erased at runtime
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = sounds }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModelWithCustomSound(
        name: String = "custom",
        file: String = "custom.mp3",
    ): SoundsViewModel {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking { SoundsRepository(context).save(Sound(name, file)) }
        return givenAViewModel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(): SoundsViewModel {
        val vm =
            SoundsViewModel(
                ApplicationProvider.getApplicationContext(),
                ioDispatcher = UnconfinedTestDispatcher(),
            )
        runBlocking { vm.isInitialLoadComplete.first { it } }
        return vm
    }
}
