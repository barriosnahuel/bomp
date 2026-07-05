/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Context
import androidx.media3.common.Player

/**
 * Recording [MediaSessionBridge] for controller/engine unit tests: counts lifecycle transitions
 * instead of touching Media3 system plumbing (real [androidx.media3.session.MediaSession] +
 * service coverage lives in the instrumented suite).
 */
internal class FakeMediaSessionBridge : MediaSessionBridge {
    var playerCreatedCount = 0
        private set
    var startedCount = 0
        private set
    var endedCount = 0
        private set

    override fun onSessionPlayerCreated(
        context: Context,
        player: Player,
    ) {
        playerCreatedCount++
    }

    override fun onSessionStarted(context: Context) {
        startedCount++
    }

    override fun onSessionEnded(context: Context) {
        endedCount++
    }
}
