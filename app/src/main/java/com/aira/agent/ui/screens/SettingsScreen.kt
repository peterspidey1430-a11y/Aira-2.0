package com.aira.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aira.agent.BuildConfig
import com.aira.agent.ui.Routes
import com.aira.agent.ui.viewmodel.AiraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val vm: AiraViewModel = viewModel(factory = AiraViewModel.Factory)
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsRow(
                title = "Gemini API key",
                subtitle = if (state.apiKeyConfigured) "Configured" else "Not set",
                icon = Icons.Filled.Key,
                onClick = { nav.navigate(Routes.API_KEY) }
            )
            SettingsRow(
                title = "Permissions & access",
                subtitle = "${state.permissionGrantedCount}/${state.permissionTotalCount} granted",
                icon = Icons.Filled.Security,
                onClick = { nav.navigate(Routes.PERMISSIONS) }
            )
            SettingsRow(
                title = "Memory",
                subtitle = "${state.memoryCount} stored",
                icon = Icons.Filled.Psychology,
                onClick = { nav.navigate(Routes.MEMORY) }
            )

            Divider()

            Text("Assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SwitchRow(
                title = "Assistant on",
                subtitle = "Turn the agent on/off",
                checked = state.assistantOn,
                onCheckedChange = { vm.toggleAssistant() }
            )
            SwitchRow(
                title = "Voice assistant",
                subtitle = "Listen for voice commands",
                checked = state.voiceListening || state.voiceAllowed,
                onCheckedChange = { vm.setVoiceAllowed(it) }
            )
            SwitchRow(
                title = "Background assistant",
                subtitle = "Occasional gentle nudges when this app isn't open",
                checked = state.bgAssistantOn,
                onCheckedChange = { vm.toggleBackgroundAssistant() }
            )

            val (freqLabel, freq) = when (state.bgFreq) {
                "rare" -> "Rare" to "rare"
                "frequent" -> "Frequent" to "frequent"
                else -> "Normal" to "normal"
            }
            ListItem(
                headlineContent = { Text("Proactive frequency") },
                supportingContent = { Text("$freqLabel — choose how often Aira checks in") },
                leadingContent = { Icon(Icons.Filled.Schedule, null) },
                trailingContent = {
                    TextButton(onClick = { vm.cycleBgFreq() }) { Text(freqLabel) }
                }
            )

            Divider()

            Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Aira v1.0.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Model: ${BuildConfig.GEMINI_MODEL_PRIMARY}", style = MaterialTheme.typography.bodyMedium)
                    Text("API: ${BuildConfig.GEMINI_API_BASE}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Aira is a friendly personal AI. She isn't a real person, and she never pretends her actions succeeded when they didn't.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}