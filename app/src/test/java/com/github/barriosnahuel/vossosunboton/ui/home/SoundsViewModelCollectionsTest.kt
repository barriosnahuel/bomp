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
            WelcomeStickerStore(ApplicationProvider.getApplicationContext()).clearForTest()
            MySoundsFilterStore(ApplicationProvider.getApplicationContext()).clearForTest()
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
