/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.home.formatDuration
import com.github.barriosnahuel.vossosunboton.ui.home.formatRelativeDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddButtonScreen(
    context: Context,
    mode: AddButtonMode,
    onSaved: (String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    var name by remember {
        mutableStateOf(if (mode is AddButtonMode.Edit) mode.sound.name else "")
    }
    var nameError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val dismissLabel = stringResource(R.string.app_snackbar_action_dismiss)

    fun save() {
        if (name.isBlank()) {
            nameError = context.getString(R.string.app_addbutton_name_is_required_error)
            return
        }
        keyboardController?.hide()
        coroutineScope.launch {
            val trimmedName = name.trim()
            val tracker = AnalyticsTrackerProvider.get(context.applicationContext)
            val feature = AddButtonFeatureProvider.get()
            when (val m = mode) {
                is AddButtonMode.Create -> {
                    val feedbackId =
                        feature.saveNewButtonAsync(context, trimmedName, m.uri.toString()).await()
                    if (feedbackId == R.string.app_addbutton_feedback_saved_ok) {
                        val totalSounds = SoundsRepository(context).sounds.first().size
                        tracker.log(
                            AnalyticsEvent.SoundAdd(
                                source = AddButtonActivity.SOURCE_SHARE,
                                nameLength = trimmedName.length,
                                nameWordCount = trimmedName.split(WORD_SPLIT_REGEX).count(),
                                nameHitLimit = trimmedName.length == MAX_NAME_LENGTH,
                                currentSounds = totalSounds,
                            ),
                        )
                    } else {
                        // Indefinite + dismiss action so the user can read the failure on their own pace
                        // (matches LandingScreen.shareWithFeedback rationale: TalkBack reads ~10–12 chars/sec
                        // and es-AR copy can exceed what SnackbarDuration.Long flushes). Skipping onSaved
                        // keeps the user on the form with the typed name preserved so they can retry.
                        snackbarHostState.showSnackbar(
                            message = context.getString(feedbackId),
                            actionLabel = dismissLabel,
                            duration = SnackbarDuration.Indefinite,
                        )
                        return@launch
                    }
                }
                is AddButtonMode.Edit -> {
                    feature.renameButtonAsync(context, m.sound, trimmedName).await()
                    tracker.log(
                        AnalyticsEvent.SoundEdit(
                            nameLength = trimmedName.length,
                            nameWordCount = trimmedName.split(WORD_SPLIT_REGEX).count(),
                            nameHitLimit = trimmedName.length == MAX_NAME_LENGTH,
                            nameChanged = trimmedName != m.sound.name,
                        ),
                    )
                }
            }
            withContext(Dispatchers.Main) { onSaved(trimmedName) }
        }
    }

    Scaffold(
        topBar = { AddButtonTopBar(mode = mode, onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        ) {
            // Edit mode resolves the file Uri via getExternalFilesDir(), which trips a StrictMode
            // DiskReadViolation if it runs synchronously during composition. produceState + IO keeps
            // composition non-blocking; the null window is invisible because AudioPreview gates on durationMs > 0.
            val previewSource: Uri? by produceState<Uri?>(initialValue = null, mode) {
                value =
                    withContext(Dispatchers.IO) {
                        when (val m = mode) {
                            is AddButtonMode.Create -> m.uri
                            is AddButtonMode.Edit -> m.sound.file?.let { getFile(context, it).toUri() }
                        }
                    }
            }
            val previewDateAdded = (mode as? AddButtonMode.Edit)?.sound?.dateAdded
            previewSource?.let { source ->
                AudioPreview(
                    context = context,
                    source = source,
                    soundName = name,
                    dateAdded = previewDateAdded,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(MAX_NAME_LENGTH)
                    nameError = null
                },
                label = {
                    Text(
                        stringResource(
                            if (mode is AddButtonMode.Edit) {
                                R.string.app_addbutton_edit_name_label
                            } else {
                                R.string.app_addbutton_name
                            },
                        ),
                    )
                },
                placeholder = { Text(stringResource(R.string.app_addbutton_placeholder)) },
                isError = nameError != null,
                supportingText = {
                    val error = nameError
                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (error != null) {
                            Text(
                                text = error,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Text(text = "${name.length}/$MAX_NAME_LENGTH")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { save() },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (mode is AddButtonMode.Edit) {
                            R.string.app_addbutton_save_changes
                        } else {
                            R.string.app_addbutton_save
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun AudioPreview(
    context: Context,
    source: Uri,
    soundName: String,
    dateAdded: Long?,
) {
    val controller = remember { PlayerControllerFactory.instance }
    var durationMs by remember(source) { mutableStateOf(0) }

    LaunchedEffect(source) {
        // MediaMetadataRetriever runs off-thread to avoid blocking the main looper. Failure leaves
        // durationMs at 0 which keeps the Card hidden — same UX as a player that can't prepare.
        // Mirrors AddButtonFeature's canonical pattern (same wrapper message so both call-sites
        // group under one Crashlytics issue; the breadcrumb disambiguates the path).
        durationMs =
            withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, source)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0
                    } finally {
                        retriever.release()
                    }
                }.onFailure {
                    Tracker.log("addbutton.preview.uri=$source")
                    Tracker.track(RuntimeException("Failed to extract duration metadata", it))
                }.getOrDefault(0)
            }
    }

    val playbackState by controller.playbackState.collectAsStateWithLifecycle()
    val isOurPreview = playbackState?.uri == source
    val isPlaying = isOurPreview && playbackState?.isPlaying == true
    val sliderPosition =
        if (isOurPreview && durationMs > 0) {
            playbackState!!.positionMs.toFloat() / durationMs
        } else {
            0f
        }

    DisposableEffect(source) {
        onDispose {
            // Stop preview playback when the user backs out of AddButton. Guard against pre-empting
            // an unrelated playback (e.g., user backed out and Home is now the active player).
            if (controller.playbackState.value?.uri == source) {
                controller.stopPlayingSound()
            }
        }
    }

    if (durationMs > 0) {
        Card(
            border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(
                    text = soundName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    val playContainerColor = MaterialTheme.colorScheme.primaryContainer
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .background(
                                    color = if (isPlaying) playContainerColor.copy(alpha = 0.18f) else Color.Transparent,
                                    shape = CircleShape,
                                ).padding(6.dp),
                    ) {
                        FilledIconButton(
                            onClick = {
                                when {
                                    isPlaying -> controller.pause()
                                    isOurPreview -> controller.resume()
                                    else -> controller.startPlayingUri(context, source)
                                }
                            },
                            modifier = Modifier.size(44.dp),
                            colors =
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = playContainerColor,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.app_addbutton_preview_audio),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Slider(
                            value = sliderPosition,
                            onValueChange = { value ->
                                if (durationMs > 0) controller.seekTo((value * durationMs).toInt())
                            },
                            enabled = isPlaying,
                            colors =
                                SliderDefaults.colors(
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
                                    disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
                                    disabledThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
                                    disabledActiveTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = formatDuration(durationMs),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (dateAdded != null) {
                                Text(
                                    text = formatRelativeDate(dateAdded),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_NAME_LENGTH = 50

private val WORD_SPLIT_REGEX = "\\s+".toRegex()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddButtonTopBar(
    mode: AddButtonMode,
    onNavigateUp: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                stringResource(
                    if (mode is AddButtonMode.Edit) {
                        R.string.app_addbutton_activity_title_edit
                    } else {
                        R.string.app_addbutton_activity_title
                    },
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
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
}
