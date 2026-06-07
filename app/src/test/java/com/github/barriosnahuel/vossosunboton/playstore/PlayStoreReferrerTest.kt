/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.playstore

import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Robolectric because [PlayStoreReferrer] uses `android.net.Uri.encode`. */
internal class PlayStoreReferrerTest : AbstractRobolectricTest() {
    private val applicationId = "com.github.barriosnahuel.vossosunboton"

    @Test
    fun `playStoreUrl points at the app details page for the given application id`() {
        val url = PlayStoreReferrer.playStoreUrl(applicationId, PlayStoreReferrer.MEDIUM_AUDIO, PlayStoreReferrer.CONTENT_CAPTION)

        assertThat(url).startsWith("https://play.google.com/store/apps/details?id=$applicationId&referrer=")
    }

    @Test
    fun `playStoreUrl encodes the audio caption referrer`() {
        val url = PlayStoreReferrer.playStoreUrl(applicationId, PlayStoreReferrer.MEDIUM_AUDIO, PlayStoreReferrer.CONTENT_CAPTION)

        assertThat(url).contains("referrer=utm_source%3Dbomp%26utm_medium%3Daudio%26utm_content%3Dcaption")
    }

    @Test
    fun `playStoreUrl encodes the app menu referrer`() {
        val url = PlayStoreReferrer.playStoreUrl(applicationId, PlayStoreReferrer.MEDIUM_APP, PlayStoreReferrer.CONTENT_MENU)

        assertThat(url).contains("referrer=utm_source%3Dbomp%26utm_medium%3Dapp%26utm_content%3Dmenu")
    }
}
