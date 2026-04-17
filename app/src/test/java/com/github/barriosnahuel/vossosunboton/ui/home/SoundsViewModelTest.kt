package com.github.barriosnahuel.vossosunboton.ui.home

import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

internal class SoundsViewModelTest : AbstractRobolectricTest() {

    @Before
    fun setUp() {
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initial tab is EXPLORE`() {
        val viewModel = givenAViewModel()

        assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.EXPLORE)
    }

    @Test
    fun `selectTab updates the selected tab`() {
        val viewModel = givenAViewModel()

        viewModel.selectTab(AppTab.HOME)

        assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.HOME)
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

        viewModel.onPlayerStart(sound)

        assertThat(viewModel.playingSound.value).isEqualTo(sound)
        assertThat(sound.isPlaying).isTrue()
    }

    @Test
    fun `onPlayerStop clears playingSound`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", rawRes = 1)
        viewModel.onPlayerStart(sound)

        viewModel.onPlayerStop(sound)

        assertThat(viewModel.playingSound.value).isNull()
        assertThat(sound.isPlaying).isFalse()
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
    fun `clearDeleteEvent clears the delete event`() {
        val viewModel = givenAViewModel()
        val sound = Sound("test", file = "test.mp3")
        viewModel.injectSounds(listOf(sound))
        viewModel.deleteSound(sound)

        viewModel.clearDeleteEvent()

        assertThat(viewModel.deletedSoundEvent.value).isNull()
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

    private fun givenAViewModel(): SoundsViewModel =
        SoundsViewModel(
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext(),
        )
}
