/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * End-to-end coverage for the MediaSession integration (ADR 0022 / spec 002d): a listen session is
 * discoverable and controllable through the real [PlaybackSessionService] + [MediaController]
 * stack — exactly what System UI's media controls do — and an EXTERNAL pause command reconciles
 * back into the controller's published state. Unit tests pin these transitions with a scripted
 * fake; this suite exercises the real ExoPlayer + MediaSession + service binder chain.
 */
@RunWith(AndroidJUnit4::class)
internal class MediaSessionFlowTest : AbstractUiTest() {
    private var mediaController: MediaController? = null

    override fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            mediaController?.release()
            mediaController = null
            PlayerControllerFactory.instance.stopPlayingSound()
        }
        super.tearDown()
    }

    @Test
    fun listenSessionIsControllableThroughTheSystemMediaStack() {
        val previewUri = TestData.seedPreviewAudio(context)
        val playerController = PlayerControllerFactory.instance
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            playerController.startUriListenSession(context, previewUri)
        }
        awaitCondition("session playback did not start") {
            playerController.playbackState.value?.isPlaying == true
        }

        // Connect exactly like System UI / external media controllers do: bind the exported
        // service and build a MediaController from its session token.
        val controller = connectMediaController()

        awaitConditionOnMain("session not visible through MediaController") { controller.isPlaying }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertThat(controller.mediaMetadata.title.toString())
                .isEqualTo(context.getString(R.string.app_playback_recording_session_title))
        }

        // External pause (media notification / media key / headset): commands the Player directly,
        // and the engine must reconcile it into the same published state the in-app UI renders.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.pause() }
        awaitCondition("external pause did not reconcile into playbackState") {
            playerController.playbackState.value?.isPlaying == false
        }
    }

    @Test
    fun listenSessionShowsAMediaNotificationWhilePlaying() {
        val previewUri = TestData.seedPreviewAudio(context)
        val playerController = PlayerControllerFactory.instance
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            playerController.startUriListenSession(context, previewUri)
        }
        awaitCondition("session playback did not start") {
            playerController.playbackState.value?.isPlaying == true
        }

        // Media-session notifications are exempt from POST_NOTIFICATIONS (which this app does not
        // request): the notification must be active while the session plays.
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        awaitCondition("media notification never appeared") {
            notificationManager.activeNotifications.isNotEmpty()
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            PlayerControllerFactory.instance.stopPlayingSound()
        }
        awaitCondition("media notification did not disappear with the session") {
            notificationManager.activeNotifications.isEmpty()
        }
    }

    @Test
    fun shortSoundboardTapNeverStartsTheSessionService() {
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(context.getString(R.string.app_play)).performClick()
            composeRule.awaitNodeWithContentDescription(context.getString(R.string.app_pause)).assertIsDisplayed()

            // The tap path (MediaPlayer engine, ADR 0022) must not publish a system surface: no
            // session service, no media notification — a 2-second Bomp burst is not a listen session.
            assertThat(isSessionServiceRunning()).isFalse()
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            assertThat(notificationManager.activeNotifications).isEmpty()
        }
    }

    private fun connectMediaController(): MediaController {
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val controller =
            MediaController
                .Builder(context, token)
                .buildAsync()
                .get(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        mediaController = controller
        return controller
    }

    @Suppress("DEPRECATION") // Still authoritative for the caller's OWN services, which is all we ask.
    private fun isSessionServiceRunning(): Boolean {
        val am = context.getSystemService(ActivityManager::class.java)
        return am.getRunningServices(Int.MAX_VALUE).any {
            it.service == ComponentName(context, PlaybackSessionService::class.java)
        }
    }

    private fun awaitCondition(
        message: String,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + CONDITION_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        assertWithMessage(message).that(condition()).isTrue()
    }

    /** Like [awaitCondition] but evaluates [condition] on the main thread ([MediaController] is main-only). */
    private fun awaitConditionOnMain(
        message: String,
        condition: () -> Boolean,
    ) {
        awaitCondition(message) {
            var result = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync { result = condition() }
            result
        }
    }

    private companion object {
        // Cold-AVD codec spin-up + service bind + binder round-trips must settle inside this window.
        private const val CONDITION_TIMEOUT_MS = 10_000L
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val POLL_INTERVAL_MS = 100L
    }
}
