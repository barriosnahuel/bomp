/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.collections.MySoundsFilterStore
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.model.CollectionProfile
import com.github.barriosnahuel.vossosunboton.model.data.manager.CollectionsRepository
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class SoundsViewModelCollectionsTest : AbstractRobolectricTest() {
    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        runBlocking {
            SoundsRepository(ApplicationProvider.getApplicationContext()).clearForTest()
            CollectionsRepository(ApplicationProvider.getApplicationContext()).clearForTest()
            MySoundsFilterStore(ApplicationProvider.getApplicationContext()).clearForTest()
            // Consume (not clear) the welcome sticker so it does NOT appear on top of the
            // sound list during these tests. clearForTest() resets the consumed flag, which
            // re-enables the sticker — exactly the opposite of what we need here.
            val welcome = WelcomeStickerStore(ApplicationProvider.getApplicationContext())
            welcome.clearForTest()
            welcome.consume()
        }
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.pause() } answers { nothing }
    }

    @After
    fun tearDown() {
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        unmockkAll()
    }

    @Test
    fun `MY_SOUNDS list shows every user audio when no filter is active`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            SoundsRepository(context).save(testSound("first", file = "first.mp3"))
            SoundsRepository(context).save(testSound("second", file = "second.mp3"))
        }
        val vm = givenAViewModel()

        assertThat(vm.sounds.value.map { it.name }).containsExactly("first", "second")
    }

    @Test
    fun `applying a public filter narrows MY_SOUNDS to that collection's audios`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repo.save(testSound("audio-a", file = "a.mp3"))
            repo.save(testSound("audio-b", file = "b.mp3"))
            val collections = CollectionsRepository(context)
            val sounds = repo.sounds.first()
            val a = sounds.first { it.name == "audio-a" }
            val collection = collections.create("Work", CollectionProfile.GENERIC_PUBLIC)
            collections.addAudio(collection.id, a.id)
        }
        val vm = givenAViewModel()
        // Wait until the VM's collections observation populates so the filter resolves the id.
        runBlocking { vm.collections.first { it.isNotEmpty() } }

        val collection = vm.collections.value.first { it.name == "Work" }
        vm.selectMySoundsFilter(collection.id)
        // selectMySoundsFilter triggers an async loadSounds on real IO — wait for the post-filter
        // emission rather than reading .value before it propagates.
        runBlocking { vm.sounds.first { list -> list.size == 1 && list.first().name == "audio-a" } }

        assertThat(vm.sounds.value.map { it.name }).containsExactly("audio-a")
    }

    @Test
    fun `clearing the filter restores the full MY_SOUNDS list`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repo.save(testSound("audio-a", file = "a.mp3"))
            repo.save(testSound("audio-b", file = "b.mp3"))
            val collections = CollectionsRepository(context)
            val sounds = repo.sounds.first()
            val a = sounds.first { it.name == "audio-a" }
            val c = collections.create("Work", CollectionProfile.GENERIC_PUBLIC)
            collections.addAudio(c.id, a.id)
        }
        val vm = givenAViewModel()
        runBlocking { vm.collections.first { it.isNotEmpty() } }
        val c = vm.collections.value.first { it.name == "Work" }
        vm.selectMySoundsFilter(c.id)
        vm.selectMySoundsFilter(null)
        runBlocking {
            vm.sounds.first { list -> list.map { it.name }.toSet() == setOf("audio-a", "audio-b") }
        }

        assertThat(vm.sounds.value.map { it.name }).containsExactly("audio-a", "audio-b")
    }

    @Test
    fun `deleting a user audio sweeps it from its collections`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repo.save(testSound("audio-a", file = "a.mp3"))
            val collections = CollectionsRepository(context)
            val sounds = repo.sounds.first()
            val a = sounds.first { it.name == "audio-a" }
            val c = collections.create("Work", CollectionProfile.GENERIC_PUBLIC)
            collections.addAudio(c.id, a.id)
        }
        val vm = givenAViewModel()
        runBlocking { vm.collections.first { it.isNotEmpty() } }
        val a = vm.sounds.value.first { it.name == "audio-a" }
        vm.deleteSound(a)
        vm.confirmDelete()
        runBlocking {
            // The forget happens asynchronously; the collections flow re-emits without the id.
            vm.collections.first { snapshot ->
                snapshot.firstOrNull { it.name == "Work" }?.audioIds?.contains(a.id) == false
            }
        }
        val collection = vm.collections.value.first { it.name == "Work" }
        assertThat(collection.audioIds).doesNotContain(a.id)
    }

    @Test
    fun `Baul system collection is seeded and reported as private`() {
        val vm = givenAViewModel()
        runBlocking { vm.collections.first { it.isNotEmpty() } }

        val baul = vm.collections.value.first { it.id == CollectionsRepository.BAUL_SYSTEM_ID }
        assertThat(baul.isPrivate).isTrue()
        assertThat(baul.isSystem).isTrue()
    }

    @Test
    fun `audio tagged only to a private collection disappears from MY_SOUNDS`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repo.save(testSound("private-only", file = "private.mp3"))
            repo.save(testSound("public-only", file = "public.mp3"))
            val collections = CollectionsRepository(context)
            val sounds = repo.sounds.first()
            val privateOne = sounds.first { it.name == "private-only" }
            val publicOne = sounds.first { it.name == "public-only" }
            val pub = collections.create("Work", CollectionProfile.GENERIC_PUBLIC)
            collections.addAudio(pub.id, publicOne.id)
            // private-only audio: only in the system Baúl.
            collections.addAudio(CollectionsRepository.BAUL_SYSTEM_ID, privateOne.id)
        }
        val vm = givenAViewModel()
        runBlocking { vm.collections.first { it.size > 1 } }
        // Wait for the loadSounds triggered by the collections emission to settle. The MY_SOUNDS
        // tab must converge to the single public-only entry once the private-only filter applies.
        runBlocking {
            vm.sounds.first { list -> list.size == 1 && list.first().name == "public-only" }
        }

        val visibleNames = vm.sounds.value.map { it.name }
        assertThat(visibleNames).containsExactly("public-only")
        assertThat(visibleNames).doesNotContain("private-only")
    }

    @Test
    fun `audio tagged to BOTH a private and a public collection still appears in MY_SOUNDS`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repo.save(testSound("cross-tagged", file = "cross.mp3"))
            val collections = CollectionsRepository(context)
            val sounds = repo.sounds.first()
            val a = sounds.first { it.name == "cross-tagged" }
            val pub = collections.create("Work", CollectionProfile.GENERIC_PUBLIC)
            collections.addAudio(pub.id, a.id)
            collections.addAudio(CollectionsRepository.BAUL_SYSTEM_ID, a.id)
        }
        val vm = givenAViewModel()
        runBlocking { vm.collections.first { it.size > 1 } }
        runBlocking {
            vm.sounds.first { list -> list.size == 1 && list.first().name == "cross-tagged" }
        }

        // Spec § 3.1 cross-preset: a public tag preserves visibility from MY_SOUNDS.
        assertThat(vm.sounds.value.map { it.name }).containsExactly("cross-tagged")
    }

    @Test
    fun `library exposes the user catalog regardless of selected tab`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repo.save(testSound("audio-a", file = "a.mp3"))
            repo.save(testSound("audio-b", file = "b.mp3"))
        }
        val vm = givenAViewModel()
        // Switch to Vault — vm.sounds collapses to emptyList(), but the library snapshot must keep
        // the user catalog so ImmersiveListenScreen can resolve collection.audioIds to real Sounds.
        vm.selectTab(AppTab.VAULT)
        runBlocking { vm.collections.first { it.isNotEmpty() } }

        assertThat(vm.sounds.value).isEmpty()
        val userLibraryNames =
            vm.library.value
                .filter { it.file != null }
                .map { it.name }
        assertThat(userLibraryNames).containsExactly("audio-a", "audio-b")
    }

    @Test
    fun `creating a private collection emits with the VAULT profile defaults`() {
        val vm = givenAViewModel()
        runBlocking { vm.collections.first { it.isNotEmpty() } }
        val outcome =
            runBlocking { vm.createCollection("Caro", CollectionAccess.PRIVATE) }
        assertThat(outcome.isSuccess).isTrue()
        val created = outcome.getOrThrow()
        assertThat(created.isPrivate).isTrue()
        assertThat(created.profile.playbackUI).isEqualTo(
            com.github.barriosnahuel.vossosunboton.model.CollectionPlaybackUI.IMMERSIVE,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(): SoundsViewModel {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm =
            SoundsViewModel(
                context,
                ioDispatcher = UnconfinedTestDispatcher(),
            )
        createdViewModels += vm
        runBlocking { vm.isInitialLoadComplete.first { it } }
        return vm
    }
}
