/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

@Composable
internal fun LegalAndPrivacySection(
    onLicenseClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onDataSafetyClick: () -> Unit,
    onTermsOfServiceClick: () -> Unit,
    onSourceClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.LG),
    ) {
        Text(
            text = stringResource(R.string.app_about_legal_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.SM, bottom = Spacing.XS),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column {
                LegalListItem(
                    icon = AppIcons.Description,
                    label = stringResource(R.string.app_about_license),
                    isExternal = false,
                    onClick = onLicenseClick,
                )
                LegalListItem(
                    icon = AppIcons.PrivacyTip,
                    label = stringResource(R.string.app_about_privacy_policy),
                    isExternal = true,
                    onClick = onPrivacyPolicyClick,
                )
                LegalListItem(
                    icon = AppIcons.Shield,
                    label = stringResource(R.string.app_about_data_safety),
                    isExternal = true,
                    onClick = onDataSafetyClick,
                )
                LegalListItem(
                    icon = AppIcons.Handshake,
                    label = stringResource(R.string.app_about_terms_of_service),
                    isExternal = true,
                    onClick = onTermsOfServiceClick,
                )
                LegalListItem(
                    icon = AppIcons.Code,
                    label = stringResource(R.string.app_about_source),
                    isExternal = true,
                    onClick = onSourceClick,
                )
            }
        }
    }
}

@Composable
private fun LegalListItem(
    icon: ImageVector,
    label: String,
    isExternal: Boolean,
    onClick: () -> Unit,
) {
    val externalLabel = stringResource(R.string.app_about_open_in_browser)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                headlineColor = MaterialTheme.colorScheme.onSurface,
                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        headlineContent = { Text(label) },
        trailingContent =
            if (isExternal) {
                {
                    Icon(
                        imageVector = AppIcons.OpenInNew,
                        contentDescription = externalLabel,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                null
            },
    )
}
