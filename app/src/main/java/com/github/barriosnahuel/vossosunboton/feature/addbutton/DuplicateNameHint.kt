/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTracker
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository

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
 * is keyed by [Sound.id] (ADR 0008). The inline play button routes through
 * [PlayerControllerFactory] (ADR 0005) so it integrates with the same pause/resume semantics
 * (ADR 0007) the main list uses.
 */
@Composable
internal fun DuplicateNameHint(
    context: Context,
    match: Sound,
    tracker: AnalyticsTracker,
) {
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
                tracker.log(AnalyticsEvent.DuplicateNameHintPlay)
                PlayerControllerFactory.instance.startPlayingSound(context, match)
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.app_addbutton_duplicate_name_hint_play_description),
                tint = MaterialTheme.colorScheme.primaryContainer,
            )
        }
    }
}
