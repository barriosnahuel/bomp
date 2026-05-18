/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.feature.collections.MySoundsFilterChipsRow
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGate
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateResult
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateStatus
import com.github.barriosnahuel.vossosunboton.feature.vault.security.VaultSessionState
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsList
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

/**
 * Vault tab — flat list of audios in any private collection, plus a filter chip row to narrow
 * to a single private collection. Conceptually identical to My Sounds (same `SoundsList` cards,
 * same chip semantics) and shares its visual language with the rest of the app; the distinction
 * is enforced by the biometric gate, not by a separate color scheme.
 *
 * Auth is per-session (spec § 5 originally per-collection-per-invocation, post-launch usability
 * feedback flipped it to one prompt per session for the whole tab). [VaultSessionState] holds
 * the flag; any other private surface (Add/Edit private chips) trusts the same flag.
 *
 * Empty state ("ZRP") is inspirational — copy and layout mirror the Claude Design polaroid +
 * heart placeholder + "Preservar el primero" CTA.
 */
@Composable
fun VaultScreen(
    privateCollections: List<Collection>,
    viewModel: SoundsViewModel,
    listState: LazyListState,
    onActiveFilterEditClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val gate = remember(activity) { activity?.let { BiometricGate(it) } }
    val status = remember(gate) { gate?.status() ?: BiometricGateStatus.UNAVAILABLE }
    val vaultOpen by VaultSessionState.flow.collectAsState()
    val tracker = remember(context) { AnalyticsTrackerProvider.get(context.applicationContext) }
    val unprotectedWarning = stringResource(R.string.app_vault_unprotected_warning_chip)

    androidx.compose.runtime.LaunchedEffect(status, vaultOpen) {
        if (!vaultOpen &&
            status != BiometricGateStatus.AVAILABLE &&
            tracker.markFiredOnce("vault_unprotected_warning")
        ) {
            tracker.log(AnalyticsEvent.VaultUnprotectedWarningShown)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            !vaultOpen ->
                UnlockGate(
                    status = status,
                    unprotectedWarning = unprotectedWarning,
                    onUnlock = {
                        requestUnlock(
                            context = context,
                            gate = gate,
                            status = status,
                            tracker = tracker,
                        )
                    },
                )
            else ->
                VaultBody(
                    privateCollections = privateCollections,
                    viewModel = viewModel,
                    listState = listState,
                    onActiveFilterEditClick = onActiveFilterEditClick,
                )
        }
    }
}

private fun requestUnlock(
    context: android.content.Context,
    gate: BiometricGate?,
    status: BiometricGateStatus,
    tracker: com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTracker,
) {
    // Devices without biometric/lock fall through to "open the Vault anyway" — spec § 6 ("No
    // protection: open directly. Warning is visible elsewhere.").
    tracker.logScreen(
        com.github.barriosnahuel.vossosunboton.commons.android.analytics
            .CanonicalScreenName.VAULT_UNLOCK,
    )
    if (status != BiometricGateStatus.AVAILABLE || gate == null) {
        VaultSessionState.markVaultOpen()
        tracker.log(AnalyticsEvent.VaultUnlock(granted = true))
        bumpVaultUnlockCounter(tracker)
        return
    }
    gate.requestUnlock(
        title = context.getString(R.string.app_vault_screen_title),
        subtitle = context.getString(R.string.app_vault_biometric_prompt_subtitle),
        negativeButtonText = context.getString(R.string.app_vault_biometric_negative),
    ) { result ->
        when (result) {
            BiometricGateResult.Granted -> {
                VaultSessionState.markVaultOpen()
                tracker.log(AnalyticsEvent.VaultUnlock(granted = true))
                bumpVaultUnlockCounter(tracker)
            }
            is BiometricGateResult.Denied -> tracker.log(AnalyticsEvent.VaultUnlock(granted = false))
        }
    }
}

private fun bumpVaultUnlockCounter(tracker: com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTracker) {
    val newCount =
        tracker.incrementCounter(
            com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsUserProperty.LIFETIME_VAULT_UNLOCKS,
        )
    tracker.setUserProperty(
        com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsUserProperty.LIFETIME_VAULT_UNLOCKS,
        newCount.toString(),
    )
}

