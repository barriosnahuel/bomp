/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTracker
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTransport
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.feature.playback.ListenSessionMeter

/**
 * Transport use from the app's own screen. Its system-surface twin is reported by
 * `ListenSessionEngine`, so the `origin` param compares one against the other.
 */
internal fun AnalyticsTracker.logScreenTransport(action: String) =
    log(AnalyticsEvent.ListenTransport(action = action, origin = AnalyticsTransport.ORIGIN_SCREEN))

/**
 * Business tracking for a listen session: that it happened, how much of the audio actually got
 * heard, and whether it kept playing once the app left the foreground (the feature's promise).
 *
 * The session is the SCREEN, not the play tap — a resume after a pause re-enters
 * `startListenSession`, so counting starts there would report one session per tap and deflate every
 * per-session ratio. Both ends of the pair are skipped on a configuration change: a rotation tears
 * the composition down and rebuilds it, and counting that would inflate starts against ends. The
 * meter and the "already reported" flag survive that rebuild via `rememberSaveable`, so a rotation
 * mid-listen neither restarts the tally nor re-opens the session.
 */
@Composable
internal fun TrackListenSession(
    tracker: AnalyticsTracker,
    soundId: String,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int?,
    isChangingConfigurations: () -> Boolean = rememberConfigurationChangeProbe(),
) {
    val meter = rememberSaveable(soundId, saver = ListenSessionMeterSaver) { ListenSessionMeter() }
    var sessionReported by rememberSaveable(soundId) { mutableStateOf(false) }

    // Read as parameters, not lambdas: the meter only accrues if this composable is subscribed to
    // the position state, and a lambda read inside SideEffect subscribes to nothing.
    SideEffect {
        meter.onPosition(positionMs)
        meter.onDuration(durationMs)
    }

    LaunchedEffect(soundId) {
        if (!sessionReported) {
            sessionReported = true
            tracker.log(AnalyticsEvent.ListenSessionStart(surface = CanonicalScreenName.VAULT_LISTEN))
        }
    }

    // The observer and the teardown outlive the recomposition that created them, so they read the
    // latest value through this handle instead of capturing the one from first composition.
    val playingNow by rememberUpdatedState(isPlaying)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, soundId) {
        var backgroundReported = false
        val observer =
            LifecycleEventObserver { _, event ->
                val leftForeground = event == Lifecycle.Event.ON_STOP && !isChangingConfigurations()
                if (leftForeground && playingNow && !backgroundReported) {
                    backgroundReported = true
                    tracker.log(AnalyticsEvent.ListenBackgrounded(surface = CanonicalScreenName.VAULT_LISTEN))
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!isChangingConfigurations()) {
                tracker.log(
                    AnalyticsEvent.ListenSessionEnd(
                        surface = CanonicalScreenName.VAULT_LISTEN,
                        listenedMs = meter.listenedMs,
                        durationMs = meter.durationMs,
                    ),
                )
            }
        }
    }
}

/** Reads "is this teardown a rotation?" off the host Activity; injectable so tests can drive it. */
@Composable
private fun rememberConfigurationChangeProbe(): () -> Boolean {
    val activity = LocalActivity.current
    return remember(activity) { { activity?.isChangingConfigurations == true } }
}

/** The tally is durable progress, so it survives an Activity recreate (CLAUDE.md § Stateful Composables). */
private val ListenSessionMeterSaver =
    listSaver<ListenSessionMeter, Int>(
        save = { listOf(it.listenedMs, it.durationMs) },
        restore = { saved -> ListenSessionMeter(initialListenedMs = saved[0]).apply { onDuration(saved[1]) } },
    )
