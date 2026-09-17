package com.aira.agent.ai

import kotlinx.serialization.Serializable

/**
 * Gemini's response. A [Plan] is a list of [Action]s plus a friendly
 * natural-language reply. Aira executes the actions and uses the reply
 * to confirm the plan to the user.
 */
@Serializable
data class Plan(
    val reply: String,
    val actions: List<Action> = emptyList(),
    val requiresConfirmation: Boolean = false
)

/**
 * Outcome of executing one [Action]. Used by the executor to drive
 * the UI, TTS, and the next action in a chain.
 */
sealed class ActionResult {
    data class Success(val message: String? = null) : ActionResult()
    data class NeedsConfirmation(val action: Action) : ActionResult()
    data class Failed(val reason: String, val recoverable: Boolean = true) : ActionResult()
}

/**
 * Snapshot of the entire task: original user request, parsed [Plan],
 * progress, and final result.
 */
@Serializable
data class Task(
    val id: String,
    val userMessage: String,
    val plan: Plan? = null,
    val status: TaskStatus = TaskStatus.Pending,
    val results: List<String> = emptyList(),
    val error: String? = null
)

@Serializable
enum class TaskStatus { Pending, Planning, Executing, Completed, Failed, Cancelled, NeedsConfirmation }