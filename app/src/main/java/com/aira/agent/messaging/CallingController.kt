package com.aira.agent.messaging

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager

/**
 * Call control. Two flows:
 *  - **Dial** — opens the in-call UI with the number pre-filled.
 *    Most Android variants require the user to hit "call" anyway once
 *    CALL_PHONE permission is granted.
 *  - **Voicemail** — opens visual voicemail if available.
 */
class CallingController(private val context: Context) {

    fun dial(phone: String): Boolean {
        val uri = Uri.fromParts("tel", phone, null)
        val intent = Intent(Intent.ACTION_DIAL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { context.startActivity(intent); true } catch (e: Throwable) { false }
    }

    /** Attempts an immediate call. Requires CALL_PHONE. */
    fun callNow(phone: String): Boolean {
        if (!hasCallPermission()) return false
        val uri = Uri.fromParts("tel", phone, null)
        val intent = Intent(Intent.ACTION_CALL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { context.startActivity(intent); true } catch (e: Throwable) { false }
    }

    private fun hasCallPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}