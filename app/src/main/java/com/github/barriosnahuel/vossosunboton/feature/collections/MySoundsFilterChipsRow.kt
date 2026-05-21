/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

/**
 * Horizontal row of filter chips above the My Sounds list. First chip is the unfiltered "All"
 * pseudo-chip (resets [activeFilterId] to `null`); the rest are the user's public collections
 * in creation order, followed by a trailing `+ New` chip that opens the create-collection sheet.
 *
 * Empty-state behavior (no public collections yet) is handled by the caller, which simply does
 * not render this row.
 */
@Composable
internal fun MySoundsFilterChipsRow(
    publicCollections: List<Collection>,
    activeFilterId: String?,
    onFilterSelected: (String?) -> Unit,
    onCreateRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allLabel = stringResource(R.string.app_my_sounds_filter_all)
    val chipDescription = stringResource(R.string.app_my_sounds_filter_chip_description)
    val clearDescription = stringResource(R.string.app_my_sounds_filter_active_clear_description)
    val newChipLabel = stringResource(R.string.app_my_sounds_filter_chip_new)
    val newChipDescription = stringResource(R.string.app_my_sounds_filter_chip_new_description)

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.LG, vertical = Spacing.SM),
        horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
    ) {
        item(key = "filter_all") {
            FilterChip(
                selected = activeFilterId == null,
                onClick = { onFilterSelected(null) },
                label = { Text(allLabel) },
                modifier =
                    Modifier.semantics {
                        contentDescription = if (activeFilterId == null) clearDescription else allLabel
                    },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
        items(publicCollections, key = { it.id }) { collection ->
            // System collections (e.g. the seeded "Baúl") store a literal name in the DataStore so
            // legacy users keep that label; we override it with the locale-aware resource at render
            // time so an English UI doesn't display the Spanish-seeded literal.
            val displayName =
                if (collection.isSystem) {
                    stringResource(R.string.app_vault_baul_name)
                } else {
                    collection.name
                }
            FilterChip(
                selected = activeFilterId == collection.id,
                onClick = {
                    val next = if (activeFilterId == collection.id) null else collection.id
                    onFilterSelected(next)
                },
                label = { Text(displayName) },
                modifier =
                    Modifier.semantics {
                        contentDescription = String.format(chipDescription, displayName)
                    },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
        item(key = "filter_new") {
            Spacer(modifier = Modifier.width(Spacing.XS))
            AssistChip(
                onClick = onCreateRequested,
                label = { Text(newChipLabel) },
                modifier = Modifier.semantics { contentDescription = newChipDescription },
                colors =
                    AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}
