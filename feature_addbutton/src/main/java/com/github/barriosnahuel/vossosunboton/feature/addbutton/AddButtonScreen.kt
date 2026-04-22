package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.home.formatDuration
import com.github.barriosnahuel.vossosunboton.ui.home.formatRelativeDate
import kotlinx.coroutines.Dispatchers
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
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    fun save() {
        if (name.isBlank()) {
            nameError = context.getString(R.string.feature_addbutton_name_is_required_error)
            return
        }
        keyboardController?.hide()
        coroutineScope.launch {
            when (val m = mode) {
                is AddButtonMode.Create ->
                    AddButtonFeature.instance.saveNewButtonAsync(context, name.trim(), m.uri.toString()).await()
                is AddButtonMode.Edit ->
                    AddButtonFeature.instance.renameButtonAsync(context, m.sound, name.trim()).await()
            }
            withContext(Dispatchers.Main) { onSaved(name.trim()) }
        }
    }

    Scaffold(
        topBar = { AddButtonTopBar(mode = mode, onNavigateUp = onNavigateUp) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                                R.string.feature_addbutton_edit_name_label
                            } else {
                                R.string.feature_addbutton_name
                            },
                        ),
                    )
                },
                placeholder = { Text(stringResource(R.string.feature_addbutton_placeholder)) },
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
                            R.string.feature_addbutton_save_changes
                        } else {
                            R.string.feature_addbutton_save
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
    fileName: String,
    soundName: String,
    dateAdded: Long?,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    val player = remember { MediaPlayer() }
    var isReady by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0) }

    DisposableEffect(fileName) {
        try {
            val file = getFile(context, fileName)
            player.setDataSource(file.absolutePath)
            player.prepare()
            durationMs = player.duration
            player.setOnCompletionListener {
                isPlaying = false
                sliderPosition = 0f
            }
            isReady = true
        } catch (e: IOException) {
            Timber.w(e, "Could not load audio preview for file: %s", fileName)
        } catch (e: IllegalStateException) {
            Timber.w(e, "MediaPlayer in invalid state for file: %s", fileName)
        }
        onDispose {
            player.release()
        }
    }

    if (isReady) {
        Card(
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
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                player.pause()
                                isPlaying = false
                            } else {
                                player.start()
                                isPlaying = true
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.feature_addbutton_preview_audio),
                        )
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
                        R.string.feature_addbutton_activity_title_edit
                    } else {
                        R.string.feature_addbutton_activity_title
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
