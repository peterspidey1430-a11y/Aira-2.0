package com.aira.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aira.agent.memory.MemoryItem
import com.aira.agent.ui.viewmodel.AiraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(nav: NavController) {
    val vm: AiraViewModel = viewModel(factory = AiraViewModel.Factory)
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<MemoryItem?>(null) }
    var confirmingClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.memory.isNotEmpty()) {
                        IconButton(onClick = { confirmingClear = true }) {
                            Icon(Icons.Filled.DeleteForever, "Clear all")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.memory.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Psychology, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("No memories yet", style = MaterialTheme.typography.titleMedium)
                        Text("Tell Aira things like \"remember I prefer lo-fi music\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.memory, key = { it.key }) { item ->
                        MemoryRow(
                            item = item,
                            onEdit = { editing = item },
                            onDelete = { vm.forget(item.key) }
                        )
                    }
                }
            }
        }
    }

    if (editing != null) {
        EditMemoryDialog(
            item = editing!!,
            onSave = { newKey, newValue, newCat ->
                vm.updateMemory(editing!!.key, newKey, newValue, newCat)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            confirmButton = {
                TextButton(onClick = { vm.clearMemory(); confirmingClear = false }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text("Cancel") } },
            title = { Text("Clear all memories?") },
            text = { Text("Aira will forget everything she knows about you.") }
        )
    }
}

@Composable
private fun MemoryRow(item: MemoryItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.key, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(item.value, style = MaterialTheme.typography.bodyMedium)
                if (item.category != "general") {
                    Text("Category: ${item.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
        }
    }
}

@Composable
private fun EditMemoryDialog(
    item: MemoryItem,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf(item.key) }
    var value by remember { mutableStateOf(item.value) }
    var category by remember { mutableStateOf(item.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key, value, category) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}