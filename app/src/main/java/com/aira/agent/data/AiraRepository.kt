package com.aira.agent.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aira.agent.memory.MemoryItem
import com.aira.agent.memory.MemoryManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * DataStore-backed persistence layer. Holds:
 *  - the memory list,
 *  - the recent-action history,
 *  - user-level preferences (assistant on/off, voice on/off, etc.).
 *
 * Sensitive data (API key) lives in [com.aira.agent.security.SecureStorage].
 */
class AiraRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val Context.dataStore by preferencesDataStore("aira_prefs")

    private val KEY_MEMORY = stringPreferencesKey("memory")
    private val KEY_HISTORY = stringPreferencesKey("history")
    private val KEY_ASSISTANT_ON = stringPreferencesKey("assistant_on")
    private val KEY_VOICE_ON = stringPreferencesKey("voice_on")
    private val KEY_BG_ON = stringPreferencesKey("bg_on")
    private val KEY_BG_FREQ = stringPreferencesKey("bg_freq")
    private val KEY_PROACTIVE = stringPreferencesKey("proactive")
    private val KEY_LANG = stringPreferencesKey("preferred_lang")

    fun saveAssistantOn(on: Boolean) = runBlocking { set(KEY_ASSISTANT_ON, on.toString()) }
    fun isAssistantOn(): Boolean = (readString(KEY_ASSISTANT_ON) ?: "true").toBoolean()

    fun saveVoiceOn(on: Boolean) = runBlocking { set(KEY_VOICE_ON, on.toString()) }
    fun isVoiceOn(): Boolean = (readString(KEY_VOICE_ON) ?: "false").toBoolean()

    fun saveBgOn(on: Boolean) = runBlocking { set(KEY_BG_ON, on.toString()) }
    fun isBgOn(): Boolean = (readString(KEY_BG_ON) ?: "false").toBoolean()

    fun saveBgFreq(freq: String) = runBlocking { set(KEY_BG_FREQ, freq) }
    fun bgFreq(): String = readString(KEY_BG_FREQ) ?: "normal"

    fun saveProactive(on: Boolean) = runBlocking { set(KEY_PROACTIVE, on.toString()) }
    fun isProactive(): Boolean = (readString(KEY_PROACTIVE) ?: "true").toBoolean()

    fun savePreferredLang(lang: String) = runBlocking { set(KEY_LANG, lang) }
    fun preferredLang(): String = readString(KEY_LANG) ?: "en-IN"

    fun saveMemory(items: List<MemoryItem>) = runBlocking {
        val str = json.encodeToString(ListSerializer(MemoryItem.serializer()), items)
        context.dataStore.edit { it[KEY_MEMORY] = str }
    }

    fun loadMemory(): List<MemoryItem> {
        val raw = readString(KEY_MEMORY) ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(MemoryItem.serializer()), raw)
        } catch (_: Throwable) { emptyList() }
    }

    fun appendHistory(item: MemoryManager.Breadcrumb) = runBlocking {
        val current = loadHistory().toMutableList()
        current += item
        if (current.size > 200) current.removeAt(0)
        val str = json.encodeToString(ListSerializer(MemoryManager.Breadcrumb.serializer()), current)
        context.dataStore.edit { it[KEY_HISTORY] = str }
    }

    fun loadHistory(): List<MemoryManager.Breadcrumb> {
        val raw = readString(KEY_HISTORY) ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(MemoryManager.Breadcrumb.serializer()), raw)
        } catch (_: Throwable) { emptyList() }
    }

    private suspend fun set(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    private fun readString(key: Preferences.Key<String>): String? = runBlocking {
        context.dataStore.data.first()[key]
    }
}