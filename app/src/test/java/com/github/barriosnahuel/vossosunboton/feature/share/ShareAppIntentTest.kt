/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.playstore.PlayStoreReferrer
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.junit.Test

internal class ShareAppIntentTest : AbstractRobolectricTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `build returns an ACTION_SEND text intent`() {
        val intent = ShareAppIntent.build(context, PlayStoreReferrer.CONTENT_MENU)

        assertThat(intent.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(intent.type).isEqualTo("text/plain")
    }

    @Test
    fun `build carries the invite text with no audio stream`() {
        val intent = ShareAppIntent.build(context, PlayStoreReferrer.CONTENT_MENU)

        assertThat(intent.getStringExtra(Intent.EXTRA_TEXT)).contains("Bomp")
        assertThat(intent.extras?.containsKey(Intent.EXTRA_STREAM)).isFalse()
    }

    @Test
    fun `build carries the app menu referrer`() {
        val intent = ShareAppIntent.build(context, PlayStoreReferrer.CONTENT_MENU)

        assertThat(intent.getStringExtra(Intent.EXTRA_TEXT))
            .contains("utm_source%3Dbomp%26utm_medium%3Dapp%26utm_content%3Dmenu")
    }

    @Test
    fun `build links to the published listing, not the debug variant id`() {
        val intent = ShareAppIntent.build(context, PlayStoreReferrer.CONTENT_MENU)

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        assertThat(text).contains("id=com.github.barriosnahuel.vossosunboton&")
        // This test runs against the debug variant; the link must never carry its `.debug` applicationIdSuffix.
        assertThat(text).doesNotContain(".debug")
    }

    @Test
    fun `build carries the about referrer when shared from About`() {
        val intent = ShareAppIntent.build(context, PlayStoreReferrer.CONTENT_ABOUT)

        assertThat(intent.getStringExtra(Intent.EXTRA_TEXT))
            .contains("utm_source%3Dbomp%26utm_medium%3Dapp%26utm_content%3Dabout")
    }

    @Test
    fun `launch records a non-fatal when no activity can handle the share`() {
        val mockedContext = spyk(ApplicationProvider.getApplicationContext<Context>())
        every { mockedContext.startActivity(any()) } throws ActivityNotFoundException("no app handles text share")

        ShareAppIntent.launch(mockedContext, PlayStoreReferrer.CONTENT_MENU)

        verify(exactly = 1) { Tracker.track(any()) }
    }
}
