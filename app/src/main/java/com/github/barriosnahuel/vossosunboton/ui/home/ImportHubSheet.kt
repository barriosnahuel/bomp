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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

// Dim applied to a disabled row's icon + text. Standard Material disabled opacity; a disabled
// control is exempt from the WCAG 1.4.3 contrast minimum (§ Accessibility). All Hub rows are live
// today; the nullable-onClick path is kept for future inert rows.
private const val HUB_DISABLED_ALPHA = 0.38f
private val HUB_ICON_TILE = 44.dp
private val HUB_TILE_RADIUS = 12.dp

/**
 * The import Hub's sheet content — rendered inside the modal bottom sheet that the Nav3
 * `BottomSheetSceneStrategy` provides for `ImportHubRoute` (swipe/scrim/back dismissal pop the
 * route; ADR 0024). Rows are ordered by likely intent for a returning user: "record" a new Bomp
 * in-app ([onRecord], ADR 0019, the acid-primary row), "bring audios from other apps"
 * ([onBringFromApps]) that opens a focused single-step guide on sharing a voice note in from
 * WhatsApp/Telegram, and "import audio from your device" ([onImport]) for a file already saved on
 * the phone (the least-frequent path on Android 11+, where app-private media is not SAF-browsable).
 * The full 3-step onboarding tour is reachable from the empty state, not from here.
 *
 * Presentational only: the caller pops the route on [onImport]/[onRecord]/[onBringFromApps] and
 * owns the file picker, the recorder Activity, and the guide destination.
 */
@Composable
internal fun ImportHubSheet(
    onImport: () -> Unit,
    onRecord: () -> Unit,
    onBringFromApps: () -> Unit,
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
        // Acid-primary row: recording is the one creation path that always works, regardless of what
        // the user already has on the device.
        HubRow(
            iconPainter = painterResource(R.drawable.app_ic_mic),
            title = stringResource(R.string.app_hub_record),
            subtitle = stringResource(R.string.app_hub_record_sub),
            tileColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onRecord,
        )
        // Neutral tile (not acid) so it never competes with the primary record row. The share icon
        // mirrors the gesture the guide teaches: "Share" a voice note from another app into Bomp.
        HubRow(
            iconPainter = painterResource(R.drawable.app_ic_share),
            title = stringResource(R.string.app_hub_bring),
            subtitle = stringResource(R.string.app_hub_bring_sub),
            tileColor = MaterialTheme.colorScheme.surfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onBringFromApps,
        )
        HubRow(
            iconPainter = painterResource(R.drawable.app_ic_add),
            title = stringResource(R.string.app_hub_import),
            subtitle = stringResource(R.string.app_hub_import_sub),
            tileColor = MaterialTheme.colorScheme.surfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onImport,
        )
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
