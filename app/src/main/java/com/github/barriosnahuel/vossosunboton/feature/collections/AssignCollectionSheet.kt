/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.fragment.app.FragmentActivity
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsSource
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.feature.addbutton.AssignToCollectionsSection
import com.github.barriosnahuel.vossosunboton.feature.addbutton.InlineCollectionCreateSheet
import com.github.barriosnahuel.vossosunboton.feature.vault.requestUnlock
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGate
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateStatus
import com.github.barriosnahuel.vossosunboton.feature.vault.security.VaultSessionState
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.ui.haptics.performRejectHaptic
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Modal bottom sheet that assigns an audio to collections and toggles its My Sounds visibility.
 * Triggered from the long-press → "Asignar a colecciones" menu.
 *
 * **Transactional (ADR 0012):** edits are staged locally — chip toggles and the visibility switch
 * change in-sheet only. **"Listo" applies** the staged state in one transaction (and fires the
 * coach / "moved to Vault" feedback); **backing out cancels** (discards everything). This mirrors
 * the New Bomp assign section, which also stages and commits at Save, and reuses its chip UI
 * ([AssignToCollectionsSection]). Creating a collection from "+ Nueva" persists it and adds it to
 * the staged selection. Vault collections sit behind the same per-session unlock as the Vault tab.
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

    val library by viewModel.library.collectAsState()
    val sound = remember(library, audioId) { library.firstOrNull { it.id == audioId } }

    // Staged edits — seeded once per opened audio (NOT keyed on `collections`, so creating a
    // collection mid-sheet doesn't reset the user's staged toggles). Applied on "Listo", dropped on
    // cancel. ADR 0012.
    var stagedPublic by remember(audioId) {
        mutableStateOf(publics.filter { audioId in it.audioIds }.map { it.id }.toSet())
    }
    var stagedPrivate by remember(audioId) {
        mutableStateOf(privates.filter { audioId in it.audioIds }.map { it.id }.toSet())
    }
    var stagedVisible by remember(audioId) { mutableStateOf(sound?.isVisibleInMySounds ?: true) }

    // Non-null = the create-with-auto-tag sheet is open for that scope.
    var pendingNewCollectionScope by remember { mutableStateOf<CollectionAccess?>(null) }

    ModalBottomSheet(
        onDismissRequest = { viewModel.dismissAssignCollections() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        AssignCollectionSheetBody(
            soundName = sound?.name.orEmpty(),
            publics = publics,
            privates = privates,
            vaultOpen = vaultOpen,
            gateStatus = gateStatus,
            stagedPublic = stagedPublic,
            stagedPrivate = stagedPrivate,
            stagedVisible = stagedVisible,
            onPublicChange = { stagedPublic = it },
            onPrivateChange = { stagedPrivate = it },
            onVisibleChange = { stagedVisible = it },
            onUnlockVault = { requestUnlock(context, gate, gateStatus, tracker, source = AnalyticsSource.ASSIGN_SHEET) },
            onCreatePublic = { pendingNewCollectionScope = CollectionAccess.PUBLIC },
            onCreatePrivate = { pendingNewCollectionScope = CollectionAccess.PRIVATE },
            onDone = {
                coroutineScope.launch { sheetState.hide() }
                viewModel.applyAssignment(
                    audioId = audioId,
                    name = sound?.name.orEmpty(),
                    publicCollectionIds = stagedPublic,
                    privateCollectionIds = stagedPrivate,
                    isVisibleInMySounds = stagedVisible,
                )
            },
        )
    }

    pendingNewCollectionScope?.let { scope ->
        InlineCollectionCreateSheet(
            scope = scope,
            source = AnalyticsSource.ASSIGN_SHEET,
            onDismiss = { pendingNewCollectionScope = null },
            onCreated = { created ->
                pendingNewCollectionScope = null
                // "+ Nueva" means "create AND tag" — stage the new collection on the long-pressed
                // audio (it persists on "Listo"), matching New Bomp.
                if (created.isPublic) stagedPublic = stagedPublic + created.id else stagedPrivate = stagedPrivate + created.id
            },
        )
    }

    LaunchedEffect(Unit) { sheetState.show() }
}

