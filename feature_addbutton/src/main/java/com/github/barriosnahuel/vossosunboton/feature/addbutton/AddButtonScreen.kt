package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddButtonScreen(
    context: Context,
    uri: Uri,
    onSaved: (String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
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
            AddButtonFeature.instance.saveNewButtonAsync(context, name.trim(), uri.toString()).await()
            withContext(Dispatchers.Main) { onSaved(name.trim()) }
        }
    }

    Scaffold(
        topBar = { AddButtonTopBar(onNavigateUp) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(MAX_NAME_LENGTH)
                    nameError = null
                },
                label = { Text(stringResource(R.string.feature_addbutton_name)) },
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feature_addbutton_save))
            }
        }
    }
}

private const val MAX_NAME_LENGTH = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddButtonTopBar(onNavigateUp: () -> Unit) {
    // Light mode: primary (#5B21B6 deep violet) — dark bar, white title.
    // Dark mode:  primaryContainer (#4A0A96 deep violet) — dark bar, lavender title.
    val isDark = isSystemInDarkTheme()
    val barContainerColor =
        if (isDark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val barContentColor =
        if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
    TopAppBar(
        title = { Text(stringResource(R.string.feature_addbutton_activity_title)) },
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
                containerColor = barContainerColor,
                titleContentColor = barContentColor,
                navigationIconContentColor = barContentColor,
            ),
    )
}
