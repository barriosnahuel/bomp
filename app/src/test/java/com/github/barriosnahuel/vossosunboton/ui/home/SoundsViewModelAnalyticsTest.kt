/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsUserProperty
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class SoundsViewModelAnalyticsTest : AbstractRobolectricTest() {
    private lateinit var fake: FakeAnalyticsTracker
    private val createdViewModels = mutableListOf<SoundsViewModel>()

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        runBlocking {
            SoundsRepository(ApplicationProvider.getApplicationContext()).clearForTest()
            WelcomeStickerStore(ApplicationProvider.getApplicationContext()).clearForTest()
            com.github.barriosnahuel.vossosunboton.model.data.manager
                .CollectionsRepository(ApplicationProvider.getApplicationContext())
                .clearForTest()
            com.github.barriosnahuel.vossosunboton.feature.collections
                .MySoundsFilterStore(ApplicationProvider.getApplicationContext())
                .clearForTest()
            com.github.barriosnahuel.vossosunboton.feature.vault
                .VaultFilterStore(ApplicationProvider.getApplicationContext())
                .clearForTest()
            com.github.barriosnahuel.vossosunboton.feature.collections
                .DualHomeCoachStore(ApplicationProvider.getApplicationContext())
                .clearForTest()
        }
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.startPlayingSound(any(), any()) } answers { nothing }
        every { PlayerControllerFactory.instance.pause() } answers { nothing }
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        // Deterministically stop the reactive `repo.sounds` collector each VM starts in `init`
        // (post-PR-#1130 fix). A bare `cancel()` is fire-and-forget: the collector can outlive the
        // test, parked on the process-singleton DataStore, and the next test's `clearForTest()` /
        // `save(...)` writes emit through it — leaking events (`milestone_sounds_3`,
        // `search_zero_results`) into the new test's `fake`. `cancelAndJoinAll()` joins until it
        // unwinds — see ViewModelTestCleanup.kt.
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    @Test
    fun `togglePin emits pin_toggle with the resulting pinned state`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", file = "test.mp3")
        viewModel.injectSounds(listOf(sound))

        viewModel.togglePin(sound)

        val event = fake.assertEmitted("pin_toggle")
        assertThat(event.params["pinned"]).isEqualTo(true)
    }

    @Test
    fun `setAudioVisibleInMySounds emits visibility_toggle with the resulting state`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", file = "test.mp3")
        viewModel.injectSounds(listOf(sound))

        viewModel.setAudioVisibleInMySounds(sound.id, sound.name, visible = false)

        val event = fake.assertEmitted("visibility_toggle")
        assertThat(event.params["visible"]).isEqualTo(false)
    }

    @Test
    fun `playOrStop emits sound_play with surface = my_sounds when home tab is active`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", null, 0, isPlaying = false)

        viewModel.playOrStop(sound)

        val event = fake.assertEmitted("sound_play")
        assertThat(event.params["surface"]).isEqualTo(CanonicalScreenName.MY_SOUNDS)
    }

    @Test
    fun `playOrStop emits sound_play with surface = explore_sounds when explore tab is active`() {
        val viewModel = givenAViewModel()
        viewModel.selectTab(AppTab.EXPLORE_SOUNDS)
        val sound = testSound("test", null, 0, isPlaying = false)

        viewModel.playOrStop(sound)

        val event = fake.assertEmitted("sound_play")
        assertThat(event.params["surface"]).isEqualTo(CanonicalScreenName.EXPLORE_SOUNDS)
    }

    @Test
    fun `playOrStop emits sound_play with surface = search_sound while search overlay is visible`() {
        val viewModel = givenAViewModel()
        viewModel.showSearch()
        val sound = testSound("test", null, 0, isPlaying = false)

        viewModel.playOrStop(sound)

        val event = fake.assertEmitted("sound_play")
        assertThat(event.params["surface"]).isEqualTo(CanonicalScreenName.SEARCH_SOUND)
    }

    @Test
    fun `playOrStop increments lifetime_plays user property monotonically`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", null, 0, isPlaying = false)

        viewModel.playOrStop(sound)
        viewModel.playOrStop(sound)

        assertThat(fake.userProperties[AnalyticsUserProperty.LIFETIME_PLAYS]).isEqualTo("2")
    }

    @Test
    fun `playOrStop on a playing sound does not emit sound_play`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", null, 1, isPlaying = true)

        viewModel.playOrStop(sound)

        fake.assertNotEmitted("sound_play")
    }

    @Test
    fun `confirmDelete emits sound_delete after the repo commit`() {
        val viewModel = givenAViewModel()
        val sound = testSound("custom", "custom.mp3", 0, isPlaying = false)
        viewModel.injectSounds(listOf(sound))
        viewModel.deleteSound(sound)

        viewModel.confirmDelete()
        // confirmDelete launches a coroutine on viewModelScope+ioDispatcher; the analytics
        // event is logged after `repo.delete` returns. Poll the fake until it is recorded.
        runBlocking { awaitAnalyticsEvent(fake, "sound_delete") }

        fake.assertEmitted("sound_delete")
    }

    @Test
    fun `confirmDelete on a bundled sound does not emit sound_delete`() {
        val viewModel = givenAViewModel()
        val bundled = testSound("bundled", rawRes = 1)
        viewModel.injectSounds(listOf(bundled))
        viewModel.deleteSound(bundled)

        viewModel.confirmDelete()

        fake.assertNotEmitted("sound_delete")
    }

    @Test
    fun `restoreSound emits sound_delete_undone and never sound_delete`() {
        val viewModel = givenAViewModel()
        val sound = testSound("custom", "custom.mp3", 0, isPlaying = false)
        viewModel.injectSounds(listOf(sound))
        viewModel.deleteSound(sound)

        viewModel.restoreSound()

        fake.assertEmitted("sound_delete_undone")
        fake.assertNotEmitted("sound_delete")
    }

    @Test
    fun `togglePin updates current_pinned user property to the count of pinned sounds`() {
        val viewModel = givenAViewModel()
        val sound = testSound("test", file = "test.mp3")
        // Persist + await the audio reaching allSoundsCache via the reactive loadSounds BEFORE
        // toggling. togglePin derives current_pinned from allSoundsCache (SoundsViewModel.kt); a
        // not-yet-arrived loadSounds on a loaded CI machine resets that cache to the empty repo, so
        // the toggle counts 0 and the assertion flakes (CLAUDE.md § JVM tests — await every async
        // input). Mirrors the Vault-bucket tests below; replaces the reflection-injection race.
        runBlocking {
            SoundsRepository(ApplicationProvider.getApplicationContext()).save(sound)
            viewModel.library.first { lib -> lib.any { it.id == sound.id } }
        }

        viewModel.togglePin(sound)
        // Await the asserted property itself, not a proxy: savePin re-emits through the reactive
        // collector and recomputes current_pinned, converging on "1" once the toggle is committed.
        runBlocking { awaitUserProperty(fake, AnalyticsUserProperty.CURRENT_PINNED, "1") }

        assertThat(fake.userProperties[AnalyticsUserProperty.CURRENT_PINNED]).isEqualTo("1")
    }

    @Test
    fun `loadSounds emits milestone_sounds_3 when crossing the threshold for the first time`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repeat(3) { idx -> repo.save(testSound("custom-$idx", "custom-$idx.mp3")) }
        }

        givenAViewModel()

        fake.assertEmitted("milestone_sounds_3")
    }

    @Test
    fun `loadSounds does not re-emit milestone_sounds_3 once already fired`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking {
            val repo = SoundsRepository(context)
            repeat(3) { idx -> repo.save(testSound("custom-$idx", "custom-$idx.mp3")) }
        }
        givenAViewModel()
        // Clear only the recorded events, NOT the fired flags — simulates the same install loading the VM again.
        fake.events.clear()

        givenAViewModel()

        fake.assertNotEmitted("milestone_sounds_3")
    }

    @Test
    fun `cancelAndJoinAll fully stops a ViewModel repo collector so it cannot pollute a later test`() {
        // Build a VM, then tear it down the way @After does — but assert the contract a bare
        // `viewModelScope.cancel()` does NOT give: the scope's Job is actually completed.
        val vm = givenAViewModel()
        createdViewModels.remove(vm) // this test owns the teardown; keep @After from double-cancelling

        listOf(vm).cancelAndJoinAll()

        // Deterministic regression anchor: `cancelAndJoin` waits for the full unwind, so the Job
        // is completed. The old fire-and-forget `cancel()` left the `repo.sounds` collector parked
        // on the singleton DataStore with the Job lingering in "cancelling".
        val job = vm.viewModelScope.coroutineContext.job
        assertThat(job.isActive).isFalse()
        assertThat(job.isCompleted).isTrue()

        // Behavioural check: a torn-down VM's collector must not react to later DataStore writes.
        // Without the join, these `save(...)` calls would emit through the leaked collector and
        // re-fire `milestone_sounds_3` into the (freshly reset) tracker.
        fake.reset()
        runBlocking {
            val repo = SoundsRepository(ApplicationProvider.getApplicationContext())
            repeat(3) { idx -> repo.save(testSound("leak-$idx", "leak-$idx.mp3")) }
        }
        fake.assertNotEmitted("milestone_sounds_3")
    }

    @Test
    fun `search emits search_zero_results with the query length when there is no match`() {
        val viewModel = givenAViewModel(searchDebounceMs = 0L)
        viewModel.injectSounds(listOf(testSound("alpha", "a.mp3"), testSound("beta", "b.mp3")))

        viewModel.onSearchQueryChange("zzzz")

        val event = fake.assertEmitted("search_zero_results")
        assertThat(event.params["query_length"]).isEqualTo(4)
    }

    @Test
    fun `search does not emit search_zero_results when there is at least one match`() {
        val viewModel = givenAViewModel(searchDebounceMs = 0L)
        viewModel.injectSounds(listOf(testSound("alpha", "a.mp3"), testSound("beta", "b.mp3")))

        viewModel.onSearchQueryChange("alp")

        fake.assertNotEmitted("search_zero_results")
    }

    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectSounds(sounds: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("_sounds")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = sounds }
        SoundsViewModel::class.java
            .getDeclaredField("allSoundsCache")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = sounds }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun givenAViewModel(searchDebounceMs: Long = 200L): SoundsViewModel {
        val vm =
            SoundsViewModel(
                ApplicationProvider.getApplicationContext(),
                ioDispatcher = UnconfinedTestDispatcher(),
                searchDebounceMs = searchDebounceMs,
            )
        createdViewModels += vm
        runBlocking {
            vm.isInitialLoadComplete.first { it }
            // Same yield pattern as SoundsViewModelTest: let the collections observer's first
            // (system-Baúl-seed) emission and any auxiliary collectors settle before the test
            // mutates state via reflection. Without this the observer can fire a `loadSounds()`
            // mid-test and overwrite user properties (`current_pinned`) the action under test
            // just set.
            delay(50)
        }
        return vm
    }

    /**
     * Polls [fake] every 25 ms until [eventName] appears, with a 5-second cap so a regression
     * never hangs the test runner. `FakeAnalyticsTracker` has no built-in suspend API and
     * adding one just for this case felt heavier than this five-line helper.
     */
    private suspend fun awaitAnalyticsEvent(
        fake: FakeAnalyticsTracker,
        eventName: String,
    ) {
        withTimeout(5_000L) {
            // Snapshot via `toList()` so we don't iterate a list that another coroutine (e.g. the
            // VM's init coroutine emitting WelcomeStickerShown from IO) may be appending to.
            while (fake.events.toList().none { it.name == eventName }) {
                delay(25L)
            }
        }
    }

    /**
     * Polls [fake] every 25 ms until user property [name] equals [expected], 5-second cap. Use this
     * instead of awaiting a *proxy* (the collections StateFlow, the toggle event) when the assertion
     * reads a bucket user property: the collections observer sets `_collections.value` BEFORE it runs
     * `syncAudioBuckets` (which writes the bucket properties) in the same pass, so a test awaiting the
     * collection update can resume — under `UnconfinedTestDispatcher` — and race ahead of the property
     * write. Awaiting the asserted value itself closes that gap (CLAUDE.md § JVM tests).
     */
    private suspend fun awaitUserProperty(
        fake: FakeAnalyticsTracker,
        name: String,
        expected: String,
    ) {
        withTimeout(5_000L) {
            while (fake.userProperties[name] != expected) {
                delay(25L)
            }
        }
    }

    @Test
    fun `welcome_sticker_shown is logged once on first VM init when flag is active`() {
        givenAViewModel()

        fake.assertEmitted("welcome_sticker_shown")
    }

    @Test
    fun `welcome_sticker_shown does not re-fire on a second VM init`() {
        givenAViewModel()
        fake.events.clear()

        givenAViewModel()

        fake.assertNotEmitted("welcome_sticker_shown")
    }

    @Test
    fun `playOrStop on welcome logs welcome_sticker_play instead of sound_play`() {
        val viewModel = givenAViewModel()
        val welcomeTitle =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.app_welcome_sticker_title)
        val welcome = viewModel.sounds.value.first { it.name == welcomeTitle }

        viewModel.playOrStop(welcome)

        fake.assertEmitted("welcome_sticker_play")
        fake.assertNotEmitted("sound_play")
    }

    @Test
    fun `onPlayerStop completed=true on welcome logs welcome_sticker_completed once`() {
        val viewModel = givenAViewModel()
        val welcomeTitle =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.app_welcome_sticker_title)
        val welcome = viewModel.sounds.value.first { it.name == welcomeTitle }

        viewModel.onPlayerStop(welcome, completed = true)
        // Await the info event the gated coroutine emits right after logging, so the first completion
        // has fully landed before we replay.
        runBlocking { withTimeout(2_000L) { viewModel.welcomeInfoEvent.first() } }
        // A replay completing again must NOT re-log it — the `acknowledged` flag gates it to once.
        viewModel.onPlayerStop(welcome, completed = true)

        assertThat(fake.events.count { it.name == "welcome_sticker_completed" }).isEqualTo(1)
    }

    @Test
    fun `restoreSound on welcome logs the generic sound_delete_undone`() {
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
        val viewModel = givenAViewModel()
        val welcomeTitle =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.app_welcome_sticker_title)
        val welcome = viewModel.sounds.value.first { it.name == welcomeTitle }
        // Manual delete (swipe) is the only path that enqueues a delete event now that completion no
        // longer self-destructs the welcome.
        viewModel.deleteSound(welcome)
        fake.events.clear()

        viewModel.restoreSound()

        // The welcome-specific `welcome_sticker_undone` was pruned: the welcome
        // is just-another-audio, so its Undo emits the generic event like any other sound.
        fake.assertEmitted("sound_delete_undone")
    }

    @Test
    fun `deleteSound on welcome logs welcome_sticker_dismissed`() {
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
        val viewModel = givenAViewModel()
        val welcomeTitle =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.app_welcome_sticker_title)
        val welcome = viewModel.sounds.value.first { it.name == welcomeTitle }

        viewModel.deleteSound(welcome)

        fake.assertEmitted("welcome_sticker_dismissed")
    }

    @Test
    fun `deleteSound on a custom sound does NOT log welcome_sticker_dismissed`() {
        every { PlayerControllerFactory.instance.forgetSound(any()) } answers { nothing }
        val viewModel = givenAViewModel()
        val sound = testSound("custom", "custom.mp3", 0, isPlaying = false)
        viewModel.injectSounds(listOf(sound))

        viewModel.deleteSound(sound)

        fake.assertNotEmitted("welcome_sticker_dismissed")
    }

    // region Collections analytics

    @Test
    fun `creating a public collection emits CollectionCreate with scope and bumps lifetime + snapshot counters`() {
        val viewModel = givenAViewModel()

        runBlocking {
            viewModel.createCollection(
                "Familia",
                com.github.barriosnahuel.vossosunboton.model.CollectionAccess.PUBLIC,
                source = "manage",
            )
        }
        runBlocking { awaitAnalyticsEvent(fake, "collection_create") }
        runBlocking { viewModel.collections.first { col -> col.any { it.name == "Familia" } } }

        val event = fake.assertEmitted("collection_create")
        assertThat(event.params["scope"]).isEqualTo("public")
        assertThat(event.params["audios"]).isEqualTo(0)
        assertThat(event.params["source"]).isEqualTo("manage")
        assertThat(fake.userProperties[AnalyticsUserProperty.LIFETIME_COLLECTION_CREATES]).isEqualTo("1")
        assertThat(fake.userProperties[AnalyticsUserProperty.CURRENT_COLLECTIONS_PUBLIC]).isEqualTo("1")
    }

    @Test
    fun `creating a private collection bumps CURRENT_COLLECTIONS_PRIVATE not PUBLIC`() {
        val viewModel = givenAViewModel()

        runBlocking {
            viewModel.createCollection(
                "Caro",
                com.github.barriosnahuel.vossosunboton.model.CollectionAccess.PRIVATE,
                source = "manage",
            )
        }
        runBlocking { awaitAnalyticsEvent(fake, "collection_create") }
        runBlocking { viewModel.collections.first { col -> col.any { it.name == "Caro" } } }

        val event = fake.assertEmitted("collection_create")
        assertThat(event.params["scope"]).isEqualTo("private")
        assertThat(fake.userProperties[AnalyticsUserProperty.CURRENT_COLLECTIONS_PRIVATE]).isEqualTo("1")
        assertThat(fake.userProperties[AnalyticsUserProperty.CURRENT_COLLECTIONS_PUBLIC]).isEqualTo("0")
    }

    @Test
    fun `renaming a collection emits CollectionRename with scope`() {
        val viewModel = givenAViewModel()
        val created =
            runBlocking {
                viewModel
                    .createCollection(
                        "Trabajo",
                        com.github.barriosnahuel.vossosunboton.model.CollectionAccess.PUBLIC,
                        source = "manage",
                    ).getOrThrow()
            }
        runBlocking { viewModel.collections.first { col -> col.any { it.id == created.id } } }

        runBlocking { viewModel.renameCollection(created.id, "Oficina") }
        runBlocking { awaitAnalyticsEvent(fake, "collection_rename") }

        val event = fake.assertEmitted("collection_rename")
        assertThat(event.params["scope"]).isEqualTo("public")
        assertThat(fake.userProperties[AnalyticsUserProperty.LIFETIME_COLLECTION_RENAMES]).isEqualTo("1")
    }

    @Test
    fun `deleting a collection emits CollectionDelete with scope and audio count`() {
        val viewModel = givenAViewModel()
        val sound = testSound("a", "a.mp3")
        runBlocking {
            SoundsRepository(ApplicationProvider.getApplicationContext()).save(sound)
        }
        val created =
            runBlocking {
                viewModel
                    .createCollection(
                        "Recetas",
                        com.github.barriosnahuel.vossosunboton.model.CollectionAccess.PUBLIC,
                        source = "manage",
                    ).getOrThrow()
            }
        runBlocking { viewModel.collections.first { col -> col.any { it.id == created.id } } }
        viewModel.toggleAudioInCollection(sound.id, created.id)
        runBlocking { viewModel.collections.first { col -> col.first { it.id == created.id }.audioIds.size == 1 } }

        runBlocking { viewModel.deleteCollection(created.id) }
        runBlocking { awaitAnalyticsEvent(fake, "collection_delete") }

        val event = fake.assertEmitted("collection_delete")
        assertThat(event.params["scope"]).isEqualTo("public")
        assertThat(event.params["audios"]).isEqualTo(1)
        assertThat(fake.userProperties[AnalyticsUserProperty.LIFETIME_COLLECTION_DELETES]).isEqualTo("1")
    }

    @Test
    fun `toggleAudioInCollection assign emits CollectionAudioToggle and bumps lifetime + audios_in_collections`() {
        val viewModel = givenAViewModel()
        val sound = testSound("first", "first.mp3")
        runBlocking {
            SoundsRepository(ApplicationProvider.getApplicationContext()).save(sound)
        }
        val created =
            runBlocking {
                viewModel
                    .createCollection(
                        "Familia",
                        com.github.barriosnahuel.vossosunboton.model.CollectionAccess.PUBLIC,
                        source = "manage",
                    ).getOrThrow()
            }
        runBlocking { viewModel.collections.first { col -> col.any { it.id == created.id } } }

        viewModel.toggleAudioInCollection(sound.id, created.id)
        runBlocking { awaitAnalyticsEvent(fake, "collection_audio_toggle") }
        runBlocking { viewModel.collections.first { col -> col.first { it.id == created.id }.audioIds.contains(sound.id) } }

        val event = fake.assertEmitted("collection_audio_toggle")
        assertThat(event.params["assigned"]).isEqualTo(true)
        assertThat(event.params["scope"]).isEqualTo("public")
        assertThat(fake.userProperties[AnalyticsUserProperty.LIFETIME_COLLECTION_ASSIGNS]).isEqualTo("1")
        assertThat(fake.userProperties[AnalyticsUserProperty.CURRENT_AUDIOS_IN_COLLECTIONS]).isEqualTo("1")
    }

    @Test
    fun `toggleAudioInCollection unassign emits CollectionAudioToggle with assigned false`() {
        val viewModel = givenAViewModel()
        val sound = testSound("second", "second.mp3")
        runBlocking {
            SoundsRepository(ApplicationProvider.getApplicationContext()).save(sound)
        }
        val created =
            runBlocking {
                viewModel
                    .createCollection(
                        "Trabajo",
                        com.github.barriosnahuel.vossosunboton.model.CollectionAccess.PUBLIC,
                        source = "manage",
                    ).getOrThrow()
            }
        runBlocking { viewModel.collections.first { col -> col.any { it.id == created.id } } }
        // First toggle: assign. Wait for it to settle so the second toggle reads the right state.
        viewModel.toggleAudioInCollection(sound.id, created.id)
        runBlocking { viewModel.collections.first { col -> col.first { it.id == created.id }.audioIds.contains(sound.id) } }
        fake.events.clear()

        viewModel.toggleAudioInCollection(sound.id, created.id)
        runBlocking { awaitAnalyticsEvent(fake, "collection_audio_toggle") }

        val event = fake.assertEmitted("collection_audio_toggle")
        assertThat(event.params["assigned"]).isEqualTo(false)
    }

    @Test
    fun `applyAssignment emits CollectionAudioToggle for a staged add and bumps lifetime`() {
        val viewModel = givenAViewModel()
        val sound = testSound("filed", "filed.mp3")
        runBlocking {
            SoundsRepository(ApplicationProvider.getApplicationContext()).save(sound)
        }
        val created =
            runBlocking {
                viewModel
                    .createCollection(
                        "Recetas",
                        com.github.barriosnahuel.vossosunboton.model.CollectionAccess.PUBLIC,
                        source = "manage",
                    ).getOrThrow()
            }
        runBlocking { viewModel.collections.first { col -> col.any { it.id == created.id } } }

        // Transactional commit ("Listo") with one staged public tag.
        viewModel.applyAssignment(
            audioId = sound.id,
            name = sound.name,
            publicCollectionIds = setOf(created.id),
            privateCollectionIds = emptySet(),
            isVisibleInMySounds = true,
        )
        runBlocking { awaitAnalyticsEvent(fake, "collection_audio_toggle") }
        runBlocking { viewModel.collections.first { col -> col.first { it.id == created.id }.audioIds.contains(sound.id) } }

        val event = fake.assertEmitted("collection_audio_toggle")
        assertThat(event.params["assigned"]).isEqualTo(true)
        assertThat(event.params["scope"]).isEqualTo("public")
        assertThat(fake.userProperties[AnalyticsUserProperty.LIFETIME_COLLECTION_ASSIGNS]).isEqualTo("1")
    }

    @Test
    fun `selectMySoundsFilter emits collection_filter_apply with scope public`() {
        val viewModel = givenAViewModel()
        val created =
            runBlocking {
                viewModel
                    .createCollection(
                        "Familia",
                        com.github.barriosnahuel.vossosunboton.model.CollectionAccess.PUBLIC,
                        source = "manage",
                    ).getOrThrow()
            }
        runBlocking { viewModel.collections.first { col -> col.any { it.id == created.id } } }
        fake.events.clear()

        viewModel.selectMySoundsFilter(created.id)
        runBlocking { awaitAnalyticsEvent(fake, "collection_filter_apply") }

        val event = fake.assertEmitted("collection_filter_apply")
        assertThat(event.params["scope"]).isEqualTo("public")
    }

    @Test
    fun `selectVaultFilter emits collection_filter_apply with scope private`() {
        val viewModel = givenAViewModel()
        val created =
            runBlocking {
                viewModel
                    .createCollection(
                        "Caro",
                        com.github.barriosnahuel.vossosunboton.model.CollectionAccess.PRIVATE,
                        source = "manage",
                    ).getOrThrow()
            }
        runBlocking { viewModel.collections.first { col -> col.any { it.id == created.id } } }
        fake.events.clear()

        viewModel.selectVaultFilter(created.id)
        runBlocking { awaitAnalyticsEvent(fake, "collection_filter_apply") }

        val event = fake.assertEmitted("collection_filter_apply")
        assertThat(event.params["scope"]).isEqualTo("private")
    }

    @Test
    fun `tagging an audio to a custom private collection counts it as current_vault_custom`() {
        val viewModel = givenAViewModel()
        val sound = testSound("c", "c.mp3")
        runBlocking {
            SoundsRepository(ApplicationProvider.getApplicationContext()).save(sound)
        }
        // Wait for the saved audio to reach allSoundsCache (via the reactive loadSounds) BEFORE
        // tagging — syncAudioBuckets iterates that cache, so a not-yet-loaded audio counts as 0.
        runBlocking { viewModel.library.first { lib -> lib.any { it.id == sound.id } } }
        val created =
            runBlocking {
                viewModel
                    .createCollection(
                        "Secretos",
                        com.github.barriosnahuel.vossosunboton.model.CollectionAccess.PRIVATE,
                        source = "manage",
                    ).getOrThrow()
            }
        runBlocking { viewModel.collections.first { col -> col.any { it.id == created.id } } }

        viewModel.toggleAudioInCollection(sound.id, created.id)
        // Await the asserted property itself, not a proxy: `syncAudioBuckets` writes it AFTER the
        // `_collections` emit in the same observer pass, so awaiting the collection update would
        // race ahead of the write under UnconfinedTestDispatcher.
        runBlocking { awaitUserProperty(fake, AnalyticsUserProperty.CURRENT_VAULT_CUSTOM, "1") }

        assertThat(fake.userProperties[AnalyticsUserProperty.CURRENT_VAULT_CUSTOM]).isEqualTo("1")
        assertThat(fake.userProperties[AnalyticsUserProperty.CURRENT_VAULT_DEFAULT]).isEqualTo("0")
    }

    @Test
    fun `tagging an audio only to the system Baul counts it as current_vault_default`() {
        val viewModel = givenAViewModel()
        val sound = testSound("c", "c.mp3")
        runBlocking {
            SoundsRepository(ApplicationProvider.getApplicationContext()).save(sound)
        }
        // Wait for the saved audio to reach allSoundsCache (via the reactive loadSounds) BEFORE
        // tagging — syncAudioBuckets iterates that cache, so a not-yet-loaded audio counts as 0.
        runBlocking { viewModel.library.first { lib -> lib.any { it.id == sound.id } } }
        runBlocking { viewModel.collections.first { cols -> cols.any { it.isSystem } } }
        val baul = viewModel.collections.value.first { it.isSystem }

        viewModel.toggleAudioInCollection(sound.id, baul.id)
        // Await the asserted property itself, not a proxy: `syncAudioBuckets` writes it AFTER the
        // `_collections` emit in the same observer pass, so awaiting the collection update would
        // race ahead of the write under UnconfinedTestDispatcher.
        runBlocking { awaitUserProperty(fake, AnalyticsUserProperty.CURRENT_VAULT_DEFAULT, "1") }

        assertThat(fake.userProperties[AnalyticsUserProperty.CURRENT_VAULT_DEFAULT]).isEqualTo("1")
        assertThat(fake.userProperties[AnalyticsUserProperty.CURRENT_VAULT_CUSTOM]).isEqualTo("0")
    }

    @Test
    fun `untagged audios count as public_default and publicly-filed ones as public_custom`() {
        val viewModel = givenAViewModel()
        val filed = testSound("filed", "filed.mp3")
        val untagged = testSound("untagged", "untagged.mp3")
        runBlocking {
            val repo = SoundsRepository(ApplicationProvider.getApplicationContext())
            repo.save(filed)
            repo.save(untagged)
        }
        runBlocking { viewModel.library.first { lib -> lib.count { !it.isBundled() } >= 2 } }
        val created =
            runBlocking {
                viewModel
                    .createCollection(
                        "Familia",
                        com.github.barriosnahuel.vossosunboton.model.CollectionAccess.PUBLIC,
                        source = "manage",
                    ).getOrThrow()
            }
        runBlocking { viewModel.collections.first { col -> col.any { it.id == created.id } } }

        viewModel.toggleAudioInCollection(filed.id, created.id)
        // Await the asserted property itself, not a proxy: `syncAudioBuckets` writes it AFTER the
        // `_collections` emit in the same observer pass, so awaiting the collection update would
        // race ahead of the write under UnconfinedTestDispatcher.
        runBlocking { awaitUserProperty(fake, AnalyticsUserProperty.CURRENT_PUBLIC_CUSTOM, "1") }

        assertThat(fake.userProperties[AnalyticsUserProperty.CURRENT_PUBLIC_CUSTOM]).isEqualTo("1")
        assertThat(fake.userProperties[AnalyticsUserProperty.CURRENT_PUBLIC_DEFAULT]).isEqualTo("1")
    }

    @Test
    fun `trackCollectionView emits collection_view with the scope`() {
        val viewModel = givenAViewModel()

        viewModel.trackCollectionView(isPublic = false)
        runBlocking { awaitAnalyticsEvent(fake, "collection_view") }

        val event = fake.assertEmitted("collection_view")
        assertThat(event.params["scope"]).isEqualTo("private")
    }

    // endregion
}
