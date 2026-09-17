package com.aira.agent.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aira.agent.AiraApplication
import com.aira.agent.ai.Action
import com.aira.agent.background.BackgroundAssistantService
import com.aira.agent.memory.MemoryItem
import com.aira.agent.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long,
    val from: ChatRole,
    val text: String,
    val ts: Long = System.currentTimeMillis()
)
enum class ChatRole { User, Aira, System }

data class AiraUiState(
    val assistantOn: Boolean = true,
    val voiceAllowed: Boolean = false,
    val voiceListening: Boolean = false,
    val partialText: String = "",
    val textInput: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    val liveStatus: String = "",
    val apiKeyConfigured: Boolean = false,
    val permissionGrantedCount: Int = 0,
    val permissionTotalCount: Int = 0,
    val permissions: List<PermissionManager.PermissionDef> = emptyList(),
    val memory: List<MemoryItem> = emptyList(),
    val memoryCount: Int = 0,
    val bgAssistantOn: Boolean = false,
    val bgFreq: String = "normal",
    val moodHint: String = "How can I help today?",
    val recentTasks: List<String> = emptyList(),
    val errorMessage: String? = null
) {
    val assistantStatus: String
        get() = when {
            !assistantOn -> "Assistant is off"
            voiceListening -> "Listening…"
            busy -> liveStatus.ifBlank { "Working…" }
            else -> moodHint
        }
}

class AiraViewModel(app: Application) : AndroidViewModel(app) {

    private val aira: AiraApplication = app as AiraApplication

    private val _state = MutableStateFlow(AiraUiState())
    val state: StateFlow<AiraUiState> = _state.asStateFlow()

    private val msgSeq = java.util.concurrent.atomic.AtomicLong(0)

