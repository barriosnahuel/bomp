/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.os.Looper
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.vault.security.VaultSessionState
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionProfile
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf

internal class SoundsViewModelSearchTest : AbstractRobolectricTest() {
    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        // Deterministically stop the reactive `repo.sounds` collector each VM starts in `init`.
        // A bare `cancel()` is fire-and-forget: the collector can outlive the
        // test, parked on the process-singleton DataStore, and pollute the next test by re-running
        // `loadSounds` against stale state. `cancelAndJoinAll()` joins until it unwinds — see
        // ViewModelTestCleanup.kt.
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        unmockkAll()
    }

    @Test
    fun `searchResults emits empty list when query is blank`() {
        val viewModel = givenAViewModel()
        viewModel.injectAllSounds(listOf(testSound("Bromas de oficina", rawRes = 1), testSound("Casa feliz", file = "casa.mp3")))

        viewModel.onSearchQueryChange("")

        assertThat(viewModel.searchResults.value).isEmpty()
    }

    @Test
    fun `searchResults filters by name case-insensitively`() {
        val viewModel = givenAViewModel()
        val bromas = testSound("Bromas de oficina", rawRes = 1)
        val casa = testSound("Casa feliz", file = "casa.mp3")
        viewModel.injectAllSounds(listOf(bromas, casa))

        viewModel.onSearchQueryChange("bro")

        assertThat(viewModel.searchResults.value).containsExactly(bromas)
    }

    @Test
    fun `searchResults reflects a pin toggled while overlay is open`() {
        val viewModel = givenAViewModel()
        val sound = testSound("custom sound", file = "custom.mp3")
        viewModel.injectSoundsAndAllSounds(listOf(sound))
        viewModel.onSearchQueryChange("custom")

        viewModel.togglePin(sound)

        assertThat(
            viewModel.searchResults.value
                .single { it.name == "custom sound" }
                .isPinned,
        ).isTrue()
    }

    @Test
    fun `searchResults removes a deleted sound immediately`() {
        val viewModel = givenAViewModel()
        val sound = testSound("custom sound", file = "custom.mp3")
        viewModel.injectSoundsAndAllSounds(listOf(sound))
        viewModel.onSearchQueryChange("custom")

        viewModel.deleteSound(sound)

        assertThat(viewModel.searchResults.value.none { it.name == "custom sound" }).isTrue()
    }

    @Test
    fun `searchResults sorts pinned result first after togglePin`() {
        val viewModel = givenAViewModel()
        val alpha = testSound("test alpha", file = "a.mp3")
        val beta = testSound("test beta", file = "b.mp3")
        viewModel.injectSoundsAndAllSounds(listOf(alpha, beta))
        viewModel.onSearchQueryChange("test")

        viewModel.togglePin(beta)

        assertThat(
            viewModel.searchResults.value
                .first()
                .name,
        ).isEqualTo("test beta")
    }

    /**
     * OWASP MASVS-STORAGE-2 / CWE-200 (search overlay must not leak names of audios tagged
     * exclusively to a private collection while the Vault is locked).
     *
     * Without this filter, searching "mama" from the My Sounds search overlay would surface a
     * Bomp named "Mama secreta" tagged only to a Vault collection — bypassing the biometric gate.
     */
    @Test
    fun `searchResults excludes private-only audios when Vault session is locked`() {
        VaultSessionState.clearForTest()
        val viewModel = givenAViewModel()
        val publicAudio = testSound("Mama publica", file = "pub.mp3")
        val privateAudio = testSound("Mama secreta", file = "priv.mp3")
        viewModel.injectAllSounds(listOf(publicAudio, privateAudio))
        viewModel.injectCollections(
            listOf(privateCollection("col_priv", "Mi vault", audioIds = listOf(privateAudio.id))),
        )

        viewModel.onSearchQueryChange("Mama")

        assertThat(viewModel.searchResults.value.map { it.id }).contains(publicAudio.id)
        assertThat(viewModel.searchResults.value.map { it.id }).doesNotContain(privateAudio.id)
    }

    /**
     * Spec § 3.1 "Restricción de cross-preset": an audio tagged to BOTH a public and a private
     * collection stays visible on public surfaces (My Sounds, Search) — the public scope wins.
     * This is the carve-out — cross-tagged audios MUST stay searchable even with Vault locked.
     */
    @Test
    fun `searchResults keeps cross-tagged audios visible when Vault is locked`() {
        VaultSessionState.clearForTest()
        val viewModel = givenAViewModel()
        val crossTagged = testSound("Carlos cross", file = "carlos.mp3")
        viewModel.injectAllSounds(listOf(crossTagged))
        viewModel.injectCollections(
            listOf(
                privateCollection("col_priv", "vault", audioIds = listOf(crossTagged.id)),
                publicCollection("col_pub", "Trabajo", audioIds = listOf(crossTagged.id)),
            ),
        )

        viewModel.onSearchQueryChange("carlos")

        assertThat(viewModel.searchResults.value.map { it.id }).contains(crossTagged.id)
    }

    /**
     * Reactive bridge between [VaultSessionState] and [SoundsViewModel.searchResults]: the user
     * starts searching (sees no private matches), taps the gated "Unlock" CTA on the overlay,
     * authenticates → `VaultSessionState.markVaultOpen()`. Without observing the session flow,
     * the search results would stay stale and the previously-hidden private matches would only
     * appear after the user re-types — a clearly broken UX.
     */
    @Test
    fun `searchResults recomputes when VaultSessionState flips to open mid-search`() {
        VaultSessionState.clearForTest()
        val viewModel = givenAViewModel()
        val privateAudio = testSound("Mama secreta", file = "priv.mp3")
        viewModel.injectAllSounds(listOf(privateAudio))
        viewModel.injectCollections(
            listOf(privateCollection("col_priv", "vault", audioIds = listOf(privateAudio.id))),
        )
        viewModel.onSearchQueryChange("Mama")
        // Same paused-looper hazard as the post-flip assert below: the search recompute runs on the
        // VM's viewModelScope collector (Dispatchers.Main), so the injected private-collection
        // membership hasn't been applied when we read searchResults.value synchronously — under CI
        // load the filter lags and the private audio leaks in. Flush the looper before asserting.
        shadowOf(Looper.getMainLooper()).idle()
        // Pre-condition: while locked, the private audio is filtered out.
        assertThat(viewModel.searchResults.value.map { it.id }).doesNotContain(privateAudio.id)

        VaultSessionState.markVaultOpen()
        // The session-flip recompute runs on the VM's `viewModelScope` collector (Dispatchers.Main,
        // SoundsViewModel.kt). Robolectric's main looper is PAUSED, so that resumption is queued and
        // hasn't run when we read searchResults.value synchronously — it flakes on the slower SDK 23
        // run under CI load. Flush the looper so the recompute lands first. (A `first {}` await would
        // deadlock here: runBlocking does not pump the Android looper the collector is parked on.)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.searchResults.value.map { it.id }).contains(privateAudio.id)
        VaultSessionState.clearForTest()
    }

    /**
     * Modifier D: once the user has authenticated the Vault during this process
     * (`VaultSessionState.markVaultOpen`), Search merges private and public results without a
     * separate gated section — the privacy boundary "this app, this session" is already met.
     */
    @Test
    fun `searchResults includes private-only audios once VaultSessionState marks open`() {
        VaultSessionState.clearForTest()
        val viewModel = givenAViewModel()
        val privateAudio = testSound("Mama secreta", file = "priv.mp3")
        viewModel.injectAllSounds(listOf(privateAudio))
        viewModel.injectCollections(
            listOf(privateCollection("col_priv", "vault", audioIds = listOf(privateAudio.id))),
        )

        VaultSessionState.markVaultOpen()
        viewModel.onSearchQueryChange("Mama")

        assertThat(viewModel.searchResults.value.map { it.id }).contains(privateAudio.id)
        VaultSessionState.clearForTest()
    }

    @Test
    fun `hideSearch resets query and clears results`() {
        val viewModel = givenAViewModel()
        viewModel.injectAllSounds(listOf(testSound("Bromas de oficina", rawRes = 1)))
        viewModel.showSearch()
        viewModel.onSearchQueryChange("bro")

        viewModel.hideSearch()

        assertThat(viewModel.isSearchVisible.value).isFalse()
        assertThat(viewModel.searchQuery.value).isEmpty()
        assertThat(viewModel.searchResults.value).isEmpty()
    }

    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectAllSounds(sounds: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("allSoundsCache")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = sounds }
    }

    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectSounds(sounds: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("_sounds")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = sounds }
    }

    private fun SoundsViewModel.injectSoundsAndAllSounds(sounds: List<Sound>) {
        injectSounds(sounds)
        injectAllSounds(sounds)
    }

    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectCollections(collections: List<Collection>) {
        SoundsViewModel::class.java
            .getDeclaredField("_collections")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Collection>>).value = collections }
    }

    private fun privateCollection(
        id: String,
        name: String,
        audioIds: List<String> = emptyList(),
    ): Collection =
        Collection(
            id = id,
            name = name,
            profile = CollectionProfile.VAULT,
            audioIds = audioIds,
        )

    private fun publicCollection(
        id: String,
        name: String,
        audioIds: List<String> = emptyList(),
    ): Collection =
        Collection(
            id = id,
            name = name,
            profile = CollectionProfile.GENERIC_PUBLIC,
            audioIds = audioIds,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(): SoundsViewModel {
        val vm =
            SoundsViewModel(
                androidx.test.core.app.ApplicationProvider
                    .getApplicationContext(),
                ioDispatcher = UnconfinedTestDispatcher(),
                searchDebounceMs = 0L,
            )
        createdViewModels += vm
        return vm
    }
}
