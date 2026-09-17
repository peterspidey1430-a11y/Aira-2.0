package com.aira.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aira.agent.BuildConfig
import com.aira.agent.R
import com.aira.agent.ui.viewmodel.AiraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyScreen(nav: NavController) {
    val vm: AiraViewModel = viewModel(factory = AiraViewModel.Factory)
    val state by vm.state.collectAsState()
    var keyInput by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var confirmingClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API key", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Gemini API key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Get one at ai.google.dev. Aira stores it locally on your device, encrypted — never sent anywhere else.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Model: ${BuildConfig.GEMINI_MODEL_PRIMARY}", style = MaterialTheme.typography.labelMedium)
                    Text("Alias: ${BuildConfig.GEMINI_MODEL_ALIAS}", style = MaterialTheme.typography.labelMedium)
                }
            }

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it.trim() },
                label = { Text("API key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                    }
                },
                supportingText = {
                    Text(if (state.apiKeyConfigured) "A key is saved. Type a new one to replace it." else "Not set yet.")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.saveApiKey(keyInput); keyInput = "" },
                    enabled = keyInput.length >= 20
                ) { Text("Save") }
                OutlinedButton(
                    onClick = { confirmingClear = true },
                    enabled = state.apiKeyConfigured
                ) { Text("Remove") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { vm.refreshApiKeyState() }) { Text("Refresh") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "If the key is invalid, Aira won't crash — she'll let you know and stay responsive for everything that doesn't need the model.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            confirmButton = { TextButton(onClick = { vm.clearApiKey(); confirmingClear = false }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text("Cancel") } },
            title = { Text("Remove API key?") },
            text = { Text("Aira will stop being able to plan actions until you add a new key.") }
        )
    }
}