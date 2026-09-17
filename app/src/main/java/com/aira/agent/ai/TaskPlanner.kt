package com.aira.agent.ai

import com.aira.agent.errors.AiraError
import com.aira.agent.errors.ErrorHandler
import com.aira.agent.learning.PreferenceLearner
import com.aira.agent.memory.MemoryManager
import com.aira.agent.mood.MoodAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * The planner owns the lifecycle of a [Task]:
 *  1. Take the user's raw message.
 *  2. Ask Gemini to decompose it into a [Plan].
 *  3. Validate each [Action] through [ActionValidator].
 *  4. Hand off to [ActionExecutor] and observe results.
 *  5. Update memory & learner based on what happened.
 *
 * UI listens to [currentTask] for live progress.
 */
class TaskPlanner(
    private val gemini: GeminiEngine,
    private val executor: ActionExecutor,
    private val memory: MemoryManager,
    private val mood: MoodAnalyzer,
    private val learner: PreferenceLearner,
    private val errors: ErrorHandler
) {

    private val _currentTask = MutableStateFlow<Task?>(null)
    val currentTask: StateFlow<Task?> = _currentTask.asStateFlow()

    private val _liveStatus = MutableStateFlow<String>("")
    val liveStatus: StateFlow<String> = _liveStatus.asStateFlow()

    suspend fun handle(
        userMessage: String,
        speak: (String) -> Unit,
        isVoiceEnabled: () -> Boolean,
        confirmAction: suspend (Action) -> Boolean
    ): Task {
        val id = UUID.randomUUID().toString()
        var task = Task(id = id, userMessage = userMessage, status = TaskStatus.Pending)
        _currentTask.value = task
        _liveStatus.value = "Thinking..."

        val ctx = GeminiContext(
            userMessage = userMessage,
            mood = mood.current().label,
            memories = memory.all(),
            recentTasks = memory.lastActions(5),
            assistantEnabled = true,
            voiceEnabled = isVoiceEnabled()
        )

        val plan = try {
            gemini.chat(userMessage, ctx)
        } catch (t: Throwable) {
            task = task.copy(status = TaskStatus.Failed, error = t.message)
            _currentTask.value = task
            _liveStatus.value = ""
            val msg = errors.explain(t).detail
            if (isVoiceEnabled()) speak(msg)
            return task
        }

        task = task.copy(plan = plan, status = TaskStatus.Planning)
        _currentTask.value = task

        // Validate every action before running.
        val validated = mutableListOf<Action>()
        val failures = mutableListOf<String>()
        for (a in plan.actions) {
            try {
                ActionValidator.validate(a)
                validated += a
            } catch (v: AiraError.Validation) {
                failures += v.message
            }
        }
        if (failures.isNotEmpty()) {
            _liveStatus.value = "Some actions blocked: ${failures.joinToString("; ")}"
        }

        // Speak the conversational reply first.
        if (plan.reply.isNotBlank() && isVoiceEnabled()) {
            speak(plan.reply)
        }

        // Confirm sensitive actions before running them.
        val final = mutableListOf<Action>()
        for (a in validated) {
            if (ActionValidator.requiresConfirmation(a) || plan.requiresConfirmation) {
                val ok = confirmAction(a)
                if (!ok) continue
            }
            final += a
        }

        task = task.copy(status = TaskStatus.Executing)
        _currentTask.value = task

        val results = mutableListOf<String>()
        var allOk = true
        for ((idx, action) in final.withIndex()) {
            _liveStatus.value = "[${idx + 1}/${final.size}] ${describe(action)}"
            val r = executor.execute(action)
            results += when (r) {
                is ActionResult.Success -> r.message ?: "Done"
                is ActionResult.Failed -> {
                    allOk = false
                    "Failed: ${r.reason}"
                }
                is ActionResult.NeedsConfirmation -> {
                    allOk = false
                    "Needs confirmation"
                }
            }
        }

        task = task.copy(
            status = if (allOk) TaskStatus.Completed else TaskStatus.Failed,
            results = results,
            error = if (allOk) null else results.lastOrNull { it.startsWith("Failed") }
        )
        _currentTask.value = task
        _liveStatus.value = if (allOk) "Done" else "Some steps failed"

        learner.observeTask(task)
        memory.recordAction(userMessage, plan.reply, allOk)

        return task
    }

    fun cancel() {
        _currentTask.value = _currentTask.value?.copy(status = TaskStatus.Cancelled)
        _liveStatus.value = ""
    }

    private fun describe(a: Action): String = when (a) {
        is Action.OpenApp -> "Opening ${a.query ?: a.packageName}"
        is Action.SearchWeb -> "Searching ${a.engine} for ${a.query}"
        is Action.OpenUrl -> "Opening ${a.url}"
        is Action.MakeCall -> "Calling ${a.contact}"
        is Action.SendMessage -> "Sending ${a.app} message to ${a.contact}"
        is Action.SendSms -> "Sending SMS to ${a.contact}"
        is Action.TapElement -> "Tapping ${a.text ?: a.resourceId ?: a.contentDesc}"
        is Action.TypeText -> "Typing"
        is Action.Scroll -> "Scrolling ${a.direction}"
        is Action.Swipe -> "Swiping ${a.direction}"
        Action.PressBack -> "Going back"
        Action.PressHome -> "Going home"
        Action.GoHome -> "Going home"
        is Action.ReadScreen -> "Reading screen"
        is Action.Wait -> "Waiting"
        is Action.Flashlight -> if (a.enable) "Torch on" else "Torch off"
        is Action.ChangeVolume -> "Changing ${a.stream} volume"
        is Action.SetVolume -> "Setting ${a.stream} volume"
        is Action.ChangeBrightness -> "Adjusting brightness"
        is Action.ToggleBluetooth -> if (a.enable) "Bluetooth on" else "Bluetooth off"
        is Action.ToggleWifi -> if (a.enable) "Wi-Fi on" else "Wi-Fi off"
        is Action.SetAlarm -> "Setting alarm ${a.hour}:${"%02d".format(a.minute)}"
        is Action.PlayYouTube -> "Playing YouTube: ${a.query}"
        is Action.PlaySpotify -> "Playing Spotify${a.query?.let { ": $it" } ?: ""}"
        Action.MediaPlayPause -> "Toggling media playback"
        Action.MediaNext -> "Next track"
        Action.MediaPrev -> "Previous track"
        Action.OpenFiles -> "Opening Files"
        is Action.FindFile -> "Searching for ${a.name}"
        is Action.Remember -> "Remembering"
        is Action.Forget -> "Forgetting"
        is Action.Reply -> "Replying"
        is Action.AskClarification -> "Asking clarification"
    }
}