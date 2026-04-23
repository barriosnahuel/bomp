/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.about

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.github.barriosnahuel.vossosunboton.BuildConfig
import com.github.barriosnahuel.vossosunboton.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isLicenseSheetVisible by remember { mutableStateOf(false) }
    var isCreditsSheetVisible by remember { mutableStateOf(false) }
    val licenseText = remember { context.readRawResource(R.raw.app_license) }
    val creditsText = remember { context.readRawResource(R.raw.app_third_party_notices) }
    val versionInfo = remember { context.versionInfo() }
    val sourceUrl = stringResource(R.string.app_about_source_url)

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.app_about_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))

            Image(
                painter = painterResource(R.mipmap.app_ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                colorFilter =
                    if (BuildConfig.DEBUG) {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                    } else {
                        null
                    },
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.app_about_tagline),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = versionInfo,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.alpha(VERSION_TEXT_ALPHA),
            )

            Spacer(Modifier.height(32.dp))

            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.app_about_license)) },
                trailingContent = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable { isLicenseSheetVisible = true },
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.app_about_credits)) },
                trailingContent = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable { isCreditsSheetVisible = true },
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.app_about_source)) },
                trailingContent = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable { openUrl(context, sourceUrl) },
            )

            HorizontalDivider()
        }
    }

    if (isLicenseSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { isLicenseSheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    Text(
                        text = licenseText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }

    if (isCreditsSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { isCreditsSheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    Text(
                        text = creditsText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}

private fun Context.versionInfo(): String =
    try {
        val info = packageManager.getPackageInfo(packageName, 0)
        "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }

private fun Context.readRawResource(resId: Int): String = resources.openRawResource(resId).bufferedReader().use { it.readText() }

private fun openUrl(
    context: Context,
    url: String,
) {
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
}

private const val VERSION_TEXT_ALPHA = 0.6f
