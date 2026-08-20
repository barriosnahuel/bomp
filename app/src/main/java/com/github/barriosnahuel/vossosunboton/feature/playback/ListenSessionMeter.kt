/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.playback

/**
 * Accumulates how much audio a listen session actually consumed, from the position samples the
 * player publishes (~every 100 ms).
 *
 * Contract, and each rule's reason:
 * - Only forward movement accrues. A pause holds the position, so it adds nothing.
 * - Seeking backwards never subtracts: re-listening is consumption too, and a negative delta would
 *   let one scrub erase minutes of real listening.
 * - A jump larger than [MAX_STEP_MS] is a scrub, not playback, and accrues nothing — otherwise
 *   dragging the waveform forward would credit audio nobody heard and could push the total past
 *   the audio's own length.
 *
 * The result is depth of listening, which is not time on screen — that distinction is the whole
 * point of the measure. [restore] rebuilds the tally after an Activity recreate so a rotation
 * mid-listen does not reset it to zero.
 *
 * Not thread-safe: samples arrive on the main thread, one session at a time.
 */
internal class ListenSessionMeter(
    initialListenedMs: Int = 0,
) {
    private var lastPositionMs: Int? = null

    var listenedMs: Int = initialListenedMs
        private set

    /** Longest audio duration seen this session, so the end event reports the audio it measured. */
    var durationMs: Int = 0
        private set

    fun onPosition(positionMs: Int) {
        val previous = lastPositionMs
        if (previous != null) {
            val delta = positionMs - previous
            if (delta in 1..MAX_STEP_MS) listenedMs += delta
        }
        lastPositionMs = positionMs
    }

    fun onDuration(durationMs: Int?) {
        if (durationMs != null && durationMs > this.durationMs) this.durationMs = durationMs
    }

    companion object {
        /**
         * Ceiling for one sample-to-sample step. Samples land ~every 100 ms; 1 s is generous room
         * for a stalled main thread while still far below any deliberate scrub.
         */
        const val MAX_STEP_MS = 1_000
    }
}
