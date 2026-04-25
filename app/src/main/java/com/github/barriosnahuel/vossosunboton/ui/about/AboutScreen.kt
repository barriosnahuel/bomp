/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.barriosnahuel.vossosunboton.ui.about

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.github.barriosnahuel.vossosunboton.BuildConfig
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import java.util.Locale

private val COLLABORATORS: List<Collaborator> = emptyList()

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isLicenseSheetVisible by remember { mutableStateOf(false) }
    val licenseText = remember { context.readRawResource(R.raw.app_license) }
    val creditsText = remember { context.readRawResource(R.raw.app_third_party_notices) }
    val creditEntries = remember { parseCreditEntries(creditsText) }
    val versionInfo = remember { context.versionInfo() }
    val sourceUrl = stringResource(R.string.app_about_source_url)
    val isEnglishLocale = remember { Locale.getDefault().language == "en" }

    var soundId by remember { mutableIntStateOf(0) }
    val soundPool =
        remember {
            SoundPool
                .Builder()
                .setMaxStreams(1)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).build()
        }
    DisposableEffect(Unit) {
        soundPool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) soundId = id
        }
        soundPool.load(context, R.raw.app_branding_audio, 1)
        onDispose { soundPool.release() }
    }

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
            HeroSection(
                versionInfo = versionInfo,
                soundPool = soundPool,
                soundId = soundId,
                isEnglishLocale = isEnglishLocale,
            )
            Spacer(Modifier.height(Spacing.XL))
            CreditsSection(creditEntries = creditEntries)
            if (COLLABORATORS.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.LG))
                CollaboratorsSection(COLLABORATORS)
            }
            Spacer(Modifier.height(Spacing.LG))
            LegalSection(
                onLicenseClick = { isLicenseSheetVisible = true },
                onSourceClick = { openUrl(context, sourceUrl) },
            )
            Spacer(Modifier.height(Spacing.XXL))
        }
    }

    if (isLicenseSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { isLicenseSheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LazyColumn(
                modifier = Modifier.padding(horizontal = Spacing.LG),
                contentPadding = PaddingValues(bottom = Spacing.XXL),
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
}

@Composable
private fun HeroSection(
    versionInfo: String,
    soundPool: SoundPool,
    soundId: Int,
    isEnglishLocale: Boolean,
) {
    Spacer(Modifier.height(Spacing.XXL))

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

    Spacer(Modifier.height(Spacing.LG))

    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(
            onClick = { if (soundId > 0) soundPool.play(soundId, 1f, 1f, 1, 0, 1f) },
            modifier = Modifier.size(44.dp),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Icon(
                imageVector = AppIcons.VolumeUp,
                contentDescription = stringResource(R.string.app_about_play_branding_audio),
            )
        }
        Spacer(Modifier.width(Spacing.SM))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.width(Spacing.SM))
        Spacer(Modifier.size(44.dp))
    }

    if (isEnglishLocale) {
        Text(
            text = stringResource(R.string.app_about_pronunciation),
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(Spacing.MD))

    Text(
        text = "“${stringResource(R.string.app_about_tagline)}”",
        style = MaterialTheme.typography.bodyLarge,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = Spacing.LG),
    )

    Spacer(Modifier.height(Spacing.SM))

    Text(
        text = versionInfo,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CreditsSection(creditEntries: List<CreditEntry>) {
    var isExpanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "credits_arrow",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = Spacing.LG, vertical = Spacing.MD),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_about_credits),
            style = MaterialTheme.typography.titleMedium,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.rotate(arrowRotation),
        )
    }

    AnimatedVisibility(visible = isExpanded) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.LG),
            verticalArrangement = Arrangement.spacedBy(Spacing.SM),
        ) {
            AiAttributionCard(
                name = stringResource(R.string.app_about_ai_gemini_name),
                role = stringResource(R.string.app_about_ai_gemini_role),
            )
            AiAttributionCard(
                name = stringResource(R.string.app_about_ai_claude_name),
                role = stringResource(R.string.app_about_ai_claude_role),
            )
            creditEntries.forEach { entry ->
                CreditCard(entry = entry)
            }
            Spacer(Modifier.height(Spacing.XS))
        }
    }
}

@Composable
private fun AiAttributionCard(
    name: String,
    role: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(Spacing.LG)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = role,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreditCard(entry: CreditEntry) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(Spacing.LG)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            entry.copyright?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = entry.license,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.url?.let { url ->
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { openUrl(context, url) },
                )
            }
        }
    }
}

@Composable
private fun CollaboratorsSection(collaborators: List<Collaborator>) {
    Text(
        text = stringResource(R.string.app_about_collaborators_section_title),
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.LG),
    )
    Spacer(Modifier.height(Spacing.SM))
    collaborators.forEach { collaborator ->
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.LG, vertical = Spacing.XS),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(Spacing.LG)) {
                Text(
                    text = collaborator.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = collaborator.role,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegalSection(
    onLicenseClick: () -> Unit,
    onSourceClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onLicenseClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.LG, vertical = Spacing.XS),
    ) {
        Text(stringResource(R.string.app_about_license))
    }
    OutlinedButton(
        onClick = onSourceClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.LG, vertical = Spacing.XS),
    ) {
        Text(stringResource(R.string.app_about_source))
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
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
