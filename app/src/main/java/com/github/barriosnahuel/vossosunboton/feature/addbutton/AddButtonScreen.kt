/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTracker
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.home.formatDuration
import com.github.barriosnahuel.vossosunboton.ui.home.formatRelativeDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

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
    var saveOutcome by remember { mutableStateOf<SaveOutcome>(SaveOutcome.Idle) }
    var pendingErrorReason by remember { mutableStateOf<String?>(null) }
    var abandonTracked by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val retryLabel = stringResource(R.string.app_snackbar_action_retry)
    val tracker = remember(context) { AnalyticsTrackerProvider.get(context.applicationContext) }

    TrackAbandonOnStop(
        pendingErrorReason = { pendingErrorReason },
        wasTracked = { abandonTracked },
        onTrack = { reason ->
            tracker.log(AnalyticsEvent.SoundAddAbandonedAfterError(reason))
            abandonTracked = true
        },
    )

    fun save() {
        if (name.isBlank()) {
            nameError = context.getString(R.string.app_addbutton_name_is_required_error)
            return
        }
        if (saveOutcome != SaveOutcome.Idle || pendingErrorReason != null) return
        keyboardController?.hide()
        saveOutcome = SaveOutcome.Loading
        coroutineScope.launch {
            val trimmedName = name.trim()
            val feature = AddButtonFeatureProvider.get()
            when (val m = mode) {
                is AddButtonMode.Create -> {
                    val feedbackId = feature.saveNewButtonAsync(context, trimmedName, m.uri.toString()).await()
                    if (feedbackId == R.string.app_addbutton_feedback_saved_ok) {
                        trackSoundAdd(context, trimmedName, tracker)
                        saveOutcome = SaveOutcome.Success(trimmedName)
                    } else {
                        pendingErrorReason = mapFeedbackToReason(feedbackId)
                        saveOutcome = SaveOutcome.Idle
                        handleSaveError(
                            feedbackId = feedbackId,
                            context = context,
                            snackbarHostState = snackbarHostState,
                            retryLabel = retryLabel,
                            onRetry = {
                                pendingErrorReason = null
                                save()
                            },
                            onDismiss = {
                                pendingErrorReason?.let { reason ->
                                    tracker.log(AnalyticsEvent.SoundAddAbandonedAfterError(reason))
                                    pendingErrorReason = null
                                    abandonTracked = true
                                }
                            },
                        )
                    }
                }
                is AddButtonMode.Edit -> {
                    feature.renameButtonAsync(context, m.sound, trimmedName).await()
                    trackSoundEdit(trimmedName, m.sound.name, tracker)
                    saveOutcome = SaveOutcome.Success(trimmedName)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { AddButtonTopBar(mode = mode, onNavigateUp = onNavigateUp) },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            ) {
                val editSound = (mode as? AddButtonMode.Edit)?.sound
                val editFile = editSound?.file
                if (editFile != null) {
                    AudioPreview(
                        context = context,
                        fileName = editFile,
                        soundName = name,
                        dateAdded = editSound.dateAdded,
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
                SaveButton(
                    outcome = saveOutcome,
                    mode = mode,
                    enabled = saveOutcome is SaveOutcome.Idle && pendingErrorReason == null,
                    onClick = { save() },
                )
            }
        }
        SaveSuccessOverlayHost(
            outcome = saveOutcome,
            mode = mode,
            onSaved = onSaved,
        )
    }
}

@Composable
private fun SaveSuccessOverlayHost(
    outcome: SaveOutcome,
    mode: AddButtonMode,
    onSaved: (String) -> Unit,
) {
    if (outcome !is SaveOutcome.Success) return
    val isEdit = mode is AddButtonMode.Edit
    SaveSuccessOverlay(
        name = outcome.name,
        announcementTemplate = if (isEdit) R.string.app_feedback_button_renamed else R.string.app_feedback_button_saved,
        subtitleId = if (isEdit) R.string.app_addbutton_overlay_subtitle_renamed else R.string.app_addbutton_overlay_subtitle_saved,
        onFinished = { onSaved(outcome.name) },
    )
}

@Composable
private fun AudioPreview(
    context: Context,
    fileName: String,
    soundName: String,
    dateAdded: Long?,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    val player = remember { MediaPlayer() }
    var isReady by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0) }

    LaunchedEffect(fileName) {
        val prepared =
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = getFile(context, fileName)
                    player.setDataSource(file.absolutePath)
                    player.prepare()
                    player.duration
                }
            }
        prepared
            .onSuccess { duration ->
                durationMs = duration
                player.setOnCompletionListener {
                    isPlaying = false
                    sliderPosition = 0f
                }
                isReady = true
            }.onFailure { e ->
                when (e) {
                    is IOException -> Timber.w(e, "Could not load audio preview for file: %s", fileName)
                    is IllegalStateException -> Timber.w(e, "MediaPlayer in invalid state for file: %s", fileName)
                    else -> throw e
                }
            }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    if (isReady) {
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
                                if (isPlaying) {
                                    player.pause()
                                    isPlaying = false
                                } else {
                                    player.start()
                                    isPlaying = true
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
                                sliderPosition = value
                                if (durationMs > 0) player.seekTo((value * durationMs).toInt())
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

private sealed interface SaveOutcome {
    data object Idle : SaveOutcome

    data object Loading : SaveOutcome

    data class Success(
        val name: String,
    ) : SaveOutcome
}

private fun mapFeedbackToReason(
    @StringRes feedbackId: Int,
): String =
    when (feedbackId) {
        R.string.app_addbutton_feedback_uri_unreadable -> "uri_unreadable"
        R.string.app_addbutton_feedback_save_failed -> "save_failed"
        else -> "unknown"
    }

private suspend fun trackSoundAdd(
    context: Context,
    trimmedName: String,
    tracker: AnalyticsTracker,
) {
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
}

private fun trackSoundEdit(
    trimmedName: String,
    previousName: String,
    tracker: AnalyticsTracker,
) {
    tracker.log(
        AnalyticsEvent.SoundEdit(
            nameLength = trimmedName.length,
            nameWordCount = trimmedName.split(WORD_SPLIT_REGEX).count(),
            nameHitLimit = trimmedName.length == MAX_NAME_LENGTH,
            nameChanged = trimmedName != previousName,
        ),
    )
}

private suspend fun handleSaveError(
    @StringRes feedbackId: Int,
    context: Context,
    snackbarHostState: SnackbarHostState,
    retryLabel: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val result =
        snackbarHostState.showSnackbar(
            message = context.getString(feedbackId),
            actionLabel = retryLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
    when (result) {
        SnackbarResult.ActionPerformed -> onRetry()
        SnackbarResult.Dismissed -> onDismiss()
    }
}

@Composable
private fun TrackAbandonOnStop(
    pendingErrorReason: () -> String?,
    wasTracked: () -> Boolean,
    onTrack: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    val reason = pendingErrorReason()
                    if (reason != null && !wasTracked()) {
                        onTrack(reason)
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun SaveButton(
    outcome: SaveOutcome,
    mode: AddButtonMode,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val idleLabel =
        stringResource(
            if (mode is AddButtonMode.Edit) {
                R.string.app_addbutton_save_changes
            } else {
                R.string.app_addbutton_save
            },
        )
    Button(
        onClick = onClick,
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = outcome,
            label = "save_button_morph",
        ) { state ->
            when (state) {
                SaveOutcome.Idle -> Text(text = idleLabel)
                SaveOutcome.Loading ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        strokeWidth = 2.dp,
                    )
                is SaveOutcome.Success ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = state.name)
                    }
            }
        }
    }
}

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
