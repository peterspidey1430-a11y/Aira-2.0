package com.aira.agent.multitasking

import com.aira.agent.ai.Action
import com.aira.agent.ai.ActionExecutor
import com.aira.agent.ai.ActionResult
import com.aira.agent.ai.Plan
import com.aira.agent.ai.TaskPlanner
import com.aira.agent.errors.ErrorHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lightweight multitasking wrapper. Exposes:
 *  - a queue of recent tasks,
 *  - the ability to launch a task in the background (so the UI stays
 *    responsive for compound commands),
 *  - cancellation.
 */
class TaskExecutionManager(private val planner: TaskPlanner) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _recentTasks = MutableStateFlow<List<String>>(emptyList())
    val recentTasks: StateFlow<List<String>> = _recentTasks.asStateFlow()

    private var running: Job? = null

    fun launch(
        message: String,
        speak: (String) -> Unit,
        isVoiceEnabled: () -> Boolean,
        confirm: suspend (Action) -> Boolean
    ) {
        running?.cancel()
        running = scope.launch {
            try {
                val task = planner.handle(message, speak, isVoiceEnabled, confirm)
                _recentTasks.value = (_recentTasks.value + "${task.status.name}: ${task.userMessage.take(80)}")
                    .takeLast(10)
            } catch (e: Throwable) {
                // Plumbed through planner already; never propagate upward.
            }
        }
    }

    fun cancel() {
        running?.cancel()
        planner.cancel()
    }

    fun shutdown() {
        scope.cancel()
    }
}