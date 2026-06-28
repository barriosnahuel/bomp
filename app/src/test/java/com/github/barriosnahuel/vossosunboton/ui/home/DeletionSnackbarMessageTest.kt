/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class DeletionSnackbarMessageTest : AbstractRobolectricTest() {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `welcome deletion speaks the warm author voice, not the generic line`() {
        val welcome = testSound(name = "Welcome", rawRes = R.raw.app_welcome_sticker)

        val message = deletionSnackbarMessage(context, welcome)

        assertThat(message).isEqualTo(context.getString(R.string.app_welcome_sticker_feedback_dismissed))
        assertThat(message).isNotEqualTo(context.getString(R.string.app_feedback_audio_deleted_unnamed))
    }

    @Test
    fun `deleting a named audio names what was deleted`() {
        val userSound = testSound(name = "Mama riendo", file = "/audio/mama.mp3")

        val message = deletionSnackbarMessage(context, userSound)

        assertThat(message).contains("Mama riendo")
        assertThat(message).isNotEqualTo(context.getString(R.string.app_feedback_audio_deleted_unnamed))
    }

    @Test
    fun `a name with quote and percent characters renders verbatim without breaking the delimiter`() {
        val tricky = testSound(name = "a\"b%c", file = "/audio/tricky.mp3")

        val message = deletionSnackbarMessage(context, tricky)

        assertThat(message).contains("a\"b%c")
    }

    @Test
    fun `deleting a blank-named audio falls back to the nameless voice so the placeholder never renders empty`() {
        val unnamed = testSound(name = "", file = "/audio/unnamed.mp3")

        val message = deletionSnackbarMessage(context, unnamed)

        assertThat(message).isEqualTo(context.getString(R.string.app_feedback_audio_deleted_unnamed))
    }
}
