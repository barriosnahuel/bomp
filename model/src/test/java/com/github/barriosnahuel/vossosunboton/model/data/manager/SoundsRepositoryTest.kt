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
    fun `replaceSyntheticCorpus sets exactly N with duration, can shrink, and preserves other sounds`() =
        runTest {
            repo.save(testSound("keep", "keep.mp3"))

            val three = (0 until 3).map { Sound("benchmark:$it", "Benchmark $it", "benchmark:$it.dat") }
            repo.replaceSyntheticCorpus("benchmark:", three, durationMs = 1234)

            assertThat(repo.sounds.first().filter { it.id.startsWith("benchmark:") }).hasSize(3)
            assertThat(repo.durations.first()["benchmark:0"]).isEqualTo(1234)

            // Shrink to 1 — what the disk-coupled delete() could not do for file-less synthetic rows.
            val one = listOf(Sound("benchmark:0", "Benchmark 0", "benchmark:0.dat"))
            repo.replaceSyntheticCorpus("benchmark:", one, durationMs = 1234)

            assertThat(repo.sounds.first().filter { it.id.startsWith("benchmark:") }).hasSize(1)
            assertThat(repo.sounds.first().any { it.id == "custom:keep" }).isTrue()
            // Cached durations of the dropped rows are gone too (durations derive from the stored list).
            assertThat(repo.durations.first().keys).containsExactly("benchmark:0")
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
    fun `legacy JSON without an id field recovers the custom sound by deriving a stable id`() =
        runTest {
            // Pre-stable-id schema (ADR 0008 → 0018): valid JSON, but StoredSound.id is now required.
            // A real user updated with this on disk; the migration derives id from name, recovering it.
            repo.setRawJsonForTest("""[{"name":"bell","file":"bell.mp3"}]""")

            val list = repo.sounds.first().filter { !it.isBundled() }

            assertThat(list).hasSize(1)
            assertThat(list.single().name).isEqualTo("bell")
            assertThat(list.single().file).isEqualTo("bell.mp3")
            // Recovery is graceful: not reported as a malformed-payload error.
            assertThat(recordedErrors).isEmpty()
        }

    @Test
    fun `legacy element with a blank id string is healed from its name`() =
        runTest {
            repo.setRawJsonForTest("""[{"id":"","name":"bell","file":"bell.mp3"}]""")

            val list = repo.sounds.first().filter { !it.isBundled() }

            assertThat(list.single().name).isEqualTo("bell")
            assertThat(recordedErrors).isEmpty()
        }

    @Test
    fun `non-array JSON root returns empty list and reports via onError`() =
        runTest {
            repo.setRawJsonForTest("""{"name":"bell","file":"bell.mp3"}""")

            val list = repo.sounds.first().filter { !it.isBundled() }

            assertThat(list).isEmpty()
            assertThat(recordedErrors).hasSize(1)
        }

    @Test
    fun `legacy element without id does not wipe sibling valid elements`() =
        runTest {
            repo.setRawJsonForTest(
                """[{"name":"old","file":"old.mp3"},{"id":"custom:new","name":"new","file":"new.mp3"}]""",
            )

            val list = repo.sounds.first().filter { !it.isBundled() }

            assertThat(list.map { it.name }).containsExactly("old", "new")
        }

    @Test
    fun `two legacy elements sharing a name collapse to one recovered sound keep-first`() =
        runTest {
            // Same derived id "custom:bell" for both — without de-dup this crashes id-keyed Compose
            // lists; keep-first drops the second, leaving the first ("a.mp3") addressable.
            repo.setRawJsonForTest(
                """[{"name":"bell","file":"a.mp3"},{"name":"bell","file":"b.mp3"}]""",
            )

            val list = repo.sounds.first().filter { !it.isBundled() }

            assertThat(list).hasSize(1)
            assertThat(list.single().id).isEqualTo("custom:bell")
            assertThat(list.single().file).isEqualTo("a.mp3")
        }

    @Test
    fun `legacy element preserves its persisted flags through the migration`() =
        runTest {
            repo.setRawJsonForTest(
                """[{"name":"bell","file":"bell.mp3","isPinned":true,"isFavorite":true}]""",
            )

            val recovered = repo.sounds.first().first { it.name == "bell" }

            assertThat(recovered.isPinned).isTrue()
            assertThat(recovered.isFavorite).isTrue()
        }

    @Test
    fun `recovered legacy sound is addressable by its derived id on the next write`() =
        runTest {
            repo.setRawJsonForTest("""[{"name":"bell","file":"bell.mp3"}]""")

            repo.rename("custom:bell", "doorbell")

            val list = repo.sounds.first().filter { !it.isBundled() }
            assertThat(list.single().name).isEqualTo("doorbell")
        }

    @Test
    fun `a non-object array element is dropped without wiping valid siblings`() =
        runTest {
            repo.setRawJsonForTest("""[5,{"id":"custom:keep","name":"keep","file":"keep.mp3"}]""")

            val list = repo.sounds.first().filter { !it.isBundled() }

            assertThat(list.map { it.name }).containsExactly("keep")
            assertThat(recordedErrors).isEmpty()
        }

    @Test
    fun `an element with a valid id but missing the required name is dropped without wiping siblings`() =
        runTest {
            repo.setRawJsonForTest(
                """[{"id":"custom:x","file":"x.mp3"},{"id":"custom:keep","name":"keep","file":"keep.mp3"}]""",
            )

            val list = repo.sounds.first().filter { !it.isBundled() }

            assertThat(list.map { it.name }).containsExactly("keep")
        }

    @Test
    fun `onLegacyRecovery fires when a legacy payload is recovered`() =
        runTest {
            var recoveries = 0
            val observed =
                SoundsRepository(context, onError = { recordedErrors += it }, onLegacyRecovery = { recoveries++ })
            observed.setRawJsonForTest("""[{"name":"bell","file":"bell.mp3"}]""")

            observed.sounds.first()

            assertThat(recoveries).isAtLeast(1)
        }

    @Test
    fun `onLegacyRecovery does not fire for a clean modern payload`() =
        runTest {
            var recoveries = 0
            val observed =
                SoundsRepository(context, onError = { recordedErrors += it }, onLegacyRecovery = { recoveries++ })
            observed.save(testSound("bell", "bell.mp3"))

            observed.sounds.first()

            assertThat(recoveries).isEqualTo(0)
        }

    @Test
    fun `legacy element missing both id and name is dropped without wiping the list`() =
        runTest {
            repo.setRawJsonForTest(
                """[{"file":"orphan.mp3"},{"id":"custom:keep","name":"keep","file":"keep.mp3"}]""",
            )

            val list = repo.sounds.first().filter { !it.isBundled() }

            assertThat(list.map { it.name }).containsExactly("keep")
            assertThat(recordedErrors).isEmpty()
        }

    // ── isVisibleInMySounds (ADR 0012) ──────────────────────────────────────────────────────

    @Test
    fun `a new custom sound defaults to visible in My Sounds`() =
        runTest {
            repo.save(testSound("bell", "bell.mp3"))

            assertThat(
                repo.sounds
                    .first()
                    .first { it.id == "custom:bell" }
                    .isVisibleInMySounds,
            ).isTrue()
        }

    @Test
    fun `saveVisibility toggles the My Sounds visibility flag`() =
        runTest {
            repo.save(testSound("bell", "bell.mp3"))

            repo.saveVisibility("custom:bell", "bell", isVisibleInMySounds = false)

            assertThat(
                repo.sounds
                    .first()
                    .first { it.id == "custom:bell" }
                    .isVisibleInMySounds,
            ).isFalse()
        }

    @Test
    fun `save does not reset a previously hidden audio back to visible`() =
        runTest {
            repo.save(testSound("bell", "bell.mp3"))
            repo.saveVisibility("custom:bell", "bell", isVisibleInMySounds = false)

            // A generic re-save must not clobber the visibility owned by saveVisibility.
            repo.save(testSound("bell", "bell.mp3"))

            assertThat(
                repo.sounds
                    .first()
                    .first { it.id == "custom:bell" }
                    .isVisibleInMySounds,
            ).isFalse()
        }

    @Test
    fun `migrateVisibilityIfNeeded hides only the private-only ids`() =
        runTest {
            repo.save(testSound("pub", "pub.mp3"))
            repo.save(testSound("priv", "priv.mp3"))

            repo.migrateVisibilityIfNeeded(privateOnlyIds = setOf("custom:priv"))

            val byId = repo.sounds.first().associateBy { it.id }
            assertThat(byId.getValue("custom:priv").isVisibleInMySounds).isFalse()
            assertThat(byId.getValue("custom:pub").isVisibleInMySounds).isTrue()
        }

    @Test
    fun `migrateVisibilityIfNeeded runs at most once so a re-shown audio is not re-hidden`() =
        runTest {
            repo.save(testSound("priv", "priv.mp3"))
            repo.migrateVisibilityIfNeeded(privateOnlyIds = setOf("custom:priv"))
            // User later chooses to show it in My Sounds again.
            repo.saveVisibility("custom:priv", "priv", isVisibleInMySounds = true)

            // A second migration pass (next launch) must NOT re-hide it — the one-shot guard wins.
            repo.migrateVisibilityIfNeeded(privateOnlyIds = setOf("custom:priv"))

            assertThat(
                repo.sounds
                    .first()
                    .first { it.id == "custom:priv" }
                    .isVisibleInMySounds,
            ).isTrue()
        }
}
