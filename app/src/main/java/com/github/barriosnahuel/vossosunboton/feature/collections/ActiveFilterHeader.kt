/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.model.Collection

/**
 * Header rendered immediately below the chip row on My Sounds and Vault. It surfaces:
 *
 * 1. The currently-applied filter context — either "All sounds · 12" or "Familia · 5 sounds" —
 *    so the user always knows what they're looking at without having to scan the chip row.
 * 2. A contextual entry point (the ✎ icon) into the Manage Collections screen, deep-linked to
 *    the active collection so the user lands on its row with a transient highlight.
 *
 * Visibility is a caller decision. The header itself never hides — it's always rendered when
 * mounted. The caller mounts it only when the surface is in a state where the header makes
 * sense (chip row visible, list not in a ZRP, vault unlocked). The reserved-width box around
 * the ✎ slot guarantees zero layout shift across filter changes within those states.
 *
 * Tab semantics:
 * - On My Sounds, [activeCollection] is null when the "All" chip is selected.
 * - On Vault, [activeCollection] is null when "All" is selected and the system Baúl when its
 *   chip is selected — `isSystem == true` for that case, which hides the ✎ slot (system
 *   collections can't be renamed or deleted).
 *
 * The header's text uses `liveRegion = LiveRegionMode.Polite` so TalkBack re-announces the
 * count after a filter change without stealing focus from the current node.
 */
@Composable
internal fun ActiveFilterHeader(
    activeCollection: Collection?,
    audioCount: Int,
    isVaultContext: Boolean,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemFallback = stringResource(R.string.app_vault_baul_name)
    val displayName =
        activeCollection?.let { if (it.isSystem) systemFallback else it.name }
    val headerText =
        when {
            activeCollection == null && isVaultContext ->
                stringResource(R.string.app_active_filter_header_all_vault, audioCount)
            activeCollection == null ->
                stringResource(R.string.app_active_filter_header_all_my_sounds, audioCount)
            else ->
                pluralStringResource(
                    R.plurals.app_active_filter_header_with_collection,
                    audioCount,
                    displayName!!,
                    audioCount,
                )
        }
    val canEdit = activeCollection != null && !activeCollection.isSystem

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = headerText,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        )
        // Reserved slot — same width regardless of canEdit so changing filters doesn't shift the
        // header text horizontally. Empty Box when the action is not available; the IconButton
        // takes the same width otherwise.
        Box(modifier = Modifier.size(EDIT_SLOT_SIZE), contentAlignment = Alignment.Center) {
            if (canEdit) {
                val description =
                    stringResource(R.string.app_active_filter_header_edit_description, displayName!!)
                IconButton(onClick = onEditClick) {
                    Icon(
                        painter = painterResource(R.drawable.app_ic_edit_outlined),
                        contentDescription = description,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val HEADER_HEIGHT = 48.dp
private val EDIT_SLOT_SIZE = 48.dp
