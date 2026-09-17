package com.aira.agent.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.aira.agent.ai.Action
import com.aira.agent.ai.TaskPlanner
import com.aira.agent.errors.ErrorHandler
import com.aira.agent.learning.PreferenceLearner
import com.aira.agent.memory.MemoryManager
import com.aira.agent.mood.MoodAnalyzer
import com.aira.agent.multitasking.TaskExecutionManager
import com.aira.agent.personality.AiraPersonality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Natural voice interaction. We rely on Android's built-in
 * `SpeechRecognizer` for STT (which already supports English / Hindi
 * depending on the device's installed engines) and `TextToSpeech` for
 * the spoken reply.
 *
 * The flow:
 *   user taps mic → STT listens → partial text streams into
 *   [partialText] → on final text, [onHeard] is invoked → Aira's
 *   reply is piped into [speak] → TTS speaks it.
 */
class VoiceAssistant(
    private val context: Context,
    private val executor: com.aira.agent.ai.ActionExecutor,
    private val memory: MemoryManager,
    private val mood: MoodAnalyzer,
    private val personality: AiraPersonality,
    private val taskPlanner: TaskPlanner,
    private val multitask: TaskExecutionManager,
    private val errors: ErrorHandler
) {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var pendingUtterances = mutableSetOf<String>()
    private var onHeard: ((String) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "IN")
                tts?.setPitch(1.05f) // a touch warmer for the female tone
                tts?.setSpeechRate(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _speaking.value = true
                    }
                    override fun onDone(utteranceId: String?) {
                        pendingUtterances.remove(utteranceId)
                        if (pendingUtterances.isEmpty()) _speaking.value = false
                    }
                    @Deprecated("Required override")
                    override fun onError(utteranceId: String?) {
                        pendingUtterances.remove(utteranceId)
                        if (pendingUtterances.isEmpty()) _speaking.value = false
                    }
                })
            } else {
                _lastError.value = "Text-to-speech not available on this device."
            }
        }
    }

    /**
     * Start listening. The user's final transcribed text is forwarded
     * to [handler].
     */
    fun listen(handler: (String) -> Unit) {
        onHeard = handler
        if (SpeechRecognizer.isRecognitionAvailable(context).not()) {
            _lastError.value = "Speech recognition not available on this device."
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(buildListener())
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, personality.languages.firstOrNull() ?: "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening…")
        }
        try {
            recognizer?.startListening(intent)
            _listening.value = true
            _lastError.value = null
        } catch (e: Throwable) {
            _lastError.value = "Couldn't start listening: ${e.message}"
            _listening.value = false
        }
    }

    fun stopListening() {
        try { recognizer?.stopListening() } catch (_: Throwable) {}
        _listening.value = false
    }

    /**
     * Speak [text] via TTS. Returns immediately; UI watches
     * [speaking] for the wave animation.
     */
    fun speak(text: String) {
        val t = tts ?: return
        if (text.isBlank()) return
        val id = UUID.randomUUID().toString()
        pendingUtterances += id
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stopSpeaking() {
        try { tts?.stop() } catch (_: Throwable) {}
        _speaking.value = false
    }

    fun shutdown() {
        stopListening()
        stopSpeaking()
        try { recognizer?.destroy() } catch (_: Throwable) {}
        try { tts?.shutdown() } catch (_: Throwable) {}
        recognizer = null
        tts = null
    }

    private fun buildListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: MutableList<String>?) {
            partialResults?.firstOrNull()?.let { _partialText.value = it }
        }
        override fun onResults(results: MutableList<String>?) {
            val finalText = results?.firstOrNull()?.trim().orEmpty()
            _partialText.value = finalText
            _listening.value = false
            if (finalText.isNotEmpty()) {
                onHeard?.invoke(finalText)
            }
        }
        override fun onError(error: Int) {
            _listening.value = false
            _lastError.value = mapSpeechError(error)
        }
    }

    private fun mapSpeechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error."
        SpeechRecognizer.ERROR_CLIENT -> "Client error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
        SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that — please try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy."
        SpeechRecognizer.ERROR_SERVER -> "Speech service error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything."
        else -> "Speech recognition error ($code)."
    }

    /** Convenience hook so callers don't have to wire up executor + planner manually. */
    fun isVoiceEnabled(): Boolean = _listening.value
}