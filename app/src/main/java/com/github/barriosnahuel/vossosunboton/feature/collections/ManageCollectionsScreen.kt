/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.feature.vault.requestUnlock
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGate
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateStatus
import com.github.barriosnahuel.vossosunboton.feature.vault.security.VaultSessionState
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.ui.home.AppTab
import com.github.barriosnahuel.vossosunboton.ui.home.SoundsViewModel
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Canonical entry point for renaming, deleting, and creating collections — both public ("My
 * Sounds") and private (Vault). Reached two ways:
 *
 * 1. **Generic**: top-app-bar overflow ⋮ → "Manage collections". `focusedCollectionId` is null;
 *    the screen opens at the top of the list with no highlight.
 * 2. **Contextual**: ✎ in the [ActiveFilterHeader] of either tab. `focusedCollectionId` carries
 *    the active filter's id; the screen scrolls to that row on open and applies a transient
 *    background tint that fades after 1.5s. The deep-link is purely visual — no menu opens
 *    automatically (the spec deliberately avoids forcing an action choice on the user).
 *
 * Vault gating: when the user hasn't unlocked the Vault for this session and the device has
 * biometric available, the Vault section collapses into a single locked-state card. Names of
 * private collections — including the [focusedCollectionId]'s collection if it happens to be a
 * Vault one — are never rendered until unlock. After unlock the section re-emits with the real
 * rows in place; the deep-link scroll + highlight only fire after that point.
 *
 * Hosting note: this composable does NOT mount its own [CollectionSheetHost] or
 * [CollectionDeleteDialog]. Those overlays live at the [com.github.barriosnahuel.vossosunboton.ui.home.LandingScreen]
 * level so they survive the navigation swap and react to the same VM state flows from anywhere
 * in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageCollectionsScreen(
    viewModel: SoundsViewModel,
    focusedCollectionId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val collections by viewModel.collections.collectAsState()
    val collectionsIndex by viewModel.audioCollectionsIndex.collectAsState()
    val vaultOpen by VaultSessionState.flow.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val gate = remember(activity) { activity?.let { BiometricGate(it) } }
    val gateStatus = remember(gate) { gate?.status() ?: BiometricGateStatus.UNAVAILABLE }
    val tracker = remember(context) { AnalyticsTrackerProvider.get(context.applicationContext) }
    val publics = remember(collections) { collections.filter { it.isPublic } }
    val privates = remember(collections) { collections.filter { it.isPrivate } }
    val lazyListState = rememberLazyListState()

    // Highlight state: we copy the deep-link id into a Saveable that the row reads. After a delay
    // we wipe it so the tint fades. Using rememberSaveable lets a rotation while the highlight is
    // active resume cleanly without retriggering the scroll — the LaunchedEffect below is gated by
    // `deepLinkConsumed`, also rememberSaveable, so rotation cannot replay the cue.
    var highlightedId by rememberSaveable { mutableStateOf(focusedCollectionId) }
    var deepLinkConsumed by rememberSaveable { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_manage_collections_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.app_manage_collections_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        titleContentColor = MaterialTheme.colorScheme.onSecondary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(vertical = Spacing.SM),
            verticalArrangement = Arrangement.spacedBy(Spacing.XS),
        ) {
            // PUBLIC SECTION
            item(key = "header_public") { SectionHeader(stringResource(R.string.app_manage_collections_section_public)) }
            if (publics.isEmpty()) {
                item(key = "empty_public") { SectionEmpty(stringResource(R.string.app_manage_collections_empty_public)) }
            } else {
                items(publics, key = { "p_${it.id}" }) { collection ->
                    ManageRow(
                        collection = collection,
                        audioCount = collectionsIndex.values.count { it.contains(collection.id) },
                        isHighlighted = highlightedId == collection.id,
                        onViewClick = {
                            viewModel.trackCollectionView(isPublic = true)
                            // Set tab + filter before closing so LandingScreen re-renders against
                            // both new values in a single composition pass.
                            viewModel.selectTab(AppTab.MY_SOUNDS)
                            viewModel.selectMySoundsFilter(collection.id)
                            onBack()
                        },
                        onRenameClick = { viewModel.requestRenameCollection(collection.id) },
                        onDeleteClick = { viewModel.requestDeleteConfirmation(collection.id) },
                    )
                }
            }
            item(key = "new_public") {
                SectionCreateButton(
                    label = stringResource(R.string.app_manage_collections_new_public),
                    onClick = { viewModel.requestCreateCollection(CollectionAccess.PUBLIC, source = "manage") },
                )
            }

            // VAULT SECTION
            item(key = "header_vault") {
                Spacer(modifier = Modifier.height(Spacing.LG))
                SectionHeader(stringResource(R.string.app_manage_collections_section_vault))
            }

            val isVaultLocked = !vaultOpen && gateStatus == BiometricGateStatus.AVAILABLE
            if (isVaultLocked) {
                item(key = "vault_locked") {
                    VaultLockedCard(
                        onUnlock = { requestUnlock(context, gate, gateStatus, tracker, source = "manage") },
                    )
                }
            } else {
                val managedPrivates = privates // all of them, system included (it just has no overflow)
                if (managedPrivates.none { !it.isSystem }) {
                    // Only the system Baúl exists — show the empty hint BEFORE the system row so the
                    // user sees the cue "you haven't created any private collection yet" plus the row.
                    item(key = "empty_vault") {
                        SectionEmpty(stringResource(R.string.app_manage_collections_empty_vault))
                    }
                }
                items(managedPrivates, key = { "v_${it.id}" }) { collection ->
                    ManageRow(
                        collection = collection,
                        audioCount = collectionsIndex.values.count { it.contains(collection.id) },
                        isHighlighted = highlightedId == collection.id,
                        onViewClick = {
                            viewModel.trackCollectionView(isPublic = false)
                            viewModel.selectTab(AppTab.VAULT)
                            viewModel.selectVaultFilter(collection.id)
                            onBack()
                        },
                        onRenameClick = { viewModel.requestRenameCollection(collection.id) },
                        onDeleteClick = { viewModel.requestDeleteConfirmation(collection.id) },
                    )
                }
                item(key = "new_vault") {
                    SectionCreateButton(
                        label = stringResource(R.string.app_manage_collections_new_vault),
                        onClick = { viewModel.requestCreateCollection(CollectionAccess.PRIVATE, source = "manage") },
                    )
                }
            }
        }
    }

    // Deep-link behavior: when the user landed here with a focused id, scroll to it and fade the
    // highlight after a short cue. Skipped silently if the id no longer exists (race: deleted by
    // another flow between ✎ tap and this composition) or if we already ran the scroll once on this
    // composition lifetime (rotation re-triggers the LaunchedEffect even though we'd already
    // pulsed — `deepLinkConsumed` blocks the replay).
    LaunchedEffect(focusedCollectionId, vaultOpen) {
        if (deepLinkConsumed) return@LaunchedEffect
        val id = focusedCollectionId ?: return@LaunchedEffect
        // For a Vault id, defer the scroll until the section actually renders.
        val targetIsPrivate = collections.firstOrNull { it.id == id }?.isPrivate == true
        if (targetIsPrivate && !vaultOpen && gateStatus == BiometricGateStatus.AVAILABLE) return@LaunchedEffect

        val flatIndex = computeRowIndex(id, publics, privates, isVaultLocked = false)
        if (flatIndex >= 0) {
            lazyListState.animateScrollToItem(flatIndex)
        }
        deepLinkConsumed = true
        delay(HIGHLIGHT_FADE_AFTER_MS)
        highlightedId = null
    }
}

