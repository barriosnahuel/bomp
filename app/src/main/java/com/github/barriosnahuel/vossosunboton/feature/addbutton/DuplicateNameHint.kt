/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTracker
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reactive lookup that returns the existing [Sound] matching [typedName], or `null` if there is no
 * match. Source is the full library (`SoundsRepository.sounds`, custom + bundled), so a typed name
 * matching any existing Bomp surfaces the hint. Match is case-insensitive, whitespace-trimmed; in
 * Edit mode the [AddButtonMode.Edit.sound] is excluded by id (ADR 0008) so editing a Bomp without
 * renaming it doesn't see a hint pointing at itself.
 *
 * Collection runs on IO inside `produceState`; the StateFlow is `distinctUntilChanged` so unchanged
 * emissions don't churn recomposition.
 */
@Composable
internal fun rememberDuplicateNameMatch(
    context: Context,
    typedName: String,
    mode: AddButtonMode,
): Sound? {
    val existingSounds by produceState(initialValue = emptyList<Sound>(), context) {
        SoundsRepository(context.applicationContext, onError = Tracker::track).sounds.collect { value = it }
    }
    return remember(typedName, existingSounds, mode) {
        val trimmed = typedName.trim().lowercase()
        if (trimmed.isBlank()) {
            null
        } else {
            existingSounds.firstOrNull { existing -> matchesDuplicate(existing, trimmed, mode) }
        }
    }
}

/**
 * Predicate isolated from the [rememberDuplicateNameMatch] body to keep `AddButtonScreen`'s
 * cyclomatic complexity below the detekt threshold; the case-insensitive equality and the
 * Edit-mode self-exclusion are both encoded here.
 */
private fun matchesDuplicate(
    existing: Sound,
    trimmedTypedName: String,
    mode: AddButtonMode,
): Boolean {
    if (existing.name.trim().lowercase() != trimmedTypedName) return false
    return mode !is AddButtonMode.Edit || existing.id != mode.sound.id
}

/**
 * `supportingText` slot for the name [OutlinedTextField] in [AddButtonScreen]. Renders the
 * error/character-counter row plus the non-blocking duplicate-name hint when applicable. Extracted
 * out so the host screen stays under the detekt cyclomatic-complexity threshold.
 */
@Composable
internal fun NameFieldSupportingText(
    error: String?,
    nameLength: Int,
    maxNameLength: Int,
    duplicateMatch: Sound?,
    context: Context,
    tracker: AnalyticsTracker,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (error != null) {
                Text(
                    text = error,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(text = "$nameLength/$maxNameLength")
        }
        // Suppressed while `error` is showing so the two messages don't fight for the same slot.
        if (error == null && duplicateMatch != null) {
            DuplicateNameHint(context = context, match = duplicateMatch, tracker = tracker)
        }
    }
}

/**
 * Hint shown under the name field when the typed name matches an existing Bomp. Non-blocking —
 * the save button stays enabled because two Bomps can legitimately share a name now that identity
 * is keyed by [Sound.id] (ADR 0008).
 *
 * The inline button is a play/stop toggle scoped to *this specific match*: tap → play from 0; tap
 * again while playing → stop and reset (no pause, no resume, no seeker). Routed through
 * [PlayerController.startPlayingUri]/[PlayerController.stopPlayingSound] — the same Uri-preview
 * path [AudioPreview] uses — so the [PlayerController.playbackState] StateFlow reflects the live
 * status (the Sound playback path goes through a single-listener slot already taken by
 * `SoundsViewModel`, so we cannot observe it from here). Reset-on-start is the intended UX
 * difference vs the home list and matches a quick "is this the audio I'm thinking of?" probe.
 */
@Composable
internal fun DuplicateNameHint(
    context: Context,
    match: Sound,
    tracker: AnalyticsTracker,
) {
    // Custom-sound URIs go through getExternalFilesDir(), which trips StrictMode DiskReadViolation
    // if resolved on the main thread (PreviewSlot in AddButtonScreen.kt has the same pattern). The
    // null window is invisible because the icon defaults to "play" and the click handler no-ops
    // while resolution is pending.
    val matchUri: Uri? by produceState<Uri?>(initialValue = null, match.id) {
        value = withContext(Dispatchers.IO) { match.toPreviewUri(context) }
    }
    val controller = PlayerControllerFactory.instance
    val playbackState by controller.playbackState.collectAsStateWithLifecycle()
    val isPlayingThisMatch =
        matchUri != null && playbackState?.uri == matchUri && playbackState?.isPlaying == true

    // Same disposal contract as AudioPreview: when the hint goes away (typed name no longer
    // matches, screen backed out, match changes id) our own preview stops.
    StopPreviewOnDispose(matchUri)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_addbutton_duplicate_name_hint),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        IconButton(
            onClick = {
                val resolvedUri = matchUri ?: return@IconButton
                if (isPlayingThisMatch) {
                    controller.stopPlayingSound()
                } else {
                    tracker.log(AnalyticsEvent.DuplicateNameHintPlay)
                    controller.startPlayingUri(context, resolvedUri)
                }
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = if (isPlayingThisMatch) rememberVectorPainter(AppIcons.Stop) else painterResource(R.drawable.app_ic_play_arrow),
                contentDescription =
                    stringResource(
                        if (isPlayingThisMatch) {
                            R.string.app_addbutton_duplicate_name_hint_stop_description
                        } else {
                            R.string.app_addbutton_duplicate_name_hint_play_description
                        },
                    ),
                tint = MaterialTheme.colorScheme.primaryContainer,
            )
        }
    }
}

/**
 * Resolves the preview [Uri] for [this] sound. Bundled sounds use the `android.resource://` scheme
 * keyed by `rawRes` (build-stable per install — see ADR 0008 § *Why `"bundled:$rawRes"` for bundled
 * sounds*); custom sounds resolve through the same [getFile] helper [AddButtonScreen]'s
 * `PreviewSlot` uses, so file lookup semantics stay aligned.
 */
private fun Sound.toPreviewUri(context: Context): Uri =
    if (isBundled()) {
        "android.resource://${context.packageName}/$rawRes".toUri()
    } else {
        getFile(context, file!!).toUri()
    }
