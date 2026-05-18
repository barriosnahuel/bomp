/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.model.CollectionProfile
import com.github.barriosnahuel.vossosunboton.model.data.manager.CollectionsRepository
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-flow ModalBottomSheet for creating a collection from the Add/Edit screen.
 *
 * Lives here (vs. reusing `CollectionSheetHost` from the Landing scope) because the Add/Edit screen
 * runs in `AddButtonActivity` — a separate ViewModelStoreOwner — and `CollectionSheetHost` reads
 * from `SoundsViewModel` which only exists on the Landing side. The local sheet writes directly
 * via `CollectionsRepository` and signals success through [onCreated] so the parent can auto-tag
 * the in-progress audio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InlineCollectionCreateSheet(
    scope: CollectionAccess,
    onDismiss: () -> Unit,
    onCreated: (Collection) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tracker = remember(context) { AnalyticsTrackerProvider.get(context.applicationContext) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus the field + show the IME once the sheet has finished animating in. Same
    // staging trick the canonical CollectionSheetHost uses — keying off `sheetState.currentValue`
    // == Expanded keeps the IME slide-in synced with the sheet's tween instead of firing on
    // first composition (which made the keyboard appear *before* the sheet was even visible).
    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue != SheetValue.Expanded) return@LaunchedEffect
        runCatching { focusRequester.requestFocus() }
            .onFailure {
                com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
                    .log("inline_collection_sheet.focus_request_failed")
                com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
                    .track(RuntimeException("InlineCollectionCreateSheet focus request failed", it))
            }
        keyboardController?.show()
    }

    var name by rememberSaveable { mutableStateOf("") }
    var errorRes by rememberSaveable { mutableStateOf<Int?>(null) }
    var inflight by rememberSaveable { mutableStateOf(false) }

    val attemptSubmit: () -> Unit = {
        when {
            name.isBlank() -> errorRes = R.string.app_collection_sheet_error_name_required
            name.length > COLLECTION_NAME_MAX -> errorRes = R.plurals.app_collection_sheet_error_name_too_long
            inflight -> Unit
            else -> {
                errorRes = null
                inflight = true
                coroutineScope.launch {
                    val outcome =
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val profile =
                                    when (scope) {
                                        CollectionAccess.PUBLIC -> CollectionProfile.GENERIC_PUBLIC
                                        CollectionAccess.PRIVATE -> CollectionProfile.VAULT
                                    }
                                CollectionsRepository(context, onError = Tracker::track)
                                    .create(name.trim(), profile)
                            }
                        }
                    inflight = false
                    outcome
                        .onSuccess { created ->
                            tracker.log(
                                AnalyticsEvent.CollectionCreate(
                                    scope = if (created.isPublic) "public" else "private",
                                    audios = 0,
                                ),
                            )
                            sheetState.hide()
                            onCreated(created)
                        }.onFailure {
                            errorRes = R.string.app_collection_sheet_error_name_taken
                        }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.XL, vertical = Spacing.LG),
            verticalArrangement = Arrangement.spacedBy(Spacing.MD),
        ) {
            Text(
                text =
                    stringResource(
                        if (scope == CollectionAccess.PRIVATE) {
                            R.string.app_collection_sheet_title_new_private
                        } else {
                            R.string.app_collection_sheet_title_new_public
                        },
                    ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(COLLECTION_NAME_MAX)
                    errorRes = null
                },
                label = { Text(stringResource(R.string.app_collection_sheet_name_label)) },
                placeholder = {
                    Text(
                        stringResource(
                            if (scope == CollectionAccess.PRIVATE) {
                                R.string.app_collection_sheet_name_placeholder_private
                            } else {
                                R.string.app_collection_sheet_name_placeholder_public
                            },
                        ),
                    )
                },
                isError = errorRes != null,
                supportingText = {
                    errorRes?.let { res ->
                        val msg =
                            if (res == R.plurals.app_collection_sheet_error_name_too_long) {
                                pluralStringResource(res, COLLECTION_NAME_MAX, COLLECTION_NAME_MAX)
                            } else {
                                stringResource(res)
                            }
                        Text(msg, color = MaterialTheme.colorScheme.error)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { attemptSubmit() }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
            )
            if (scope == CollectionAccess.PRIVATE) {
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
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.app_collection_sheet_cancel))
            }
        }
    }
    // Ensure the sheet animates closed when its host removes it from the tree.
    LaunchedEffect(Unit) { sheetState.show() }
}

// Mirror of CollectionSheetHost.COLLECTION_NAME_MAX. Both sheets cap the field at 20 chars so the
// resulting chip stays readable on phone-sized screens. Keeping the constant local — file-private
// — avoids a circular module dep just for a single number; if it drifts a code-review catches it.
private const val COLLECTION_NAME_MAX = 20
