/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import kotlinx.coroutines.launch

// Dim applied to a disabled row's icon + text. Standard Material disabled opacity; a disabled
// control is exempt from the WCAG 1.4.3 contrast minimum (§ Accessibility). All Hub rows are live
// today; the nullable-onClick path is kept for future inert rows.
private const val HUB_DISABLED_ALPHA = 0.38f
private val HUB_ICON_TILE = 44.dp
private val HUB_TILE_RADIUS = 12.dp

/**
 * The import Hub — a [ModalBottomSheet] opened by the + FAB on My Bomps. Three creation paths: "import
 * audio from your device" ([onImport]), "record" a new Bomp in-app ([onRecord], ADR 0019), and "see how
 * it works" ([onHowItWorks]) that opens the onboarding tour, re-openable any time.
 *
 * Presentational only: the caller dismisses on [onImport]/[onRecord]/[onHowItWorks]/[onDismiss] and owns
 * the file picker, the recorder Activity, and the tour state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportHubSheet(
    onImport: () -> Unit,
    onRecord: () -> Unit,
    onHowItWorks: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
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
            verticalArrangement = Arrangement.spacedBy(Spacing.SM),
        ) {
            Text(
                text = stringResource(R.string.app_hub_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.app_hub_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.SM))
            HubRow(
                iconPainter = painterResource(R.drawable.app_ic_add),
                title = stringResource(R.string.app_hub_import),
                subtitle = stringResource(R.string.app_hub_import_sub),
                tileColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                // Animate the sheet closed before handing off, mirroring InlineCollectionCreateSheet
                // (sheetState.hide() then the callback) — the host then clears state + launches the picker.
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onImport()
                    }
                },
            )
            HubRow(
                iconPainter = painterResource(R.drawable.app_ic_mic),
                title = stringResource(R.string.app_hub_record),
                subtitle = stringResource(R.string.app_hub_record_sub),
                tileColor = MaterialTheme.colorScheme.surfaceVariant,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onRecord()
                    }
                },
            )
            // Soft secondary helper — neutral tile (not acid) so it never competes with the import
            // primary. Animate the sheet closed first, then hand off, mirroring the import row.
            HubRow(
                iconPainter = painterResource(R.drawable.app_ic_play_arrow),
                title = stringResource(R.string.app_hub_how),
                subtitle = stringResource(R.string.app_hub_how_sub),
                tileColor = MaterialTheme.colorScheme.surfaceVariant,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onHowItWorks()
                    }
                },
            )
        }
    }
}

@Composable
private fun HubRow(
    iconPainter: Painter,
    title: String,
    subtitle: String,
    tileColor: Color,
    iconColor: Color,
    onClick: (() -> Unit)?,
) {
    val enabled = onClick != null
    val contentAlpha = if (enabled) 1f else HUB_DISABLED_ALPHA
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(HUB_TILE_RADIUS))
                .then(
                    if (enabled) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        // Merge so the title/subtitle/badge form one node TalkBack announces as
                        // disabled — a bare semantics{} wrapper would leave them separate and unmarked.
                        Modifier.semantics(mergeDescendants = true) { disabled() }
                    },
                ).padding(vertical = Spacing.SM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.MD),
    ) {
        Box(
            modifier =
                Modifier
                    .size(HUB_ICON_TILE)
                    .clip(RoundedCornerShape(HUB_TILE_RADIUS))
                    .background(tileColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = iconColor.copy(alpha = contentAlpha),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
    }
}
