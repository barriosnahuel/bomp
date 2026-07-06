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
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.SoundSource
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
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
 * SDK-boundary coverage for [AddButtonActivity.handleIntent], which reads its Parcelable extras
 * (the edit [Sound] and the shared [Uri]) through `Intent.getParcelableExtra(name, Class)` on
 * TIRAMISU+ and the deprecated single-arg overload below it. The matrix straddles that boundary:
 * M (23) exercises the legacy branch, TIRAMISU (33) the typed one.
 *
 * Assertions are deliberately SDK-independent — they observe the *effect* of a successful
 * extraction (the Activity recomposes into Create/Edit and logs the screen, and does not finish)
 * rather than calling the typed two-arg API, which does not exist below TIRAMISU and would fail the
 * M run on the test side instead of the production side. [AddButtonActivityIntentDispatchTest]
 * covers the TIRAMISU behaviour in depth; this test only adds the legacy-branch leg.
 */
@Config(sdk = [Build.VERSION_CODES.M, Build.VERSION_CODES.TIRAMISU]) // sdk-boundary: handleIntent branches on SDK_INT >= TIRAMISU
internal class AddButtonActivitySdkBoundaryTest : AbstractRobolectricTest() {
    // screen_view now fires from AddButtonScreen's composition (not the Activity): flush the
    // pending frame (waitForIdle in launch) before asserting on fake.screens.
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var fake: FakeAnalyticsTracker
    private var controller: ActivityController<AddButtonActivity>? = null

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        AddButtonFeatureProvider.setForTest(FakeAddButtonFeature())
    }

    @After
    fun tearDown() {
        controller?.let { runCatching { it.pause().stop().destroy() } }
        controller = null
        AnalyticsTrackerProvider.setForTest(null)
        AddButtonFeatureProvider.setForTest(null)
    }

    @Test
    fun `share intent resolves the stream URI on both the typed and legacy getParcelableExtra branches`() {
        val activity = launch(shareIntent())

        assertThat(activity.isFinishing).isFalse()
        assertThat(fake.screens.map { it.name }).contains(CanonicalScreenName.ADD_SOUND)
    }

    @Test
    fun `edit intent resolves the Sound on both the typed and legacy getParcelableExtra branches`() {
        val activity = launch(editIntent())

        assertThat(activity.isFinishing).isFalse()
        assertThat(fake.screens.map { it.name }).contains(CanonicalScreenName.EDIT_SOUND)
    }

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

    private fun shareIntent(): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, SAMPLE_URI)
        }

    private fun editIntent(): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            putExtra(LandingActivity.EXTRA_EDIT_SOUND, testSound(EXISTING_NAME, file = "existing.mp3"))
        }

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
        const val EXISTING_NAME = "Existing sound"
    }
}
