/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.barriosnahuel.vossosunboton.ui.about

import android.content.ActivityNotFoundException
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing
import com.github.barriosnahuel.vossosunboton.util.withDeviceHl
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Saveable so a rotation while reading the license sheet does not collapse it back to the
    // About screen and force the user to find + reopen it.
    var isLicenseSheetVisible by rememberSaveable { mutableStateOf(false) }
    val licenseText = remember { context.readRawResource(R.raw.app_license) }
    val creditsText = remember { context.readRawResource(R.raw.app_third_party_notices) }
    val creditEntries = remember { parseCreditEntries(creditsText) }
    val versionInfo = remember { context.versionInfo() }
    val sourceUrl = stringResource(R.string.app_about_source_url)
    val privacyPolicyUrl = stringResource(R.string.app_about_privacy_policy_url)
    val dataSafetyUrl = stringResource(R.string.app_about_data_safety_url)
    val termsOfServiceUrl = stringResource(R.string.app_about_terms_of_service_url)
    val cafecitoUrl = stringResource(R.string.app_about_gratitude_cafecito_url)
    val kofiUrl = stringResource(R.string.app_about_gratitude_kofi_url)
    val noBrowserMessage = stringResource(R.string.app_about_error_no_browser)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                debugRootDirName = BuildConfig.DEBUG_ROOT_DIR_NAME,
                soundPool = soundPool,
                soundId = soundId,
            )
            Spacer(Modifier.height(Spacing.XL))
            CreditsSection(creditEntries = creditEntries)
            Spacer(Modifier.height(Spacing.XL))
            GratitudeSection(
                onCafecitoClick = {
                    if (openUrl(context, cafecitoUrl)) {
                        AnalyticsTrackerProvider
                            .get(context.applicationContext)
                            .log(AnalyticsEvent.AboutGratitudeCafecitoOpen)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(noBrowserMessage) }
                    }
                },
                onKofiClick = {
                    if (openUrl(context, kofiUrl)) {
                        AnalyticsTrackerProvider
                            .get(context.applicationContext)
                            .log(AnalyticsEvent.AboutGratitudeKofiOpen)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(noBrowserMessage) }
                    }
                },
            )
            Spacer(Modifier.height(Spacing.XL))
            LegalAndPrivacySection(
                onLicenseClick = {
                    AnalyticsTrackerProvider.get(context.applicationContext).log(AnalyticsEvent.AboutLicenseOpen)
                    isLicenseSheetVisible = true
                },
                onPrivacyPolicyClick = {
                    if (openUrl(context, privacyPolicyUrl.withDeviceHl())) {
                        AnalyticsTrackerProvider.get(context.applicationContext).log(AnalyticsEvent.AboutPrivacyPolicyOpen)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(noBrowserMessage) }
                    }
                },
                onDataSafetyClick = {
                    if (openUrl(context, dataSafetyUrl.withDeviceHl())) {
                        AnalyticsTrackerProvider.get(context.applicationContext).log(AnalyticsEvent.AboutDataSafetyOpen)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(noBrowserMessage) }
                    }
                },
                onTermsOfServiceClick = {
                    if (openUrl(context, termsOfServiceUrl.withDeviceHl())) {
                        AnalyticsTrackerProvider.get(context.applicationContext).log(AnalyticsEvent.AboutTermsOfServiceOpen)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(noBrowserMessage) }
                    }
                },
                onSourceClick = {
                    if (openUrl(context, sourceUrl)) {
                        AnalyticsTrackerProvider.get(context.applicationContext).log(AnalyticsEvent.AboutSourceOpen)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(noBrowserMessage) }
                    }
                },
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
    debugRootDirName: String,
    soundPool: SoundPool,
    soundId: Int,
) {
    Spacer(Modifier.height(Spacing.XXL))

    Image(
        painter = painterResource(R.drawable.app_brand_mark_about),
        contentDescription = null,
        modifier = Modifier.size(96.dp),
    )

    Spacer(Modifier.height(Spacing.LG))

    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(
            onClick = {
                if (soundId > 0) {
                    soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
                    AnalyticsTrackerProvider
                        .get(context.applicationContext)
                        .log(AnalyticsEvent.AboutBrandingAudioPlay)
                }
            },
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

    if (debugRootDirName.isNotBlank()) {
        Spacer(Modifier.height(Spacing.XS))
        Text(
            text = debugRootDirName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CreditsSection(creditEntries: List<CreditEntry>) {
    var isExpanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "credits_arrow",
    )
    val context = LocalContext.current

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    val opening = !isExpanded
                    isExpanded = opening
                    if (opening) {
                        AnalyticsTrackerProvider
                            .get(context.applicationContext)
                            .log(AnalyticsEvent.AboutCreditsOpen)
                    }
                }.padding(horizontal = Spacing.LG, vertical = Spacing.MD),
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
            AttributionCard(
                name = stringResource(R.string.app_about_collaborators_family_name),
                role = stringResource(R.string.app_about_collaborators_family_role),
            )
            AttributionCard(
                name = stringResource(R.string.app_about_collaborators_first_audios_name),
                role = stringResource(R.string.app_about_collaborators_first_audios_role),
            )
            AttributionCard(
                name = stringResource(R.string.app_about_ai_gemini_name),
                role = stringResource(R.string.app_about_ai_gemini_role),
            )
            AttributionCard(
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
private fun AttributionCard(
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
): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (e: ActivityNotFoundException) {
        Tracker.log("about.url=$url")
        Tracker.track(RuntimeException("Could not launch ACTION_VIEW", e))
        false
    }
