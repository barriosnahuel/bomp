/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class SeekTargetTest {
    @Test
    fun `maps a scrub fraction to millis within the clip`() {
        assertThat(seekTargetMs(durationMs = 10_000, fraction = 0.5f)).isEqualTo(5_000)
    }

    @Test
    fun `coerces an out-of-range fraction into the clip bounds`() {
        assertThat(seekTargetMs(durationMs = 10_000, fraction = -0.3f)).isEqualTo(0)
    }

    @Test
    fun `clamps a full-right scrub just shy of the end so it never lands on EOS`() {
        // A seek to exactly durationMs can fire onCompletion and stop/reset the preview.
        assertThat(seekTargetMs(durationMs = 10_000, fraction = 1f)).isEqualTo(9_999)
        assertThat(seekTargetMs(durationMs = 10_000, fraction = 1.5f)).isEqualTo(9_999)
    }

    @Test
    fun `returns null for a non-positive duration so nothing seeks`() {
        assertThat(seekTargetMs(durationMs = 0, fraction = 0.5f)).isNull()
    }
}
