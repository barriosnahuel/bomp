/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel

/**
 * Confirmation dialog for collection deletion. Observes [SoundsViewModel.pendingCollectionDelete]
 * — `null` means no dialog is showing. Hosted near the top of `LandingScreen` so the dialog
 * survives tab switches and is reachable from every surface that emits a delete intent
 * ([AssignCollectionSheet] today; future surfaces — a dedicated "manage collections" screen —
 * will plug into the same VM state).
 *
 * The body switches between two copies depending on whether the collection has any audios:
 * - Empty → soft confirm ("This collection is empty. Are you sure?")
 * - Non-empty → "%1$d audios will leave this collection. …"
 *
 * **System collections** (the seeded Baúl) reach this dialog only via a programming error: the
 * VM guards them in `requestDeleteConfirmation` and the AssignCollectionSheet row hides the
 * overflow entry-point. If we ever see a system id here we render nothing rather than risk an
 * accidental delete (the repo would throw too, but failing silently in production is friendlier
 * than crashing).
 */
@Composable
internal fun CollectionDeleteDialog(viewModel: SoundsViewModel) {
    val pendingId = viewModel.pendingCollectionDelete.collectAsState().value
    val collections by viewModel.collections.collectAsState()
    val target = pendingId?.let { id -> collections.firstOrNull { it.id == id && !it.isSystem } } ?: return

    val displayName = target.name
    val audioCount = target.audioIds.size

    AlertDialog(
        onDismissRequest = { viewModel.dismissDeleteConfirmation() },
        title = {
            Text(
                text = stringResource(R.string.app_collection_delete_dialog_title, displayName),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text =
                    if (audioCount == 0) {
                        stringResource(R.string.app_collection_delete_dialog_body_empty)
                    } else {
                        pluralStringResource(
                            R.plurals.app_collection_delete_dialog_body_with_audios,
                            audioCount,
                            audioCount,
                        )
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = { viewModel.confirmCollectionDelete() }) {
                Text(
                    text = stringResource(R.string.app_collection_delete_dialog_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
                Text(stringResource(R.string.app_collection_delete_dialog_dismiss))
            }
        },
    )
}
