/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION_CODES
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
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
 * SDK-boundary coverage for [AddButtonActivity.handleIntent], which reads the shared [Uri] through
 * `IntentCompat.getParcelableExtra`. The branch lives inside androidx.core, not in our code: it calls the
 * typed `Intent.getParcelableExtra(name, Class)` on UPSIDE_DOWN_CAKE+ and the deprecated single-arg
 * overload plus an `isInstance` check below it. TIRAMISU (33) and UPSIDE_DOWN_CAKE (34) straddle that
 * boundary; N (24, the minSdk) keeps a run of the whole share-to-Create flow at the floor.
 *
 * Assertions are deliberately SDK-independent — they observe the *effect* of an extraction (the
 * trampoline composes the Create screen, or finishes on rejection). [AddButtonTrampolineTest] covers the
 * behaviour in depth at the default SDK; this class adds the legacy-branch leg and the non-Uri
 * rejection on both branches.
 */
@Config(sdk = [VERSION_CODES.N, VERSION_CODES.TIRAMISU, VERSION_CODES.UPSIDE_DOWN_CAKE]) // sdk-boundary: IntentCompat, SDK_INT >= 34
internal class AddButtonActivitySdkBoundaryTest : AbstractRobolectricTest() {
    // screen_view fires from AddButtonScreen's composition (not the Activity): flush the
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
    fun `share intent resolves the stream URI on both the typed and legacy IntentCompat branches`() {
        val activity = launch(shareIntent())

        assertThat(activity.isFinishing).isFalse()
        assertThat(fake.screens.map { it.name }).contains(CanonicalScreenName.ADD_SOUND)
    }

    /** OWASP MASVS-PLATFORM-1 / CWE-20 (inbound share: a non-Uri EXTRA_STREAM is rejected, never cast). */
    @Test
    fun `share intent whose stream extra is not a Uri finishes without composing the Create screen`() {
        val intent = shareIntent().apply { putExtra(Intent.EXTRA_STREAM, Intent(Intent.ACTION_VIEW)) }

        val activity = launch(intent)

        assertThat(activity.isFinishing).isTrue()
        assertThat(fake.screens.map { it.name }).doesNotContain(CanonicalScreenName.ADD_SOUND)
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
    }
}
