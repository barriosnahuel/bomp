/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.barriosnahuel.vossosunboton.R

/**
 * Asked before a fresh recording when a draft is still pending, so starting a new take can never
 * silently drop the unsaved one (ADR 0019 § Draft recovery). Same title as [RecorderDraftBanner] so
 * the user recognizes it as the same Bomp.
 *
 * Stateless: [onKeep] resumes the pending draft (the primary, keep-it path); [onRecordNew] drops it and
 * starts fresh; [onDismiss] changes nothing.
 */
@Composable
internal fun RecorderDraftGuardDialog(
    onKeep: () -> Unit,
    onRecordNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_recorder_draft_banner_message)) },
        text = { Text(stringResource(R.string.app_recorder_draft_guard_message)) },
        confirmButton = {
            TextButton(onClick = onKeep) { Text(stringResource(R.string.app_recorder_draft_guard_keep)) }
        },
        dismissButton = {
            TextButton(onClick = onRecordNew) { Text(stringResource(R.string.app_recorder_draft_guard_record_new)) }
        },
    )
}
