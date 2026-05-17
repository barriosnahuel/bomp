/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGate
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateResult
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateStatus
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

/**
 * Vault tab — list of private collections.
 *
 * Cards show only the name + audio count (spec § 3.4 — "Sin preview del contenido sin autenticar.").
 * Tapping a card triggers [BiometricGate.requestUnlock]; on success the immersive view opens. On
 * devices with no biometric AND no screen lock, a persistent warning chip is shown per § 6.
 *
 * The card overflow exposes Rename + Delete. Delete is disabled for the system Baúl per § 6;
 * the disabled menu item carries a tooltip-style label explaining why.
 */
@Composable
fun VaultScreen(
    collections: List<Collection>,
    viewModel: SoundsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val gate = remember(activity) { activity?.let { BiometricGate(it) } }
    val status = remember(gate) { gate?.status() ?: BiometricGateStatus.UNAVAILABLE }
    val tracker = remember(context) { AnalyticsTrackerProvider.get(context.applicationContext) }
    val unprotectedWarningText = stringResource(R.string.app_vault_unprotected_warning_chip)

    // Fire the "unprotected warning shown" event at most once per process when private
    // collections exist and biometric is not available. Sweep across collections (not per-card)
    // because dashboards care about "device state" not "card count".
    androidx.compose.runtime.LaunchedEffect(status, collections) {
        if (status != BiometricGateStatus.AVAILABLE &&
            collections.isNotEmpty() &&
            tracker.markFiredOnce("vault_unprotected_warning")
        ) {
            tracker.log(AnalyticsEvent.VaultUnprotectedWarningShown)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (collections.isEmpty()) {
            VaultEmptyState()
        } else {
            LazyColumn(
                contentPadding =
                    PaddingValues(
                        start = Spacing.LG,
                        end = Spacing.LG,
                        top = Spacing.LG,
                        bottom = LIST_BOTTOM_PADDING,
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.MD),
            ) {
                item(key = "vault_header") {
                    VaultHeader(
                        showUnprotectedWarning = status != BiometricGateStatus.AVAILABLE,
                    )
                }
                items(collections, key = { it.id }) { collection ->
                    PrivateCollectionCard(
                        collection = collection,
                        status = status,
                        unprotectedWarningText = unprotectedWarningText,
                        onClick = {
                            val target = collection.id
                            if (status != BiometricGateStatus.AVAILABLE) {
                                // No protection: open directly. The unprotected warning is visible
                                // both on the card and in the immersive view back-button copy.
                                viewModel.openImmersiveView(target)
                                tracker.log(AnalyticsEvent.VaultUnlock(granted = true))
                            } else {
                                gate?.requestUnlock(
                                    title =
                                        context.getString(
                                            R.string.app_vault_biometric_prompt_title,
                                            collection.name,
                                        ),
                                    subtitle = context.getString(R.string.app_vault_biometric_prompt_subtitle),
                                    negativeButtonText = context.getString(R.string.app_vault_biometric_negative),
                                ) { result ->
                                    when (result) {
                                        BiometricGateResult.Granted -> {
                                            viewModel.openImmersiveView(target)
                                            tracker.log(AnalyticsEvent.VaultUnlock(granted = true))
                                        }
                                        is BiometricGateResult.Denied -> {
                                            tracker.log(AnalyticsEvent.VaultUnlock(granted = false))
                                        }
                                    }
                                }
                            }
                        },
                        onRename = { viewModel.requestRenameCollection(collection.id) },
                        onDelete = { viewModel.requestDeleteConfirmation(collection.id) },
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { viewModel.requestCreateCollection(CollectionAccess.PRIVATE) },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.LG)
                    .semantics {
                        contentDescription = unprotectedWarningText // set proper a11y below
                    },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = {
                Icon(Icons.Default.Add, contentDescription = null)
            },
            text = { Text(stringResource(R.string.app_vault_fab_new)) },
        )
    }
}

@Composable
private fun VaultHeader(showUnprotectedWarning: Boolean) {
    Column {
        Text(
            text = stringResource(R.string.app_vault_screen_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(Spacing.XS))
        Text(
            text = stringResource(R.string.app_vault_screen_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showUnprotectedWarning) {
            Spacer(modifier = Modifier.height(Spacing.MD))
            UnprotectedDeviceBanner()
        }
    }
}

@Composable
private fun UnprotectedDeviceBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.MD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.app_vault_unprotected_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun PrivateCollectionCard(
    collection: Collection,
    status: BiometricGateStatus,
    unprotectedWarningText: String,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    val cardContentDescription =
        if (collection.isSystem) {
            stringResource(R.string.app_vault_baul_name) + " — " + audioCountLabel(collection.audioCount)
        } else {
            collection.name + " — " + audioCountLabel(collection.audioCount)
        }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = cardContentDescription },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.LG)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(Spacing.SM))
                Text(
                    text =
                        if (collection.isSystem) {
                            stringResource(R.string.app_vault_baul_name)
                        } else {
                            collection.name
                        },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                CardOverflow(
                    collection = collection,
                    expanded = overflowExpanded,
                    onExpand = { overflowExpanded = true },
                    onDismiss = { overflowExpanded = false },
                    onRename = {
                        overflowExpanded = false
                        onRename()
                    },
                    onDelete = {
                        overflowExpanded = false
                        onDelete()
                    },
                )
            }
            Spacer(modifier = Modifier.height(Spacing.XS))
            Text(
                text = audioCountLabel(collection.audioCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.XS))
            Text(
                text = stringResource(R.string.app_vault_card_locked_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (status != BiometricGateStatus.AVAILABLE) {
                Spacer(modifier = Modifier.height(Spacing.XS))
                Text(
                    text = unprotectedWarningText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CardOverflow(
    collection: Collection,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val overflowDescription = stringResource(R.string.app_vault_card_overflow_description, collection.name)
    IconButton(onClick = onExpand, modifier = Modifier.semantics { contentDescription = overflowDescription }) {
        Icon(Icons.Default.MoreVert, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.app_vault_card_overflow_rename)) },
            onClick = onRename,
        )
        if (collection.isSystem) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.app_vault_card_overflow_delete_system_disabled),
                        color = MaterialTheme.colorScheme.outline,
                    )
                },
                enabled = false,
                onClick = {},
            )
        } else {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.app_vault_card_overflow_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun VaultEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.XXL),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_vault_empty_baul_headline),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.MD))
        Text(
            text = stringResource(R.string.app_vault_empty_baul_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun audioCountLabel(count: Int): String =
    when (count) {
        0 -> stringResource(R.string.app_vault_collection_count_zero)
        1 -> stringResource(R.string.app_vault_collection_count_one)
        else -> stringResource(R.string.app_vault_collection_count_other, count)
    }

/**
 * Walks the Context wrapper chain to find a [FragmentActivity]. Compose's `LocalContext.current`
 * returns an Activity proxy that may be wrapped (e.g. ContextThemeWrapper) — naive `as` casts can
 * fail. Returns null when the composable is previewed outside an activity (e.g. `@Preview`).
 */
private fun android.content.Context.findFragmentActivity(): FragmentActivity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

private val LIST_BOTTOM_PADDING = 96.dp
