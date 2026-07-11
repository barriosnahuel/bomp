/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsSource
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.feature.collections.MySoundsFilterChipsRow
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGate
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateResult
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateStatus
import com.github.barriosnahuel.vossosunboton.feature.vault.security.ScreenLockSettings
import com.github.barriosnahuel.vossosunboton.feature.vault.security.VaultSessionState
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.model.Sound
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
    onImmersivePlay: (Sound) -> Unit,
    onEditSound: (Sound) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val gate = remember(activity) { activity?.let { BiometricGate(it) } }
    // Re-read the gate status on every ON_RESUME (not just first composition): when the user taps the
    // "set up screen lock" shortcut, leaves to Settings, enrolls a lock and returns, the snapshot must
    // flip from unprotected to AVAILABLE so the warning + shortcut disappear instead of going stale.
    var status by remember(gate) { mutableStateOf(gate?.status() ?: BiometricGateStatus.UNAVAILABLE) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, gate) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    status = gate?.status() ?: BiometricGateStatus.UNAVAILABLE
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // The set-screen-lock intent's resolvability does not change at runtime, so cache it once. The
    // status snapshot above is what flips the affordance off once a lock exists.
    val screenLockShortcutAvailable = remember(context) { ScreenLockSettings.isAvailable(context) }
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
                    showScreenLockShortcut = screenLockShortcutAvailable,
                    onUnlock = {
                        requestUnlock(
                            context = context,
                            gate = gate,
                            status = status,
                            tracker = tracker,
                            source = AnalyticsSource.VAULT_TAB,
                        )
                    },
                    onSetUpScreenLock = { ScreenLockSettings.open(context) },
                )
            else ->
                VaultBody(
                    privateCollections = privateCollections,
                    viewModel = viewModel,
                    listState = listState,
                    onActiveFilterEditClick = onActiveFilterEditClick,
                    onImmersivePlay = onImmersivePlay,
                    onEditSound = onEditSound,
                )
        }
    }
}

/**
 * Top-level helper so both [VaultScreen]'s primary unlock affordance and the SearchOverlay's
 * "Search your Vault too" CTA share the same biometric flow + analytics. Visibility is `internal`
 * (not `private`) so `LandingScreen.kt` — the SearchOverlay host — can invoke it without
 * duplicating the gate + tracker plumbing.
 */
internal fun requestUnlock(
    context: android.content.Context,
    gate: BiometricGate?,
    status: BiometricGateStatus,
    tracker: com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTracker,
    source: String,
) {
    // Devices without biometric/lock fall through to "open the Vault anyway" — spec § 6 ("No
    // protection: open directly. Warning is visible elsewhere.").
    tracker.logScreen(
        com.github.barriosnahuel.vossosunboton.commons.android.analytics
            .CanonicalScreenName.VAULT_UNLOCK,
    )
    if (status != BiometricGateStatus.AVAILABLE || gate == null) {
        VaultSessionState.markVaultOpen()
        tracker.log(AnalyticsEvent.VaultUnlock(granted = true, source = source))
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
                tracker.log(AnalyticsEvent.VaultUnlock(granted = true, source = source))
                bumpVaultUnlockCounter(tracker)
            }
            is BiometricGateResult.Denied -> tracker.log(AnalyticsEvent.VaultUnlock(granted = false, source = source))
        }
    }
}

internal fun bumpVaultUnlockCounter(tracker: com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTracker) {
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
    showScreenLockShortcut: Boolean,
    onUnlock: () -> Unit,
    onSetUpScreenLock: () -> Unit,
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
            painter = painterResource(R.drawable.app_ic_lock),
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
                painter = painterResource(R.drawable.app_ic_lock),
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
            // Secondary remediation affordance: a text-tier button (ADR 0010 — `primary` inherited,
            // never an outlined/tonal tier) that deep-links straight to the system set-screen-lock
            // flow. Only rendered when the intent actually resolves on this device, so it can never
            // be a dead button. Below the primary "unlock" CTA on purpose — it's the fallback path,
            // not the main action.
            if (showScreenLockShortcut) {
                Spacer(modifier = Modifier.height(Spacing.SM))
                TextButton(onClick = onSetUpScreenLock) {
                    Icon(
                        painter = painterResource(R.drawable.app_ic_lock),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(Spacing.XS))
                    Text(stringResource(R.string.app_vault_unprotected_setup_screenlock_cta))
                }
            }
        }
    }
}

