package com.aira.agent.learning

import android.content.Context
import com.aira.agent.ai.Task
import com.aira.agent.memory.MemoryManager
import com.aira.agent.mood.MoodAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Light-touch preference learning. Aira keeps a count of how often
 * the user invokes certain apps / music / commands, and after a
 * threshold (3+) writes a memory entry like "preferred_music=lofi".
 * Every learned entry is user-editable via the Memory screen.
 *
 * The learner never modifies core system behaviour, never stores
 * secrets, and is fully transparent.
 */
class PreferenceLearner(
    private val context: Context,
    private val memory: MemoryManager
) {

    private val _learned = MutableStateFlow<Map<String, Int>>(emptyMap())
    val learned: StateFlow<Map<String, Int>> = _learned.asStateFlow()

    private val counters = mutableMapOf<String, Int>()

    /** Inspect a completed task and update internal counters. */
    fun observeTask(task: Task) {
        val text = task.userMessage.lowercase()

        // Detect music preference
        listOf("lofi", "lo-fi", "punjabi", "bollywood", "rock", "pop", "jazz", "classical", "hip hop", "indie", "k-pop")
            .forEach { genre ->
                if (text.contains(genre)) bump("music_$genre")
            }

        // Detect app preference
        listOf("whatsapp", "youtube", "spotify", "instagram", "chrome", "gmail", "maps", "calendar", "camera")
            .forEach { app ->
                if (text.contains(app)) bump("app_$app")
            }

        // Language preference
        if (text.any { it in 'ऀ'..'ॿ' }) bump("lang_hindi")
        else if (text.matches(Regex(".*[a-z].*"))) bump("lang_english")

        // Convert counts to memories if threshold reached
        val snapshot = counters.toMap()
        snapshot.filterValues { it == 3 }.forEach { (k, _) ->
            val (cat, value) = parseKey(k)
            memory.put("preferred_$cat", value, "Preference")
        }
        _learned.value = snapshot
    }

    private fun bump(key: String) {
        counters[key] = (counters[key] ?: 0) + 1
    }

    private fun parseKey(k: String): Pair<String, String> {
        val parts = k.split("_", limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else "general" to k
    }

    fun reset() {
        counters.clear()
        _learned.value = emptyMap()
    }
}