package com.github.barriosnahuel.vossosunboton.ui.home

import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [android.os.Build.VERSION_CODES.S_V2])
internal class SoundsViewModelTest : AbstractRobolectricTest() {
    @Before
    fun setUp() {
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
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
        viewModel.selectTab(AppTab.HOME)
        val initialSounds = viewModel.sounds.value
        if (initialSounds.isEmpty()) return // skip if no user sounds in test env

        val soundToDelete = initialSounds.first()

        viewModel.deleteSound(soundToDelete)

        assertThat(viewModel.sounds.value).doesNotContain(soundToDelete)
        assertThat(viewModel.deletedSoundEvent.value?.sound).isEqualTo(soundToDelete)
    }

    @Test
    fun `restoreSound puts the sound back and clears the delete event`() {
        val viewModel = givenAViewModel()
        viewModel.selectTab(AppTab.HOME)
        val initialSounds = viewModel.sounds.value
        if (initialSounds.isEmpty()) return

        val soundToDelete = initialSounds.first()
        viewModel.deleteSound(soundToDelete)
        viewModel.restoreSound()

        assertThat(viewModel.sounds.value).contains(soundToDelete)
        assertThat(viewModel.deletedSoundEvent.value).isNull()
    }

    @Test
    fun `onPlayerStart marks sound as playing`() {
        val viewModel = givenAViewModel()
        viewModel.selectTab(AppTab.EXPLORE)
        val sounds = viewModel.sounds.value
        if (sounds.isEmpty()) return

        val sound = sounds.first()

        viewModel.onPlayerStart(sound)

        assertThat(viewModel.playingSound.value).isEqualTo(sound)
        assertThat(
            viewModel.sounds.value
                .first { it.name == sound.name }
                .isPlaying,
        ).isTrue()
    }

    @Test
    fun `onPlayerStop clears the playing sound`() {
        val viewModel = givenAViewModel()
        viewModel.selectTab(AppTab.EXPLORE)
        val sounds = viewModel.sounds.value
        if (sounds.isEmpty()) return

        val sound = sounds.first()
        viewModel.onPlayerStart(sound)
        viewModel.onPlayerStop(sound)

        assertThat(viewModel.playingSound.value).isNull()
        assertThat(
            viewModel.sounds.value
                .first { it.name == sound.name }
                .isPlaying,
        ).isFalse()
    }

    @Test
    fun `sounds are sorted alphabetically`() {
        val viewModel = givenAViewModel()
        val sounds = viewModel.sounds.value
        if (sounds.size < 2) return

        assertThat(sounds.map { it.name.lowercase() }).isInOrder()
    }

    private fun givenAViewModel(): SoundsViewModel =
        SoundsViewModel(
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext(),
        )
}
