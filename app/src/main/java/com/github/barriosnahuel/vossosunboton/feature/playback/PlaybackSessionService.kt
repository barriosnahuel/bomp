/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Publishes the active listen session (docs/adr/0022) to the system: media notification, lock
 * screen controls and media keys. Started by [MediaSessionBridgeImpl] when a listen session
 * begins and stopped when it ends — short soundboard taps never reach this service, so they never
 * produce a notification. The session player is process-lifetime and owned by
 * [PlayerControllerImpl]; this service only borrows it (remove-not-release on destroy).
 */
internal class PlaybackSessionService : MediaSessionService() {
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Exported component: any package can startService this explicitly. With no session there
        // is nothing to publish — refuse to stay started instead of idling as a keep-alive.
        val session = MediaSessionBridgeImpl.mediaSession
        if (session == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // Adopt the bridge's session on every explicit start: covers both the fresh-start and the
        // service-restart case (addSession is not implied by onGetSession, which only serves binds).
        if (session !in sessions) addSession(session)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = MediaSessionBridgeImpl.mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Bomp is not a music app: swiping the task away ends the listening session instead of
        // letting audio outlive the app.
        MediaSessionBridgeImpl.mediaSession?.player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        MediaSessionBridgeImpl.mediaSession?.let { removeSession(it) }
        super.onDestroy()
    }
}