@Composable
private fun UnlockGate(
    status: BiometricGateStatus,
    unprotectedWarning: String,
    onUnlock: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(Spacing.LG))
        Text(
            text = stringResource(R.string.app_vault_screen_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.SM))
        Text(
            text = stringResource(R.string.app_vault_unlock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.XL))
        Button(
            onClick = onUnlock,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(Spacing.SM))
            Text(stringResource(R.string.app_vault_unlock_cta))
        }
        if (status != BiometricGateStatus.AVAILABLE) {
            Spacer(modifier = Modifier.height(Spacing.MD))
            Text(
                text = unprotectedWarning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun VaultBody(
    privateCollections: List<Collection>,
    viewModel: SoundsViewModel,
    listState: LazyListState,
    onActiveFilterEditClick: (String) -> Unit,
) {
    val vaultAudios by viewModel.vaultAudios.collectAsState()
    val activeFilter by viewModel.activeVaultFilter.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val pausedProgress by viewModel.pausedProgress.collectAsState()
    val durations by viewModel.soundDurations.collectAsState()
    val collectionsByAudio by viewModel.audioCollectionsIndex.collectAsState()
    val allCollections by viewModel.collections.collectAsState()
    val context = LocalContext.current
    val activeCollection = privateCollections.firstOrNull { it.id == activeFilter }
    val systemCollectionLabel = stringResource(R.string.app_vault_baul_name)
    val activeName =
        activeCollection?.let { if (it.isSystem) systemCollectionLabel else it.name }.orEmpty()
    val showZrp = vaultAudios.isEmpty() && activeFilter == null
    val showFilterEmpty = vaultAudios.isEmpty() && activeFilter != null
    // Header rendered alongside the chip row whenever the body shows real audios. ZRP and
    // filtered-empty states both hide it — header on top of an empty body is just noise.
    val showHeader = !showZrp && !showFilterEmpty

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            MySoundsFilterChipsRow(
                publicCollections = privateCollections,
                activeFilterId = activeFilter,
                onFilterSelected = { id -> viewModel.selectVaultFilter(id) },
                onCreateRequested = {
                    viewModel.requestCreateCollection(CollectionAccess.PRIVATE)
                },
            )
            if (showHeader) {
                com.github.barriosnahuel.vossosunboton.feature.collections.ActiveFilterHeader(
                    activeCollection = activeCollection,
                    audioCount = vaultAudios.size,
                    isVaultContext = true,
                    onEditClick = { activeCollection?.id?.let(onActiveFilterEditClick) },
                )
            }
            if (showZrp) {
                VaultEmptyState()
            } else if (showFilterEmpty) {
                FilteredEmptyState(collectionName = activeName)
            } else {
                SoundsList(
                    sounds = vaultAudios,
                    playbackProgress = playbackProgress,
                    pausedProgress = pausedProgress,
                    soundDurations = durations,
                    listState = listState,
                    collectionsByAudio = collectionsByAudio,
                    allCollections = allCollections,
                    filterIsActive = activeFilter != null,
                    shareEnabled = false,
                    // Leave room for the ExtendedFloatingActionButton overlay so the last row
                    // can scroll fully into view instead of sitting under the FAB. Default FAB
                    // height (56dp) + Spacing.LG margin + Spacing.MD breathing room.
                    bottomContentPadding = VAULT_FAB_CLEARANCE,
                    onPlayClick = { sound -> viewModel.playOrStop(sound) },
                    onSeek = { positionMs -> viewModel.seekTo(positionMs) },
                    onShareClick = { sound -> viewModel.share(sound) },
                    onDelete = { sound -> viewModel.deleteSound(sound) },
                    onPinClick = { sound -> viewModel.togglePin(sound) },
                    onEdit = { sound ->
                        context.startActivity(LandingActivity.editIntent(context, sound))
                    },
                    onAddToCollection = { sound -> viewModel.requestAssignCollections(sound.id) },
                )
            }
        }
        // Always-visible FAB to create a private collection. Mirrors the Vault chip row's
        // "+ Nueva" entry point but stays reachable when the list is full.
        val fabContentDescription = stringResource(R.string.app_vault_fab_new)
        ExtendedFloatingActionButton(
            onClick = { viewModel.requestCreateCollection(CollectionAccess.PRIVATE) },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.LG)
                    .semantics { contentDescription = fabContentDescription },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = {
                Icon(
                    imageVector = AppIcons.Add,
                    contentDescription = null,
                )
            },
            text = { Text(stringResource(R.string.app_vault_fab_new)) },
        )
    }
}

@Composable
private fun VaultEmptyState() {
    // Warm radial wash centered behind the polaroid. We adapt Claude Design's brown/amber gradient
    // to the project's ink × acid palette: the accent role at low alpha gives an emotional glow
    // without introducing a one-off warm token (and works in both light and dark mode because the
    // role itself is theme-aware).
    val accent = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background
    val zrpBackground =
        remember(accent, backgroundColor) {
            Brush.radialGradient(
                colorStops =
                    arrayOf(
                        0.0f to accent.copy(alpha = ZRP_GLOW_CENTER_ALPHA),
                        0.5f to accent.copy(alpha = ZRP_GLOW_MID_ALPHA),
                        1.0f to backgroundColor,
                    ),
            )
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(zrpBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.XXL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Eyebrow tracker line, mimics the Claude Design "VAULT · SESIÓN ABIERTA" marker.
            Text(
                text = stringResource(R.string.app_vault_zrp_eyebrow).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Spacing.LG))
            PolaroidPlaceholder()
            Spacer(modifier = Modifier.height(Spacing.XL))
            Text(
                text = stringResource(R.string.app_vault_zrp_headline_lead),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.app_vault_zrp_headline_emphasis),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Spacing.MD))
            Text(
                text = stringResource(R.string.app_vault_zrp_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Alpha values for the ZRP radial gradient. Low-on-purpose: the goal is an emotional warmth, not
// a chip-color competing with the polaroid for attention. Verified against AppThemeContrastTest
// indirectly — the gradient stays under the bodyMedium text, which still reads against background.
private const val ZRP_GLOW_CENTER_ALPHA = 0.16f
private const val ZRP_GLOW_MID_ALPHA = 0.04f

// Space the LazyColumn reserves at the bottom so the ExtendedFloatingActionButton can sit on top
// of the last row without clipping it. Computed from the FAB design: 56dp (M3 ExtendedFAB minimum
// height) + Spacing.LG (16dp outer margin) + Spacing.MD (12dp breathing room) = 84dp. Round to 88
// to match the 8dp grid.
private val VAULT_FAB_CLEARANCE = 88.dp

@Composable
private fun PolaroidPlaceholder() {
    Box(
        modifier =
            Modifier
                .size(width = 200.dp, height = 220.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(4.dp),
                ).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(Spacing.MD))
            Text(
                text = stringResource(R.string.app_vault_zrp_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

@Composable
private fun FilteredEmptyState(collectionName: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_my_sounds_filter_empty_collection_headline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.SM))
        Text(
            text = stringResource(R.string.app_my_sounds_filter_empty_collection_body, collectionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
