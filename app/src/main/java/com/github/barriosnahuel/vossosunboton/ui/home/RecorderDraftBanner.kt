/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

/**
 * Non-intrusive banner offering to resume a recording the user captured but never saved (ADR 0019 §
 * Draft recovery). Shown on the My Sounds list when [RecorderDraftStore] reports a pending draft —
 * surviving a launcher re-entry or process death that would otherwise silently drop the clip.
 *
 * Stateless: [onContinue] reopens the recorder on the draft; [onDiscard] drops it. Text-tier actions
 * (ADR 0010) on a `surfaceVariant` container with a divider — separation from the scrolling list
 * without a border.
 */
@Composable
internal fun RecorderDraftBanner(
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.LG, end = Spacing.SM, top = Spacing.XS, bottom = Spacing.XS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
            ) {
                Icon(
                    painter = painterResource(R.drawable.app_ic_mic),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ICON_SIZE),
                )
                Text(
                    text = stringResource(R.string.app_recorder_draft_banner_message),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.app_recorder_draft_discard))
                }
                TextButton(onClick = onContinue) {
                    Text(stringResource(R.string.app_recorder_draft_continue))
                }
            }
            HorizontalDivider()
        }
    }
}

private val ICON_SIZE = 20.dp
