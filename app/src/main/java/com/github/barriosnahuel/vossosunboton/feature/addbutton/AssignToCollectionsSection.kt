/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateStatus
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

/**
 * "Assign to collections" section of the Add/Edit Bomp screen.
 *
 * Two stacked blocks per spec § 3.7:
 *
 * 1. **Public** — multi-select chips visible without any gate. Trailing `+ New` chip opens the
 *    create-collection sheet.
 *
 * 2. **Private** — closed by default with a single CTA `"Show private collections (requires
 *    unlock)"`. When the user authenticates, the chips reveal. Auth scope is per Add/Edit
 *    session (spec § 7) — leaving and re-entering re-asks. The reveal/hide toggle is local
 *    to the section, not a VM concern.
 *
 * When biometric is **unavailable** on the device, the locked CTA is replaced by an inline
 * "shown because no protection" hint and the chips are visible immediately. This preserves the
 * tagging path without paternalistically blocking the user on an unprotected device — § 6.
 *
 * Tagging changes are committed by the parent at save time; the section publishes deltas via
 * the `onPublicSelectionChange` / `onPrivateSelectionChange` callbacks so the parent owns the
 * truth and tests can drive the section without a real ViewModel.
 */
@Composable
internal fun AssignToCollectionsSection(
    publicCollections: List<Collection>,
    privateCollections: List<Collection>,
    publicSelection: Set<String>,
    privateSelection: Set<String>,
    biometricStatus: BiometricGateStatus,
    privateRevealed: Boolean,
    onPublicSelectionChange: (Set<String>) -> Unit,
    onPrivateSelectionChange: (Set<String>) -> Unit,
    onCreatePublicRequested: () -> Unit,
    onCreatePrivateRequested: () -> Unit,
    onRequestPrivateUnlock: () -> Unit,
    onHidePrivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
        Text(
            text = stringResource(R.string.app_addbutton_collections_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        PublicChipsBlock(
            collections = publicCollections,
            selection = publicSelection,
            onSelectionChange = onPublicSelectionChange,
            onCreateRequested = onCreatePublicRequested,
        )

        Spacer(modifier = Modifier.height(Spacing.XS))

        PrivateBlock(
            collections = privateCollections,
            selection = privateSelection,
            biometricStatus = biometricStatus,
            revealed = privateRevealed,
            onSelectionChange = onPrivateSelectionChange,
            onCreateRequested = onCreatePrivateRequested,
            onRequestUnlock = onRequestPrivateUnlock,
            onHide = onHidePrivate,
        )
    }
}

@Composable
private fun PublicChipsBlock(
    collections: List<Collection>,
    selection: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onCreateRequested: () -> Unit,
) {
    Text(
        text = stringResource(R.string.app_addbutton_collections_public_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(Spacing.XS))
    if (collections.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_addbutton_collections_public_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            AssistChip(
                onClick = onCreateRequested,
                label = { Text(stringResource(R.string.app_addbutton_collections_new_chip)) },
            )
        }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.SM)) {
            collections.forEach { collection ->
                val selected = collection.id in selection
                FilterChip(
                    selected = selected,
                    onClick = {
                        onSelectionChange(
                            if (selected) selection - collection.id else selection + collection.id,
                        )
                    },
                    label = { Text(collection.name) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                )
            }
            AssistChip(
                onClick = onCreateRequested,
                label = { Text(stringResource(R.string.app_addbutton_collections_new_chip)) },
            )
        }
    }
}

@Composable
private fun PrivateBlock(
    collections: List<Collection>,
    selection: Set<String>,
    biometricStatus: BiometricGateStatus,
    revealed: Boolean,
    onSelectionChange: (Set<String>) -> Unit,
    onCreateRequested: () -> Unit,
    onRequestUnlock: () -> Unit,
    onHide: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(Spacing.XS))
        Text(
            text = stringResource(R.string.app_addbutton_collections_private_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(modifier = Modifier.height(Spacing.XS))

    val isUnprotected = biometricStatus != BiometricGateStatus.AVAILABLE
    val showChips = revealed || isUnprotected

    if (!showChips) {
        TextButton(
            onClick = onRequestUnlock,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "biometric unlock cta"
                    },
        ) {
            Text(stringResource(R.string.app_addbutton_collections_private_locked_cta))
        }
        return
    }

    if (isUnprotected) {
        Text(
            text = stringResource(R.string.app_addbutton_collections_private_unprotected_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(Spacing.XS))
    }
    val systemFallback = stringResource(R.string.app_vault_baul_name)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.SM)) {
        collections.forEach { collection ->
            val selected = collection.id in selection
            val displayName = if (collection.isSystem) systemFallback else collection.name
            FilterChip(
                selected = selected,
                onClick = {
                    onSelectionChange(
                        if (selected) selection - collection.id else selection + collection.id,
                    )
                },
                label = { Text(displayName) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
        AssistChip(
            onClick = onCreateRequested,
            label = { Text(stringResource(R.string.app_addbutton_collections_new_private_chip)) },
        )
    }
    if (revealed && !isUnprotected) {
        TextButton(onClick = onHide) {
            Text(stringResource(R.string.app_addbutton_collections_private_unlocked_cta))
        }
    }
}
