/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel

/**
 * Hosts the destructive delete-collection dialog as an overlay on top of
 * [com.github.barriosnahuel.vossosunboton.ui.home.LandingScreen]. The immersive collection view
 * was dropped in this iteration — the Vault is now a flat list of audios, not a list of
 * collection cards, so there is no separate "open a collection" surface anymore.
 */
@Composable
internal fun VaultRoutingOverlay(viewModel: SoundsViewModel) {
    val collections by viewModel.collections.collectAsState()
    val pendingDeleteId by viewModel.pendingCollectionDelete.collectAsState()

    val pendingDelete = pendingDeleteId?.let { id -> collections.firstOrNull { it.id == id } }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = {
                Text(stringResource(R.string.app_collection_delete_dialog_title, pendingDelete.name))
            },
            text = {
                Text(
                    if (pendingDelete.audioIds.isEmpty()) {
                        stringResource(R.string.app_collection_delete_dialog_body_empty)
                    } else {
                        stringResource(
                            R.string.app_collection_delete_dialog_body_with_audios,
                            pendingDelete.audioIds.size,
                        )
                    },
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
}