@Composable
private fun AssignCollectionSheetBody(
    soundName: String,
    publics: List<Collection>,
    privates: List<Collection>,
    vaultOpen: Boolean,
    gateStatus: BiometricGateStatus,
    stagedPublic: Set<String>,
    stagedPrivate: Set<String>,
    stagedVisible: Boolean,
    onPublicChange: (Set<String>) -> Unit,
    onPrivateChange: (Set<String>) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
    onUnlockVault: () -> Unit,
    onCreatePublic: () -> Unit,
    onCreatePrivate: () -> Unit,
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
            text = stringResource(R.string.app_assign_collection_sheet_title_named, soundName),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.app_assign_collection_sheet_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VisibleInMySoundsRow(
            isVisible = stagedVisible,
            // Anti-orphan: only allow turning visibility OFF when the audio is staged into ≥1 private
            // collection, so it never ends up reachable from nowhere. Uses the STAGED set so the user
            // can stage a Vault tag then hide, all before "Listo".
            canToggleOff = stagedPrivate.isNotEmpty(),
            onVisibleChange = onVisibleChange,
        )
        AssignToCollectionsSection(
            publicCollections = publics,
            privateCollections = privates,
            publicSelection = stagedPublic,
            privateSelection = stagedPrivate,
            biometricStatus = gateStatus,
            privateRevealed = vaultOpen,
            onPublicSelectionChange = onPublicChange,
            onPrivateSelectionChange = onPrivateChange,
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
 * Explicit "Visible en Mis Bomps" control (ADR 0012) — the only input to whether the Bomp shows in
 * the My Sounds "Todo" surface. Plain M3 [Switch] so it inherits the theme's acid `primary` track.
 *
 * The switch is **always enabled** (no confusing disabled-but-on state). The anti-orphan rule is
 * taught reactively: if the user tries to turn it OFF without the audio being in the Vault
 * ([canToggleOff] == false), the toggle is rejected with a reject haptic + a transient inline hint,
 * teaching the rule at the moment of intent instead of cluttering the default screen with a
 * permanent caption. An app-level Snackbar isn't used because the modal sheet sits above it.
 */
@Composable
private fun VisibleInMySoundsRow(
    isVisible: Boolean,
    canToggleOff: Boolean,
    onVisibleChange: (Boolean) -> Unit,
) {
    val view = LocalView.current
    val switchLabel = stringResource(R.string.app_assign_visible_label)
    var showVaultFirstHint by remember { mutableStateOf(false) }
    // Once the audio is staged into a Vault collection, turning off is allowed — clear any hint.
    LaunchedEffect(canToggleOff) { if (canToggleOff) showVaultFirstHint = false }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = switchLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // The ON caption only echoed the label ("Visible en Mis Bomps" / "Aparece en Mis
                // Bomps"). Show the caption only when OFF, where it adds info — where the Bomp lives
                // instead. The row reflows (no reserved height) so the text stays legible at any font
                // scale; a fixed height would clip it at large accessibility sizes (WCAG 1.4.4/1.4.10).
                if (!isVisible) {
                    Text(
                        text = stringResource(R.string.app_assign_visible_caption_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(Spacing.MD))
            Switch(
                checked = isVisible,
                // Accessible name = the visible label (the Switch role announces on/off state) —
                // labels-match-names, and lets the UI test target the control.
                modifier = Modifier.semantics { contentDescription = switchLabel },
                onCheckedChange = { wantVisible ->
                    if (!wantVisible && !canToggleOff) {
                        performRejectHaptic(view)
                        showVaultFirstHint = true
                    } else {
                        showVaultFirstHint = false
                        onVisibleChange(wantVisible)
                    }
                },
            )
        }
        if (showVaultFirstHint) {
            Text(
                text = stringResource(R.string.app_assign_visible_off_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.XS),
            )
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
