/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.feature.collections.MySoundsFilterStore
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.share.ShareFeature
import com.github.barriosnahuel.vossosunboton.feature.share.ShareIntentOutcome
import com.github.barriosnahuel.vossosunboton.feature.vault.VaultFilterStore
import com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.CollectionsRepository
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

@Suppress("LargeClass")
internal class SoundsViewModelTest : AbstractRobolectricTest() {
    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        runBlocking {
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            SoundsRepository(ctx).clearForTest()
            WelcomeStickerStore(ctx).clearForTest()
            CollectionsRepository(ctx).clearForTest()
            MySoundsFilterStore(ctx).clearForTest()
            VaultFilterStore(ctx).clearForTest()
        }
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
        // deleteSound always asks the controller to forget the sound. Stubbing here keeps individual
        // delete-related tests free of boilerplate; tests that need to assert the call still verify
        // it explicitly.
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
        // playOrStop on a playing sound now routes through pause(), not stopPlayingSound().
        every { PlayerControllerFactory.instance.pause() } answers { nothing }
    }

    @After
    fun tearDown() {
        // Deterministically stop the reactive `repo.sounds` collector each VM starts in `init`
        // (post-PR-#1130 fix). A bare `cancel()` is fire-and-forget: the collector can outlive the
        // test, parked on the process-singleton DataStore, and pollute the next test (e.g. firing
        // `loadSounds` against the prior VM's stale `_searchQuery`). `cancelAndJoinAll()` joins
        // until it unwinds — see ViewModelTestCleanup.kt.
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
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
        val sound = testSound("test", file = "test.mp3")
        viewModel.injectSounds(listOf(sound))

        viewModel.deleteSound(sound)

        assertThat(viewModel.sounds.value).doesNotContain(sound)
        assertThat(viewModel.deletedSoundEvent.value?.sound).isEqualTo(sound)
    }

    @Test
    fun `restoreSound puts the sound back and clears the delete event`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", file = "test.mp3")
        viewModel.injectSounds(listOf(sound))

        viewModel.deleteSound(sound)
        viewModel.restoreSound()

        assertThat(viewModel.sounds.value).contains(sound)
        assertThat(viewModel.deletedSoundEvent.value).isNull()
    }

    @Test
    fun `onPlayerStart sets playingSound to the given sound`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", rawRes = 1)

        viewModel.onPlayerStart(sound, durationMs = 1000)

        assertThat(viewModel.playingSound.value?.name).isEqualTo(sound.name)
        assertThat(viewModel.playingSound.value?.isPlaying).isTrue()
        assertThat(sound.isPlaying).isFalse()
    }

    @Test
    fun `onPlayerStop clears playingSound`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", rawRes = 1)
        viewModel.onPlayerStart(sound, durationMs = 1000)

        viewModel.onPlayerStop(sound, completed = false)

        assertThat(viewModel.playingSound.value).isNull()
        assertThat(sound.isPlaying).isFalse()
    }

    @Test
    fun `onPlayerStart updates sounds list with isPlaying true without mutating original sound`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", rawRes = 1)
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
        val sound = testSound("test", null, 1, isPlaying = true)
        viewModel.injectSounds(listOf(sound))

        viewModel.onPlayerStop(sound, completed = false)

        assertThat(
            viewModel.sounds.value
                .single { it.name == "test" }
                .isPlaying,
        ).isFalse()
        assertThat(sound.isPlaying).isTrue()
    }

    @Test
    fun `onPlayerPause retains the sound position in pausedProgress and clears playingSound`() {
        // The core "pause is not a reset" guarantee: after a pause the sound is no longer the
        // active one (playingSound / playbackProgress clear), but its position stays in
        // pausedProgress so the UI keeps the progress bar where it was instead of snapping to 0.
        val viewModel = givenAViewModel()
        val sound = testSound("test", rawRes = 1)
        viewModel.injectSounds(listOf(sound))
        viewModel.onPlayerStart(sound, durationMs = 10_000)

        viewModel.onPlayerPause(sound, positionMs = 3_500, durationMs = 10_000)

        assertThat(viewModel.playingSound.value).isNull()
        assertThat(viewModel.playbackProgress.value).isNull()
        val paused = viewModel.pausedProgress.value[sound.id]
        assertThat(paused).isNotNull()
        assertThat(paused!!.positionMs).isEqualTo(3_500)
        assertThat(paused.durationMs).isEqualTo(10_000)
        assertThat(
            viewModel.sounds.value
                .single { it.name == "test" }
                .isPlaying,
        ).isFalse()
    }

    @Test
    fun `onPlayerStart clears a previously-paused sound from pausedProgress`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", rawRes = 1)
        viewModel.injectSounds(listOf(sound))
        viewModel.onPlayerPause(sound, positionMs = 3_500, durationMs = 10_000)

        viewModel.onPlayerStart(sound, durationMs = 10_000, positionMs = 3_500)

        assertThat(viewModel.pausedProgress.value).doesNotContainKey(sound.id)
    }

    @Test
    fun `onPlayerStop clears a previously-paused sound from pausedProgress`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", rawRes = 1)
        viewModel.injectSounds(listOf(sound))
        viewModel.onPlayerPause(sound, positionMs = 3_500, durationMs = 10_000)

        viewModel.onPlayerStop(sound, completed = false)

        assertThat(viewModel.pausedProgress.value).doesNotContainKey(sound.id)
    }

    @Test
    fun `seekTo with soundId moves the player and updates live progress while playing`() {
        every { PlayerControllerFactory.instance.seekTo(any()) } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = testSound("test", rawRes = 1)
        viewModel.onPlayerStart(sound, durationMs = 10_000)

        viewModel.seekTo(5_000, sound.id)

        verify { PlayerControllerFactory.instance.seekTo(5_000) }
        assertThat(viewModel.playbackProgress.value?.positionMs).isEqualTo(5_000)
    }

    @Test
    fun `seekTo with soundId updates the retained position while paused (back-to-start)`() {
        every { PlayerControllerFactory.instance.seekTo(any()) } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = testSound("test", rawRes = 1)
        viewModel.onPlayerStart(sound, durationMs = 10_000)
        viewModel.onPlayerPause(sound, positionMs = 3_500, durationMs = 10_000)

        viewModel.seekTo(0, sound.id)

        verify { PlayerControllerFactory.instance.seekTo(0) }
        // Paused, so live progress stays null; the retained position (which the wave + a later
        // resume read) moves to the start.
        assertThat(viewModel.playbackProgress.value).isNull()
        assertThat(viewModel.pausedProgress.value[sound.id]?.positionMs).isEqualTo(0)
    }

    @Test
    fun `playOrStop when sound is not playing calls startPlayingSound`() {
        every { PlayerControllerFactory.instance.startPlayingSound(any(), any()) } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = testSound("test", null, 1, false)

        viewModel.playOrStop(sound)

        verify { PlayerControllerFactory.instance.startPlayingSound(any(), sound) }
    }

    @Test
    fun `playOrStop when sound is playing calls pause to preserve position`() {
        // After the play/pause unification, tap-while-playing pauses the controller (saving the
        // position in its in-process cache) instead of fully stopping. The UI still collapses
        // because the controller fires onPlayerStop(completed=false) on pause — the saved position
        // is invisible to the VM and only surfaces when the user re-taps and the controller
        // resumes via mp.start() without resetting.
        val controller = PlayerControllerFactory.instance
        val viewModel = givenAViewModel()
        val sound = testSound("test", null, 1, isPlaying = true)

        viewModel.playOrStop(sound)

        verify { controller.pause() }
        verify(exactly = 0) { controller.stopPlayingSound() }
    }

    @Test
    fun `deleteSound asks the controller to forget the sound`() {
        // Always — regardless of whether the sound is currently playing, paused, or untouched —
        // the controller must drop its in-memory state for this sound so a future name collision
        // can't seekTo a stale position. forgetSound handles all three cases internally (no-op
        // when the sound has no saved state).
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = testSound("custom", "custom.mp3", 0, isPlaying = false)
        viewModel.injectSounds(listOf(sound))
        viewModel.onPlayerStart(sound, durationMs = 1000) // establece _playingSound como fuente autoritativa

        viewModel.deleteSound(sound)

        verify { PlayerControllerFactory.instance.forgetSound(sound) }
    }

    @Test
    fun `restoreSound when deleted sound was playing restores it as not playing`() {
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = testSound("custom", "custom.mp3", 0, isPlaying = false)
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
    fun `deleteSound when sound is not playing still asks the controller to forget it`() {
        // The forgetSound call is unconditional: the controller may hold a saved position for this
        // sound even when nothing is currently playing (e.g. the user paused it earlier). Calling
        // forgetSound here drops that saved state so a future name collision doesn't resurrect it.
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
        val controller = PlayerControllerFactory.instance
        val viewModel = givenAViewModel()
        val sound = testSound("custom", "custom.mp3", 0, isPlaying = false)
        viewModel.injectSounds(listOf(sound))

        viewModel.deleteSound(sound)

        verify { controller.forgetSound(sound) }
        verify(exactly = 0) { controller.stopPlayingSound() }
    }

    @Test
    fun `clearDeleteEvent clears the delete event`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", file = "test.mp3")
        viewModel.injectSounds(listOf(sound))
        viewModel.deleteSound(sound)

        viewModel.clearDeleteEvent()

        assertThat(viewModel.deletedSoundEvent.value).isNull()
    }

    @Test
    fun `togglePin marks a sound as pinned`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", file = "test.mp3")
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
        val sound = testSound("test", "test.mp3", 0, false, isPinned = true)
        viewModel.injectSounds(listOf(sound))

        viewModel.togglePin(sound)

        assertThat(
            viewModel.sounds.value
                .single { it.name == "test" }
                .isPinned,
        ).isFalse()
    }

    @Test
    fun `togglePin moves pinned sound to top of list`() {
        val viewModel = givenAViewModel()
        val sound1 = testSound(name = "alpha", file = "a.mp3", rawRes = 0, isPlaying = false, dateAdded = 2000L)
        val sound2 = testSound(name = "beta", file = "b.mp3", rawRes = 0, isPlaying = false, dateAdded = 1000L)
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
            val sound = testSound("test", file = "test.mp3")
            viewModel.injectSounds(listOf(sound))

            viewModel.togglePin(sound)

            viewModel.scrollToTopEvent.first()
        }

    @Test
    fun `togglePin does not emit scrollToTopEvent when sound is unpinned`() =
        runTest {
            val viewModel = givenAViewModel()
            val sound = testSound("test", "test.mp3", 0, false, isPinned = true)
            viewModel.injectSounds(listOf(sound))

            viewModel.togglePin(sound)

            val received = withTimeoutOrNull(50) { viewModel.scrollToTopEvent.first() }
            assertThat(received).isNull()
        }

    @Test
    fun `togglePin on bundled sound updates isPinned to true`() {
        val viewModel = givenAViewModel()
        val sound = testSound("bundled", rawRes = 1)
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
        val alpha = testSound(name = "alpha", file = "a.mp3", rawRes = 0, isPlaying = false, dateAdded = 2000L)
        val beta = testSound(name = "beta", file = "b.mp3", rawRes = 0, isPlaying = false, dateAdded = 1000L)
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
    fun `deleteSound forgets the sound and removes it even when passed a stale copy with isPlaying false`() {
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = testSound("custom", "custom.mp3", 0, isPlaying = false)
        viewModel.injectSounds(listOf(sound.copy(isPlaying = true)))
        viewModel.onPlayerStart(sound, durationMs = 1000)

        viewModel.deleteSound(sound)

        verify { PlayerControllerFactory.instance.forgetSound(sound) }
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
    fun `deleting several sounds in a row persists every deletion, not just the last`() {
        // Regression: each delete shows an undo snackbar; a new delete replaces the previous one
        // (cancelling its LaunchedEffect) before the previous deletion is confirmed. With a single
        // pending-deletion slot, only the LAST deletion was ever persisted — the earlier ones were
        // dropped from the in-memory list but left on disk, so the next repo re-emit (triggered when
        // the last deletion is finally confirmed) repopulated them and they reappeared. The user
        // saw: delete 4-5 in a row → empty Vault → last snackbar fades → the deleted Bomps return.
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SoundsRepository(context)
        val files = listOf("one.mp3", "two.mp3", "three.mp3")
        runBlocking {
            files.forEach { name ->
                // `repo.delete` only removes a sound from the store when its backing file is actually
                // deleted from disk, so the persistence assertion below needs real files to delete.
                getFile(context, name).apply {
                    parentFile?.mkdirs()
                    createNewFile()
                }
                repo.save(testSound(name.removeSuffix(".mp3"), file = name))
            }
        }
        val viewModel = givenAViewModel()
        val names = setOf("one", "two", "three")
        val toDelete = viewModel.sounds.value.filter { it.name in names }
        assertThat(toDelete).hasSize(names.size)

        // Rapid deletes — no undo, no per-delete confirm between them.
        toDelete.forEach { viewModel.deleteSound(it) }
        // Only the last deletion's snackbar survives to be dismissed.
        viewModel.confirmDelete()

        runBlocking {
            // Every deletion must reach persistence (the async DataStore writes settle slightly after
            // the calls return). With the bug, "one"/"two" never get a confirm and stay on disk
            // forever, so the poll times out and the assertion still sees them — exactly what the
            // repo re-emit would resurrect on screen.
            val persisted =
                withTimeoutOrNull(3_000) {
                    var remaining = repo.sounds.first().filter { it.name in names }
                    while (remaining.isNotEmpty()) {
                        kotlinx.coroutines.delay(20)
                        remaining = repo.sounds.first().filter { it.name in names }
                    }
                    remaining
                } ?: repo.sounds.first().filter { it.name in names }
            assertThat(persisted).isEmpty()
        }
    }

    @Test
    fun `a concurrent repo write while a delete is pending does not resurrect the sound`() {
        // Companion to the fix above. fix #1's flush persists the previous deletion, and that write
        // fires the reactive `repo.sounds` collector, which re-runs loadSounds. loadSounds must keep
        // the pending soft-deletion out of `allSoundsCache` (rebuilt from persistence, which still
        // holds the pending sound) — otherwise the just-dismissed sound resurfaces in the views that
        // read the cache (search, Vault). This drives that real re-emit path via a concurrent write.
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking { SoundsRepository(context).save(testSound("solo", file = "solo.mp3")) }
        val viewModel =
            SoundsViewModel(
                context,
                ioDispatcher = UnconfinedTestDispatcher(),
                searchDebounceMs = 0L,
            )
        createdViewModels += viewModel
        runBlocking {
            viewModel.isInitialLoadComplete.first { it }
            kotlinx.coroutines.delay(50)
        }
        val sound = viewModel.sounds.value.single { it.name == "solo" }

        viewModel.deleteSound(sound)
        // A concurrent repo write fires the reactive collector → loadSounds rebuilds the cache from
        // persistence (which still has `solo`, its delete being unconfirmed).
        runBlocking {
            SoundsRepository(context).save(testSound("other", file = "other.mp3"))
            kotlinx.coroutines.delay(200)
        }
        viewModel.showSearch()
        viewModel.onSearchQueryChange("solo")

        assertThat(viewModel.searchResults.value.none { it.name == "solo" }).isTrue()
    }

    @Test
    fun `sounds list preserves isPlaying state after switching tabs and returning`() =
        runTest {
            val viewModel = givenAViewModelWithCustomSound()
            // Pick the saved custom sound explicitly — `sounds.value.first()` would resolve to the
            // welcome sticker on a fresh install, and the sticker is filtered out of the synchronous
            // `applyTabFilterFromCache` projection that `selectTab` now uses to bridge the gap to
            // loadSounds. The contract the test guards is independent of welcome placement.
            val playingSound = viewModel.sounds.value.single { it.name == "custom" }

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
        val sound = testSound("test", rawRes = 1)

        viewModel.onPlayerStart(sound, durationMs = 3000)

        assertThat(viewModel.playbackProgress.value?.positionMs).isEqualTo(0)
        assertThat(viewModel.playbackProgress.value?.durationMs).isEqualTo(3000)
    }

    @Test
    fun `onPlayerStop clears playbackProgress`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", rawRes = 1)
        viewModel.onPlayerStart(sound, durationMs = 3000)

        viewModel.onPlayerStop(sound, completed = false)

        assertThat(viewModel.playbackProgress.value).isNull()
    }

    @Test
    fun `onProgressUpdate updates positionMs while keeping durationMs`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", rawRes = 1)
        viewModel.onPlayerStart(sound, durationMs = 3000)

        viewModel.onProgressUpdate(positionMs = 1500)

        assertThat(viewModel.playbackProgress.value?.positionMs).isEqualTo(1500)
        assertThat(viewModel.playbackProgress.value?.durationMs).isEqualTo(3000)
    }

    @Test
    fun `seekTo delegates to PlayerController and optimistically updates progress`() {
        every { PlayerControllerFactory.instance.seekTo(any()) } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = testSound("test", rawRes = 1)
        viewModel.onPlayerStart(sound, durationMs = 5000)

        viewModel.seekTo(2000)

        verify { PlayerControllerFactory.instance.seekTo(2000) }
        assertThat(viewModel.playbackProgress.value?.positionMs).isEqualTo(2000)
    }

    @Test
    fun `onPlayerError emits playbackErrorEvent`() =
        runTest {
            val viewModel = givenAViewModel()

            viewModel.onPlayerError(testSound("test", rawRes = 1))

            viewModel.playbackErrorEvent.first()
        }

    @Test
    fun `repo save after VM init makes the new sound appear without selectTab`() {
        // Regression for the post-PR-#1130 bug: AddButton save persisted but the visible list
        // stayed stale until the user killed and reopened the app. The reactive `combine` in
        // `observeSoundsList` is what pulls the new emission through. Uses `runBlocking` rather
        // than `runTest` because the underlying `repo.save` suspends on real `Dispatchers.IO`
        // (StateFlow propagation runs in real time, not virtual).
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SoundsRepository(context)
        val viewModel = givenAViewModel()
        assertThat(viewModel.sounds.value.none { it.name == "fresh" }).isTrue()

        runBlocking {
            repo.save(testSound("fresh", "fresh.mp3"))
            withTimeoutOrNull(5_000L) {
                viewModel.sounds.first { list -> list.any { it.name == "fresh" } }
            }
        }
        assertThat(viewModel.sounds.value.any { it.name == "fresh" }).isTrue()
    }

    @Test
    fun `repo rename after VM init replaces the old name in the list without selectTab`() {
        // Same regression family as the save test above, but for the Edit-flow rename path —
        // see `AddButtonActivity` and `SoundsRepository.rename`.
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SoundsRepository(context)
        runBlocking { repo.save(testSound("old-name", "old.mp3")) }
        val viewModel = givenAViewModel()
        assertThat(viewModel.sounds.value.any { it.name == "old-name" }).isTrue()

        runBlocking {
            // Rename now takes the stable id (ADR 0008). Custom-sound ids in tests are
            // derived as `custom:<name>` by the `testSound` helper.
            repo.rename("custom:old-name", "new-name")
            withTimeoutOrNull(5_000L) {
                viewModel.sounds.first { list -> list.any { it.name == "new-name" } }
            }
        }
        assertThat(viewModel.sounds.value.any { it.name == "new-name" }).isTrue()
        assertThat(viewModel.sounds.value.none { it.name == "old-name" }).isTrue()
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
        // `_sounds` is the visible (tab-filtered) list; `allSoundsCache` is the canonical full
        // catalog that `deleteSound` / `togglePin` resolve identity against. Inject into both so
        // the test scenario mirrors what `loadSounds` would produce — without `allSoundsCache`
        // the production code's "audio not found in catalog" early-return path masks the
        // behavior under test.
        SoundsViewModel::class.java
            .getDeclaredField("_sounds")
            .also { it.isAccessible = true }
            // Safe: _sounds is always MutableStateFlow<List<Sound>> — type parameter erased at runtime
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = sounds }
        SoundsViewModel::class.java
            .getDeclaredField("allSoundsCache")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = sounds }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModelWithCustomSound(
        name: String = "custom",
        file: String = "custom.mp3",
    ): SoundsViewModel {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking { SoundsRepository(context).save(testSound(name, file)) }
        return givenAViewModel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(
        welcomeStore: WelcomeStickerStore? = null,
        shareFeature: ShareFeature? = null,
    ): SoundsViewModel {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm =
            SoundsViewModel(
                context,
                ioDispatcher = UnconfinedTestDispatcher(),
                welcomeStore = welcomeStore ?: WelcomeStickerStore(context),
                shareFeature = shareFeature ?: ShareFeature.instance,
            )
        createdViewModels += vm
        runBlocking {
            vm.isInitialLoadComplete.first { it }
            // Yield briefly to let init's auxiliary coroutines (collections collector, filter
            // prime, repo.sounds.drop(1) collector) reach their suspension points before tests
            // mutate state via reflection. Without this yield, those collectors can race with
            // the test's `injectSounds(...)` and overwrite `_sounds` / `allSoundsCache` via a
            // late-arriving loadSounds emission. The previous post-PR-#1130 timing relied on
            // loadSounds being a single synchronous read; v2.4.0 added a per-load DataStore
            // round-trip for the private-only filter, widening the window where the test sees
            // a half-applied projection.
            kotlinx.coroutines.delay(50)
        }
        return vm
    }

    @Test
    fun `welcome sticker is at index 0 of MY_SOUNDS when flag is active`() {
        val viewModel = givenAViewModel()
        val welcomeTitle =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.app_welcome_sticker_title)

        assertThat(
            viewModel.sounds.value
                .firstOrNull()
                ?.name,
        ).isEqualTo(welcomeTitle)
    }

    @Test
    fun `welcome sticker is absent when flag is consumed`() {
        runBlocking {
            WelcomeStickerStore(ApplicationProvider.getApplicationContext()).consume()
        }
        val viewModel = givenAViewModel()
        val welcomeTitle =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.app_welcome_sticker_title)

        assertThat(viewModel.sounds.value.none { it.name == welcomeTitle }).isTrue()
    }

    @Test
    fun `welcome sticker is absent from EXPLORE_SOUNDS regardless of flag`() {
        val viewModel = givenAViewModel()
        val welcomeTitle =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.app_welcome_sticker_title)

        viewModel.selectTab(AppTab.EXPLORE_SOUNDS)
        // selectTab launches loadSounds asynchronously on ioDispatcher. Wait for the welcome to
        // drop out of _sounds before asserting — isInitialLoadComplete already flipped on the first
        // load and is no longer a useful sync signal.
        runBlocking {
            withTimeoutOrNull(5_000L) {
                viewModel.sounds.first { list -> list.none { it.name == welcomeTitle } }
            }
        }

        assertThat(viewModel.sounds.value.none { it.name == welcomeTitle }).isTrue()
    }

    @Test
    fun `onPlayerStop with completed=true on welcome enqueues delete event and hides sticker`() {
        val viewModel = givenAViewModel()
        val welcomeTitle =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.app_welcome_sticker_title)
        val welcome = viewModel.sounds.value.first { it.name == welcomeTitle }

        viewModel.onPlayerStop(welcome, completed = true)

        assertThat(
            viewModel.deletedSoundEvent.value
                ?.sound
                ?.name,
        ).isEqualTo(welcomeTitle)
        assertThat(viewModel.welcomeStickerVisible.value).isFalse()
        assertThat(viewModel.sounds.value.none { it.name == welcomeTitle }).isTrue()
    }

    @Test
    fun `onPlayerStop with completed=false on welcome leaves the sticker visible`() {
        val viewModel = givenAViewModel()
        val welcomeTitle =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.app_welcome_sticker_title)
        val welcome = viewModel.sounds.value.first { it.name == welcomeTitle }

        viewModel.onPlayerStop(welcome, completed = false)

        assertThat(viewModel.deletedSoundEvent.value).isNull()
        assertThat(viewModel.welcomeStickerVisible.value).isTrue()
        assertThat(viewModel.sounds.value.any { it.name == welcomeTitle }).isTrue()
    }

    @Test
    fun `restoreSound on welcome restores visibility and asks the store to restore`() {
        val welcomeStore = mockk<WelcomeStickerStore>(relaxed = true)
        coEvery { welcomeStore.isActive() } returns true
        coEvery { welcomeStore.wasRestored() } returns false
        val viewModel = givenAViewModel(welcomeStore = welcomeStore)
        val welcomeTitle =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.app_welcome_sticker_title)
        val welcome = viewModel.sounds.value.first { it.name == welcomeTitle }
        viewModel.onPlayerStop(welcome, completed = true)

        viewModel.restoreSound()

        assertThat(viewModel.welcomeStickerVisible.value).isTrue()
        assertThat(viewModel.sounds.value.any { it.name == welcomeTitle }).isTrue()
        // Behavior assertion only — disk persistence is covered by `WelcomeStickerStoreTest`.
        // The previous version polled a fresh store instance, which raced with the IO write and
        // could permanently cache a stale read.
        coVerify { welcomeStore.restore() }
    }

    @Test
    fun `confirmDelete on welcome calls welcomeStore consume and skips repo delete`() {
        val welcomeStore = mockk<WelcomeStickerStore>(relaxed = true)
        coEvery { welcomeStore.isActive() } returns true
        coEvery { welcomeStore.wasRestored() } returns false
        val viewModel = givenAViewModel(welcomeStore = welcomeStore)
        val welcomeTitle =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.app_welcome_sticker_title)
        val welcome = viewModel.sounds.value.first { it.name == welcomeTitle }
        viewModel.onPlayerStop(welcome, completed = true)

        viewModel.confirmDelete()

        // See the restoreSound test above for the rationale on verifying behavior over disk.
        coVerify { welcomeStore.consume() }
        assertThat(viewModel.deletedSoundEvent.value).isNull()
    }

    @Test
    fun `deleteSound on welcome flips visibility and emits delete event`() {
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
        val viewModel = givenAViewModel()
        val welcomeTitle =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.app_welcome_sticker_title)
        val welcome = viewModel.sounds.value.first { it.name == welcomeTitle }

        viewModel.deleteSound(welcome)

        assertThat(viewModel.welcomeStickerVisible.value).isFalse()
        assertThat(
            viewModel.deletedSoundEvent.value
                ?.sound
                ?.name,
        ).isEqualTo(welcomeTitle)
        assertThat(viewModel.sounds.value.none { it.name == welcomeTitle }).isTrue()
    }

    @Test
    fun `restoreSound on welcome inserts at the END not at original position 0`() {
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repo.save(testSound("custom", "custom.mp3"))
        }
        val viewModel = givenAViewModel()
        val welcomeTitle = context.getString(R.string.app_welcome_sticker_title)
        // Pre-condition: welcome at row 0, custom at row 1.
        assertThat(
            viewModel.sounds.value
                .first()
                .name,
        ).isEqualTo(welcomeTitle)
        val welcome = viewModel.sounds.value.first { it.name == welcomeTitle }
        viewModel.deleteSound(welcome)

        viewModel.restoreSound()

        // Post-condition: welcome demoted to the END, custom now at row 0.
        assertThat(
            viewModel.sounds.value
                .last()
                .name,
        ).isEqualTo(welcomeTitle)
        assertThat(
            viewModel.sounds.value
                .first()
                .name,
        ).isEqualTo("custom")
    }

    @Test
    fun `loadSounds appends welcome at the end when wasRestored is true`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking {
            // Seed store as if a previous Undo happened: restore() flips consumed=false AND
            // was_restored=true atomically.
            val store = WelcomeStickerStore(context)
            store.consume()
            store.restore()
            val repo = SoundsRepository(context as android.app.Application)
            repo.save(testSound("custom-a", "custom-a.mp3"))
            repo.save(testSound("custom-b", "custom-b.mp3"))
        }

        val viewModel = givenAViewModel()
        val welcomeTitle = context.getString(R.string.app_welcome_sticker_title)

        // Welcome must be at the end, after the user's two custom sounds.
        assertThat(
            viewModel.sounds.value
                .last()
                .name,
        ).isEqualTo(welcomeTitle)
        assertThat(
            viewModel.sounds.value
                .first()
                .name,
        ).isNotEqualTo(welcomeTitle)
    }

    @Test
    fun `share emits to shareIntentEvent on Success outcome`() =
        runTest {
            val intent = mockk<Intent>(relaxed = true)
            val shareFeature =
                mockk<ShareFeature> {
                    coEvery { prepareShareIntent(any(), any(), any()) } returns
                        ShareIntentOutcome.Success(intent, CanonicalScreenName.MY_SOUNDS)
                }
            val viewModel = givenAViewModel(shareFeature = shareFeature)

            viewModel.share(testSound("s", file = "s.mp3"))

            val event = withTimeoutOrNull(1000) { viewModel.shareIntentEvent.first() }
            assertThat(event?.intent).isEqualTo(intent)
            assertThat(event?.surface).isEqualTo(CanonicalScreenName.MY_SOUNDS)
        }

    @Test
    fun `share emits to shareErrorEvent with feedback res on Failure outcome`() =
        runTest {
            val shareFeature =
                mockk<ShareFeature> {
                    coEvery { prepareShareIntent(any(), any(), any()) } returns
                        ShareIntentOutcome.Failure(R.string.app_share_feedback_unshareable)
                }
            val viewModel = givenAViewModel(shareFeature = shareFeature)

            viewModel.share(testSound("s", file = "s.mp3"))

            val emitted = withTimeoutOrNull(1000) { viewModel.shareErrorEvent.first() }
            assertThat(emitted).isEqualTo(R.string.app_share_feedback_unshareable)
        }

    @Test
    fun `share passes MY_SOUNDS surface when MY_SOUNDS tab selected and search hidden`() =
        runTest {
            val shareFeature = mockShareFeatureReturning(CanonicalScreenName.MY_SOUNDS)
            val viewModel = givenAViewModel(shareFeature = shareFeature)
            val sound = testSound("s", file = "s.mp3")
            viewModel.selectTab(AppTab.MY_SOUNDS)

            viewModel.share(sound)
            withTimeoutOrNull(1000) { viewModel.shareIntentEvent.first() }

            coVerify { shareFeature.prepareShareIntent(any(), sound, CanonicalScreenName.MY_SOUNDS) }
        }

    @Test
    fun `share passes EXPLORE_SOUNDS surface when EXPLORE_SOUNDS tab selected`() =
        runTest {
            val shareFeature = mockShareFeatureReturning(CanonicalScreenName.EXPLORE_SOUNDS)
            val viewModel = givenAViewModel(shareFeature = shareFeature)
            val sound = testSound("s", file = "s.mp3")
            viewModel.selectTab(AppTab.EXPLORE_SOUNDS)

            viewModel.share(sound)
            withTimeoutOrNull(1000) { viewModel.shareIntentEvent.first() }

            coVerify { shareFeature.prepareShareIntent(any(), sound, CanonicalScreenName.EXPLORE_SOUNDS) }
        }

    @Test
    fun `share passes SEARCH_SOUND surface when search overlay is visible`() =
        runTest {
            val shareFeature = mockShareFeatureReturning(CanonicalScreenName.SEARCH_SOUND)
            val viewModel = givenAViewModel(shareFeature = shareFeature)
            val sound = testSound("s", file = "s.mp3")
            viewModel.showSearch()

            viewModel.share(sound)
            withTimeoutOrNull(1000) { viewModel.shareIntentEvent.first() }

            coVerify { shareFeature.prepareShareIntent(any(), sound, CanonicalScreenName.SEARCH_SOUND) }
        }

    private fun mockShareFeatureReturning(surface: String): ShareFeature =
        mockk {
            coEvery { prepareShareIntent(any(), any(), any()) } returns
                ShareIntentOutcome.Success(mockk(relaxed = true), surface)
        }
}
