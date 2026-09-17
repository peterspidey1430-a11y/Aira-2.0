package com.aira.agent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aira.agent.ui.Routes
import com.aira.agent.ui.viewmodel.AiraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val vm: AiraViewModel = viewModel(factory = AiraViewModel.Factory)
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.refresh()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Aira", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(state.assistantStatus, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.toggleAssistant() }) {
                        Icon(if (state.assistantOn) Icons.Filled.Power else Icons.Filled.PowerOff, contentDescription = "Assistant on/off")
                    }
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.CHAT) },
                icon = { Icon(Icons.Filled.Chat, null) },
                text = { Text("Chat") }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero / mic card
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Hi, I'm Aira.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.moodHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(16.dp))
                    val pulseScale by animateFloatAsState(if (state.voiceListening) 1.0f else 1.0f, tween(700), label = "micScale")
                    Box(
                        Modifier
                            .size(96.dp)
                            .scale(pulseScale)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable(enabled = state.assistantOn) {
                                if (state.voiceListening) vm.stopVoice() else vm.startVoice()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (state.voiceListening) Icons.Filled.Mic else Icons.Filled.MicNone,
                            contentDescription = "Voice",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.voiceListening) "Listening… tap to stop" else if (!state.assistantOn) "Assistant is off" else "Tap to talk",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    AnimatedVisibility(visible = state.partialText.isNotEmpty()) {
                        Text(
                            state.partialText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Quick text chat
            OutlinedTextField(
                value = state.textInput,
                onValueChange = vm::updateTextInput,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask Aira anything…") },
                trailingIcon = {
                    IconButton(onClick = { vm.sendFromHome() }, enabled = state.assistantOn && state.textInput.isNotBlank()) {
                        Icon(Icons.Filled.Send, contentDescription = "Send")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                singleLine = false,
                maxLines = 4
            )

            // Live task status
            AnimatedVisibility(visible = state.liveStatus.isNotBlank()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(state.liveStatus, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Quick controls
            Text("Quick controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            QuickControlsRow(state, vm)

            // Permissions summary
            PermissionSummaryCard(onClick = { nav.navigate(Routes.PERMISSIONS) }, granted = state.permissionGrantedCount, total = state.permissionTotalCount)

            // Memory shortcut
            MemoryShortcutCard(onClick = { nav.navigate(Routes.MEMORY) }, count = state.memoryCount)

            // Background assistant shortcut
            BackgroundAssistantCard(
                enabled = state.bgAssistantOn,
                onToggle = { vm.toggleBackgroundAssistant() }
            )

            // API key shortcut
            ApiKeyShortcutCard(onClick = { nav.navigate(Routes.API_KEY) }, configured = state.apiKeyConfigured)

            // Recent tasks
            if (state.recentTasks.isNotEmpty()) {
                Text("Recent activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        state.recentTasks.take(5).forEach { entry ->
                            Text(
                                entry,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp)) // FAB clearance
        }
    }
}

@Composable
private fun QuickControlsRow(state: com.aira.agent.ui.viewmodel.AiraUiState, vm: AiraViewModel) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        item {
            QuickChip(icon = Icons.Filled.FlashlightOn, label = "Torch", onClick = { vm.toggleTorch(true) })
        }
        item {
            QuickChip(icon = Icons.Filled.FlashlightOff, label = "Torch off", onClick = { vm.toggleTorch(false) })
        }
        item {
            QuickChip(icon = Icons.Filled.VolumeUp, label = "Vol +", onClick = { vm.changeVolume(1) })
        }
        item {
            QuickChip(icon = Icons.Filled.VolumeDown, label = "Vol −", onClick = { vm.changeVolume(-1) })
        }
        item {
            QuickChip(icon = Icons.Filled.Brightness6, label = "Bright +", onClick = { vm.changeBrightness(40) })
        }
        item {
            QuickChip(icon = Icons.Filled.Brightness4, label = "Bright −", onClick = { vm.changeBrightness(-40) })
        }
        item {
            QuickChip(icon = Icons.Filled.AlarmAdd, label = "Set alarm", onClick = { vm.setAlarmNow() })
        }
        item {
            QuickChip(icon = Icons.Filled.PlayArrow, label = "Music", onClick = { vm.playMyMusic() })
        }
    }
}

@Composable
private fun QuickChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        leadingIcon = { Icon(icon, null) },
        label = { Text(label) }
    )
}

@Composable
private fun PermissionSummaryCard(onClick: () -> Unit, granted: Int, total: Int) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Security, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Permissions & access", style = MaterialTheme.typography.titleMedium)
                Text("$granted of $total granted", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null)
        }
    }
}

@Composable
private fun MemoryShortcutCard(onClick: () -> Unit, count: Int) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Psychology, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Memory", style = MaterialTheme.typography.titleMedium)
                Text("$count stored", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null)
        }
    }
}

@Composable
private fun BackgroundAssistantCard(enabled: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AccessTime, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Background assistant", style = MaterialTheme.typography.titleMedium)
                Text(if (enabled) "On — occasional gentle nudges" else "Off", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun ApiKeyShortcutCard(onClick: () -> Unit, configured: Boolean) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Key, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Gemini API key", style = MaterialTheme.typography.titleMedium)
                Text(if (configured) "Configured" else "Not set", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null)
        }
    }
}