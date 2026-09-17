package com.aira.agent.ai

import com.aira.agent.errors.AiraError
import kotlin.math.abs

/**
 * Every action Gemini returns goes through here before being executed.
 * This is the security boundary between the LLM and the device.
 *
 * Validation rules:
 *  - Every action must be one of the well-known subtypes.
 *  - Volume changes are clamped to safe increments.
 *  - Tapping by raw pixel coordinates is forbidden; only semantic
 *    lookup (text / resource-id / content-desc) is allowed.
 *  - SMS, calls, and "send message" require explicit user confirmation.
 *  - Network calls are limited to known Gemini / search hosts.
 *  - Brightness is clamped to 0..255.
 */
object ActionValidator {

    private val ALLOWED_SCHEMES = setOf("https", "intent")
    private val ALLOWED_HOSTS = setOf(
        "google.com", "www.google.com",
        "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be",
        "spotify.com", "open.spotify.com",
        "wikipedia.org", "en.wikipedia.org",
        "duckduckgo.com",
        "bing.com"
    )

    fun validate(action: Action) {
        when (action) {
            is Action.OpenApp -> {
                if (action.packageName != null) requireValidPackage(action.packageName)
                if (action.query != null) requireTextLength(action.query, 200)
            }
            is Action.SearchWeb -> {
                requireTextLength(action.query, 500)
                if (action.engine !in setOf("google", "duckduckgo", "bing"))
                throw AiraError.Validation("Unknown search engine: ${action.engine}")
            }
            is Action.OpenUrl -> {
                val url = action.url.trim()
                val scheme = url.substringBefore("://", missingDelimiterValue = "")
                if (scheme !in ALLOWED_SCHEMES && !url.startsWith("intent:"))
                    throw AiraError.Validation("Refusing to open scheme: $scheme")
                if (url.startsWith("https://")) {
                    val host = url.removePrefix("https://").substringBefore("/")
                    if (ALLOWED_HOSTS.none { host == it || host.endsWith(".$it") })
                        throw AiraError.Validation("Refusing unknown host: $host")
                }
            }
            is Action.MakeCall -> {
                if (action.contact.isBlank() && action.number.isNullOrBlank())
                    throw AiraError.Validation("Call needs a contact or number")
            }
            is Action.SendMessage -> {
                if (action.contact.isBlank())
                    throw AiraError.Validation("SendMessage needs a contact")
                if (action.message.isBlank())
                    throw AiraError.Validation("SendMessage needs a message body")
                if (action.app !in setOf("whatsapp", "facebook", "instagram", "snapchat", "sms"))
                    throw AiraError.Validation("Unsupported messaging app: ${action.app}")
            }
            is Action.SendSms -> {
                if (action.contact.isBlank() && action.phone.isNullOrBlank())
                    throw AiraError.Validation("SMS needs a contact or number")
                if (action.message.isBlank())
                    throw AiraError.Validation("SMS needs a message body")
                if (action.message.length > 1000)
                    throw AiraError.Validation("SMS body too long")
            }
            is Action.TapElement -> {
                if (action.text == null && action.resourceId == null && action.contentDesc == null)
                    throw AiraError.Validation("Tap needs text/resourceId/contentDesc (no raw coordinates allowed)")
            }
            is Action.TypeText -> {
                requireTextLength(action.text, 4000)
            }
            is Action.Scroll -> {
                if (action.direction !in setOf("up", "down", "left", "right"))
                    throw AiraError.Validation("Bad scroll direction: ${action.direction}")
                if (action.amount !in 1..10)
                    throw AiraError.Validation("Bad scroll amount: ${action.amount}")
            }
            is Action.Swipe -> {
                if (action.direction !in setOf("up", "down", "left", "right"))
                    throw AiraError.Validation("Bad swipe direction: ${action.direction}")
                if (action.magnitude !in 0.1f..1.0f)
                    throw AiraError.Validation("Bad swipe magnitude: ${action.magnitude}")
            }
            is Action.ChangeVolume -> {
                if (abs(action.delta) > 10)
                    throw AiraError.Validation("Volume delta too large: ${action.delta}")
                if (action.stream !in setOf("media", "ring", "alarm", "notification", "call"))
                    throw AiraError.Validation("Bad audio stream: ${action.stream}")
            }
            is Action.SetVolume -> {
                if (action.level !in 0..100)
                    throw AiraError.Validation("Volume level out of range: ${action.level}")
            }
            is Action.ChangeBrightness -> {
                if (action.delta !in -255..255)
                    throw AiraError.Validation("Brightness delta out of range")
                if (action.absolute != null && action.absolute !in 0..255)
                    throw AiraError.Validation("Absolute brightness out of range")
            }
            is Action.SetAlarm -> {
                if (action.hour !in 0..23 || action.minute !in 0..59)
                    throw AiraError.Validation("Bad alarm time")
            }
            is Action.PlayYouTube -> requireTextLength(action.query, 200)
            is Action.PlaySpotify -> action.query?.let { requireTextLength(it, 200) }
            is Action.FindFile -> requireTextLength(action.name, 200)
            is Action.Remember -> {
                if (action.key.isBlank() || action.value.isBlank())
                    throw AiraError.Validation("Remember needs key and value")
                if (sensitive(action.value))
                    throw AiraError.Validation("Refusing to remember sensitive value")
            }
            is Action.Forget -> requireTextLength(action.key, 100)
            is Action.AskClarification -> requireTextLength(action.question, 500)
            is Action.Reply -> requireTextLength(action.text, 2000)
            is Action.Wait -> {
                if (action.ms < 0 || action.ms > 30_000)
                    throw AiraError.Validation("Wait out of range")
            }
            else -> { /* no extra constraints */ }
        }
    }

    fun requiresConfirmation(action: Action): Boolean = when (action) {
        is Action.MakeCall, is Action.SendMessage, is Action.SendSms,
        is Action.Remember -> true
        else -> false
    }

    private fun requireValidPackage(pkg: String) {
        if (pkg.isBlank()) throw AiraError.Validation("Empty package name")
        if (!pkg.matches(Regex("^[a-zA-Z][a-zA-Z0-9_.]*$")))
            throw AiraError.Validation("Invalid package: $pkg")
    }

    private fun requireTextLength(s: String, max: Int) {
        if (s.isBlank()) throw AiraError.Validation("Empty text")
        if (s.length > max) throw AiraError.Validation("Text too long ($max)")
    }

    private fun sensitive(value: String): Boolean {
        val v = value.lowercase()
        val patterns = listOf(
            "password", "pin", "otp", "cvv", "credit card", "ssn", "aadhaar",
            "api key", "secret", "token"
        )
        return patterns.any { v.contains(it) }
    }
}