package com.aira.agent.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aira.agent.ui.viewmodel.AiraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(nav: NavController) {
    val vm: AiraViewModel = viewModel(factory = AiraViewModel.Factory)
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions & access", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Why Aira asks for these", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Every permission is opt-in. Aira will explain what each one unlocks before asking. She never silently grants access, and you can revoke anything at any time.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.permissions) { perm ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.requestPermission(context, perm.androidPermission, perm.id) }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val color = when (perm.status) {
                                com.aira.agent.permissions.PermissionManager.Status.Granted -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            }
                            Icon(
                                when (perm.status) {
                                    com.aira.agent.permissions.PermissionManager.Status.Granted -> Icons.Filled.CheckCircle
                                    else -> Icons.Filled.ErrorOutline
                                },
                                contentDescription = null,
                                tint = color
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(perm.titleRes), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(perm.whyRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (perm.id == "AccessibilityService") {
                                IconButton(onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }) { Icon(Icons.Filled.OpenInNew, "Open settings") }
                            } else if (perm.androidPermission != null) {
                                IconButton(onClick = { vm.requestPermission(context, perm.androidPermission, perm.id) }) {
                                    Icon(Icons.Filled.LockOpen, "Grant")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}