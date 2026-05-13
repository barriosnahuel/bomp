/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTracker
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.ui.haptics.performRejectHaptic
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
        // Pre-populate the cursor at the end in Edit mode so the user can append/correct without
        // first navigating to the end of the existing name. In Create mode the field is empty so
        // position 0 is correct.
        val initial = if (mode is AddButtonMode.Edit) mode.sound.name else ""
        mutableStateOf(TextFieldValue(text = initial, selection = TextRange(initial.length)))
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
    val nameFocusRequester = remember { FocusRequester() }
    val view = LocalView.current

    LaunchedEffect(Unit) {
        // Save the user one tap: the name field is the primary action on this screen, so focus it
        // on entry and let the IME come up immediately. Both modes (Create from share-sheet and
        // Edit from long-press) lead with a name input. `withFrameNanos` waits for the first
        // layout pass so the FocusRequester has a node attached — without it, requestFocus on the
        // very first composition is a no-op under Robolectric (caught the regression in tests).
        withFrameNanos { /* wait for first frame so the node is attached */ }
        runCatching { nameFocusRequester.requestFocus() }
    }

    TrackAbandonOnStop(
        pendingErrorReason = { pendingErrorReason },
        wasTracked = { abandonTracked },
        onTrack = { reason ->
            tracker.log(AnalyticsEvent.SoundAddAbandonedAfterError(reason))
            abandonTracked = true
        },
    )

    fun save() {
        if (name.text.isBlank()) {
            nameError = context.getString(R.string.app_addbutton_name_is_required_error)
            performRejectHaptic(view)
            return
        }
        if (saveOutcome != SaveOutcome.Idle || pendingErrorReason != null) return
        keyboardController?.hide()
        saveOutcome = SaveOutcome.Loading
        coroutineScope.launch {
            val trimmedName = name.text.trim()
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
                PreviewSlot(context = context, mode = mode, displayedName = name.text)

                OutlinedTextField(
                    value = name,
                    onValueChange = { new ->
                        name = new.copy(text = new.text.take(MAX_NAME_LENGTH))
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
                            Text(text = "${name.text.length}/$MAX_NAME_LENGTH")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester),
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
private fun PreviewSlot(
    context: Context,
    mode: AddButtonMode,
    displayedName: String,
) {
    // Edit mode resolves the file Uri via getExternalFilesDir(), which trips a StrictMode
    // DiskReadViolation if it runs synchronously during composition (see develop's 3f514cb).
    // produceState + Dispatchers.IO keeps composition non-blocking; the null window is invisible
    // because AudioPreview itself gates rendering on durationMs > 0.
    val source: Uri? by produceState<Uri?>(initialValue = null, mode) {
        value =
            withContext(Dispatchers.IO) {
                when (val m = mode) {
                    is AddButtonMode.Create -> m.uri
                    is AddButtonMode.Edit -> m.sound.file?.let { getFile(context, it).toUri() }
                }
            }
    }
    source?.let { resolvedSource ->
        val dateAdded = (mode as? AddButtonMode.Edit)?.sound?.dateAdded
        AudioPreview(
            context = context,
            source = resolvedSource,
            soundName = displayedName,
            dateAdded = dateAdded,
        )
        Spacer(modifier = Modifier.height(16.dp))
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
