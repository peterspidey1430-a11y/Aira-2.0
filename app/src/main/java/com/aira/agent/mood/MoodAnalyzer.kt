package com.aira.agent.mood

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Naive but useful mood & context analyzer.
 *
 * We do NOT claim to detect real emotions; the result is an *estimate*
 * derived from lexical cues in the last few user messages. Aira uses
 * it to choose a softer / more upbeat tone, never to make intrusive
 * assumptions about the user.
 */
class MoodAnalyzer {

    enum class Mood(val label: String) {
        Neutral("neutral"),
        Happy("happy"),
        Sad("sad"),
        Angry("angry"),
        Tired("tired"),
        Excited("excited")
    }

    private val _mood = MutableStateFlow(Mood.Neutral)
    val mood: StateFlow<Mood> = _mood.asStateFlow()

    private val recent = ArrayDeque<String>(20)

    fun observe(text: String): Mood {
        recent.addLast(text.lowercase())
        while (recent.size > 20) recent.removeFirst()
        return classify()
    }

    fun current(): Mood = _mood.value

    private fun classify(): Mood {
        if (recent.isEmpty()) return Mood.Neutral
        val blob = recent.joinToString(" ")

        val scores = mutableMapOf(
            Mood.Sad to sadWords.count { blob.contains(it) },
            Mood.Angry to angryWords.count { blob.contains(it) },
            Mood.Tired to tiredWords.count { blob.contains(it) },
            Mood.Happy to happyWords.count { blob.contains(it) },
            Mood.Excited to excitedWords.count { blob.contains(it) },
        )

        val best = scores.filterValues { it > 0 }.maxByOrNull { it.value }
        val mood = best?.key ?: Mood.Neutral
        _mood.value = mood
        return mood
    }

    private val sadWords = listOf("sad", "down", "unhappy", "cry", "upset", "depressed", "miss", "अकेला", "उदास")
    private val angryWords = listOf("angry", "mad", "hate", "furious", "annoyed", "frustrated", "गुस्सा")
    private val tiredWords = listOf("tired", "exhausted", "sleepy", "थक", "थका")
    private val happyWords = listOf("happy", "great", "awesome", "love", "yay", "fun", "खुश")
    private val excitedWords = listOf("wow", "amazing", "excited", "incredible", "!", "let's go")
}