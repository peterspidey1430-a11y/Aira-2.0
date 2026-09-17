package com.aira.agent.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Wraps [EncryptedSharedPreferences] so the Gemini API key and other
 * sensitive values never touch plain prefs / disk.
 *
 * The master key itself is stored in the AndroidKeyStore — we never
 * handle raw key material.
 */
class SecureStorage(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        // If keystore is corrupted (e.g. user wiped lockscreen), fall back
        // to plain prefs so the app still launches — and warn loudly.
        Log.w(TAG, "Encrypted prefs unavailable, falling back: ${t.message}")
        context.getSharedPreferences("${FILE_NAME}_fallback", Context.MODE_PRIVATE)
    }

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI, key.trim()).apply()
    }

    fun getGeminiApiKey(): String? = prefs.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }

    fun hasGeminiApiKey(): Boolean = !getGeminiApiKey().isNullOrBlank()

    fun clearGeminiApiKey() {
        prefs.edit().remove(KEY_GEMINI).apply()
    }

    fun setVoiceKey(key: String) { prefs.edit().putString(KEY_VOICE, key).apply() }
    fun getVoiceKey(): String? = prefs.getString(KEY_VOICE, null)

    companion object {
        private const val TAG = "SecureStorage"
        private const val FILE_NAME = "aira_secure_prefs"
        const val KEY_GEMINI = "gemini_api_key"
        const val KEY_VOICE = "voice_engine_key"
    }
}