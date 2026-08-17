/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton.trim

import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The gate in front of the cutter: when the user changed nothing, saving must hand the pipeline the
 * original audio untouched — no transcode, and nothing flagged as a failed trim.
 *
 * The cut itself is not exercised here: `Transformer` drives `MediaCodec`, which has no JVM
 * implementation, so a real export is verified on a device (ADR 0028 § Consequences).
 */
internal class TrimSaveTest : AbstractRobolectricTest() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `saving without touching the handles keeps the original audio and reports no trim`() =
        runTest {
            val result = applyTrim(context, SOURCE, durationMs = 60_000, selection = TrimSelection.WHOLE)

            assertThat(result.uri).isEqualTo(SOURCE)
            assertThat(result.trimmed).isFalse()
            assertThat(result.fellBack).isFalse()
        }

    @Test
    fun `a selection made before the duration landed is not treated as a cut`() =
        runTest {
            val moved = TrimSelection(startFraction = 0.2f, endFraction = 0.6f)

            val result = applyTrim(context, SOURCE, durationMs = 0, selection = moved)

            assertThat(result.uri).isEqualTo(SOURCE)
            assertThat(result.trimmed).isFalse()
        }

    @Test
    fun `a selection that still spans the clip end to end is not treated as a cut`() =
        runTest {
            // Fractions the handles can legitimately produce at their extremes, which still resolve to
            // the whole clip in milliseconds — re-encoding for that would be pure waste.
            val untouched = TrimSelection(startFraction = 0f, endFraction = 1.0f)

            val result = applyTrim(context, SOURCE, durationMs = 30_000, selection = untouched)

            assertThat(result.trimmed).isFalse()
        }

    private companion object {
        val SOURCE = "content://media/external/audio/media/42".toUri()
    }
}
