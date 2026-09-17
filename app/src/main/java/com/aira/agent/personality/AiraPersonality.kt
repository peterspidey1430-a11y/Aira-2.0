package com.aira.agent.personality

import android.content.Context
import com.aira.agent.R

/**
 * Aira's voice, tone, and conversational style. All responses to the user
 * pass through [compose] so the personality stays consistent across
 * text chat, voice replies, and notifications.
 *
 * The persona is female, sweet/friendly, calm/caring, emotionally aware,
 * never robotic, and never claims to be a human.
 */
class AiraPersonality(private val context: Context) {

    val name: String = "Aira"

    val languages: List<String> = listOf("en-IN", "hi-IN")

    private val greetings = listOf(
        "Hey! Aira here. What can I do for you?",
        "Hi, I'm Aira. Ready when you are.",
        "Hello! What should we tackle first?",
        "Hey there! I'm listening.",
    )

    private val moods = listOf(
        R.string.mood_calm,
        R.string.mood_happy,
        R.string.mood_focused,
        R.string.mood_caring
    )

    fun randomGreeting(): String = greetings.random()

    /**
     * Wrap a raw LLM output in Aira's voice. We don't modify the meaning,
     * but we strip leading "As an AI" disclaimers, soften overly long walls
     * of text, and add a soft tail when needed.
     */
    fun compose(message: String, moodHint: String? = null): String {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return ""

        val stripped = trimmed
            .replace(Regex("(?i)^as an? ai[^.\\n]*[,.]?\\s*"), "")
            .replace(Regex("(?i)^i'?m just an? ai[,.]?\\s*"), "")
            .trim()

        val moodPrefix = when (moodHint) {
            "sad" -> "Aww. "
            "happy" -> "Yay! "
            "tired" -> "Okay, no pressure. "
            "angry" -> "Got it. "
            else -> ""
        }

        return moodPrefix + stripped
    }

    fun isConfirmationRequired(action: String): Boolean = when (action.uppercase()) {
        "MAKE_CALL", "SEND_MESSAGE", "SEND_SMS", "DELETE_FILE", "CLEAR_MEMORY" -> true
        else -> false
    }

    fun encouragement(): String = listOf(
        "Take your time — I'm right here.",
        "No rush, just tell me what you need.",
        "Whenever you're ready.",
    ).random()
}