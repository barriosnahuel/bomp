/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.material3.SnackbarDuration
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class DeletionSnackbarDurationTest {
    @Test
    fun `returns Short for the welcome sticker so dismissal feedback is not lingering`() {
        val welcome = Sound(name = "Welcome", rawRes = R.raw.app_welcome_sticker)

        val duration = deletionSnackbarDuration(welcome)

        assertThat(duration).isEqualTo(SnackbarDuration.Short)
    }

    @Test
    fun `returns Long for a user-created sound so Undo is reachable after destructive delete`() {
        val userSound = Sound(name = "Abrazo", file = "/audio/abrazo.mp3")

        val duration = deletionSnackbarDuration(userSound)

        assertThat(duration).isEqualTo(SnackbarDuration.Long)
    }
}
