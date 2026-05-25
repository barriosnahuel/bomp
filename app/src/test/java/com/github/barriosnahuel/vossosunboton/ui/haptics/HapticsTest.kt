/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.haptics

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * SDK-boundary coverage for [performConfirmHaptic] / [performRejectHaptic], which branch on
 * `Build.VERSION.SDK_INT >= R` to use a richer haptic constant on R+ and fall back to a generic one
 * below. The matrix straddles that boundary: M (23) exercises the legacy fallback, R (30) the modern
 * branch. Without the M run the fallback path never executes in tests.
 */
@Config(sdk = [Build.VERSION_CODES.M, Build.VERSION_CODES.R]) // sdk-boundary: Haptics branches on SDK_INT >= R
internal class HapticsTest : AbstractRobolectricTest() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `performConfirmHaptic uses CONFIRM on R and up and VIRTUAL_KEY below`() {
        val view = View(context)

        performConfirmHaptic(view)

        val expected =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        assertThat(Shadows.shadowOf(view).lastHapticFeedbackPerformed()).isEqualTo(expected)
    }

    @Test
    fun `performRejectHaptic uses REJECT on R and up and LONG_PRESS below`() {
        val view = View(context)

        performRejectHaptic(view)

        val expected =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        assertThat(Shadows.shadowOf(view).lastHapticFeedbackPerformed()).isEqualTo(expected)
    }
}
