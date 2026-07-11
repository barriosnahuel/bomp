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
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.SoundSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Activity-level contract for [AddButtonActivity], now the share-sheet trampoline only (ADR 0024 D4):
 * an external `ACTION_SEND` carrying an audio [Uri] in `Intent.EXTRA_STREAM` opens a Create flow, and
 * anything without that stream is rejected with a toast + `finish()` instead of stranding the user on
 * an empty screen. A second share dispatched to the live instance via `onNewIntent` always wins — even
 * when it carries the very same audio — because each intent is a fresh user intent that must re-emit
 * `screen_view` (the `intentSequence` contract). Every internal creation/edit flow lives in Landing's
 * graph and is covered there.
 *
 * Uses [Robolectric.buildActivity] (not `ActivityScenario`) because the Activity mutates
 * `Activity.getIntent()` via `setIntent(...)` inside `onNewIntent`. `ActivityScenario` matches
 * lifecycle callbacks against the launching intent to track which Activity it controls, and
 * silently drops events once the live intent diverges — `scenario.close()` then waits forever
 * for a DESTROYED transition that no longer reaches it. The lower-level controller has no such
 * coupling and lets us drive the lifecycle deterministically.
 */
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class AddButtonTrampolineTest : AbstractRobolectricTest() {
    // screen_view fires from AddButtonScreen's composition (not the Activity), so screen asserts must
    // flush the pending frame first: waitForIdle after every launch/newIntent.
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

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
    fun `an incoming share reaches RESUMED and logs add_sound tagged as a share`() {
        val activity = launch(shareIntent(SAMPLE_URI))

        assertThat(activity.lifecycle.currentState).isEqualTo(Lifecycle.State.RESUMED)
        val addScreen =
            fake.screens.lastOrNull { it.name == CanonicalScreenName.ADD_SOUND }
                ?: error("The share did not log ADD_SOUND. Recorded: ${fake.screens.map { it.name }}")
        assertThat(addScreen.extras["source"]).isEqualTo(AddSoundSource.SHARE)
    }

    @Test
    fun `a share arriving while a previous one is open re-emits screen_view for the new audio`() {
        // Two consecutive shares without finishing the first. Each share is a fresh user intent, so
        // analytics must record both and Activity.getIntent() must reflect the most recent URI —
        // otherwise a "stuck on the first share" bug could hide here.
        val activity = launch(shareIntent(SAMPLE_URI))
        assertThat(fake.screens.count { it.name == CanonicalScreenName.ADD_SOUND }).isEqualTo(1)

        controller!!.newIntent(shareIntent(OTHER_URI))
        composeTestRule.waitForIdle()

        assertThat(fake.screens.count { it.name == CanonicalScreenName.ADD_SOUND }).isEqualTo(2)
        assertThat(activity.intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
            .isEqualTo(OTHER_URI)
    }

    @Test
    fun `a share arriving with the same audio re-emits screen_view`() {
        // Re-sharing the exact same file is still a fresh user intent: the screen must re-emit
        // even though (mode, source) are structurally identical to the live composition's.
        launch(shareIntent(SAMPLE_URI))
        assertThat(fake.screens.count { it.name == CanonicalScreenName.ADD_SOUND }).isEqualTo(1)

        controller!!.newIntent(shareIntent(SAMPLE_URI))
        composeTestRule.waitForIdle()

        assertThat(fake.screens.count { it.name == CanonicalScreenName.ADD_SOUND }).isEqualTo(2)
    }

    @Test
    fun `a share with no audio finishes the trampoline`() {
        // The trampoline's only input is the shared stream. Without it there is nothing to name, so it
        // toasts and gets out of the way rather than showing an empty naming form.
        val activity = launch(Intent(context, AddButtonActivity::class.java))

        assertThat(activity.isFinishing).isTrue()
        assertThat(fake.screens.map { it.name }).doesNotContain(CanonicalScreenName.ADD_SOUND)
    }

    @Test
    fun `a malformed onNewIntent finishes the trampoline even while a share is being named`() {
        // The most recent intent always wins, including a malformed one. Without this, a buggy
        // share-receiver caller could leave the user stuck on a stale naming screen with no way back
        // to the app they shared from. Toast + finish is consistent with onCreate.
        val activity = launch(shareIntent(SAMPLE_URI))

        controller!!.newIntent(Intent(context, AddButtonActivity::class.java))
        composeTestRule.waitForIdle()

        assertThat(activity.isFinishing).isTrue()
    }

    /**
     * Build, attach and resume an [AddButtonActivity] with [intent]. Stashed in `controller` so
     * `@After` can drive it to DESTROYED, releasing the Looper-shared resources between tests.
     */
    private fun launch(intent: Intent): AddButtonActivity {
        val newController = Robolectric.buildActivity(AddButtonActivity::class.java, intent)
        controller = newController
        val activity =
            newController
                .create()
                .start()
                .resume()
                // Attach the view hierarchy: without visible() the composition never gets a frame,
                // so the screen_view LaunchedEffect would not run.
                .visible()
                .get()
        composeTestRule.waitForIdle()
        return activity
    }

    private fun shareIntent(uri: Uri): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
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
            source: SoundSource,
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
    }
}
