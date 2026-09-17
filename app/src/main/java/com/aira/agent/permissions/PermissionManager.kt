package com.aira.agent.permissions

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aira.agent.R
import com.aira.agent.accessibility.AiraAccessibilityService

/**
 * Centralised permission handling. Every permission we use has a
 * stable [PermissionDef] entry so the UI can list them with human-
 * readable titles and reasons.
 */
class PermissionManager(private val context: Context) {

    enum class Status { Granted, Denied, NotAvailable }

    data class PermissionDef(
        val id: String,
        val titleRes: Int,
        val whyRes: Int,
        val androidPermission: String?,
        val check: () -> Status
    )

    val all: List<PermissionDef> by lazy {
        listOf(
            perm("RECORD_AUDIO", "Microphone", R.string.perm_microphone_why,
                R.string.perm_microphone, android.Manifest.permission.RECORD_AUDIO),
            perm("READ_CONTACTS", "Contacts", R.string.perm_contacts_why,
                R.string.perm_contacts, android.Manifest.permission.READ_CONTACTS),
            perm("SEND_SMS", "SMS", R.string.perm_sms_why,
                R.string.perm_sms, android.Manifest.permission.SEND_SMS),
            perm("CALL_PHONE", "Phone", R.string.perm_phone_why,
                R.string.perm_phone, android.Manifest.permission.CALL_PHONE),
            perm("POST_NOTIFICATIONS", "Notifications", R.string.perm_post_notifications_why,
                R.string.perm_post_notifications, android.Manifest.permission.POST_NOTIFICATIONS),
            perm("BLUETOOTH_CONNECT", "Bluetooth", R.string.perm_bluetooth_connect_why,
                R.string.perm_bluetooth_connect, android.Manifest.permission.BLUETOOTH_CONNECT),
            perm("MODIFY_AUDIO_SETTINGS", "Audio settings", R.string.perm_modify_audio_settings_why,
                R.string.perm_modify_audio_settings, android.Manifest.permission.MODIFY_AUDIO_SETTINGS),
            perm("WRITE_SETTINGS", "System settings", R.string.perm_write_settings_why,
                R.string.perm_write_settings, android.Manifest.permission.WRITE_SETTINGS),
            perm("READ_MEDIA_IMAGES", "Photos & videos", R.string.perm_read_media_images_why,
                R.string.perm_read_media_images, android.Manifest.permission.READ_MEDIA_IMAGES),
            perm("READ_MEDIA_AUDIO", "Music files", R.string.perm_read_media_audio_why,
                R.string.perm_read_media_audio, android.Manifest.permission.READ_MEDIA_AUDIO),
            perm("SCHEDULE_EXACT_ALARM", "Alarms", R.string.perm_schedule_exact_alarm_why,
                R.string.perm_schedule_exact_alarm, android.Manifest.permission.SCHEDULE_EXACT_ALARM),
            a11y()
        )
    }

    private fun perm(
        id: String,
        @Suppress("UNUSED_PARAMETER") title: String,
        whyRes: Int,
        titleRes: Int,
        androidPermission: String
    ): PermissionDef = PermissionDef(id, titleRes, whyRes, androidPermission) {
        if (ContextCompat.checkSelfPermission(context, androidPermission) == PackageManager.PERMISSION_GRANTED)
            Status.Granted else Status.Denied
    }

    private fun a11y(): PermissionDef = PermissionDef(
        id = "AccessibilityService",
        titleRes = R.string.perm_a11y,
        whyRes = R.string.perm_a11y_why,
        androidPermission = null,
        check = { if (AiraAccessibilityService.isEnabled(context)) Status.Granted else Status.Denied }
    )

    /** Returns true if permission is granted *now*. */
    fun isGranted(androidPermission: String): Boolean =
        ContextCompat.checkSelfPermission(context, androidPermission) == PackageManager.PERMISSION_GRANTED

    /** Convenience: returns Status for a permission def. */
    fun status(def: PermissionDef): Status = def.check()

    /** Used by the executor; if missing, throws AiraError.PermissionDenied. */
    fun ensure(androidPermission: String): Boolean =
        if (isGranted(androidPermission)) true else throw com.aira.agent.errors.AiraError.PermissionDenied(androidPermission)

    fun ensureOrFalse(androidPermission: String): Boolean = isGranted(androidPermission)

    /** Best-effort runtime request for a single permission. Returns immediately. */
    fun request(activity: Activity, androidPermission: String, requestCode: Int) {
        if (isGranted(androidPermission)) return
        ActivityCompat.requestPermissions(activity, arrayOf(androidPermission), requestCode)
    }
}