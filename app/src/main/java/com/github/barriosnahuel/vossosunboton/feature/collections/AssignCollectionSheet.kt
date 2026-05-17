/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGate
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateResult
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateStatus
import com.github.barriosnahuel.vossosunboton.feature.vault.security.VaultSessionState
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Modal bottom sheet that lets the user assign or unassign an audio to/from any collection,
 * grouped public first then private. Triggered from the long-press → "Add to collection" menu
 * item inside [com.github.barriosnahuel.vossosunboton.ui.home.SoundItem]; hosted by
 * [com.github.barriosnahuel.vossosunboton.ui.home.LandingScreen] so a single instance survives
 * tab switches.
 *
 * Vault collections are gated by the same per-session unlock used by the Vault tab itself —
 * an audio already in private collections still shows them collapsed behind an "unlock to
 * reveal" affordance when the session is closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssignCollectionSheet(viewModel: SoundsViewModel) {
    val audioId = viewModel.activeAssignAudioId.collectAsState().value ?: return
    val collections by viewModel.collections.collectAsState()
    val vaultOpen by VaultSessionState.flow.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val gate = remember(activity) { activity?.let { BiometricGate(it) } }
    val gateStatus = remember(gate) { gate?.status() ?: BiometricGateStatus.UNAVAILABLE }

    val publics = remember(collections) { collections.filter { it.isPublic } }
    val privates = remember(collections) { collections.filter { it.isPrivate } }

    ModalBottomSheet(
        onDismissRequest = { viewModel.dismissAssignCollections() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        AssignCollectionSheetBody(
            audioId = audioId,
            publics = publics,
            privates = privates,
            vaultOpen = vaultOpen,
            gateStatus = gateStatus,
            onUnlockVault = {
                requestVaultUnlock(context, gate, gateStatus)
            },
            onToggle = { collectionId ->
                viewModel.toggleAudioInCollection(audioId, collectionId)
            },
            onDone = {
                coroutineScope.launch { sheetState.hide() }
                viewModel.dismissAssignCollections()
            },
        )
    }
    LaunchedEffect(Unit) { sheetState.show() }
}

@Composable
private fun AssignCollectionSheetBody(
    audioId: String,
    publics: List<Collection>,
    privates: List<Collection>,
    vaultOpen: Boolean,
    gateStatus: BiometricGateStatus,
    onUnlockVault: () -> Unit,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.XL, vertical = Spacing.LG),
        verticalArrangement = Arrangement.spacedBy(Spacing.MD),
    ) {
        Text(
            text = stringResource(R.string.app_assign_collection_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (publics.isEmpty() && privates.isEmpty()) {
            Text(
                text = stringResource(R.string.app_assign_collection_sheet_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.XS),
            ) {
                if (publics.isNotEmpty()) {
                    item(key = "header_public") {
                        SectionHeader(stringResource(R.string.app_assign_collection_sheet_section_public))
                    }
                    items(publics, key = { it.id }) { collection ->
                        CollectionRow(
                            collection = collection,
                            checked = audioId in collection.audioIds,
                            onToggle = { onToggle(collection.id) },
                        )
                    }
                }
                if (privates.isNotEmpty()) {
                    item(key = "divider_private") {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Spacing.SM),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    item(key = "header_private") {
                        SectionHeader(stringResource(R.string.app_assign_collection_sheet_section_private))
                    }
                    val privateLockedByBiometric = !vaultOpen && gateStatus == BiometricGateStatus.AVAILABLE
                    if (privateLockedByBiometric) {
                        item(key = "private_locked") {
                            VaultLockedRow(onUnlock = onUnlockVault)
                        }
                    } else {
                        items(privates, key = { it.id }) { collection ->
                            CollectionRow(
                                collection = collection,
                                checked = audioId in collection.audioIds,
                                onToggle = { onToggle(collection.id) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.SM))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Text(stringResource(R.string.app_assign_collection_sheet_done))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.XS, bottom = Spacing.XS),
    )
}

@Composable
private fun CollectionRow(
    collection: Collection,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val labelRes = if (collection.isSystem) R.string.app_vault_baul_name else null
    val resolvedName = labelRes?.let { stringResource(it) } ?: collection.name
    // Action-oriented description: TalkBack reads "Add to Familia" (or, when already in,
    // "In Familia, double-tap to remove") instead of the raw "Familia, Checkbox, …" assembly.
    // It also gives instrumented tests a unique semantic anchor — the same collection name
    // appears in the My Sounds filter chip row, so disambiguating by text alone is unreliable.
    val rowDescription =
        if (checked) {
            stringResource(R.string.app_assign_collection_sheet_row_action_selected, resolvedName)
        } else {
            stringResource(R.string.app_assign_collection_sheet_row_action_unselected, resolvedName)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = rowDescription
                }
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(modifier = Modifier.padding(start = Spacing.SM))
        Text(
            text = resolvedName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun VaultLockedRow(onUnlock: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.SM),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text(
                text = stringResource(R.string.app_assign_collection_sheet_private_locked_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onUnlock) {
                Text(stringResource(R.string.app_assign_collection_sheet_private_locked_cta))
            }
        }
    }
}

private fun requestVaultUnlock(
    context: android.content.Context,
    gate: BiometricGate?,
    status: BiometricGateStatus,
) {
    if (status != BiometricGateStatus.AVAILABLE || gate == null) {
        VaultSessionState.markVaultOpen()
        return
    }
    gate.requestUnlock(
        title = context.getString(R.string.app_vault_screen_title),
        subtitle = context.getString(R.string.app_vault_biometric_prompt_subtitle),
        negativeButtonText = context.getString(R.string.app_vault_biometric_negative),
    ) { result ->
        if (result is BiometricGateResult.Granted) {
            VaultSessionState.markVaultOpen()
        }
    }
}

private fun android.content.Context.findFragmentActivity(): FragmentActivity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
