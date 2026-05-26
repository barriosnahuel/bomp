/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.data.manager.CollectionsRepository
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Drives [DebugSoundSeeder.seed] with in-memory [SeedSource]s — no dependency on the (uncommitted)
 * bundled debug audio, so the seeder's persistence behaviour stays verifiable on CI.
 */
internal class DebugSoundSeederTest : AbstractRobolectricTest() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val repo: SoundsRepository get() = SoundsRepository(context)
    private val collectionsRepo: CollectionsRepository get() = CollectionsRepository(context)

    @Before
    fun setUp() =
        runBlocking {
            repo.clearForTest()
            collectionsRepo.clearForTest()
        }

    @After
    fun tearDown() =
        runBlocking {
            repo.clearForTest()
            collectionsRepo.clearForTest()
        }

    private fun fakeSources(vararg names: String): List<SeedSource> =
        names.mapIndexed { index, name ->
            SeedSource(key = "k$index", name = name) { "fake-audio-bytes-$index".byteInputStream() }
        }

    private fun fakeSource(
        key: String,
        name: String,
        publicCollections: List<String> = emptyList(),
        privateCollections: List<String> = emptyList(),
        inSystemVault: Boolean = false,
        visibleInMySounds: Boolean = true,
    ): SeedSource =
        SeedSource(
            key = key,
            name = name,
            publicCollections = publicCollections,
            privateCollections = privateCollections,
            inSystemVault = inSystemVault,
            visibleInMySounds = visibleInMySounds,
        ) { "fake-audio-bytes-$key".byteInputStream() }

    private suspend fun customSounds() = repo.sounds.first().filterNot { it.isBundled() }

    @Test
    fun `seed saves the requested samples as custom sounds backed by a file`() =
        runTest {
            DebugSoundSeeder.seed(context, fakeSources("Alpha", "Beta"))

            val custom = customSounds()
            assertThat(custom.map { it.name }).containsExactly("Alpha", "Beta")
            assertThat(custom.all { it.file != null }).isTrue()
        }

    @Test
    fun `seed copies the audio payload to the Music external dir`() =
        runTest {
            DebugSoundSeeder.seed(context, fakeSources("Alpha"))

            val seeded = customSounds().single()
            assertThat(getFile(context, seeded.file!!).exists()).isTrue()
        }

    @Test
    fun `seed stops at the requested limit`() =
        runTest {
            DebugSoundSeeder.seed(context, fakeSources("A", "B", "C", "D"), limit = 2)

            assertThat(customSounds()).hasSize(2)
        }

    @Test
    fun `seed run twice does not duplicate entries`() =
        runTest {
            val sources = fakeSources("Alpha", "Beta")
            DebugSoundSeeder.seed(context, sources)
            DebugSoundSeeder.seed(context, sources)

            assertThat(customSounds()).hasSize(2)
        }

    @Test
    fun `seed keys identity on the stable key so samples sharing a display name both seed`() =
        runTest {
            val sources =
                listOf(
                    SeedSource(key = "1", name = "Twin") { "a".byteInputStream() },
                    SeedSource(key = "2", name = "Twin") { "b".byteInputStream() },
                )
            DebugSoundSeeder.seed(context, sources)

            assertThat(customSounds()).hasSize(2)
        }

    @Test
    fun `seed creates the requested public collection and assigns the audio to it`() =
        runTest {
            DebugSoundSeeder.seed(
                context = context,
                sources = listOf(fakeSource("a", "Alpha", publicCollections = listOf("Familia"))),
                limit = 1,
                publicCollections = listOf("Familia"),
            )

            val familia = collectionsRepo.collections.first().single { it.name == "Familia" }
            assertThat(familia.isPublic).isTrue()
            assertThat(familia.audioIds).contains("seed:a")
        }

    @Test
    fun `seed plants a vault-only audio hidden from My Sounds and inside the system Baul`() =
        runTest {
            DebugSoundSeeder.seed(
                context = context,
                sources = listOf(fakeSource("v", "Secreto", inSystemVault = true, visibleInMySounds = false)),
                limit = 1,
            )

            val seeded = customSounds().single { it.id == "seed:v" }
            assertThat(seeded.isVisibleInMySounds).isFalse()

            val baul = collectionsRepo.collections.first().single { it.isSystem }
            assertThat(baul.isPrivate).isTrue()
            assertThat(baul.audioIds).contains("seed:v")
        }

    @Test
    fun `seed creates a non-system private collection and assigns its members`() =
        runTest {
            DebugSoundSeeder.seed(
                context = context,
                sources =
                    listOf(
                        fakeSource("p", "Nuestro", privateCollections = listOf("Solo nuestro"), visibleInMySounds = false),
                    ),
                limit = 1,
                privateCollections = listOf("Solo nuestro"),
            )

            val nuestro = collectionsRepo.collections.first().single { it.name == "Solo nuestro" }
            assertThat(nuestro.isPrivate).isTrue()
            assertThat(nuestro.isSystem).isFalse()
            assertThat(nuestro.audioIds).contains("seed:p")
        }

    @Test
    fun `seed run twice does not duplicate collections or memberships`() =
        runTest {
            val sources = listOf(fakeSource("a", "Alpha", publicCollections = listOf("Familia")))
            DebugSoundSeeder.seed(context, sources, limit = 1, publicCollections = listOf("Familia"))
            DebugSoundSeeder.seed(context, sources, limit = 1, publicCollections = listOf("Familia"))

            val familia = collectionsRepo.collections.first().filter { it.name == "Familia" }
            assertThat(familia).hasSize(1)
            assertThat(familia.single().audioIds).containsExactly("seed:a")
        }
}
