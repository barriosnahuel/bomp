/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import java.util.Locale

@Composable
internal fun GratitudeSection(
    onCafecitoClick: () -> Unit,
    onKofiClick: () -> Unit,
    showCafecito: Boolean = Locale.getDefault().country == AR_COUNTRY,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.LG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(Spacing.LG)) {
            Text(
                text = stringResource(R.string.app_about_gratitude_eyebrow),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.XS))
            Text(
                text = stringResource(R.string.app_about_gratitude_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.SM))
            Text(
                text = stringResource(R.string.app_about_gratitude_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.LG))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
            ) {
                if (showCafecito) {
                    Button(
                        onClick = onCafecitoClick,
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    ) {
                        Text(
                            stringResource(R.string.app_about_gratitude_cafecito_button),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Button(
                    onClick = onKofiClick,
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                ) {
                    Text(
                        stringResource(R.string.app_about_gratitude_kofi_button),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private const val AR_COUNTRY = "AR"
