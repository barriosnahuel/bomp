/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.collections.DualHomeCoachStore
import com.github.barriosnahuel.vossosunboton.feature.collections.MySoundsFilterStore
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore
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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Behaviour of the explicit `isVisibleInMySounds` flag (ADR 0012): tagging an audio to a private
 * (Vault) collection never removes it from My Sounds by itself; only the switch does. Plus the
 * chip-vs-flag rule, the dual-home coach, and the "moved to Vault" feedback.
 *
 * Pinned to a single Robolectric SDK (like [com.github.barriosnahuel.vossosunboton.ui.home.SoundItemTest]
 * et al.): this class drives SDK-independent business logic — there is no `Build.VERSION` branch
 * anywhere in the exercised path — so Robolectric's default of a single SDK fully covers it. Running
 * it across the [AbstractRobolectricTest] M/TIRAMISU/VANILLA matrix would only triple the runtime for
 * identical assertions, with no added coverage.
 */
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class SoundsViewModelVisibilityTest : AbstractRobolectricTest() {
    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            SoundsRepository(context).clearForTest()
            CollectionsRepository(context).clearForTest()
            MySoundsFilterStore(context).clearForTest()
            DualHomeCoachStore(context).clearForTest()
            // Keep the welcome sticker off the asserted list. Run the one-time persistence migration
            // first so it can't resurrect the welcome, then consume it (a new-model manual delete,
            // which sticks). clearForTest() alone would re-enable it.
            val welcome = WelcomeStickerStore(context)
            welcome.clearForTest()
            welcome.migrateToPersistentIfNeeded()
            welcome.consume()
        }
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
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
    fun `tagging a visible audio to a private collection keeps it in MY_SOUNDS`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking { SoundsRepository(context).save(testSound("dual", file = "dual.mp3")) }
        val vm = givenAViewModel()
        runBlocking { withTimeout(TIMEOUT_MS) { vm.collections.first { it.isNotEmpty() } } }

        // The core fix: adding a private tag must NOT silently drop the audio from the botonera.
        vm.toggleAudioInCollection("custom:dual", CollectionsRepository.BAUL_SYSTEM_ID)
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                vm.collections.first { cols ->
                    cols.any { it.id == CollectionsRepository.BAUL_SYSTEM_ID && "custom:dual" in it.audioIds }
                }
                vm.vaultAudios.first { audios -> audios.any { it.name == "dual" } }
            }
        }

        assertThat(vm.sounds.value.map { it.name }).contains("dual")
        assertThat(vm.vaultAudios.value.map { it.name }).contains("dual")
    }

    @Test
    fun `turning visibility off hides the audio from the MY_SOUNDS Todo view`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            SoundsRepository(context).save(testSound("hideme", file = "h.mp3"))
            CollectionsRepository(context).apply {
                collections.first() // seed Baúl
                addAudio(CollectionsRepository.BAUL_SYSTEM_ID, "custom:hideme")
            }
        }
        val vm = givenAViewModel()
        // "hideme" lives only in the Baúl (private), so the one-time ADR-0012 visibility migration may
        // seed it hidden. Asserting it starts *visible* without making that explicit races the
        // migration (and `repo.sounds` distinctUntilChanged) — the source of this test's earlier
        // non-deterministic hang. Set the dual-home state explicitly (visible in My Sounds *and* in the
        // Vault), then assert that turning the flag off removes it from "Todo".
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                vm.collections.first { it.isNotEmpty() }
                // Await the reactive loadSounds priming allSoundsCache with "hideme" BEFORE toggling
                // visibility. Without it, on a loaded CI machine setAudioVisibleInMySounds runs
                // against an empty cache and the toggle is lost, so the sounds-contains-hideme await
                // below times out (CLAUDE.md § JVM tests — await every async input).
                vm.library.first { lib -> lib.any { it.name == "hideme" } }
                vm.setAudioVisibleInMySounds("custom:hideme", "hideme", visible = true)
                vm.sounds.first { list -> list.any { it.name == "hideme" } }
            }
        }

        vm.setAudioVisibleInMySounds("custom:hideme", "hideme", visible = false)
        runBlocking { withTimeout(TIMEOUT_MS) { vm.sounds.first { list -> list.none { it.name == "hideme" } } } }

        assertThat(vm.sounds.value.map { it.name }).doesNotContain("hideme")
    }

    @Test
    fun `a hidden audio still appears when its public collection chip is selected`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repo.save(testSound("recipe", file = "r.mp3"))
            val collections = CollectionsRepository(context)
            val recetas = collections.create("Recetas", CollectionProfile.GENERIC_PUBLIC)
            collections.addAudio(recetas.id, "custom:recipe")
        }
        val vm = givenAViewModel()
        runBlocking { withTimeout(TIMEOUT_MS) { vm.collections.first { it.any { c -> c.name == "Recetas" } } } }
        val recetas = vm.collections.value.first { it.name == "Recetas" }

        // Hidden from "Todo"...
        vm.setAudioVisibleInMySounds("custom:recipe", "recipe", visible = false)
        runBlocking { withTimeout(TIMEOUT_MS) { vm.sounds.first { list -> list.none { it.name == "recipe" } } } }
        assertThat(vm.sounds.value.map { it.name }).doesNotContain("recipe")

        // ...but the public chip surfaces it by membership (ADR 0012 — independent axes).
        vm.selectMySoundsFilter(recetas.id)
        runBlocking { withTimeout(TIMEOUT_MS) { vm.sounds.first { list -> list.any { it.name == "recipe" } } } }
        assertThat(vm.sounds.value.map { it.name }).containsExactly("recipe")
    }

    @Test
    fun `applyAssignment persists staged collections and visibility in one transaction`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            SoundsRepository(context).save(testSound("tx", file = "tx.mp3"))
            CollectionsRepository(context).apply {
                create("Recetas", CollectionProfile.GENERIC_PUBLIC)
                collections.first() // seed Baúl
            }
        }
        val vm = givenAViewModel()
        runBlocking { withTimeout(TIMEOUT_MS) { vm.collections.first { it.any { c -> c.name == "Recetas" } } } }
        val recetas = vm.collections.value.first { it.name == "Recetas" }

        vm.applyAssignment(
            audioId = "custom:tx",
            name = "tx",
            publicCollectionIds = setOf(recetas.id),
            privateCollectionIds = setOf(CollectionsRepository.BAUL_SYSTEM_ID),
            isVisibleInMySounds = false,
        )
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                vm.collections.first { cols ->
                    cols.first { it.id == recetas.id }.audioIds.contains("custom:tx") &&
                        cols.first { it.id == CollectionsRepository.BAUL_SYSTEM_ID }.audioIds.contains("custom:tx")
                }
                vm.library.first { lib -> lib.firstOrNull { it.id == "custom:tx" }?.isVisibleInMySounds == false }
            }
        }

        assertThat(
            vm.library.value
                .first { it.id == "custom:tx" }
                .isVisibleInMySounds,
        ).isFalse()
    }

    @Test
    fun `applying the assignment with a visible audio in a private collection fires the coach once`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking { SoundsRepository(context).save(testSound("coach", file = "c.mp3")) }
        val vm = givenAViewModel()
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                vm.collections.first { it.isNotEmpty() }
                // Await the reactive loadSounds priming allSoundsCache with "coach" BEFORE applyAssignment:
                // evaluateAssignCloseEffects early-returns when the cache lacks the audio, so on a loaded CI
                // machine the dual-home coach never fires and the await below times out (CLAUDE.md § JVM
                // tests — await every async input).
                vm.library.first { lib -> lib.any { it.name == "coach" } }
            }
        }

        // "Listo" with the audio staged into the Baúl while staying visible → the dual-home coach.
        vm.applyAssignment(
            "custom:coach",
            "coach",
            publicCollectionIds = emptySet(),
            privateCollectionIds = setOf(CollectionsRepository.BAUL_SYSTEM_ID),
            isVisibleInMySounds = true,
        )
        runBlocking { withTimeout(TIMEOUT_MS) { vm.dualHomeCoachEvent.first() } }

        // Applying again must NOT re-fire — the coach is one-shot per device.
        vm.applyAssignment(
            "custom:coach",
            "coach",
            publicCollectionIds = emptySet(),
            privateCollectionIds = setOf(CollectionsRepository.BAUL_SYSTEM_ID),
            isVisibleInMySounds = true,
        )
        val secondCoach = runBlocking { withTimeoutOrNull(SHORT_TIMEOUT_MS) { vm.dualHomeCoachEvent.first() } }
        assertThat(secondCoach).isNull()
    }

    @Test
    fun `applying the assignment after hiding an audio that stays in the Vault fires the moved-to-vault event`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking { SoundsRepository(context).save(testSound("moved", file = "m.mp3")) }
        val vm = givenAViewModel()
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                vm.collections.first { it.isNotEmpty() }
                // Await allSoundsCache priming with "moved" before applyAssignment — evaluateAssignCloseEffects
                // early-returns on an unprimed cache, so the moved-to-vault event never fires on a loaded CI
                // machine (CLAUDE.md § JVM tests — await every async input).
                vm.library.first { lib -> lib.any { it.name == "moved" } }
            }
        }

        vm.applyAssignment(
            "custom:moved",
            "moved",
            publicCollectionIds = emptySet(),
            privateCollectionIds = setOf(CollectionsRepository.BAUL_SYSTEM_ID),
            isVisibleInMySounds = false,
        )
        val event = runBlocking { withTimeout(TIMEOUT_MS) { vm.movedToVaultEvent.first() } }

        assertThat(event.audioId).isEqualTo("custom:moved")
        assertThat(event.name).isEqualTo("moved")
    }

    @Test
    fun `cancelling the assign sheet fires no coach and persists nothing`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            SoundsRepository(context).save(testSound("cancel", file = "x.mp3"))
            CollectionsRepository(context).apply {
                collections.first()
                addAudio(CollectionsRepository.BAUL_SYSTEM_ID, "custom:cancel")
            }
        }
        val vm = givenAViewModel()
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                vm.collections.first { cols ->
                    cols.any { it.id == CollectionsRepository.BAUL_SYSTEM_ID && "custom:cancel" in it.audioIds }
                }
            }
        }

        // Back/dismiss = cancel: no coach fires (that belongs to applyAssignment).
        vm.requestAssignCollections("custom:cancel")
        vm.dismissAssignCollections()

        val coach = runBlocking { withTimeoutOrNull(SHORT_TIMEOUT_MS) { vm.dualHomeCoachEvent.first() } }
        assertThat(coach).isNull()
    }

    @Test
    fun `removal from My Bomps is allowed when the audio is in ANY collection, public or private`() {
        // The anti-orphan gate keys off "reachable somewhere", not "in the Vault".
        assertThat(isReachableOutsideMySounds(publicCollectionIds = setOf("recetas"), privateCollectionIds = emptySet())).isTrue()
        assertThat(isReachableOutsideMySounds(publicCollectionIds = emptySet(), privateCollectionIds = setOf("baul"))).isTrue()
        assertThat(isReachableOutsideMySounds(publicCollectionIds = setOf("recetas"), privateCollectionIds = setOf("baul"))).isTrue()
        assertThat(isReachableOutsideMySounds(publicCollectionIds = emptySet(), privateCollectionIds = emptySet())).isFalse()
    }

    @Test
    fun `applyAssignment can hide an audio that lives only in a public collection`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            SoundsRepository(context).save(testSound("pubonly", file = "p.mp3"))
            CollectionsRepository(context).create("Recetas", CollectionProfile.GENERIC_PUBLIC)
        }
        val vm = givenAViewModel()
        runBlocking { withTimeout(TIMEOUT_MS) { vm.collections.first { it.any { c -> c.name == "Recetas" } } } }
        val recetas = vm.collections.value.first { it.name == "Recetas" }

        // Hide it while only in a public collection — allowed (reachable via its chip, not orphaned).
        vm.applyAssignment(
            audioId = "custom:pubonly",
            name = "pubonly",
            publicCollectionIds = setOf(recetas.id),
            privateCollectionIds = emptySet(),
            isVisibleInMySounds = false,
        )
        runBlocking { withTimeout(TIMEOUT_MS) { vm.sounds.first { list -> list.none { it.name == "pubonly" } } } }
        assertThat(
            vm.library.value
                .first { it.id == "custom:pubonly" }
                .isVisibleInMySounds,
        ).isFalse()

        // ...still surfaced by its public chip.
        vm.selectMySoundsFilter(recetas.id)
        runBlocking { withTimeout(TIMEOUT_MS) { vm.sounds.first { list -> list.any { it.name == "pubonly" } } } }
        assertThat(vm.sounds.value.map { it.name }).containsExactly("pubonly")
    }

    @Test
    fun `applyAssignment never orphans an audio with no collections — keeps it visible`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking { SoundsRepository(context).save(testSound("orphan", file = "o.mp3")) }
        val vm = givenAViewModel()
        runBlocking { withTimeout(TIMEOUT_MS) { vm.collections.first { it.isNotEmpty() } } }

        // Attempt to hide with NO collections → coerced back to visible (anti-orphan safety net).
        vm.applyAssignment(
            audioId = "custom:orphan",
            name = "orphan",
            publicCollectionIds = emptySet(),
            privateCollectionIds = emptySet(),
            isVisibleInMySounds = false,
        )
        val becameHidden =
            runBlocking {
                withTimeoutOrNull(SHORT_TIMEOUT_MS) {
                    vm.library.first { lib -> lib.firstOrNull { it.id == "custom:orphan" }?.isVisibleInMySounds == false }
                }
            }

        assertThat(becameHidden).isNull()
        assertThat(
            vm.library.value
                .first { it.id == "custom:orphan" }
                .isVisibleInMySounds,
        ).isTrue()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(): SoundsViewModel {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = SoundsViewModel(context, ioDispatcher = UnconfinedTestDispatcher())
        createdViewModels += vm
        runBlocking { withTimeout(TIMEOUT_MS) { vm.isInitialLoadComplete.first { it } } }
        return vm
    }

    private companion object {
        // Generous headroom for the DataStore → repo → loadSounds → StateFlow chain on a loaded CI
        // machine (a 3s cap intermittently timed out under load). It only delays the genuine-hang
        // case; passing awaits return as soon as the awaited state arrives.
        const val TIMEOUT_MS = 10_000L
        const val SHORT_TIMEOUT_MS = 750L
    }
}
