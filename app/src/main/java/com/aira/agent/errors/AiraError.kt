package com.aira.agent.errors

/**
 * Typed errors Aira uses internally. The UI layer maps these to
 * user-friendly messages; we never leak raw stack traces.
 */
sealed class AiraError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** User hasn't entered a key, or it's invalid. */
    class MissingApiKey : AiraError("Gemini API key is missing.")
    class InvalidApiKey(detail: String) : AiraError("API key rejected: $detail")
    class InvalidApiError(detail: String) : AiraError("Bad request: $detail")

    /** Validation rejected an LLM-emitted action. */
    class Validation(detail: String) : AiraError("Validation: $detail")

    /** User refused the Android runtime permission. */
    class PermissionDenied(perm: String) : AiraError("Permission denied: $perm")

    /** The requested app isn't installed. */
    class AppNotInstalled(pkg: String) : AiraError("App not installed: $pkg")

    /** User disabled AccessibilityService or device blocked automation. */
    class AutomationBlocked(detail: String) : AiraError("Automation blocked: $detail")

    /** Network is down. */
    class Offline : AiraError("No internet connection.")

    /** Gemini returned a bad / empty response. */
    class Model(detail: String) : AiraError("Model error: $detail")

    /** Action failed mid-execution but might be retryable. */
    class ActionFailed(detail: String) : AiraError("Action failed: $detail")

    /** Catastrophic, non-recoverable. */
    class Fatal(detail: String) : AiraError("Fatal: $detail")
}

/**
 * Centralised error handler. Every component reports here; the UI listens
 * to a single StateFlow so error toasts never get duplicated.
 */
class ErrorHandler {

    data class FriendlyMessage(val title: String, val detail: String, val isFatal: Boolean = false)

    fun explain(error: Throwable): FriendlyMessage = when (error) {
        is AiraError.MissingApiKey -> FriendlyMessage(
            "API key needed",
            "Open Settings → API Key and paste your Gemini API key."
        )
        is AiraError.InvalidApiKey -> FriendlyMessage(
            "API key rejected",
            "Gemini says this key is invalid, expired, or rate-limited. Check it in Settings."
        )
        is AiraError.Validation -> FriendlyMessage("Invalid request", error.message ?: "")
        is AiraError.PermissionDenied -> FriendlyMessage(
            "Permission needed",
            "Aira needs ${permissionName(error.message ?: "")} for that. Open Permissions to grant it."
        )
        is AiraError.AppNotInstalled -> FriendlyMessage(
            "App not installed",
            "I can't find ${error.message?.substringAfter(':')?.trim()}. Want me to search Play Store?"
        )
        is AiraError.AutomationBlocked -> FriendlyMessage(
            "Automation blocked",
            error.message ?: "Android blocked the action."
        )
        is AiraError.Offline -> FriendlyMessage(
            "You're offline",
            "Reconnect to the internet and try again."
        )
        is AiraError.Model -> FriendlyMessage("Model error", error.message ?: "")
        is AiraError.ActionFailed -> FriendlyMessage("Couldn't finish", error.message ?: "")
        is AiraError.Fatal -> FriendlyMessage("Something went wrong", error.message ?: "", isFatal = true)
        else -> FriendlyMessage("Hmm", error.message ?: error.javaClass.simpleName)
    }

    private fun permissionName(raw: String): String =
        raw.substringAfter(':').trim().ifBlank { raw }
}