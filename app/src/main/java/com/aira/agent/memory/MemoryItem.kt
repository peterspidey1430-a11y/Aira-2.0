package com.aira.agent.memory

import kotlinx.serialization.Serializable

@Serializable
data class MemoryItem(
    val key: String,
    val value: String,
    val category: String = "general",
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class MemoryCategory(val label: String) {
    General("General"),
    Preference("Preference"),
    Music("Music"),
    Language("Language"),
    App("Frequent app"),
    Command("Command habit"),
    Person("Person")
}