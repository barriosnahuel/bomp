/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Activity-level contract for [AddButtonActivity]: a fresh [Intent] (initial via `onCreate` or
 * a subsequent `onNewIntent` while the task is alive) always replaces the current screen. Guards
 * against the regression where a share from another app, arriving while the user had a rename
 * (Edit mode) open without saving, would surface the abandoned Edit screen with the previous
 * Sound's data instead of starting a fresh Create flow for the incoming audio.
 *
 * Uses [Robolectric.buildActivity] (not `ActivityScenario`) because the production fix mutates
 * `Activity.getIntent()` via `setIntent(...)` inside `onNewIntent`. `ActivityScenario` matches
 * lifecycle callbacks against the launching intent to track which Activity it controls, and
 * silently drops events once the live intent diverges — `scenario.close()` then waits forever
 * for a DESTROYED transition that no longer reaches it. The lower-level controller has no such
 * coupling and lets us drive the lifecycle deterministically.
 */
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class AddButtonActivityIntentDispatchTest : AbstractRobolectricTest() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var fake: FakeAnalyticsTracker
    private lateinit var feature: FakeAddButtonFeature
    private var controller: ActivityController<AddButtonActivity>? = null

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        feature = FakeAddButtonFeature()
        AddButtonFeatureProvider.setForTest(feature)
    }

    @After
    fun tearDown() {
        controller?.let { runCatching { it.pause().stop().destroy() } }
        controller = null
        AnalyticsTrackerProvider.setForTest(null)
        AddButtonFeatureProvider.setForTest(null)
    }

    @Test
    fun `AddButtonActivity reaches RESUMED without crashing for an incoming share`() {
        val activity = launch(shareIntent(SAMPLE_URI))
        assertThat(activity.lifecycle.currentState).isEqualTo(Lifecycle.State.RESUMED)
    }

    @Test
    fun `share intent arriving while Edit is open replaces the screen with a fresh Create flow`() {
        // Reproduces the bug: user opens Edit (rename), goes Home without saving, then a share
        // from WhatsApp arrives. singleTask routes the new intent to the live instance via
        // onNewIntent — the contract is that the new ACTION_SEND ALWAYS wins, the abandoned
        // Edit state is discarded, and Activity.getIntent() now points at the share.
        val activity = launch(editIntent())
        assertThat(fake.screens.map { it.name }).contains(CanonicalScreenName.EDIT_SOUND)

        controller!!.newIntent(shareIntent(SAMPLE_URI))

        val addScreen =
            fake.screens.lastOrNull { it.name == CanonicalScreenName.ADD_SOUND }
                ?: error("onNewIntent did not log ADD_SOUND. Recorded: ${fake.screens.map { it.name }}")
        assertThat(addScreen.extras["source"]).isEqualTo(AddButtonActivity.SOURCE_SHARE)
        assertThat(activity.intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
            .isEqualTo(SAMPLE_URI)
        assertThat(activity.intent.getParcelableExtra(LandingActivity.EXTRA_EDIT_SOUND, Sound::class.java))
            .isNull()
        // The user transited Edit → Create; assert relative order, not absolute indices,
        // because other screen_view emissions from the surrounding Compose tree are allowed.
        val editIndex = fake.screens.indexOfFirst { it.name == CanonicalScreenName.EDIT_SOUND }
        val createIndex = fake.screens.indexOfLast { it.name == CanonicalScreenName.ADD_SOUND }
        assertThat(editIndex).isLessThan(createIndex)
    }

    @Test
    fun `share intent arriving while a previous Create is open re-emits screen_view for the new audio`() {
        // Symmetric scenario: two consecutive shares without finishing the first. Each share is
        // a fresh user intent, so analytics must record both and Activity.getIntent() must
        // reflect the most recent URI — otherwise a "stuck on first share" bug could hide later.
        val activity = launch(shareIntent(SAMPLE_URI))
        assertThat(fake.screens.count { it.name == CanonicalScreenName.ADD_SOUND }).isEqualTo(1)

        controller!!.newIntent(shareIntent(OTHER_URI))

        assertThat(fake.screens.count { it.name == CanonicalScreenName.ADD_SOUND }).isEqualTo(2)
        assertThat(activity.intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
            .isEqualTo(OTHER_URI)
    }

    @Test
    fun `Edit intent arriving while a Create share is open replaces the screen with Edit`() {
        // The fix is launchMode-agnostic: any new intent dispatched to the live instance refreshes
        // mode. Covers the (rarer) path where the user is in Create from a share and the host app
        // re-enters Edit via singleTask routing (e.g. internal navigation while the task is alive).
        val activity = launch(shareIntent(SAMPLE_URI))
        assertThat(fake.screens.map { it.name }).contains(CanonicalScreenName.ADD_SOUND)

        controller!!.newIntent(editIntent())

        assertThat(fake.screens.map { it.name }).contains(CanonicalScreenName.EDIT_SOUND)
        assertThat(activity.intent.getParcelableExtra(LandingActivity.EXTRA_EDIT_SOUND, Sound::class.java))
            .isNotNull()
    }

    @Test
    fun `malformed onNewIntent finishes the Activity even when a valid mode was alive`() {
        // Option A guard: the most recent intent always wins, including a malformed one. Without
        // this, a buggy share-receiver caller could leave the user stuck on a stale Edit screen
        // with no way to get to the rest of the app. Toast + finish is consistent with onCreate.
        val activity = launch(editIntent())
        val malformed = Intent(context, AddButtonActivity::class.java)

        controller!!.newIntent(malformed)

        assertThat(activity.isFinishing).isTrue()
    }

    /**
     * Build, attach and resume an [AddButtonActivity] with [intent]. Stashed in `controller` so
     * `@After` can drive it to DESTROYED, releasing the Looper-shared resources between tests.
     */
    private fun launch(intent: Intent): AddButtonActivity {
        val newController = Robolectric.buildActivity(AddButtonActivity::class.java, intent)
        controller = newController
        return newController
            .create()
            .start()
            .resume()
            .get()
    }

    private fun shareIntent(uri: Uri): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
        }

    private fun editIntent(): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            putExtra(LandingActivity.EXTRA_EDIT_SOUND, testSound(EXISTING_NAME, file = "existing.mp3"))
        }

    /**
     * Test double for [AddButtonFeature]. Same pattern as `AddButtonScreenAnalyticsTest` — returns
     * already-completed deferreds so the Compose tree does not race with the test.
     */
    private class FakeAddButtonFeature : AddButtonFeature {
        override fun saveNewButtonAsync(
            context: Context,
            name: String,
            uri: String,
            publicCollectionIds: Set<String>,
            privateCollectionIds: Set<String>,
        ): Deferred<Int> = CompletableDeferred(0)

        override fun renameButtonAsync(
            context: Context,
            sound: Sound,
            newName: String,
        ): Deferred<Unit> = CompletableDeferred(Unit)
    }

    private companion object {
        val SAMPLE_URI: Uri = Uri.parse("content://test/audio.mp3")
        val OTHER_URI: Uri = Uri.parse("content://test/other.mp3")
        const val EXISTING_NAME = "Existing sound"
    }
}
