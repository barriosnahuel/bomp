/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Seam between [ListenSessionEngine] and the system media surfaces (media notification, lock
 * screen, media keys) : docs/adr/0022. The engine reports listen-session lifecycle transitions;
 * the production implementation maintains the app's [MediaSession] and the
 * [PlaybackSessionService] that publishes it to the system. Unit tests substitute a recording
 * fake so engine transitions are assertable without Media3 system plumbing.
 */
internal interface MediaSessionBridge {
    /** Called once, right after the session [Player] is created; wraps it in a [MediaSession]. */
    fun onSessionPlayerCreated(
        context: Context,
        player: Player,
    )

    /** A listen session started (or restarted): publish it to the system. */
    fun onSessionStarted(context: Context)

    /** The listen session ended definitively (stop / completion / error): retract it. */
    fun onSessionEnded(context: Context)
}

/**
 * Rejects every controller-supplied media item: Bomp listen sessions play app-curated content
 * only. Media3's default callback accepts any item carrying a URI, which would let any app that
 * binds the exported [PlaybackSessionService] inject arbitrary audio into Bomp's player.
 * Transport controls (play/pause/seek) stay available to system surfaces.
 */
internal object CuratedContentSessionCallback : MediaSession.Callback {
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> =
        Futures.immediateFailedFuture(UnsupportedOperationException("External media items are not accepted"))
}

/**
 * Production [MediaSessionBridge]. Owns the process-lifetime [MediaSession] (same lifetime as the
 * session player it wraps — neither is ever released, matching [PlayerControllerImpl]'s
 * singleton design) and starts/stops [PlaybackSessionService] around each listen session so the
 * media notification exists exactly while a session does.
 */
internal object MediaSessionBridgeImpl : MediaSessionBridge {
    var mediaSession: MediaSession? = null
        private set

    override fun onSessionPlayerCreated(
        context: Context,
        player: Player,
    ) {
        if (mediaSession != null) return
        val builder =
            MediaSession
                .Builder(context, player)
                .setCallback(CuratedContentSessionCallback)
        sessionActivityIntent(context)?.let { builder.setSessionActivity(it) }
        mediaSession = builder.build()
    }

    override fun onSessionStarted(context: Context) {
        // Sessions start from foreground UI, so startService is legal; the catch guards the
        // background-start race (e.g. a tap landing right as the app backgrounds) — the session
        // still plays, it just misses the system surface.
        runCatching { context.startService(Intent(context, PlaybackSessionService::class.java)) }
            .onFailure { e ->
                Tracker.log("playback.service=start")
                Tracker.track(RuntimeException("Media session service can't start", e))
            }
    }

    override fun onSessionEnded(context: Context) {
        context.stopService(Intent(context, PlaybackSessionService::class.java))
    }

    /** Tapping the media notification brings the existing task to front, state intact. */
    private fun sessionActivityIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
