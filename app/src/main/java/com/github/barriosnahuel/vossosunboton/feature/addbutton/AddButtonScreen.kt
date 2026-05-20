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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGate
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateResult
import com.github.barriosnahuel.vossosunboton.feature.vault.security.BiometricGateStatus
import com.github.barriosnahuel.vossosunboton.feature.vault.security.VaultSessionState
import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.model.data.manager.CollectionsRepository
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
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        // Pre-populate the cursor at the end in Edit mode so the user can append/correct without
        // first navigating to the end of the existing name. In Create mode the field is empty so
        // position 0 is correct. Saveable so a mid-typing rotation does not reset the user's input
        // back to the original (Edit) or empty (Create).
        val initial = if (mode is AddButtonMode.Edit) mode.sound.name else ""
        mutableStateOf(TextFieldValue(text = initial, selection = TextRange(initial.length)))
    }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var saveOutcome by rememberSaveable(stateSaver = SaveOutcomeSaver) {
        mutableStateOf<SaveOutcome>(SaveOutcome.Idle)
    }
    var pendingErrorReason by rememberSaveable { mutableStateOf<String?>(null) }
    var abandonTracked by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val retryLabel = stringResource(R.string.app_snackbar_action_retry)
    val tracker = remember(context) { AnalyticsTrackerProvider.get(context.applicationContext) }
    val nameFocusRequester = remember { FocusRequester() }
    val view = LocalView.current

    // Reactive lookup against the user's library (custom + bundled). Derivation, predicate, and
    // the supporting `produceState` live in `DuplicateNameHint.kt` so this composable stays under
    // the detekt complexity threshold. Fires `duplicate_name_hint_shown` once per matched sound id
    // (ADR 0008) so per-keystroke recompositions while the same match holds emit a single event;
    // a transition to a different match (or to no-match-then-back) re-emits, which is acceptable
    // signal for typing exploration.
    val duplicateMatch = rememberDuplicateNameMatch(context, name.text, mode)
    LaunchedEffect(duplicateMatch?.id) {
        if (duplicateMatch != null) {
            tracker.log(AnalyticsEvent.DuplicateNameHintShown)
        }
    }

    LaunchedEffect(Unit) {
        // Save the user one tap: the name field is the primary action on this screen, so focus it
        // on entry and let the IME come up immediately. Both modes (Create from share-sheet and
        // Edit from long-press) lead with a name input. `withFrameNanos` waits for the first
        // layout pass so the FocusRequester has a node attached — without it, requestFocus on the
        // very first composition is a no-op under Robolectric (caught the regression in tests).
        withFrameNanos { /* wait for first frame so the node is attached */ }
        runCatching { nameFocusRequester.requestFocus() }
            .onFailure {
                // requestFocus() on a never-attached FocusRequester silently failed before; the
                // user lost focus on the screen's primary action with nothing in logcat. Surface
                // as a non-fatal so a spike points at a real composition-lifecycle bug (disposed
                // before first frame, IME-driven recomposition, etc.). Static wrapper title +
                // breadcrumb per CLAUDE.md § Error tracking.
                Tracker.log("addbutton.mode=${if (mode is AddButtonMode.Edit) "edit" else "create"}")
                Tracker.track(RuntimeException("AddButton name field focus request failed", it))
            }
    }

    TrackAbandonOnStop(
        pendingErrorReason = { pendingErrorReason },
        wasTracked = { abandonTracked },
        onTrack = { reason ->
            tracker.log(AnalyticsEvent.SoundAddAbandonedAfterError(reason))
            abandonTracked = true
        },
    )

    val collectionsState = rememberCollectionsState(context = context, mode = mode)
    // Start revealed if the session already proved access to the Vault. Avoids forcing a second
    // biometric prompt inside the same session.
    var privateRevealed by rememberSaveable { mutableStateOf(VaultSessionState.isVaultOpen()) }
    // Pending "+ Nueva" sheet request — null when no sheet is open. Holds the scope (PUBLIC vs
    // PRIVATE) so the sheet renders the right title and so the auto-tag logic knows which set
    // to grow on success.
    var pendingNewCollectionScope by rememberSaveable {
        mutableStateOf<CollectionAccess?>(null)
    }
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricGate = remember(activity) { activity?.let { BiometricGate(it) } }
    val biometricStatus = remember(biometricGate) { biometricGate?.status() ?: BiometricGateStatus.UNAVAILABLE }
    val privateUnlockTitle =
        stringResource(R.string.app_vault_biometric_prompt_title, stringResource(R.string.app_addbutton_collections_section_title))
    val privateUnlockSubtitle = stringResource(R.string.app_vault_biometric_prompt_subtitle)
    val privateUnlockNegative = stringResource(R.string.app_vault_biometric_negative)

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
                    val feedbackId =
                        feature
                            .saveNewButtonAsync(
                                context = context,
                                name = trimmedName,
                                uri = m.uri.toString(),
                                publicCollectionIds = collectionsState.publicSelection.value,
                                privateCollectionIds = collectionsState.privateSelection.value,
                            ).await()
                    if (feedbackId == R.string.app_addbutton_feedback_saved_ok) {
                        stopActivePreviewPlayback()
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
                    // Persist tag deltas alongside the rename. Tagging failures don't roll back
                    // the rename (the audio is renamed, just with stale tags); the non-fatal is
                    // tracked inside CollectionsRepository's error path.
                    val collectionsRepo = CollectionsRepository(context, onError = Tracker::track)
                    runCatching {
                        collectionsRepo.setAudioCollections(
                            m.sound.id,
                            collectionsState.publicSelection.value,
                            CollectionAccess.PUBLIC,
                        )
                        collectionsRepo.setAudioCollections(
                            m.sound.id,
                            collectionsState.privateSelection.value,
                            CollectionAccess.PRIVATE,
                        )
                    }.onFailure {
                        Tracker.log("addbutton.edit_tag_apply_failed=${it.javaClass.simpleName}")
                        Tracker.track(RuntimeException("Failed to apply tag changes on edit", it))
                    }
                    stopActivePreviewPlayback()
                    trackSoundEdit(trimmedName, m.sound.name, tracker)
                    saveOutcome = SaveOutcome.Success(trimmedName)
                }
            }
        }
    }

    // imePadding() lives on the outer Box, not the inner content Column. With the modifier
    // applied inside the Scaffold body the IME inset was only reducing the *content* region —
    // the SaveButton (pinned to the bottom of that region) would slide up correctly, but on
    // some devices the scroll container's measured height didn't update fast enough and the
    // last field appeared to sit under the IME with no overflow to scroll into. Anchoring the
    // imePadding on the screen root keeps the whole Scaffold above the keyboard, so every
    // child — top bar, scrollable form, pinned button — shifts together and there is always
    // either room on screen or visible overflow to scroll.
    Box(modifier = Modifier.fillMaxSize().imePadding()) {
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
                // The form content scrolls; the SaveButton stays pinned at the bottom so the
                // primary action is always reachable even when the device is short / the IME is
                // up / a long Assign-to-Collections section overflows the viewport.
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
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
                            NameFieldSupportingText(
                                error = nameError,
                                nameLength = name.text.length,
                                maxNameLength = MAX_NAME_LENGTH,
                                duplicateMatch = duplicateMatch,
                                context = context,
                                tracker = tracker,
                            )
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
                    AssignToCollectionsSection(
                        publicCollections = collectionsState.publicCollections.value,
                        privateCollections = collectionsState.privateCollections.value,
                        publicSelection = collectionsState.publicSelection.value,
                        privateSelection = collectionsState.privateSelection.value,
                        biometricStatus = biometricStatus,
                        privateRevealed = privateRevealed,
                        onPublicSelectionChange = { collectionsState.publicSelection.value = it },
                        onPrivateSelectionChange = { collectionsState.privateSelection.value = it },
                        onCreatePublicRequested = { pendingNewCollectionScope = CollectionAccess.PUBLIC },
                        onCreatePrivateRequested = { pendingNewCollectionScope = CollectionAccess.PRIVATE },
                        onRequestPrivateUnlock = {
                            val gate = biometricGate
                            if (gate == null || biometricStatus != BiometricGateStatus.AVAILABLE) {
                                privateRevealed = true
                                return@AssignToCollectionsSection
                            }
                            gate.requestUnlock(
                                title = privateUnlockTitle,
                                subtitle = privateUnlockSubtitle,
                                negativeButtonText = privateUnlockNegative,
                            ) { result ->
                                when (result) {
                                    BiometricGateResult.Granted -> {
                                        // Mark the whole Vault session as open — the user proved
                                        // ownership here once and any other private surface in this
                                        // process trusts the same flag.
                                        VaultSessionState.markVaultOpen()
                                        privateRevealed = true
                                    }
                                    is BiometricGateResult.Denied -> {
                                        // Stay closed — user can retry by tapping again.
                                        tracker.log(AnalyticsEvent.VaultUnlock(granted = false, source = "add_bomp"))
                                    }
                                }
                            }
                        },
                        onHidePrivate = { privateRevealed = false },
                    )
                }
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

    val scope = pendingNewCollectionScope
    if (scope != null) {
        InlineCollectionCreateSheet(
            scope = scope,
            onDismiss = { pendingNewCollectionScope = null },
            onCreated = { created ->
                // Auto-tag the in-progress audio with the freshly created collection so the user
                // doesn't have to find it in the chip row and tap it again — the "+ Nueva" intent
                // is "create AND tag", not just "create".
                if (created.isPublic) {
                    collectionsState.publicSelection.value =
                        collectionsState.publicSelection.value + created.id
                    collectionsState.publicCollections.value =
                        collectionsState.publicCollections.value + created
                } else {
                    collectionsState.privateSelection.value =
                        collectionsState.privateSelection.value + created.id
                    collectionsState.privateCollections.value =
                        collectionsState.privateCollections.value + created
                }
                pendingNewCollectionScope = null
            },
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

/**
 * Persists [SaveOutcome] across Activity recreate (e.g. rotation during the success overlay).
 * [SaveOutcome.Loading] deliberately collapses to [SaveOutcome.Idle] on save: the save coroutine is
 * launched in `rememberCoroutineScope`, which is cancelled with the composition — restoring
 * `Loading` would leave the spinner running with no completer. The `Loading` window is sub-second,
 * so the cost of the rare re-tap is bounded.
 */
private val SaveOutcomeSaver: Saver<SaveOutcome, Any> =
    Saver(
        save = { outcome ->
            when (outcome) {
                SaveOutcome.Idle, SaveOutcome.Loading -> IDLE_TAG
                is SaveOutcome.Success -> outcome.name
            }
        },
        restore = { value ->
            when (value) {
                IDLE_TAG -> SaveOutcome.Idle
                is String -> SaveOutcome.Success(value)
                else -> SaveOutcome.Idle
            }
        },
    )

private const val IDLE_TAG = "__idle__"

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

internal class CollectionsScreenState(
    val publicCollections: androidx.compose.runtime.MutableState<List<Collection>>,
    val privateCollections: androidx.compose.runtime.MutableState<List<Collection>>,
    val publicSelection: androidx.compose.runtime.MutableState<Set<String>>,
    val privateSelection: androidx.compose.runtime.MutableState<Set<String>>,
)

@Composable
private fun rememberCollectionsState(
    context: Context,
    mode: AddButtonMode,
): CollectionsScreenState {
    val publicCollections = remember { mutableStateOf<List<Collection>>(emptyList()) }
    val privateCollections = remember { mutableStateOf<List<Collection>>(emptyList()) }
    val publicSelection = rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }
    val privateSelection = rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }
    val editingAudioId = (mode as? AddButtonMode.Edit)?.sound?.id

    LaunchedEffect(editingAudioId) {
        withContext(Dispatchers.IO) {
            val repo = CollectionsRepository(context, onError = Tracker::track)
            val list = repo.collections.first()
            publicCollections.value = list.filter { it.isPublic }
            privateCollections.value = list.filter { it.isPrivate }
            if (editingAudioId != null) {
                publicSelection.value = list.filter { it.isPublic && editingAudioId in it.audioIds }.map { it.id }.toSet()
                privateSelection.value = list.filter { it.isPrivate && editingAudioId in it.audioIds }.map { it.id }.toSet()
            }
        }
    }

    return CollectionsScreenState(
        publicCollections = publicCollections,
        privateCollections = privateCollections,
        publicSelection = publicSelection,
        privateSelection = privateSelection,
    )
}

// rememberSaveable(stateSaver) takes a Saver over the VALUE type, not the MutableState wrapper —
// the harness builds the MutableState around the restored value itself. Typing this as
// `Saver<MutableState<Set<String>>, ...>` would nest a MutableState inside a MutableState at
// the call site and the kotlinc type checker rejects it as `MutableState<MutableState<Set<String>>>`.
private val StringSetSaver: Saver<Set<String>, Any> =
    Saver(
        save = { value -> ArrayList(value) },
        restore = { value ->
            @Suppress("UNCHECKED_CAST")
            (value as ArrayList<String>).toSet()
        },
    )

/**
 * Walks the Context wrapper chain to find a [androidx.fragment.app.FragmentActivity]. Compose's
 * `LocalContext.current` may return a ContextThemeWrapper around the activity; naive `as` casts
 * fail. Returns null only when the composable is previewed outside an activity (e.g. @Preview).
 */
internal fun Context.findFragmentActivity(): androidx.fragment.app.FragmentActivity? {
    var current: Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is androidx.fragment.app.FragmentActivity) return current
        current = current.baseContext
    }
    return null
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
