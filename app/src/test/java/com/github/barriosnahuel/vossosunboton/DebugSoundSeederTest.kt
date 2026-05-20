/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
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

    @Before
    fun setUp() = runBlocking { repo.clearForTest() }

    @After
    fun tearDown() = runBlocking { repo.clearForTest() }

    private fun fakeSources(vararg names: String): List<SeedSource> =
        names.mapIndexed { index, name ->
            SeedSource(key = "k$index", name = name) { "fake-audio-bytes-$index".byteInputStream() }
        }

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
}
