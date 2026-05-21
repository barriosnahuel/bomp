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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.feature.addbutton.AssignToCollectionsSection
import com.github.barriosnahuel.vossosunboton.feature.addbutton.InlineCollectionCreateSheet
import com.github.barriosnahuel.vossosunboton.feature.vault.requestUnlock
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGate
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateStatus
import com.github.barriosnahuel.vossosunboton.feature.vault.security.VaultSessionState
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Modal bottom sheet that assigns / unassigns an audio to/from any collection. Triggered from the
 * long-press → "Add to collection" menu.
 *
 * Shares the exact chip UI + create flow with the New Bomp assign section by reusing
 * [AssignToCollectionsSection]. Two surface-specific behaviors: changes apply immediately (no save
 * step — each chip toggle commits right away), and creating a collection from "+ Nueva" auto-tags
 * the long-pressed audio (parity with New Bomp). Vault collections sit behind the same per-session
 * unlock used by the Vault tab.
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
    val tracker = remember(context) { AnalyticsTrackerProvider.get(context.applicationContext) }

    val publics = remember(collections) { collections.filter { it.isPublic } }
    val privates = remember(collections) { collections.filter { it.isPrivate } }

    // Non-null = the create-with-auto-tag sheet is open for that scope.
    var pendingNewCollectionScope by remember { mutableStateOf<CollectionAccess?>(null) }

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
            onUnlockVault = { requestUnlock(context, gate, gateStatus, tracker, source = "assign_sheet") },
            onToggle = { collectionId -> viewModel.toggleAudioInCollection(audioId, collectionId) },
            onCreatePublic = { pendingNewCollectionScope = CollectionAccess.PUBLIC },
            onCreatePrivate = { pendingNewCollectionScope = CollectionAccess.PRIVATE },
            onDone = {
                coroutineScope.launch { sheetState.hide() }
                viewModel.dismissAssignCollections()
            },
        )
    }

    pendingNewCollectionScope?.let { scope ->
        InlineCollectionCreateSheet(
            scope = scope,
            source = "assign_sheet",
            onDismiss = { pendingNewCollectionScope = null },
            onCreated = { created ->
                pendingNewCollectionScope = null
                // "+ Nueva" means "create AND tag" — auto-tag the long-pressed audio, matching New Bomp.
                viewModel.toggleAudioInCollection(audioId, created.id)
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
    onCreatePublic: () -> Unit,
    onCreatePrivate: () -> Unit,
    onDone: () -> Unit,
) {
    val publicSelection =
        remember(publics, audioId) { publics.filter { audioId in it.audioIds }.map { it.id }.toSet() }
    val privateSelection =
        remember(privates, audioId) { privates.filter { audioId in it.audioIds }.map { it.id }.toSet() }
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
        AssignToCollectionsSection(
            publicCollections = publics,
            privateCollections = privates,
            publicSelection = publicSelection,
            privateSelection = privateSelection,
            biometricStatus = gateStatus,
            privateRevealed = vaultOpen,
            onPublicSelectionChange = { next -> applyImmediateToggles(publicSelection, next, onToggle) },
            onPrivateSelectionChange = { next -> applyImmediateToggles(privateSelection, next, onToggle) },
            onCreatePublicRequested = onCreatePublic,
            onCreatePrivateRequested = onCreatePrivate,
            onRequestPrivateUnlock = onUnlockVault,
            onHidePrivate = {},
            showTitle = false,
            showHidePrivate = false,
        )
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

/**
 * The chip component reports the full intended selection; this long-press surface commits each
 * change immediately, so apply exactly the ids that flipped versus the current set.
 */
private fun applyImmediateToggles(
    current: Set<String>,
    next: Set<String>,
    onToggle: (String) -> Unit,
) {
    ((next - current) + (current - next)).forEach(onToggle)
}

private fun android.content.Context.findFragmentActivity(): FragmentActivity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
