/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.ListenSessionMeter

/**
 * Listen-session pair (`listen_session_start` / `listen_session_end`) for audios played from the
 * grid — the Botonera counterpart of the Vault's screen-scoped `TrackListenSession`.
 *
 * One session per playback of one audio. It opens when the audio actually starts, and survives a
 * pause + resume of that same audio — including a pause forced by a Uri preview, which never reports
 * a start. It closes when the audio completes or is stopped, when a different Sound (or a long-form
 * listen session) starts in its place, or via [close] when the Bomper leaves the screen. Only
 * advancing playback while playing accrues: progress ticks that land while paused belong to some
 * other playback (a preview, a recorder review) and are ignored.
 *
 * Driven from the [com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerListener]
 * callbacks, so main-thread only.
 */
internal class GridListenSessions(
    private val tracker: () -> AnalyticsTracker,
) {
    private class Session(
        val soundId: String,
        val surface: String,
    ) {
        val meter = ListenSessionMeter()
        var playing = true
    }

    private var current: Session? = null

    /**
     * An audio started or resumed. [tracked] is false for playbacks that must not open a grid
     * session (the welcome sticker, a long-form listen session); they still close the open one.
     */
    fun onStart(
        soundId: String,
        surface: String,
        durationMs: Int,
        positionMs: Int,
        tracked: Boolean,
    ) {
        val open = current
        val session =
            if (open != null && open.soundId == soundId && tracked) {
                open
            } else {
                close()
                if (!tracked) return
                Session(soundId, surface).also {
                    current = it
                    tracker().log(AnalyticsEvent.ListenSessionStart(surface = surface))
                }
            }
        session.playing = true
        session.meter.onDuration(durationMs)
        session.meter.onPosition(positionMs)
    }

    fun onProgress(positionMs: Int) {
        val session = current?.takeIf { it.playing } ?: return
        session.meter.onPosition(positionMs)
    }

    /** Paused or preempted: accrues up to [positionMs], then stops accruing until the next start. */
    fun onPause(
        soundId: String,
        positionMs: Int,
    ) {
        val session = current?.takeIf { it.soundId == soundId } ?: return
        if (session.playing) session.meter.onPosition(positionMs)
        session.playing = false
    }

    /** Stopped or completed. A completion credits the tail between the last tick and the end. */
    fun onStop(
        soundId: String,
        completed: Boolean,
    ) {
        val session = current?.takeIf { it.soundId == soundId } ?: return
        if (completed && session.playing) session.meter.onPosition(session.meter.durationMs)
        close()
    }

    /** Ends the open session, if any. Idempotent. */
    fun close() {
        val session = current ?: return
        current = null
        tracker().log(
            AnalyticsEvent.ListenSessionEnd(
                surface = session.surface,
                listenedMs = session.meter.listenedMs,
                durationMs = session.meter.durationMs,
            ),
        )
    }
}
