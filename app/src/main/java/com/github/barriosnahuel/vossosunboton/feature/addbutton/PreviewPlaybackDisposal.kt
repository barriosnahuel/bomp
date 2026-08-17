/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory

/**
 * Stops [source]'s URI-bound preview when this composable leaves, unless the host Activity is only
 * being recreated (rotation, locale or theme switch).
 *
 * An Activity recreate disposes the composition exactly like a real exit, so
 * [android.app.Activity.isChangingConfigurations] is the only signal that tells them apart. Same
 * guard as `RecorderHost` and `TrackAbandonOnStop`; opting out of the recreate via manifest
 * `android:configChanges` is not the alternative — recreation can never be fully disabled, so the
 * disposal has to be correct regardless.
 *
 * Recreate-safe by construction: [PlayerControllerFactory] is process-wide, so the rebuilt
 * composition re-attaches to the same in-flight playback and the position slider keeps tracking it.
 * A process death takes the controller down with it, so nothing survives a teardown it should not.
 *
 * Never pre-empts a playback that is not ours — a stop only fires while the controller still holds
 * [source], leaving Sound-bound playback owned by other surfaces (Home/Explore, ADR 0007) alone.
 * A null [source] (not resolved yet) disposes without stopping anything.
 */
@Composable
internal fun StopPreviewOnDispose(source: Uri?) {
    val activity = LocalActivity.current
    val controller = remember { PlayerControllerFactory.instance }
    DisposableEffect(source) {
        onDispose {
            val ours = source ?: return@onDispose
            if (activity?.isChangingConfigurations == true) return@onDispose
            if (controller.playbackState.value?.uri == ours) {
                controller.stopPlayingSound()
            }
        }
    }
}