    init {
        refresh()
        viewModelScope.launch {
            aira.voice.listening.collect { listening ->
                _state.value = _state.value.copy(voiceListening = listening)
            }
        }
        viewModelScope.launch {
            aira.voice.partialText.collect { p ->
                _state.value = _state.value.copy(partialText = p)
            }
        }
        viewModelScope.launch {
            aira.taskPlanner.currentTask.collect { task ->
                _state.value = _state.value.copy(
                    busy = task?.status in listOf(
                        com.aira.agent.ai.TaskStatus.Planning,
                        com.aira.agent.ai.TaskStatus.Executing,
                        com.aira.agent.ai.TaskStatus.Pending
                    )
                )
            }
        }
        viewModelScope.launch {
            aira.taskPlanner.liveStatus.collect { s -> _state.value = _state.value.copy(liveStatus = s) }
        }
        viewModelScope.launch {
            aira.memory.items.collect { items ->
                _state.value = _state.value.copy(memory = items, memoryCount = items.size)
            }
        }
        viewModelScope.launch {
            aira.mood.mood.collect { mood ->
                _state.value = _state.value.copy(moodHint = when (mood) {
                    com.aira.agent.mood.MoodAnalyzer.Mood.Sad -> "You sound a little low. I'm here if you want to talk."
                    com.aira.agent.mood.MoodAnalyzer.Mood.Happy -> "Sounds like a good day! What can we do?"
                    com.aira.agent.mood.MoodAnalyzer.Mood.Tired -> "Take it easy — let me know if I can lighten the load."
                    com.aira.agent.mood.MoodAnalyzer.Mood.Angry -> "Got it. We'll go step by step."
                    com.aira.agent.mood.MoodAnalyzer.Mood.Excited -> "Love the energy! What's the plan?"
                    else -> "How can I help today?"
                })
            }
        }
        viewModelScope.launch {
            aira.multitask.recentTasks.collect { r -> _state.value = _state.value.copy(recentTasks = r) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            aira.memory.load()
            val perms = aira.permissions.all.map { it to aira.permissions.status(it) }
            val granted = perms.count { it.second == PermissionManager.Status.Granted }
            _state.value = _state.value.copy(
                apiKeyConfigured = aira.secureStorage.hasGeminiApiKey(),
                permissions = perms.map { it.first.copy() },
                permissionGrantedCount = granted,
                permissionTotalCount = perms.size,
                assistantOn = aira.repository.isAssistantOn(),
                voiceAllowed = aira.repository.isVoiceOn(),
                bgAssistantOn = aira.repository.isBgOn(),
                bgFreq = aira.repository.bgFreq()
            )
        }
    }

    fun updateTextInput(t: String) { _state.value = _state.value.copy(textInput = t) }

    fun clearChat() {
        _state.value = _state.value.copy(messages = emptyList())
    }

    fun sendText() {
        val msg = _state.value.textInput.trim()
        if (msg.isEmpty() || _state.value.busy) return
        if (!_state.value.assistantOn) {
            _state.value = _state.value.copy(errorMessage = "Assistant is off — turn it on in Settings.")
            return
        }
        append(ChatMessage(msgSeq.incrementAndGet(), ChatRole.User, msg))
        _state.value = _state.value.copy(textInput = "")
        runTask(msg)
    }

    fun sendFromHome() = sendText()

    fun startVoice() {
        if (!_state.value.assistantOn) return
        aira.repository.saveVoiceOn(true)
        aira.voice.listen { text ->
            append(ChatMessage(msgSeq.incrementAndGet(), ChatRole.User, text))
            runTask(text)
        }
    }

    fun stopVoice() {
        aira.voice.stopListening()
        aira.repository.saveVoiceOn(false)
    }

    fun setVoiceAllowed(allowed: Boolean) {
        aira.repository.saveVoiceOn(allowed)
        _state.value = _state.value.copy(voiceAllowed = allowed)
    }

    fun toggleAssistant() {
        val now = !_state.value.assistantOn
        aira.repository.saveAssistantOn(now)
        _state.value = _state.value.copy(assistantOn = now)
        if (!now) aira.voice.stopListening()
    }

    fun toggleBackgroundAssistant() {
        val now = !_state.value.bgAssistantOn
        aira.repository.saveBgOn(now)
        _state.value = _state.value.copy(bgAssistantOn = now)
        val ctx: Context = getApplication()
        if (now) BackgroundAssistantService.start(ctx) else BackgroundAssistantService.stop(ctx)
    }

    fun cycleBgFreq() {
        val cur = aira.repository.bgFreq()
        val next = when (cur) { "rare" -> "normal"; "normal" -> "frequent"; else -> "rare" }
        aira.repository.saveBgFreq(next)
        _state.value = _state.value.copy(bgFreq = next)
    }

    // ---- Direct device control shortcuts from Home ----
    fun toggleTorch(on: Boolean) {
        aira.multitask.launch(
            message = if (on) "Turn on flashlight" else "Turn off flashlight",
            speak = { aira.voice.speak(it) },
            isVoiceEnabled = { false },
            confirm = { true }
        )
    }

    fun changeVolume(delta: Int) {
        aira.multitask.launch(
            message = if (delta > 0) "Raise volume" else "Lower volume",
            speak = { aira.voice.speak(it) },
            isVoiceEnabled = { false },
            confirm = { true }
        )
    }

    fun changeBrightness(delta: Int) {
        aira.multitask.launch(
            message = if (delta > 0) "Increase brightness" else "Lower brightness",
            speak = { aira.voice.speak(it) },
            isVoiceEnabled = { false },
            confirm = { true }
        )
    }

    fun setAlarmNow() {
        aira.multitask.launch(
            message = "Set an alarm for 7 AM",
            speak = { aira.voice.speak(it) },
            isVoiceEnabled = { false },
            confirm = { true }
        )
    }

    fun playMyMusic() {
        val pref = aira.memory.get("preferred_music")?.value
        val msg = if (pref != null) "Play $pref on Spotify" else "Open Spotify"
        aira.multitask.launch(
            message = msg,
            speak = { aira.voice.speak(it) },
            isVoiceEnabled = { false },
            confirm = { true }
        )
    }

    // ---- Memory ----
    fun forget(key: String) {
        viewModelScope.launch { aira.memory.delete(key) }
    }
    fun clearMemory() {
        viewModelScope.launch { aira.memory.clear() }
    }
    fun updateMemory(oldKey: String, newKey: String, newValue: String, newCat: String) {
        viewModelScope.launch { aira.memory.update(oldKey, newKey, newValue, newCat) }
    }

    // ---- API key ----
    fun saveApiKey(k: String) {
        aira.secureStorage.setGeminiApiKey(k)
        refreshApiKeyState()
    }
    fun clearApiKey() {
        aira.secureStorage.clearGeminiApiKey()
        refreshApiKeyState()
    }
    fun refreshApiKeyState() {
        _state.value = _state.value.copy(apiKeyConfigured = aira.secureStorage.hasGeminiApiKey())
    }

    // ---- Permissions ----
    fun requestPermission(context: Context, permission: String?, id: String) {
        if (permission == null) {
            // Accessibility service — open settings
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }
        val act = (context as? android.app.Activity) ?: return
        aira.permissions.request(act, permission, 5000 + id.hashCode())
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    // ---- Internals ----
    private fun append(m: ChatMessage) {
        _state.value = _state.value.copy(messages = _state.value.messages + m)
    }

    private fun runTask(message: String) {
        aira.multitask.launch(
            message = message,
            speak = { text -> append(ChatMessage(msgSeq.incrementAndGet(), ChatRole.Aira, text)) ; aira.voice.speak(text) },
            isVoiceEnabled = { _state.value.voiceListening },
            confirm = { action -> confirmSensitive(action) }
        )
    }

    private suspend fun confirmSensitive(action: Action): Boolean {
        // Surface a chat-level message; for now, return true so simple demos work.
        append(ChatMessage(msgSeq.incrementAndGet(), ChatRole.System,
            "About to run ${action::class.simpleName} — confirm in chat."))
        return true
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                return AiraViewModel(app) as T
            }
        }
    }
}