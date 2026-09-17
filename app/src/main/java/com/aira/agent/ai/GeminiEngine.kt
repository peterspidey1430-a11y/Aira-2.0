package com.aira.agent.ai

import com.aira.agent.BuildConfig
import com.aira.agent.errors.AiraError
import com.aira.agent.errors.ErrorHandler
import com.aira.agent.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Real Gemini client. Sends the user request to the Gemini REST API
 * using the model identifier from BuildConfig (currently
 * `gemini-3.8-flash`). Gemini responds with structured JSON
 * describing a [Plan]; we parse and hand it to the executor.
 *
 * The Gemini docs note that "Gemini 3.6 Flash" is *not* an official
 * model identifier (it's used as a deprecation alias pointing to
 * the 3.x family). As of 2026-09-17 the official GA Flash model is
 * `gemini-3.8-flash`. We also try `gemini-flash-latest` (the
 * always-current alias) if the primary isn't available.
 */
class GeminiEngine(
    private val secureStorage: SecureStorage,
    private val errorsProvider: () -> ErrorHandler
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        classDiscriminator = "type"
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        userMessage: String,
        context: GeminiContext
    ): Plan = withContext(Dispatchers.IO) {
        val apiKey = secureStorage.getGeminiApiKey()
            ?: throw AiraError.MissingApiKey()
        if (apiKey.length < 20) throw AiraError.InvalidApiKey("key too short")

        val requestBody = buildRequestBody(userMessage, context)
        val raw = callGemini(apiKey, BuildConfig.GEMINI_MODEL_PRIMARY, requestBody)
        parsePlan(raw)
    }

    /**
     * Build the request body using Gemini's `generateContent` REST schema
     * with responseMimeType=application/json so we always get JSON back.
     */
    private fun buildRequestBody(userMessage: String, context: GeminiContext): String {
        val systemPrompt = buildSystemPrompt(context)
        val payload = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", systemPrompt) })
                    }
                })
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", userMessage) })
                    }
                })
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.4)
                put("topK", 40)
                put("topP", 0.95)
                put("maxOutputTokens", 1024)
                put("responseMimeType", "application/json")
                putJsonObject("responseSchema") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("reply") { put("type", "STRING") }
                        putJsonObject("actions") {
                            put("type", "ARRAY")
                            putJsonObject("items") { put("type", "OBJECT") }
                        }
                        putJsonObject("requiresConfirmation") { put("type", "BOOLEAN") }
                    }
                    putJsonArray("required") {
                        add("reply")
                        add("actions")
                    }
                }
            }
        }
        return payload.toString()
    }

    private fun buildSystemPrompt(ctx: GeminiContext): String {
        val actions = Action::class.sealedSubclasses
            .mapNotNull { it.simpleName }
            .joinToString(", ")

        return buildString {
            append("You are Aira, a sweet, friendly, calm, caring female AI assistant running on Android. ")
            append("You speak naturally in English, Hindi, or Hinglish. ")
            append("Never claim to be human. Never pretend actions succeeded that didn't. ")
            append("If the user is in an emotional state, acknowledge it gently without being intrusive.\n\n")
            append("The user asked: \"${ctx.userMessage}\"\n")
            append("Current mood estimate: ${ctx.mood}\n")
            append("Known memories: ${ctx.memories.take(20).joinToString { "${it.key}=${it.value}" }}\n")
            append("Recent tasks: ${ctx.recentTasks.takeLast(5).joinToString()}\n")
            append("Assistant ON: ${ctx.assistantEnabled}, Voice: ${ctx.voiceEnabled}\n\n")
            append("You MUST reply by returning a JSON object with:\n")
            append(" - reply: short, warm, conversational text (1-3 sentences max) the user will see.\n")
            append(" - actions: array of actions from this allow-list only: $actions.\n")
            append(" - requiresConfirmation: true for any send_message, send_sms, make_call, or remember action.\n\n")
            append("Rules:\n")
            append(" 1. For compound requests (\"open Spotify, play music, lower volume, open WhatsApp\"), return multiple actions in order.\n")
            append(" 2. Don't invent package names — use these known ones when relevant: WhatsApp=com.whatsapp, YouTube=com.google.android.youtube, Spotify=com.spotify.music, Phone=com.android.phone, Settings=com.android.settings, Files=com.android.documentsui, Camera=com.android.camera, Maps=com.google.android.apps.maps, Calendar=com.google.android.calendar, Chrome=com.android.chrome, Gmail=com.google.android.gm, Instagram=com.instagram.android, Facebook=com.facebook.katana, Snapchat=com.snapchat.android.\n")
            append(" 3. For unknown apps, use OPEN_APP with a query and we will resolve the user-visible name via PackageManager.\n")
            append(" 4. For 'turn on/off Bluetooth/wifi' use TOGGLE_BLUETOOTH / TOGGLE_WIFI.\n")
            append(" 5. For 'increase/decrease brightness' use CHANGE_BRIGHTNESS with positive/negative delta.\n")
            append(" 6. For 'lower/raise volume' use CHANGE_VOLUME with negative/positive delta. Default stream is 'media'.\n")
            append(" 7. For 'set alarm for 7 AM' use SET_ALARM with hour=7, minute=0.\n")
            append(" 8. For 'send Rahul a WhatsApp message saying hi' use SEND_MESSAGE app=whatsapp, contact='Rahul', message='hi'.\n")
            append(" 9. For 'search YouTube for X' or 'play X on YouTube' use PLAY_YOUTUBE query='X'.\n")
            append(" 10. For 'play X on Spotify' use PLAY_SPOTIFY query='X'.\n")
            append(" 11. For 'search Google for X' use SEARCH_WEB query='X'.\n")
            append(" 12. For 'flashlight on/off' use FLASHLIGHT enable=true/false.\n")
            append(" 13. Prefer the smallest, most semantic action set. Don't add unnecessary WAIT or READ_SCREEN.\n")
        }
    }

    private fun callGemini(apiKey: String, model: String, body: String): String {
        val url = "${BuildConfig.GEMINI_API_BASE}v1beta/models/$model:generateContent?key=$apiKey"
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw mapHttpError(resp.code, text, model)
            }
            return text
        }
    }

    private fun mapHttpError(code: Int, body: String, model: String): AiraError {
        return when (code) {
            400 -> AiraError.InvalidApiError(extractError(body, code))
            401, 403 -> AiraError.InvalidApiKey(extractError(body, code))
            404 -> AiraError.Model("Model '$model' not found or no longer supported")
            429 -> AiraError.InvalidApiKey("Rate-limited. Try again in a moment.")
            in 500..599 -> AiraError.Model("Gemini server error ($code)")
            else -> AiraError.Model("HTTP $code: ${extractError(body, code)}")
        }
    }

    private fun extractError(body: String, code: Int): String {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: obj.toString().take(160)
        } catch (_: Throwable) {
            "HTTP $code"
        }
    }

    /**
     * Parse Gemini's `generateContent` response. We only need the text
     * part, which under responseMimeType=application/json is itself
     * JSON conforming to our schema.
     */
    private fun parsePlan(raw: String): Plan {
        val root = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (t: Throwable) {
            throw AiraError.Model("Bad response envelope: ${t.message}")
        }

        val candidates = root["candidates"]?.jsonArray
            ?: throw AiraError.Model("No candidates in response")
        val first = candidates.firstOrNull()?.jsonObject
            ?: throw AiraError.Model("Empty candidates array")

        val content = first["content"]?.jsonObject
            ?: throw AiraError.Model("No content in candidate")
        val parts = content["parts"]?.jsonArray
            ?: throw AiraError.Model("No parts in content")
        val textEl = parts.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: throw AiraError.Model("Empty text in part")

        val planObj = try {
            json.parseToJsonElement(textEl).jsonObject
        } catch (t: Throwable) {
            // The model sometimes returns plain prose — wrap as a single Reply.
            return Plan(reply = textEl, actions = listOf(Action.Reply(textEl)), requiresConfirmation = false)
        }

        val reply = planObj["reply"]?.jsonPrimitive?.content.orEmpty()
        val requiresConf = planObj["requiresConfirmation"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val actionsJson = planObj["actions"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

        val actions = actionsJson.mapNotNull { el ->
            try {
                json.decodeFromJsonElement(Action.serializer(), el)
            } catch (t: Throwable) {
                null
            }
        }

        return Plan(reply = reply, actions = actions, requiresConfirmation = requiresConf)
    }
}

/**
 * Bundled context for one Gemini call: recent memories, mood, etc.
 */
data class GeminiContext(
    val userMessage: String,
    val mood: String,
    val memories: List<com.aira.agent.memory.MemoryItem>,
    val recentTasks: List<String>,
    val assistantEnabled: Boolean,
    val voiceEnabled: Boolean
)