/**
 * Computes the LazyColumn item index for the row carrying [id]. The layout interleaves header,
 * empty-state hint, rows, and the "new" button per section — this central helper keeps the
 * deep-link scroll target in sync with whatever ordering the body uses.
 */
private fun computeRowIndex(
    id: String,
    publics: List<Collection>,
    privates: List<Collection>,
    isVaultLocked: Boolean,
): Int {
    var index = 1 // header_public
    if (publics.isEmpty()) index += 1 // empty_public
    val publicHit = publics.indexOfFirst { it.id == id }
    val hit =
        when {
            publicHit >= 0 -> index + publicHit
            isVaultLocked -> -1
            else -> {
                index += publics.size + 1 // rows + new_public
                index += 1 // header_vault
                val showEmptyVault = privates.none { !it.isSystem }
                if (showEmptyVault) index += 1
                val privateHit = privates.indexOfFirst { it.id == id }
                if (privateHit >= 0) index + privateHit else -1
            }
        }
    return hit
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Spacing.LG, vertical = Spacing.XS),
    )
}

@Composable
private fun SectionEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.LG, vertical = Spacing.XS),
    )
}

@Composable
private fun SectionCreateButton(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = Spacing.SM),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(Spacing.XS))
        Text(text = label)
    }
}

@Composable
private fun ManageRow(
    collection: Collection,
    audioCount: Int,
    isHighlighted: Boolean,
    onViewClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val systemFallback = stringResource(R.string.app_vault_baul_name)
    val displayName = if (collection.isSystem) systemFallback else collection.name
    val highlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    // Mirror AOSP HighlightablePreferenceGroupAdapter exactly: a fade-in animator with
    // duration=200ms, repeatMode=REVERSE, repeatCount=4 → 5 alternating phases that end AT the
    // highlight color (3 perceived peaks), then a separate 500ms fade-out to transparent.
    val animatedBackground = remember { Animatable(Color.Transparent) }
    LaunchedEffect(isHighlighted) {
        if (!isHighlighted) {
            animatedBackground.snapTo(Color.Transparent)
            return@LaunchedEffect
        }
        repeat(2) {
            animatedBackground.animateTo(highlightColor, tween(HIGHLIGHT_FADE_IN_MS))
            animatedBackground.animateTo(Color.Transparent, tween(HIGHLIGHT_FADE_IN_MS))
        }
        animatedBackground.animateTo(highlightColor, tween(HIGHLIGHT_FADE_IN_MS))
        animatedBackground.animateTo(Color.Transparent, tween(HIGHLIGHT_FADE_OUT_MS))
    }

    // Spec § 3: "Tap en el row completo: no-op". We intentionally do NOT merge the descendants
    // nor add a click action on the Row — the only interactive target is the trailing ⋮ icon
    // button. TalkBack steps through the name, the system tag (when present), the audio count,
    // and finally the overflow button as separate nodes.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(animatedBackground.value)
                .padding(horizontal = Spacing.LG, vertical = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (collection.isSystem) {
                    Spacer(modifier = Modifier.size(Spacing.SM))
                    Text(
                        text = stringResource(R.string.app_manage_collections_system_tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text =
                    pluralStringResource(
                        R.plurals.app_manage_collections_row_sounds_count,
                        audioCount,
                        audioCount,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ManageRowOverflow(
            collectionName = displayName,
            // System collections (the seeded Baúl) only expose the "View collection" action —
            // they cannot be renamed or deleted (repo guards both with `require(!isSystem)`).
            // Hiding the entire overflow used to leave the user without a contextual entry to
            // jump to the Vault tab; offering a view-only overflow keeps the affordance.
            isSystem = collection.isSystem,
            onViewClick = onViewClick,
            onRenameClick = onRenameClick,
            onDeleteClick = onDeleteClick,
        )
    }
}

@Composable
private fun ManageRowOverflow(
    collectionName: String,
    isSystem: Boolean,
    onViewClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val description = stringResource(R.string.app_vault_card_overflow_description, collectionName)
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = description,
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            // "View collection" sits above Rename — most common positive action; bumping the user
            // straight to the relevant tab with the filter chip applied is the canonical happy path
            // from Manage. For system collections it's the *only* action: rename and delete are
            // refused at the repo layer, so they should not appear in the menu.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_manage_collections_overflow_view)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onViewClick()
                },
            )
            if (!isSystem) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_vault_card_overflow_rename)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRenameClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_vault_card_overflow_delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDeleteClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun VaultLockedCard(onUnlock: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.LG, vertical = Spacing.XS),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.LG),
            verticalArrangement = Arrangement.spacedBy(Spacing.SM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(Spacing.SM))
                Text(
                    text = stringResource(R.string.app_manage_collections_vault_locked_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.app_manage_collections_vault_locked_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onUnlock,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Text(stringResource(R.string.app_manage_collections_vault_locked_cta))
            }
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

// AOSP HighlightablePreferenceGroupAdapter constants (frameworks Settings widget). Pulse duration
// is 5 * 200ms = 1000ms; fade-out adds 500ms, so the outer wipe waits ≥ 1500ms before clearing
// `highlightedId`.
private const val HIGHLIGHT_FADE_IN_MS = 200
private const val HIGHLIGHT_FADE_OUT_MS = 500
private const val HIGHLIGHT_FADE_AFTER_MS = 1600L
