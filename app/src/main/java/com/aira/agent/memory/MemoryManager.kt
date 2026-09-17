package com.aira.agent.memory

import android.content.Context
import com.aira.agent.data.AiraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Long-term persistent memory for Aira. Stored in DataStore as JSON
 * so we don't need Room. Each item is a (key, value, category) tuple;
 * the UI can list, edit, and delete individual items or clear all.
 *
 * Memory NEVER stores secrets (passwords, OTPs, API keys, etc.) —
 * [ActionValidator] blocks [com.aira.agent.ai.Action.Remember] when
 * the value looks sensitive, and [put] also re-checks.
 */
class MemoryManager(
    private val context: Context,
    private val repository: AiraRepository
) {
    private val _items = MutableStateFlow<List<MemoryItem>>(emptyList())
    val items: StateFlow<List<MemoryItem>> = _items.asStateFlow()

    suspend fun load() {
        _items.value = repository.loadMemory()
    }

    fun all(): List<MemoryItem> = _items.value

    fun get(key: String): MemoryItem? = _items.value.firstOrNull { it.key.equals(key, ignoreCase = true) }

    suspend fun put(key: String, value: String, category: String = "general"): Boolean {
        if (key.isBlank() || value.isBlank()) return false
        if (looksSensitive(value)) return false
        val updated = _items.value.toMutableList()
        val idx = updated.indexOfFirst { it.key.equals(key, ignoreCase = true) }
        val item = MemoryItem(key = key.trim(), value = value.trim(), category = category)
        if (idx >= 0) updated[idx] = item else updated += item
        _items.value = updated
        repository.saveMemory(updated)
        return true
    }

    suspend fun delete(key: String): Boolean {
        val cur = _items.value
        val filtered = cur.filterNot { it.key.equals(key, ignoreCase = true) }
        if (filtered.size == cur.size) return false
        _items.value = filtered
        repository.saveMemory(filtered)
        return true
    }

    suspend fun update(key: String, newKey: String, newValue: String, newCategory: String): Boolean {
        val cur = _items.value.toMutableList()
        val idx = cur.indexOfFirst { it.key.equals(key, ignoreCase = true) }
        if (idx < 0) return false
        cur[idx] = MemoryItem(newKey.trim(), newValue.trim(), newCategory)
        _items.value = cur
        repository.saveMemory(cur)
        return true
    }

    suspend fun clear() {
        _items.value = emptyList()
        repository.saveMemory(emptyList())
    }

    suspend fun recordAction(userMessage: String, reply: String, ok: Boolean) {
        // Just a small breadcrumb for future analysis; not exposed in UI.
        repository.appendHistory(Breadcrumb(
            ts = System.currentTimeMillis(),
            userMessage = userMessage.take(200),
            reply = reply.take(200),
            ok = ok
        ))
    }

    fun lastActions(n: Int): List<String> =
        repository.loadHistory().takeLast(n).map { it.userMessage }

    private fun looksSensitive(v: String): Boolean {
        val s = v.lowercase()
        return listOf("password", "pin", "otp", "cvv", "credit card", "ssn", "aadhaar",
            "api key", "secret", "token", "passcode").any { s.contains(it) }
    }

    @Serializable
    data class Breadcrumb(
        val ts: Long,
        val userMessage: String,
        val reply: String,
        val ok: Boolean
    )
}