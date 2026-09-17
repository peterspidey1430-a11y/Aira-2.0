package com.aira.agent.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

/**
 * All the third-party chat-app integrations live here. Each method
 * tries a real intent / deep link and returns whether it succeeded.
 *
 * **Important reality check:** WhatsApp, Instagram, Facebook, and
 * Snapchat don't expose a public API for fully automated "send this
 * message" flows. The closest legitimate approach is to:
 *  - resolve the contact,
 *  - open the correct chat via deep link,
 *  - pre-fill the message where the platform supports it.
 *
 * We NEVER claim the message was actually sent — the user has to tap
 * the send button. This is what the spec calls "take the user as
 * close as technically possible".
 */
class MessagingController(private val context: Context) {

    // --------------- WhatsApp ---------------

    /**
     * Opens a WhatsApp chat with `phone` and pre-fills `message` via
     * the wa.me deep link. WhatsApp does not support truly sending
     * programmatically without Business API access, so this opens the
     * chat with the text ready and lets the user hit send.
     */
    fun openWhatsAppChat(phone: String, message: String): Boolean {
        val sanitizedPhone = phone.filter { it.isDigit() || it == '+' }
        val url = "https://wa.me/${sanitizedPhone.removePrefix("+")}?text=${Uri.encode(message)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            `package` = "com.whatsapp"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Throwable) {
            // Fall back to web WhatsApp
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e2: Throwable) { false }
        }
    }

    // --------------- Facebook Messenger ---------------

    fun openMessengerChat(user: String, message: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.me/$user")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { context.startActivity(intent); true } catch (e: Throwable) { false }
    }

    // --------------- Instagram ---------------

    fun openInstagramProfile(username: String): Boolean {
        val cleaned = username.trim().removePrefix("@")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/$cleaned")).apply {
            `package` = "com.instagram.android"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { context.startActivity(intent); true } catch (e: Throwable) { false }
    }

    // --------------- Snapchat ---------------

    fun openSnapchat(username: String): Boolean {
        val cleaned = username.trim().removePrefix("@")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://snapchat.com/add/$cleaned")).apply {
            `package` = "com.snapchat.android"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { context.startActivity(intent); true } catch (e: Throwable) { false }
    }

    // --------------- SMS ---------------

    fun sendSms(phone: String, message: String): Boolean {
        return try {
            val sm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: return false
            val parts = sm.divideMessage(message)
            if (parts.size > 1) sm.sendMultipartTextMessage(phone, null, parts, null, null)
            else sm.sendTextMessage(phone, null, message, null, null)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "SMS failed: ${e.message}")
            // Fall back to the system composer
            try {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (e2: Throwable) { false }
        }
    }

    companion object { private const val TAG = "MessagingController" }
}