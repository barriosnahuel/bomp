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
import com.github.barriosnahuel.vossosunboton.model.testSound
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
            repo.save(testSound("bell", "bell.mp3"))

            val result = repo.sounds.first().filter { !it.isBundled() }

            assertThat(result).hasSize(1)
            assertThat(result.single().name).isEqualTo("bell")
            assertThat(result.single().file).isEqualTo("bell.mp3")
        }

    @Test
    fun `save then clear then sounds emits no custom sounds`() =
        runTest {
            repo.save(testSound("bell", "bell.mp3"))

            repo.clearForTest()

            assertThat(repo.sounds.first().filter { !it.isBundled() }).isEmpty()
        }

    @Test
    fun `two custom sounds with the same name coexist`() =
        runTest {
            repo.save(Sound(id = "custom:a", name = "Bell", file = "a.mp3"))
            repo.save(Sound(id = "custom:b", name = "Bell", file = "b.mp3"))

            val customSounds = repo.sounds.first().filter { !it.isBundled() }

            assertThat(customSounds).hasSize(2)
            assertThat(customSounds.map { it.id }.toSet()).containsExactly("custom:a", "custom:b")
            assertThat(customSounds.map { it.name }.toSet()).containsExactly("Bell")
        }

    @Test
    fun `save with the same id upserts instead of duplicating`() =
        runTest {
            repo.save(Sound(id = "custom:a", name = "Bell", file = "a.mp3"))
            repo.save(Sound(id = "custom:a", name = "Doorbell", file = "a.mp3"))

            val customSounds = repo.sounds.first().filter { !it.isBundled() }

            assertThat(customSounds).hasSize(1)
            assertThat(customSounds.single().name).isEqualTo("Doorbell")
        }

    @Test
    fun `savePin true is reflected as isPinned in sounds emission`() =
        runTest {
            repo.save(testSound("bell", "bell.mp3"))

            repo.savePin("custom:bell", "bell", true)

            assertThat(
                repo.sounds
                    .first()
                    .single { it.id == "custom:bell" }
                    .isPinned,
            ).isTrue()
        }

    @Test
    fun `savePin false overrides a previous savePin true`() =
        runTest {
            repo.save(testSound("bell", "bell.mp3"))
            repo.savePin("custom:bell", "bell", true)

            repo.savePin("custom:bell", "bell", false)

            assertThat(
                repo.sounds
                    .first()
                    .single { it.id == "custom:bell" }
                    .isPinned,
            ).isFalse()
        }

    @Test
    fun `savePin survives across two reads of the flow`() =
        runTest {
            repo.save(testSound("bell", "bell.mp3"))
            repo.savePin("custom:bell", "bell", true)

            // First read
            assertThat(
                repo.sounds
                    .first()
                    .single { it.id == "custom:bell" }
                    .isPinned,
            ).isTrue()

            // Re-instantiating the repository hits the same DataStore (delegate is process-singleton).
            // True cold-restart coverage requires the manual Auto Backup smoke step (see plan).
            val repo2 = SoundsRepository(context)
            assertThat(
                repo2.sounds
                    .first()
                    .single { it.id == "custom:bell" }
                    .isPinned,
            ).isTrue()
        }

    @Test
    fun `durations is empty when no durations saved`() =
        runTest {
            assertThat(repo.durations.first()).isEmpty()
        }

    @Test
    fun `saveDuration then durations emits the saved duration keyed by id`() =
        runTest {
            repo.save(testSound("bell", "bell.mp3"))

            repo.saveDuration("custom:bell", "bell", 42_000)

            assertThat(repo.durations.first()).containsEntry("custom:bell", 42_000)
        }

    @Test
    fun `delete also removes the persisted duration`() =
        runTest {
            val sound = testSound("bell", "bell.mp3")
            repo.save(sound)
            repo.saveDuration("custom:bell", "bell", 42_000)
            val soundFile = getFile(context, "bell.mp3")
            soundFile.parentFile?.mkdirs()
            soundFile.createNewFile()

            repo.delete(sound)

            assertThat(repo.durations.first()).doesNotContainKey("custom:bell")
            assertThat(repo.sounds.first().any { it.id == "custom:bell" && !it.isBundled() }).isFalse()
        }

    @Test
    fun `rename preserves pinned and duration and keeps the stable id`() =
        runTest {
            repo.save(testSound("bell", "bell.mp3"))
            repo.savePin("custom:bell", "bell", true)
            repo.saveDuration("custom:bell", "bell", 99_999)

            repo.rename("custom:bell", "doorbell")

            val renamed = repo.sounds.first().single { it.id == "custom:bell" && !it.isBundled() }
            assertThat(renamed.name).isEqualTo("doorbell")
            assertThat(renamed.isPinned).isTrue()
            // Duration survives the rename because it is keyed by the stable id, not the name.
            assertThat(repo.durations.first()).containsEntry("custom:bell", 99_999)
            assertThat(repo.sounds.first().none { it.name == "bell" && !it.isBundled() }).isTrue()
        }

    @Test
    fun `concurrent saves do not lose data`() =
        runTest {
            val jobs =
                (1..20).map { i ->
                    async { repo.save(testSound("sound-$i", "sound-$i.mp3")) }
                }
            jobs.awaitAll()

            val customSounds = repo.sounds.first().filter { !it.isBundled() }
            assertThat(customSounds).hasSize(20)
            assertThat(customSounds.map { it.id }.toSet()).hasSize(20)
            assertThat(customSounds.map { it.name }.toSet())
                .isEqualTo((1..20).map { "sound-$it" }.toSet())
        }

    @Test(expected = IllegalArgumentException::class)
    fun `save with blank name throws`() =
        runTest {
            repo.save(testSound("", "bell.mp3"))
        }

    @Test(expected = IllegalArgumentException::class)
    fun `save with name longer than 200 chars throws`() =
        runTest {
            repo.save(testSound("x".repeat(201), "bell.mp3"))
        }

    @Test(expected = IllegalArgumentException::class)
    fun `rename with blank new name throws`() =
        runTest {
            repo.save(testSound("bell", "bell.mp3"))
            repo.rename("custom:bell", "")
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

    @Test
    fun `legacy JSON without an id field returns empty list and reports via onError`() =
        runTest {
            // Pre-stable-id schema: valid JSON, but StoredSound.id is now a required field.
            // No data migration ships (the app had no users) — the read degrades to an empty list.
            repo.setRawJsonForTest("""[{"name":"bell","file":"bell.mp3"}]""")

            val list = repo.sounds.first().filter { !it.isBundled() }

            assertThat(list).isEmpty()
            assertThat(recordedErrors).hasSize(1)
            assertThat(recordedErrors.single().message).contains("Malformed sounds_json")
        }
}
