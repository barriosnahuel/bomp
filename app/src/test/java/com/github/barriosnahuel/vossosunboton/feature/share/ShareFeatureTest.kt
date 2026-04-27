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
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

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
    }

    @Test
    fun `share emits Share with the surface passed in by the caller`() {
        val sound = givenASoundWithUri()

        whenSharingThe(sound)

        val event = fake.assertEmitted("share")
        assertThat(event.params["surface"]).isEqualTo(CanonicalScreenName.MY_SOUNDS)
    }

    @Test
    fun `share increments lifetime_shares user property monotonically across calls`() {
        whenSharingThe(givenASoundWithUri())
        whenSharingThe(givenASoundWithUri())

        assertThat(fake.userProperties[AnalyticsUserProperty.LIFETIME_SHARES]).isEqualTo("2")
    }

    @Test
    fun `share does not increment lifetime_shares when the chooser activity cannot be started`() {
        val sound = givenASoundWithUri()
        val mockedContext = spyk<Context>(ApplicationProvider.getApplicationContext<Context>())
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(mockedContext, any(), any()) } returns Uri.EMPTY
        every { mockedContext.startActivity(any()) } throws ActivityNotFoundException("no app handles audio share")

        try {
            ShareFeature.instance.share(mockedContext, sound, CanonicalScreenName.MY_SOUNDS)
        } catch (_: ActivityNotFoundException) {
            // expected — propagates so the caller can show an error UI
        }

        fake.assertNotEmitted("share")
        assertThat(fake.userProperties[AnalyticsUserProperty.LIFETIME_SHARES]).isNull()
    }

    @Test
    fun `share when sound Uri and resource are null must throw an exception`() {
        val sound = givenASoundWithNullUri()

        try {
            whenSharingThe(sound)
        } catch (e: IllegalStateException) {
            thenWeThrowAnIllegalStateException(e)
        }
    }

    @Test
    fun `share when sound URI is valid show disambiguation window`() {
        val sound = givenASoundWithUri()

        val generatedIntent = whenSharingThe(sound)

        thenOSShowDisambiguationWindow(generatedIntent)
    }

    @Test
    fun `share when sound raw resource ID is valid show disambiguation window`() {
        val sound = givenASoundWithResourceId()

        val generatedIntent = whenSharingThe(sound)

        thenOSShowDisambiguationWindow(generatedIntent)
    }

    @Test
    fun `share when sound URI is valid sends the audio file`() {
        val sound = givenASoundWithUri()

        val generatedIntent = whenSharingThe(sound)

        thenWeSendAnAudio(generatedIntent)
    }

    @Test
    fun `share should generate a content Uri under Music directory as described in app_file_provider_paths resource`() {
        val sound = givenASoundWithUri()

        val capturedFile = whenSharingTheSoundCapturingThePath(sound)

        thenWeCheckSoundPathIsAtMusicDirectory(capturedFile)
    }

    @Test
    fun `share packaged sound temp filename is soundName dot mp3 without any prefix`() {
        val sound = givenASoundWithResourceId()

        val capturedFile = whenSharingTheSoundCapturingThePath(sound)

        assertThat(capturedFile.name).isEqualTo("$dummyButtonName.mp3")
    }

    private fun thenWeCheckSoundPathIsAtMusicDirectory(capturedFile: File) {
        assertThat(capturedFile.absolutePath.split("/").contains(Environment.DIRECTORY_MUSIC)).isTrue()
    }

    private fun givenASoundWithUri() = Sound(dummyButtonName, "a/dummy/sound/uri")

    private fun givenASoundWithResourceId() = Sound(dummyButtonName, rawRes = R.raw.app_test_sound)

    private fun givenASoundWithNullUri() = Sound(dummyButtonName)

    private fun whenSharingTheSoundCapturingThePath(sound: Sound): File {
        val mockedContext = spyk<Context>(ApplicationProvider.getApplicationContext<Context>())

        val slotFile = slot<File>()
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(mockedContext, any(), capture(slotFile)) } returns Uri.EMPTY

        val slot = slot<Intent>()
        every { mockedContext.startActivity(capture(slot)) } answers { nothing }

        ShareFeature.instance.share(mockedContext, sound, CanonicalScreenName.MY_SOUNDS)

        return slotFile.captured
    }

    private fun whenSharingThe(sound: Sound): Intent {
        val mockedContext = spyk<Context>(ApplicationProvider.getApplicationContext<Context>())

        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(mockedContext, any(), any()) } returns Uri.EMPTY

        val slot = slot<Intent>()
        every { mockedContext.startActivity(capture(slot)) } answers { nothing }

        ShareFeature.instance.share(mockedContext, sound, CanonicalScreenName.MY_SOUNDS)

        return slot.captured
    }

    private fun thenWeThrowAnIllegalStateException(e: IllegalStateException) {
        assertThat(e.message).isEqualTo("Either file URI or raw resource ID must exist on a given sound")
    }

    private fun thenOSShowDisambiguationWindow(intent: Intent) {
        assertThat(intent.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(intent.extras?.containsKey(Intent.EXTRA_INTENT)).isTrue()
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