@Composable
private fun VaultBody(
    privateCollections: List<Collection>,
    viewModel: SoundsViewModel,
    listState: LazyListState,
    onActiveFilterEditClick: (String) -> Unit,
    onImmersivePlay: (Sound) -> Unit,
    onEditSound: (Sound) -> Unit,
) {
    // Read BEFORE `vaultAudios`: loadSounds() writes the vault list before flipping the flag, so
    // the mirrored flag→list read order guarantees a `true` flag never pairs with a stale empty
    // list (cold-start ZRP flash). Don't move below the vaultAudios read.
    val initialLoadComplete by viewModel.isInitialLoadComplete.collectAsStateWithLifecycle()
    val vaultAudios by viewModel.vaultAudios.collectAsStateWithLifecycle()
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
    // Both empty states gate on the initial load (read above, before vaultAudios) so a cold start
    // on the Vault tab never flashes the ZRP — nor the filtered "no results" for a persisted
    // chip — while the first loadSounds + collections snapshot are still hydrating vaultAudios.
    val showZrp = vaultAudios.isEmpty() && activeFilter == null && initialLoadComplete
    val showFilterEmpty = vaultAudios.isEmpty() && activeFilter != null && initialLoadComplete
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
                    viewModel.requestCreateCollection(CollectionAccess.PRIVATE, source = AnalyticsSource.VAULT_FILTER)
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
                    // Vault audios use the immersive listen-mode player (CollectionPlaybackUI.IMMERSIVE)
                    // instead of the inline card transport: tapping play opens the full-screen screen.
                    onPlayClick = { sound -> onImmersivePlay(sound) },
                    onSeek = { positionMs -> viewModel.seekTo(positionMs) },
                    onShareClick = { sound -> viewModel.share(sound) },
                    onDelete = { sound -> viewModel.deleteSound(sound) },
                    onPinClick = { sound -> viewModel.togglePin(sound) },
                    onEdit = onEditSound,
                    onAddToCollection = { sound -> viewModel.requestAssignCollections(sound.id) },
                )
            }
        }
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
                        ZRP_GLOW_STOP_CENTER to accent.copy(alpha = ZRP_GLOW_CENTER_ALPHA),
                        ZRP_GLOW_STOP_MID to accent.copy(alpha = ZRP_GLOW_MID_ALPHA),
                        ZRP_GLOW_STOP_EDGE to backgroundColor,
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
            // Soft outline heart — a muted accent, not a focal element; the headline carries the
            // message. No photo/polaroid frame, eyebrow, or caption: the Vault stores neither images
            // nor contact assignments, and a tall stack of lines would overflow (scroll) the empty
            // state on large accessibility font scales.
            Icon(
                painter = painterResource(R.drawable.app_ic_favorite_border),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HEART_ALPHA),
                modifier = Modifier.size(56.dp),
            )
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
// a chip-color competing with the placeholder for attention. Verified against AppThemeContrastTest
// indirectly — the gradient stays under the bodyMedium text, which still reads against background.
private const val ZRP_GLOW_CENTER_ALPHA = 0.16f
private const val ZRP_GLOW_MID_ALPHA = 0.04f

// Radial-gradient stops for the ZRP glow. Center is at the placeholder; mid (50%) keeps a soft halo
// before fading to the surface background at the edges.
private const val ZRP_GLOW_STOP_CENTER = 0.0f
private const val ZRP_GLOW_STOP_MID = 0.5f
private const val ZRP_GLOW_STOP_EDGE = 1.0f

// The heart is a soft accent, not a focal element — kept muted so the headline carries the message.
private const val HEART_ALPHA = 0.5f

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
