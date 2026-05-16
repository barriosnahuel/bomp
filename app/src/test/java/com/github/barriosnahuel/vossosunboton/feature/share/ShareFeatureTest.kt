/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsUserProperty
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

internal class ShareFeatureTest : AbstractRobolectricTest() {
    private val dummyButtonName = "my button name"
    private lateinit var fake: FakeAnalyticsTracker

    @Before
    fun setUpAnalytics() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
    }

    @After
    fun tearDownAnalytics() {
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    // ─── prepareShareIntent: file resolution + intent build ───

    @Test
    fun `prepareShareIntent returns Success carrying the surface passed in by the caller`() {
        val outcome = whenPreparing(givenASoundWithUri(), CanonicalScreenName.EXPLORE_SOUNDS)

        assertThat((outcome as ShareIntentOutcome.Success).surface).isEqualTo(CanonicalScreenName.EXPLORE_SOUNDS)
    }

    @Test
    fun `prepareShareIntent returns Success with ACTION_SEND audio intent for sound with uri`() {
        val outcome = whenPreparing(givenASoundWithUri())

        val intent = (outcome as ShareIntentOutcome.Success).intent
        assertThat(intent.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(intent.type).isEqualTo("audio/*")
        assertThat(intent.extras?.containsKey(Intent.EXTRA_STREAM)).isTrue()
    }

    @Test
    fun `prepareShareIntent returns Success for sound with rawRes resource`() {
        val outcome = whenPreparing(givenASoundWithResourceId())

        assertThat(outcome).isInstanceOf(ShareIntentOutcome.Success::class.java)
    }

    @Test
    fun `prepareShareIntent generates content Uri under Music directory`() {
        val capturedFile = whenPreparingCapturingTheFilePath(givenASoundWithUri())

        assertThat(capturedFile.absolutePath.split("/").contains(Environment.DIRECTORY_MUSIC)).isTrue()
    }

    @Test
    fun `prepareShareIntent uses a stable bundled filename keyed by rawRes for the share cache`() {
        val capturedFile = whenPreparingCapturingTheFilePath(givenASoundWithResourceId())

        // Cache key migrated from `name + .mp3` to `bundled_<rawRes>.mp3` as part of the stable-id
        // refactor (ADR 0008): names are no longer unique, so a name-based cache key would collide.
        assertThat(capturedFile.name).isEqualTo("bundled_${R.raw.app_test_sound}.mp3")
    }

    /** OWASP MASVS-PLATFORM-1 / CWE-22 (Path Traversal defense — FileProvider rejects out-of-scope paths). */
    @Test
    fun `prepareShareIntent returns Failure unshareable when FileProvider rejects the file`() {
        val sound = givenASoundWithUri()
        val mockedContext = spyk<Context>(ApplicationProvider.getApplicationContext<Context>())

        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(mockedContext, any(), any()) } throws
            IllegalArgumentException("Failed to find configured root that contains /data/local/tmp/external/x.mp3")

        val tracked = slot<Throwable>()
        every { Tracker.track(capture(tracked)) } answers { nothing }
        every { mockedContext.startActivity(any()) } answers { nothing }

        val outcome =
            runBlocking { ShareFeature.instance.prepareShareIntent(mockedContext, sound, CanonicalScreenName.MY_SOUNDS) }

        assertThat((outcome as ShareIntentOutcome.Failure).feedback).isEqualTo(R.string.app_share_feedback_unshareable)
        verify(exactly = 1) { Tracker.track(any()) }
        verify(atLeast = 1) { Tracker.log(any()) }
        assertThat(tracked.captured).isInstanceOf(RuntimeException::class.java)
        assertThat(tracked.captured.cause).isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { mockedContext.startActivity(any()) }
    }

    @Test
    fun `prepareShareIntent returns Failure broken_data when sound has neither file nor rawRes`() {
        val sound = givenASoundWithNullUri()
        val mockedContext = spyk<Context>(ApplicationProvider.getApplicationContext<Context>())

        val tracked = slot<Throwable>()
        every { Tracker.track(capture(tracked)) } answers { nothing }
        every { mockedContext.startActivity(any()) } answers { nothing }

        val outcome =
            runBlocking { ShareFeature.instance.prepareShareIntent(mockedContext, sound, CanonicalScreenName.MY_SOUNDS) }

        assertThat((outcome as ShareIntentOutcome.Failure).feedback).isEqualTo(R.string.app_share_feedback_broken_data)
        verify(exactly = 1) { Tracker.track(any()) }
        verify(atLeast = 1) { Tracker.log(any()) }
        assertThat(tracked.captured).isInstanceOf(RuntimeException::class.java)
        verify(exactly = 0) { mockedContext.startActivity(any()) }
    }

    @Test
    fun `prepareShareIntent returns Failure with copy-error feedback when bundled copy throws IOException`() {
        val sound = givenASoundWithResourceId()
        val realContext = ApplicationProvider.getApplicationContext<Context>()
        val mockedContext = spyk<Context>(realContext)

        // Robolectric's external dir survives across tests in the same JVM run; if a sibling test (e.g. the one
        // that captures the path) already copied "$dummyButtonName.mp3" to it, the bundled branch short-circuits
        // on `fileForSharing.exists()` and never reaches our `copy(...)` mock. Delete the leftover so the copy
        // path is exercised deterministically regardless of test execution order.
        com.github.barriosnahuel.vossosunboton.commons.file
            .getFile(realContext, "$dummyButtonName.mp3")
            .delete()

        mockkStatic("com.github.barriosnahuel.vossosunboton.commons.file.FileUtils")
        every {
            com.github.barriosnahuel.vossosunboton.commons.file
                .copy(any(), any())
        } throws IOException("simulated disk full while copying raw resource")

        val tracked = slot<Throwable>()
        every { Tracker.track(capture(tracked)) } answers { nothing }
        every { mockedContext.startActivity(any()) } answers { nothing }

        val outcome =
            runBlocking { ShareFeature.instance.prepareShareIntent(mockedContext, sound, CanonicalScreenName.MY_SOUNDS) }

        assertThat((outcome as ShareIntentOutcome.Failure).feedback).isEqualTo(R.string.app_share_feedback_copy_failed)
        verify(exactly = 1) { Tracker.track(any()) }
        verify(atLeast = 1) { Tracker.log(any()) }
        assertThat(tracked.captured).isInstanceOf(RuntimeException::class.java)
        assertThat(tracked.captured.cause).isInstanceOf(IOException::class.java)
        verify(exactly = 0) { mockedContext.startActivity(any()) }
    }

    // ─── launchChooser: startActivity + analytics ───

    @Test
    fun `launchChooser emits Share with the surface passed in by the caller`() {
        val (context, intent) = givenAReadyToLaunchPair()

        ShareFeature.instance.launchChooser(context, intent, CanonicalScreenName.MY_SOUNDS)

        val event = fake.assertEmitted("share")
        assertThat(event.params["surface"]).isEqualTo(CanonicalScreenName.MY_SOUNDS)
    }

    @Test
    fun `launchChooser increments lifetime_shares user property monotonically across calls`() {
        val (context, intent) = givenAReadyToLaunchPair()

        ShareFeature.instance.launchChooser(context, intent, CanonicalScreenName.MY_SOUNDS)
        ShareFeature.instance.launchChooser(context, intent, CanonicalScreenName.MY_SOUNDS)

        assertThat(fake.userProperties[AnalyticsUserProperty.LIFETIME_SHARES]).isEqualTo("2")
    }

    @Test
    fun `launchChooser returns null when chooser launches successfully`() {
        val (context, intent) = givenAReadyToLaunchPair()

        val feedback = ShareFeature.instance.launchChooser(context, intent, CanonicalScreenName.MY_SOUNDS)

        assertThat(feedback).isNull()
    }

    @Test
    fun `launchChooser wraps the share intent in ACTION_CHOOSER before startActivity`() {
        val mockedContext = spyk<Context>(ApplicationProvider.getApplicationContext<Context>())
        val baseIntent =
            Intent().apply {
                action = Intent.ACTION_SEND
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, Uri.EMPTY)
            }
        val captured = slot<Intent>()
        every { mockedContext.startActivity(capture(captured)) } answers { nothing }

        ShareFeature.instance.launchChooser(mockedContext, baseIntent, CanonicalScreenName.MY_SOUNDS)

        assertThat(captured.captured.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(captured.captured.extras?.containsKey(Intent.EXTRA_INTENT)).isTrue()
        thenWeSendAnAudio(captured.captured)
    }

    @Test
    fun `launchChooser returns no_app_for_audio when activity not found and tracks non-fatal without analytics`() {
        val mockedContext = spyk<Context>(ApplicationProvider.getApplicationContext<Context>())
        val baseIntent = Intent().apply { action = Intent.ACTION_SEND }
        every { mockedContext.startActivity(any()) } throws ActivityNotFoundException("no app handles audio share")

        val tracked = slot<Throwable>()
        every { Tracker.track(capture(tracked)) } answers { nothing }

        val feedback = ShareFeature.instance.launchChooser(mockedContext, baseIntent, CanonicalScreenName.MY_SOUNDS)

        assertThat(feedback).isEqualTo(R.string.app_share_feedback_no_app_for_audio)
        verify(exactly = 1) { Tracker.track(any()) }
        assertThat(tracked.captured).isInstanceOf(RuntimeException::class.java)
        assertThat(tracked.captured.cause).isInstanceOf(ActivityNotFoundException::class.java)
        fake.assertNotEmitted("share")
        assertThat(fake.userProperties[AnalyticsUserProperty.LIFETIME_SHARES]).isNull()
    }

    // ─── helpers ───

    private fun givenASoundWithUri() = testSound(dummyButtonName, "a/dummy/sound/uri")

    private fun givenASoundWithResourceId() = testSound(dummyButtonName, rawRes = R.raw.app_test_sound)

    private fun givenASoundWithNullUri() = testSound(dummyButtonName)

    private fun whenPreparing(
        sound: Sound,
        surface: String = CanonicalScreenName.MY_SOUNDS,
    ): ShareIntentOutcome {
        val mockedContext = spyk<Context>(ApplicationProvider.getApplicationContext<Context>())
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(mockedContext, any(), any()) } returns Uri.EMPTY
        return runBlocking { ShareFeature.instance.prepareShareIntent(mockedContext, sound, surface) }
    }

    private fun whenPreparingCapturingTheFilePath(sound: Sound): File {
        val mockedContext = spyk<Context>(ApplicationProvider.getApplicationContext<Context>())
        val slotFile = slot<File>()
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(mockedContext, any(), capture(slotFile)) } returns Uri.EMPTY
        runBlocking {
            ShareFeature.instance.prepareShareIntent(mockedContext, sound, CanonicalScreenName.MY_SOUNDS)
        }
        return slotFile.captured
    }

    private fun givenAReadyToLaunchPair(): Pair<Context, Intent> {
        val mockedContext = spyk<Context>(ApplicationProvider.getApplicationContext<Context>())
        every { mockedContext.startActivity(any()) } answers { nothing }
        val intent =
            Intent().apply {
                action = Intent.ACTION_SEND
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, Uri.EMPTY)
            }
        return mockedContext to intent
    }

    private fun thenWeSendAnAudio(intent: Intent) {
        val shareIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            }

        assertThat(shareIntent?.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(shareIntent?.type).isEqualTo("audio/*")
        assertThat(shareIntent?.extras?.containsKey(Intent.EXTRA_STREAM)).isTrue()
    }
}
