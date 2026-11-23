/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * Interaction to Next Paint (INP) analog for Android: per tap in the measured block, the time from
 * the tap's `ACTION_UP` reaching the system to the **present** time of the first app frame drawn
 * after the app finished handling it. One sample per tap, so Macrobenchmark reports percentiles
 * across every tap of every iteration.
 *
 * Read straight from the Perfetto trace Macrobenchmark already captures (atrace `input` + the
 * FrameTimeline data source), so the app needs no instrumentation:
 * - **Tap:** the motion events UiAutomator injects (`injectInputEventToTarget` server slices with an
 *   `interceptMotionBeforeQueueing` child), grouped into taps when one starts < [TAP_GAP_NS] after
 *   the previous one ended (injection waits for the app to finish, so a slow DOWN can't split a
 *   tap); the last one of a group is the `ACTION_UP` — where a Compose `clickable` fires.
 * - **Handled:** end of the app's first `deliverInputEvent` async slice that starts after the UP.
 * - **Response frame:** the first app `Choreographer#doFrame` ending at or after "handled" that has
 *   a FrameTimeline row — the same frame when the UP was consumed inside a running frame (batched
 *   input), else the next one.
 * - **Next paint:** that frame's SurfaceFlinger display frame end (on-screen update), joined through
 *   `display_frame_token`.
 *
 * A frame is not proof its pixels show the response: when the click is consumed inside a frame whose
 * recomposition already ran, the visible change lands one frame later, so this can under-read by one
 * vsync. Requires the app's process name to equal its package (true for this app) and API 31+ for
 * FrameTimeline. Fails the run when it can't measure every one of [expectedTaps]. Excludes touch-panel and
 * scan-out latency, like web INP — compare against INP thresholds, not end-to-end: CONTRIBUTING.md § Performance.
 */
@OptIn(ExperimentalMetricApi::class)
internal class InteractionToNextPaintMetric(
    private val expectedTaps: Int,
) : TraceMetric() {
    override fun getMeasurements(
        captureInfo: CaptureInfo,
        traceSession: TraceProcessor.Session,
    ): List<Measurement> {
        val rows = traceSession.query(query(captureInfo.targetPackageName)).toList()
        // A tap missing its response frame would otherwise drop out of the percentiles silently.
        check(rows.size == expectedTaps) {
            "Measured ${rows.size} of $expectedTaps taps: a tap or its presented response frame is missing from the trace."
        }
        return listOf(
            Measurement("inpTapToPresentMs", rows.map { it.double("tap_to_present_ms") }),
            Measurement("inpTapHandledMs", rows.map { it.double("handled_ms") }),
        )
    }

    private fun query(packageName: String) =
        """
        with app as (select upid, pid from process where name = '$packageName'),
        inj as (
          select s.ts, s.dur from slice s join slice c on c.parent_id = s.id
          where s.name = 'AIDL::java::IInputManager::injectInputEventToTarget::server'
            and c.name = 'interceptMotionBeforeQueueing'
        ),
        inj_g as (select ts, case when ts - lag(ts + dur) over (order by ts) < $TAP_GAP_NS then 0 else 1 end as new_tap from inj),
        inj_n as (select ts, sum(new_tap) over (order by ts) as tap from inj_g),
        taps as (select tap, max(ts) as up_ts from inj_n group by tap),
        deliver as (
          select s.ts, s.ts + s.dur as end_ts from slice s join process_track pt on s.track_id = pt.id join app using(upid)
          where s.name = 'deliverInputEvent'
        ),
        up_handled as (
          select tap, up_ts, end_ts as up_end from (
            select t.tap, t.up_ts, d.end_ts, row_number() over (partition by t.tap order by d.ts) as rn
            from taps t join deliver d on d.ts >= t.up_ts
          ) where rn = 1
        ),
        app_frame as (
          select cast(a.name as int) as vsync, a.display_frame_token from actual_frame_timeline_slice a join app using(upid)
        ),
        doframe as (
          select s.ts, s.dur, cast(substr(s.name, 23) as int) as vsync
          from slice s join thread_track tt on s.track_id = tt.id join thread t using(utid)
          join app a on t.upid = a.upid and t.tid = a.pid
          where s.name glob 'Choreographer#doFrame *'
        ),
        resp as (
          select * from (
            select u.tap, u.up_ts, u.up_end, af.display_frame_token,
              row_number() over (partition by u.tap order by f.ts) as rn
            from up_handled u join doframe f on f.ts + f.dur >= u.up_end join app_frame af on af.vsync = f.vsync
          ) where rn = 1
        ),
        sf as (
          select a.display_frame_token, max(a.ts + a.dur) as present_ts from actual_frame_timeline_slice a
          join process p using(upid) where p.name glob '*surfaceflinger*' group by a.display_frame_token
        )
        select r.tap,
          (r.up_end - r.up_ts) / 1e6 as handled_ms,
          (sf.present_ts - r.up_ts) / 1e6 as tap_to_present_ms
        from resp r join sf using(display_frame_token) order by r.tap
        """.trimIndent()

    private companion object {
        // UiAutomator's click injects the UP right after the DOWN finishes; journey taps are seconds apart.
        const val TAP_GAP_NS = 300_000_000L
    }
}
