package com.aira.agent.ai

import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.aira.agent.device.DeviceController
import com.aira.agent.errors.AiraError
import com.aira.agent.errors.ErrorHandler
import com.aira.agent.media.SpotifyController
import com.aira.agent.media.YouTubeController
import com.aira.agent.memory.MemoryManager
import com.aira.agent.messaging.CallingController
import com.aira.agent.messaging.MessagingController
import com.aira.agent.permissions.PermissionManager
import com.aira.agent.web.BrowserController
import com.aira.agent.web.SearchController

/**
 * Real execution of every validated [Action]. This is the only layer
 * that touches Android APIs / Intents for action-handling; everything
 * upstream stays pure data.
 *
 * Every action returns an [ActionResult] that the planner chains into
 * the next step. We never throw across module boundaries — every
 * exception becomes a [ActionResult.Failed] with a human-readable reason.
 */
class ActionExecutor(
    private val device: DeviceController,
    private val messaging: MessagingController,
    private val calling: CallingController,
    private val browser: BrowserController,
    private val search: SearchController,
    private val youTube: YouTubeController,
    private val spotify: SpotifyController,
    private val permissions: PermissionManager,
    private val memory: MemoryManager,
    private val errors: ErrorHandler
) {

    suspend fun execute(action: Action): ActionResult {
        return try {
            when (action) {
                is Action.OpenApp -> {
                    val pkg = action.packageName ?: device.resolvePackageByName(action.query ?: "")
                        ?: throw AiraError.AppNotInstalled(action.query ?: "?")
                    device.openApp(pkg)
                    ActionResult.Success("Opened ${device.labelForPackage(pkg)}")
                }
                is Action.SearchWeb -> {
                    search.search(action.query, action.engine)
                    ActionResult.Success("Searching for ${action.query}")
                }
                is Action.OpenUrl -> {
                    browser.open(action.url)
                    ActionResult.Success("Opened ${action.url}")
                }
                is Action.MakeCall -> {
                    if (!permissions.ensure(android.Manifest.permission.READ_CONTACTS))
                        throw AiraError.PermissionDenied("READ_CONTACTS")
                    val num = action.number ?: device.resolveContactNumber(action.contact)
                        ?: throw AiraError.ActionFailed("Couldn't find ${action.contact} in contacts")
                    calling.dial(num)
                    ActionResult.Success("Calling ${action.contact}")
                }
                is Action.SendMessage -> {
                    val num = action.phone ?: device.resolveContactNumber(action.contact)
                    if (num.isNullOrBlank())
                        throw AiraError.ActionFailed("Couldn't find ${action.contact}")
                    when (action.app.lowercase()) {
                        "whatsapp" -> messaging.openWhatsAppChat(num, action.message)
                        "facebook" -> messaging.openMessengerChat(num, action.message)
                        "instagram" -> messaging.openInstagramProfile(action.contact)
                        "snapchat" -> messaging.openSnapchat(action.contact)
                        "sms" -> messaging.sendSms(num, action.message)
                        else -> messaging.openWhatsAppChat(num, action.message)
                    }
                    ActionResult.Success("Handed off to ${action.app}")
                }
                is Action.SendSms -> {
                    if (!permissions.ensure(android.Manifest.permission.SEND_SMS))
                        throw AiraError.PermissionDenied("SEND_SMS")
                    val num = action.phone ?: device.resolveContactNumber(action.contact)
                        ?: throw AiraError.ActionFailed("Couldn't find ${action.contact}")
                    val ok = messaging.sendSms(num, action.message)
                    if (!ok) throw AiraError.ActionFailed("SMS send failed")
                    ActionResult.Success("Sent SMS")
                }
                is Action.TapElement -> device.accessibility
                    ?.tap(action.text, action.resourceId, action.contentDesc)
                    ?: throw AiraError.AutomationBlocked("Accessibility Service is off")
                is Action.TypeText -> device.accessibility
                    ?.type(action.text, action.target)
                    ?: throw AiraError.AutomationBlocked("Accessibility Service is off")
                is Action.Scroll -> device.accessibility
                    ?.scroll(action.direction, action.amount)
                    ?: throw AiraError.AutomationBlocked("Accessibility Service is off")
                is Action.Swipe -> device.accessibility
                    ?.swipe(action.direction, action.magnitude)
                    ?: throw AiraError.AutomationBlocked("Accessibility Service is off")
                Action.PressBack -> {
                    device.pressBack()
                    ActionResult.Success("Back")
                }
                Action.PressHome, Action.GoHome -> {
                    device.pressHome()
                    ActionResult.Success("Home")
                }
                is Action.ReadScreen -> {
                    val items = device.accessibility?.readScreen(action.maxItems).orEmpty()
                    ActionResult.Success(if (items.isEmpty()) "Empty screen" else items.joinToString("\n"))
                }
                is Action.Wait -> {
                    kotlinx.coroutines.delay(action.ms)
                    ActionResult.Success()
                }
                is Action.Flashlight -> {
                    device.setFlashlight(action.enable)
                    ActionResult.Success(if (action.enable) "Torch on" else "Torch off")
                }
                is Action.ChangeVolume -> device.changeVolume(action.stream, action.delta)
                is Action.SetVolume -> device.setVolumeLevel(action.stream, action.level)
                is Action.ChangeBrightness -> {
                    device.changeBrightness(action.delta, action.absolute)
                    ActionResult.Success("Brightness adjusted")
                }
                is Action.ToggleBluetooth -> {
                    val ok = device.toggleBluetooth(action.enable)
                    if (!ok) ActionResult.Failed("Bluetooth toggle not permitted on this device", recoverable = false)
                    else ActionResult.Success(if (action.enable) "Bluetooth on" else "Bluetooth off")
                }
                is Action.ToggleWifi -> {
                    val ok = device.toggleWifi(action.enable)
                    ActionResult.Success(if (ok) (if (action.enable) "Wi-Fi on" else "Wi-Fi off") else "Wi-Fi toggle declined")
                }
                is Action.SetAlarm -> {
                    device.setAlarm(action.hour, action.minute, action.label)
                    ActionResult.Success("Alarm set for ${action.hour}:${"%02d".format(action.minute)}")
                }
                is Action.PlayYouTube -> {
                    youTube.searchAndPlay(action.query)
                    ActionResult.Success("Searching YouTube for ${action.query}")
                }
                is Action.PlaySpotify -> {
                    if (action.query.isNullOrBlank()) spotify.openApp() else spotify.searchAndPlay(action.query)
                    ActionResult.Success("Playing on Spotify")
                }
                Action.MediaPlayPause -> {
                    device.mediaPlayPause()
                    ActionResult.Success("Toggled playback")
                }
                Action.MediaNext -> { device.mediaNext(); ActionResult.Success("Next") }
                Action.MediaPrev -> { device.mediaPrev(); ActionResult.Success("Previous") }
                Action.OpenFiles -> { device.openFiles(); ActionResult.Success("Opened Files") }
                is Action.FindFile -> {
                    val path = device.findFile(action.name)
                    ActionResult.Success(path?.let { "Found at $it" } ?: "Couldn't find ${action.name}")
                }
                is Action.Remember -> {
                    memory.put(action.key, action.value, action.category)
                    ActionResult.Success("Got it — remembered.")
                }
                is Action.Forget -> {
                    memory.delete(action.key)
                    ActionResult.Success("Forgot ${action.key}")
                }
                is Action.Reply -> ActionResult.Success(action.text)
                is Action.AskClarification -> ActionResult.NeedsConfirmation(action)
            }
        } catch (e: AiraError) {
            ActionResult.Failed(e.message ?: "Error", recoverable = e !is AiraError.Fatal)
        } catch (e: Throwable) {
            ActionResult.Failed(e.message ?: e.javaClass.simpleName, recoverable = true)
        }
    }
}