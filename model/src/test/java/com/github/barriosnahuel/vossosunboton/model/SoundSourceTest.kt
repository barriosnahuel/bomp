/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Constructor → [SoundSource] mapping (ADR 0019): the bundled constructor is the sole BUNDLED
 * producer; the import constructor and the default land on IMPORTED. RECORDED is set explicitly by
 * the recorder save path, never by a constructor.
 */
class SoundSourceTest {
    @Test
    fun `bundled constructor tags the sound as BUNDLED`() {
        assertThat(Sound("bell", 42).source).isEqualTo(SoundSource.BUNDLED)
    }

    @Test
    fun `import constructor defaults the source to IMPORTED`() {
        assertThat(Sound("custom:bell", "bell", "bell.mp3").source).isEqualTo(SoundSource.IMPORTED)
    }

    @Test
    fun `primary constructor defaults the source to IMPORTED`() {
        assertThat(Sound("custom:bell", "bell", "bell.mp3", 0, false).source).isEqualTo(SoundSource.IMPORTED)
    }
}
