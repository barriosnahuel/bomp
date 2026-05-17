/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.ui.home.CollectionSheetRequest
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Modal bottom sheet that renders the create / rename collection form. Hosted near the top of
 * [com.github.barriosnahuel.vossosunboton.ui.home.LandingScreen] so any surface (filter chip row,
 * Vault FAB, Add/Edit tagging chips) can request it by calling
 * [SoundsViewModel.requestCreateCollection] or [SoundsViewModel.requestRenameCollection].
 *
 * Error handling: the duplicate-name + length errors are surfaced inline below the field. The
 * sheet only dismisses on a successful save or on explicit Cancel — never on first error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionSheetHost(viewModel: SoundsViewModel) {
    val request by viewModel.activeCollectionSheet.collectAsState()
    val activeRequest = request ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = { viewModel.dismissCollectionSheet() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CollectionSheetBody(
            request = activeRequest,
            onCancel = {
                coroutineScope.launch { sheetState.hide() }
                viewModel.dismissCollectionSheet()
            },
            onSubmit = { name ->
                val result =
                    when (val r = activeRequest) {
                        is CollectionSheetRequest.Create -> viewModel.createCollection(name, r.access).map { Unit }
                        is CollectionSheetRequest.Rename -> viewModel.renameCollection(r.id, name)
                    }
                if (result.isSuccess) {
                    sheetState.hide()
                    viewModel.dismissCollectionSheet()
                }
                result
            },
        )
    }
}

@Composable
private fun CollectionSheetBody(
    request: CollectionSheetRequest,
    onCancel: () -> Unit,
    onSubmit: suspend (String) -> Result<Unit>,
) {
    val initial =
        when (request) {
            is CollectionSheetRequest.Create -> ""
            is CollectionSheetRequest.Rename -> request.currentName
        }
    var name by rememberSaveable { mutableStateOf(initial) }
    var errorRes by rememberSaveable { mutableStateOf<Int?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val isPrivate =
        when (request) {
            is CollectionSheetRequest.Create -> request.access == CollectionAccess.PRIVATE
            is CollectionSheetRequest.Rename -> false
        }
    val titleRes =
        when (request) {
            is CollectionSheetRequest.Rename -> R.string.app_collection_sheet_title_rename
            is CollectionSheetRequest.Create ->
                if (request.access == CollectionAccess.PRIVATE) {
                    R.string.app_collection_sheet_title_new_private
                } else {
                    R.string.app_collection_sheet_title_new_public
                }
        }
    val placeholderRes =
        if (isPrivate) {
            R.string.app_collection_sheet_name_placeholder_private
        } else {
            R.string.app_collection_sheet_name_placeholder_public
        }

    val attemptSubmit: () -> Unit = {
        when {
            name.isBlank() -> errorRes = R.string.app_collection_sheet_error_name_required
            name.length > COLLECTION_NAME_MAX -> errorRes = R.string.app_collection_sheet_error_name_too_long
            else -> {
                errorRes = null
                coroutineScope.launch {
                    val result = onSubmit(name.trim())
                    if (result.isFailure) {
                        errorRes = R.string.app_collection_sheet_error_name_taken
                    }
                }
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.XL, vertical = Spacing.LG),
        verticalArrangement = Arrangement.spacedBy(Spacing.MD),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(COLLECTION_NAME_MAX_INPUT)
                errorRes = null
            },
            label = { Text(stringResource(R.string.app_collection_sheet_name_label)) },
            placeholder = { Text(stringResource(placeholderRes)) },
            isError = errorRes != null,
            supportingText = {
                errorRes?.let { res ->
                    val msg =
                        if (res == R.string.app_collection_sheet_error_name_too_long) {
                            stringResource(res, COLLECTION_NAME_MAX)
                        } else {
                            stringResource(res)
                        }
                    Text(msg, color = MaterialTheme.colorScheme.error)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { attemptSubmit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (isPrivate) {
            Text(
                text = stringResource(R.string.app_collection_sheet_private_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.SM))
        Button(
            onClick = attemptSubmit,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Text(stringResource(R.string.app_collection_sheet_save))
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.app_collection_sheet_cancel))
        }
    }
}

private const val COLLECTION_NAME_MAX = 80
private const val COLLECTION_NAME_MAX_INPUT = COLLECTION_NAME_MAX
