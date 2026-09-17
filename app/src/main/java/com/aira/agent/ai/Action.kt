package com.aira.agent.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Every action Aira can perform on the device. The LLM never gets
 * to call arbitrary code; it returns a [Plan] which we validate
 * through [ActionValidator] before [ActionExecutor] runs anything.
 *
 * Adding a new capability means: add a new [Action] subtype, add it
 * to [ActionValidator] allow-list, add a handler in [ActionExecutor].
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class Action {

    @Serializable
    @SerialName("OPEN_APP")
    data class OpenApp(
        val packageName: String? = null,
        val query: String? = null
    ) : Action()

    @Serializable
    @SerialName("SEARCH_WEB")
    data class SearchWeb(val query: String, val engine: String = "google") : Action()

    @Serializable
    @SerialName("OPEN_URL")
    data class OpenUrl(val url: String) : Action()

    @Serializable
    @SerialName("MAKE_CALL")
    data class MakeCall(val contact: String, val number: String? = null) : Action()

    @Serializable
    @SerialName("SEND_MESSAGE")
    data class SendMessage(
        val app: String,
        val contact: String,
        val message: String,
        val phone: String? = null
    ) : Action()

    @Serializable
    @SerialName("SEND_SMS")
    data class SendSms(val contact: String, val message: String, val phone: String? = null) : Action()

    @Serializable
    @SerialName("TAP_ELEMENT")
    data class TapElement(
        val text: String? = null,
        val resourceId: String? = null,
        val contentDesc: String? = null,
        val index: Int? = null
    ) : Action()

    @Serializable
    @SerialName("TYPE_TEXT")
    data class TypeText(val text: String, val target: String? = null) : Action()

    @Serializable
    @SerialName("SCROLL")
    data class Scroll(val direction: String = "down", val amount: Int = 1) : Action()

    @Serializable
    @SerialName("SWIPE")
    data class Swipe(val direction: String, val magnitude: Float = 0.5f) : Action()

    @Serializable
    @SerialName("PRESS_BACK")
    data object PressBack : Action()

    @Serializable
    @SerialName("PRESS_HOME")
    data object PressHome : Action()

    @Serializable
    @SerialName("GO_HOME")
    data object GoHome : Action()

    @Serializable
    @SerialName("READ_SCREEN")
    data class ReadScreen(val maxItems: Int = 40) : Action()

    @Serializable
    @SerialName("WAIT")
    data class Wait(val ms: Long) : Action()

    @Serializable
    @SerialName("FLASHLIGHT")
    data class Flashlight(val enable: Boolean) : Action()

    @Serializable
    @SerialName("CHANGE_VOLUME")
    data class ChangeVolume(val stream: String = "media", val delta: Int) : Action()

    @Serializable
    @SerialName("SET_VOLUME")
    data class SetVolume(val stream: String = "media", val level: Int) : Action()

    @Serializable
    @SerialName("CHANGE_BRIGHTNESS")
    data class ChangeBrightness(val delta: Int, val absolute: Int? = null) : Action()

    @Serializable
    @SerialName("TOGGLE_BLUETOOTH")
    data class ToggleBluetooth(val enable: Boolean) : Action()

    @Serializable
    @SerialName("TOGGLE_WIFI")
    data class ToggleWifi(val enable: Boolean) : Action()

    @Serializable
    @SerialName("SET_ALARM")
    data class SetAlarm(val hour: Int, val minute: Int, val label: String? = null) : Action()

    @Serializable
    @SerialName("PLAY_YOUTUBE")
    data class PlayYouTube(val query: String) : Action()

    @Serializable
    @SerialName("PLAY_SPOTIFY")
    data class PlaySpotify(val query: String? = null) : Action()

    @Serializable
    @SerialName("MEDIA_PLAY_PAUSE")
    data object MediaPlayPause : Action()

    @Serializable
    @SerialName("MEDIA_NEXT")
    data object MediaNext : Action()

    @Serializable
    @SerialName("MEDIA_PREV")
    data object MediaPrev : Action()

    @Serializable
    @SerialName("OPEN_FILES")
    data object OpenFiles : Action()

    @Serializable
    @SerialName("FIND_FILE")
    data class FindFile(val name: String) : Action()

    @Serializable
    @SerialName("REMEMBER")
    data class Remember(val key: String, val value: String, val category: String = "general") : Action()

    @Serializable
    @SerialName("FORGET")
    data class Forget(val key: String) : Action()

    @Serializable
    @SerialName("REPLY")
    data class Reply(val text: String, val speak: Boolean = true) : Action()

    @Serializable
    @SerialName("ASK_CLARIFICATION")
    data class AskClarification(val question: String) : Action()
}