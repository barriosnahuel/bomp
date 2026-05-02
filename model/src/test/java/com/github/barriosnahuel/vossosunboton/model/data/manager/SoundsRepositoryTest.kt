/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model.data.manager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class SoundsRepositoryTest : AbstractRobolectricTest() {
    private lateinit var context: Context
    private val recordedErrors = mutableListOf<Throwable>()
    private lateinit var repo: SoundsRepository

    @Before
    fun setUp() =
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            repo = SoundsRepository(context, onError = { recordedErrors += it })
            repo.clearForTest()
            recordedErrors.clear()
        }

    @After
    fun tearDown() =
        runBlocking {
            repo.clearForTest()
        }

    @Test
    fun `save then sounds emits the saved custom sound`() =
        runTest {
            repo.save(Sound("bell", "bell.mp3"))

            val result = repo.sounds.first().filter { !it.isBundled() }

            assertThat(result).hasSize(1)
            assertThat(result.single().name).isEqualTo("bell")
            assertThat(result.single().file).isEqualTo("bell.mp3")
        }

    @Test
    fun `save then clear then sounds emits no custom sounds`() =
        runTest {
            repo.save(Sound("bell", "bell.mp3"))

            repo.clearForTest()

            assertThat(repo.sounds.first().filter { !it.isBundled() }).isEmpty()
        }

    @Test
    fun `savePin true is reflected as isPinned in sounds emission`() =
        runTest {
            repo.save(Sound("bell", "bell.mp3"))

            repo.savePin("bell", true)

            assertThat(
                repo.sounds
                    .first()
                    .single { it.name == "bell" }
                    .isPinned,
            ).isTrue()
        }

    @Test
    fun `savePin false overrides a previous savePin true`() =
        runTest {
            repo.save(Sound("bell", "bell.mp3"))
            repo.savePin("bell", true)

            repo.savePin("bell", false)

            assertThat(
                repo.sounds
                    .first()
                    .single { it.name == "bell" }
                    .isPinned,
            ).isFalse()
        }

    @Test
    fun `savePin survives across two reads of the flow`() =
        runTest {
            repo.save(Sound("bell", "bell.mp3"))
            repo.savePin("bell", true)

            // First read
            assertThat(
                repo.sounds
                    .first()
                    .single { it.name == "bell" }
                    .isPinned,
            ).isTrue()

            // Re-instantiating the repository hits the same DataStore (delegate is process-singleton).
            // True cold-restart coverage requires the manual Auto Backup smoke step (see plan).
            val repo2 = SoundsRepository(context)
            assertThat(
                repo2.sounds
                    .first()
                    .single { it.name == "bell" }
                    .isPinned,
            ).isTrue()
        }

    @Test
    fun `durations is empty when no durations saved`() =
        runTest {
            assertThat(repo.durations.first()).isEmpty()
        }

    @Test
    fun `saveDuration then durations emits the saved duration`() =
        runTest {
            repo.save(Sound("bell", "bell.mp3"))

            repo.saveDuration("bell", 42_000)

            assertThat(repo.durations.first()).containsEntry("bell", 42_000)
        }

    @Test
    fun `delete also removes the persisted duration`() =
        runTest {
            val sound = Sound("bell", "bell.mp3")
            repo.save(sound)
            repo.saveDuration("bell", 42_000)
            val soundFile = getFile(context, "bell.mp3")
            soundFile.parentFile?.mkdirs()
            soundFile.createNewFile()

            repo.delete(sound)

            assertThat(repo.durations.first()).doesNotContainKey("bell")
            assertThat(repo.sounds.first().any { it.name == "bell" && !it.isBundled() }).isFalse()
        }

    @Test
    fun `rename preserves pinned and duration`() =
        runTest {
            repo.save(Sound("bell", "bell.mp3"))
            repo.savePin("bell", true)
            repo.saveDuration("bell", 99_999)

            repo.rename("bell", "doorbell")

            val renamed = repo.sounds.first().single { it.name == "doorbell" && !it.isBundled() }
            assertThat(renamed.isPinned).isTrue()
            assertThat(repo.durations.first()).containsEntry("doorbell", 99_999)
            assertThat(repo.sounds.first().none { it.name == "bell" && !it.isBundled() }).isTrue()
        }

    @Test
    fun `concurrent saves do not lose data`() =
        runTest {
            val jobs =
                (1..20).map { i ->
                    async { repo.save(Sound("sound-$i", "sound-$i.mp3")) }
                }
            jobs.awaitAll()

            val customSounds = repo.sounds.first().filter { !it.isBundled() }
            assertThat(customSounds).hasSize(20)
            assertThat(customSounds.map { it.name }.toSet())
                .isEqualTo((1..20).map { "sound-$it" }.toSet())
        }

    @Test(expected = IllegalArgumentException::class)
    fun `save with blank name throws`() =
        runTest {
            repo.save(Sound("", "bell.mp3"))
        }

    @Test(expected = IllegalArgumentException::class)
    fun `save with name longer than 200 chars throws`() =
        runTest {
            repo.save(Sound("x".repeat(201), "bell.mp3"))
        }

    @Test(expected = IllegalArgumentException::class)
    fun `rename with blank new name throws`() =
        runTest {
            repo.save(Sound("bell", "bell.mp3"))
            repo.rename("bell", "")
        }

    @Test
    fun `malformed JSON in store returns empty list and reports via onError`() =
        runTest {
            repo.setRawJsonForTest("not-json{")

            val list = repo.sounds.first().filter { !it.isBundled() }

            assertThat(list).isEmpty()
            assertThat(recordedErrors).hasSize(1)
            assertThat(recordedErrors.single().message).contains("Malformed sounds_json")
        }
}
