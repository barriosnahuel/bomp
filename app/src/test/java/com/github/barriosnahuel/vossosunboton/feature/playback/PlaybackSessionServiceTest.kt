/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * Smoke coverage for [PlaybackSessionService] (CLAUDE.md § Activity smoke tests, extended to the
 * project's first Service): the lifecycle must survive without a session — the exported service
 * can be started or bound by external packages at any time, including before any listen session
 * ever existed in the process, and must refuse to stay started in that case.
 */
internal class PlaybackSessionServiceTest : AbstractRobolectricTest() {
    @Test
    fun `an explicit start without a session refuses to stay started`() {
        val controller = Robolectric.buildService(PlaybackSessionService::class.java)

        val service = controller.create().startCommand(0, 0).get()

        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
        assertThat(service.onGetSession(mockk())).isNull()
        controller.destroy()
    }

    @Test
    fun `task removal without a session stops the service without crashing`() {
        val controller = Robolectric.buildService(PlaybackSessionService::class.java)
        val service = controller.create().get()

        service.onTaskRemoved(null)

        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
        controller.destroy()
    }
}
