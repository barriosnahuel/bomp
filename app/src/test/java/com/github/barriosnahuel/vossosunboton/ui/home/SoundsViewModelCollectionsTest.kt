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
            // Force the system Baúl seed before addAudio() against BAUL_SYSTEM_ID — addAudio is a
            // no-op when the target collection is missing from the persisted list, and the seed
            // only runs as a side effect of reading the `collections` Flow.
            collections.collections.first()
            val sounds = repo.sounds.first()
            val privateOne = sounds.first { it.name == "private-only" }
            val publicOne = sounds.first { it.name == "public-only" }
            val pub = collections.create("Work", CollectionProfile.GENERIC_PUBLIC)
            collections.addAudio(pub.id, publicOne.id)
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
            // Force the system Baúl seed before addAudio() — see other private-only test.
            collections.collections.first()
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
    fun `vaultAudios exposes only audios in private collections`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repo.save(testSound("only-vault", file = "v.mp3"))
            repo.save(testSound("only-public", file = "p.mp3"))
            val collections = CollectionsRepository(context)
            collections.collections.first() // seed Baúl
            val sounds = repo.sounds.first()
            val v = sounds.first { it.name == "only-vault" }
            val p = sounds.first { it.name == "only-public" }
            val pub = collections.create("Work", CollectionProfile.GENERIC_PUBLIC)
            collections.addAudio(pub.id, p.id)
            collections.addAudio(CollectionsRepository.BAUL_SYSTEM_ID, v.id)
        }
        val vm = givenAViewModel()
        runBlocking { vm.collections.first { it.size > 1 } }
        runBlocking {
            vm.vaultAudios.first { list -> list.size == 1 && list.first().name == "only-vault" }
        }

        assertThat(vm.vaultAudios.value.map { it.name }).containsExactly("only-vault")
    }

    @Test
    fun `selecting a Vault filter narrows vaultAudios to that collection`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repo.save(testSound("baul-only", file = "b.mp3"))
            repo.save(testSound("custom-only", file = "c.mp3"))
            val collections = CollectionsRepository(context)
            collections.collections.first()
            val sounds = repo.sounds.first()
            val baulOnly = sounds.first { it.name == "baul-only" }
            val customOnly = sounds.first { it.name == "custom-only" }
            val custom = collections.create("Caro", CollectionProfile.VAULT)
            collections.addAudio(CollectionsRepository.BAUL_SYSTEM_ID, baulOnly.id)
            collections.addAudio(custom.id, customOnly.id)
        }
        val vm = givenAViewModel()
        runBlocking { vm.collections.first { it.size > 1 } }
        runBlocking { vm.vaultAudios.first { it.size == 2 } }

        val caro = vm.collections.value.first { it.name == "Caro" }
        vm.selectVaultFilter(caro.id)
        runBlocking { vm.vaultAudios.first { it.size == 1 && it.first().name == "custom-only" } }

        assertThat(vm.vaultAudios.value.map { it.name }).containsExactly("custom-only")
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
        // Wait until the library is hydrated by the initial loadSounds before asserting on .value.
        runBlocking { vm.library.first { list -> list.any { it.name == "audio-b" } } }
        // Switch to Vault — vm.sounds collapses to emptyList(), but the library snapshot must keep
        // the user catalog so ImmersiveListenScreen can resolve collection.audioIds to real Sounds.
        vm.selectTab(AppTab.VAULT)
        runBlocking { vm.sounds.first { it.isEmpty() } }

        assertThat(vm.sounds.value).isEmpty()
        val userLibraryNames =
            vm.library.value
                .filter { it.file != null }
                .map { it.name }
        assertThat(userLibraryNames).containsExactly("audio-a", "audio-b")
    }

    @Test
    fun `deleting a Vault-only audio removes it from vaultAudios and emits a delete event for undo`() {
        // Regression: pre-fix, deleteSound returned early on the Vault tab because `_sounds.value`
        // is empty there — the audio was never removed from `_vaultAudios`, the snackbar never
        // fired, and the SwipeToDismissBox kept its swiped-away state forever (the red background
        // with the trash icon stayed pinned where the card used to be).
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repo.save(testSound("only-vault", file = "v.mp3"))
            val collections = CollectionsRepository(context)
            collections.collections.first() // seed Baúl
            val sounds = repo.sounds.first()
            val v = sounds.first { it.name == "only-vault" }
            collections.addAudio(CollectionsRepository.BAUL_SYSTEM_ID, v.id)
        }
        val vm = givenAViewModel()
        runBlocking { vm.collections.first { it.size >= 1 } }
        runBlocking { vm.vaultAudios.first { it.size == 1 } }
        // The user is on the Vault tab when they swipe-to-delete; `_sounds` collapses to empty on
        // Vault by design (the VaultScreen reads `_vaultAudios` directly).
        vm.selectTab(AppTab.VAULT)
        runBlocking { vm.sounds.first { it.isEmpty() } }

        val target = vm.vaultAudios.value.single()
        vm.deleteSound(target)

        // Bug repro asserts:
        assertThat(vm.vaultAudios.value.map { it.id }).doesNotContain(target.id)
        assertThat(vm.deletedSoundEvent.value).isNotNull()
        assertThat(
            vm.deletedSoundEvent.value
                ?.sound
                ?.id,
        ).isEqualTo(target.id)
    }

    @Test
    fun `restoring a Vault-only audio from undo brings it back into vaultAudios`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repo.save(testSound("only-vault", file = "v.mp3"))
            val collections = CollectionsRepository(context)
            collections.collections.first()
            val sounds = repo.sounds.first()
            val v = sounds.first { it.name == "only-vault" }
            collections.addAudio(CollectionsRepository.BAUL_SYSTEM_ID, v.id)
        }
        val vm = givenAViewModel()
        runBlocking { vm.collections.first { it.isNotEmpty() } }
        runBlocking { vm.vaultAudios.first { it.size == 1 } }
        vm.selectTab(AppTab.VAULT)
        runBlocking { vm.sounds.first { it.isEmpty() } }

        val target = vm.vaultAudios.value.single()
        vm.deleteSound(target)
        vm.restoreSound()

        assertThat(vm.vaultAudios.value.map { it.id }).contains(target.id)
        assertThat(vm.deletedSoundEvent.value).isNull()
    }

    @Test
    fun `deleting the currently-filtered public collection clears the My Sounds filter`() {
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
        val collection = vm.collections.value.first { it.name == "Work" }
        vm.selectMySoundsFilter(collection.id)
        runBlocking { vm.activeMySoundsFilter.first { it == collection.id } }

        // The repo deletion path is what the Manage screen triggers behind the dialog confirm —
        // it propagates through the collections observer in the VM init, which is responsible
        // for resetting the stale active-filter id.
        runBlocking { CollectionsRepository(context).delete(collection.id) }
        runBlocking { vm.activeMySoundsFilter.first { it == null } }

        assertThat(vm.activeMySoundsFilter.value).isNull()
    }

    @Test
    fun `deleting the currently-filtered private collection clears the Vault filter`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repo.save(testSound("audio-v", file = "v.mp3"))
            val collections = CollectionsRepository(context)
            collections.collections.first() // seed Baúl
            val sounds = repo.sounds.first()
            val v = sounds.first { it.name == "audio-v" }
            val c = collections.create("Caro", CollectionProfile.VAULT)
            collections.addAudio(c.id, v.id)
        }
        val vm = givenAViewModel()
        runBlocking { vm.collections.first { it.size > 1 } }
        val caro = vm.collections.value.first { it.name == "Caro" }
        vm.selectVaultFilter(caro.id)
        runBlocking { vm.activeVaultFilter.first { it == caro.id } }

        runBlocking { CollectionsRepository(context).delete(caro.id) }
        runBlocking { vm.activeVaultFilter.first { it == null } }

        assertThat(vm.activeVaultFilter.value).isNull()
    }

    @Test
    fun `creating a private collection emits with the VAULT profile defaults`() {
        val vm = givenAViewModel()
        runBlocking { vm.collections.first { it.isNotEmpty() } }
        val outcome =
            runBlocking { vm.createCollection("Caro", CollectionAccess.PRIVATE, source = "manage") }
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
