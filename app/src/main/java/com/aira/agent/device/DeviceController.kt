package com.aira.agent.device

import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.aira.agent.accessibility.AiraAccessibilityService
import com.aira.agent.errors.AiraError

/**
 * Direct device controls — flashlight, brightness, volume, Bluetooth toggle,
 * Wi-Fi toggle, alarm scheduling, media transport, file search, contact
 * resolution, package lookup, etc.
 *
 * Every method returns Boolean where applicable; throws [AiraError] only
 * when the caller can't recover.
 */
class DeviceController(private val context: Context) {

    private val audio by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val alarmManager by lazy { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }

    val accessibility: AiraAccessibilityService?
        get() = AiraAccessibilityService.instance

    // ----------------- App launcher -----------------

    fun openApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw AiraError.AppNotInstalled(packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun labelForPackage(packageName: String): String = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (e: Throwable) { packageName }

    /** Resolve a user-friendly app name (e.g. "WhatsApp") to a package. */
    fun resolvePackageByName(query: String): String? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return packages
            .mapNotNull { app ->
                val label = pm.getApplicationLabel(app).toString().lowercase()
                val pkg = app.packageName.lowercase()
                when {
                    label == q -> app.packageName
                    label.contains(q) || pkg.contains(q) -> app.packageName
                    else -> null
                }
            }
            .firstOrNull()
    }

    // ----------------- Navigation buttons -----------------

    fun pressHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun pressBack(): Boolean {
        // Use accessibility when available (works everywhere); else KEYCODE_BACK.
        val acc = accessibility
        return if (acc != null) { acc.globalBack(); true } else {
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (e: Throwable) { false }
        }
    }

    // ----------------- Flashlight -----------------

    fun setFlashlight(enable: Boolean): Boolean {
        return try {
            val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camId = cam.cameraIdList.firstOrNull { id ->
                val characteristics = cam.getCameraCharacteristics(id)
                characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cam.cameraIdList.firstOrNull()
                ?: return false

            cam.setTorchMode(camId, enable)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Flashlight failed: ${e.message}")
            false
        }
    }

    // ----------------- Volume -----------------

    fun changeVolume(stream: String, delta: Int): Boolean {
        val s = streamFor(stream)
        val max = audio.getStreamMaxVolume(s)
        val current = audio.getStreamVolume(s)
        val target = (current + delta).coerceIn(0, max)
        audio.setStreamVolume(s, target, 0)
        return true
    }

    fun setVolumeLevel(stream: String, level: Int): Boolean {
        val s = streamFor(stream)
        val max = audio.getStreamMaxVolume(s)
        audio.setStreamVolume(s, level.coerceIn(0, max), 0)
        return true
    }

    private fun streamFor(name: String): Int = when (name.lowercase()) {
        "media" -> AudioManager.STREAM_MUSIC
        "ring" -> AudioManager.STREAM_RING
        "alarm" -> AudioManager.STREAM_ALARM
        "notification" -> AudioManager.STREAM_NOTIFICATION
        "call" -> AudioManager.STREAM_VOICE_CALL
        else -> AudioManager.STREAM_MUSIC
    }

    // ----------------- Brightness -----------------

    fun changeBrightness(delta: Int, absolute: Int?): Boolean {
        return try {
            val resolver = context.contentResolver
            val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val lp = resolver
                Settings.System.getInt(lp, Settings.System.SCREEN_BRIGHTNESS, 128)
            } else 128

            val target = (absolute ?: (current + delta)).coerceIn(0, 255)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Need WRITE_SETTINGS — caller must have granted via settings.
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, target)
            } else {
                val mode = if (target > 8) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, target)
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Brightness adjust failed: ${e.message}")
            false
        }
    }

    // ----------------- Bluetooth (toggle only — Android limits us) -----------------

    fun toggleBluetooth(enable: Boolean): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) return false
                val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = mgr.adapter ?: return false
                return if (enable) adapter.enable() else adapter.disable()
            } else {
                @Suppress("DEPRECATION")
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
                return if (enable) adapter.enable() else adapter.disable()
            }
        } catch (e: SecurityException) { false }
    }

    // ----------------- Wi-Fi (best-effort toggle) -----------------

    fun toggleWifi(enable: Boolean): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            return wm.setWifiEnabled(enable)
        } catch (e: Throwable) {
            Log.w(TAG, "Wi-Fi toggle failed: ${e.message}")
            false
        }
    }

    // ----------------- Alarm -----------------

    fun setAlarm(hour: Int, minute: Int, label: String?): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label ?: "Aira alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Alarm intent failed: ${e.message}")
            // Fallback to internal AlarmManager
            try {
                val pi = PendingIntent.getBroadcast(
                    context, 0,
                    Intent(context, AlarmReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                    set(java.util.Calendar.SECOND, 0)
                    if (timeInMillis < System.currentTimeMillis())
                        add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                true
            } catch (e2: Throwable) { false }
        }
    }

    // ----------------- Media transport -----------------

    fun mediaPlayPause(): Boolean {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = msm.getActiveSessions(null)
            val target: MediaController = sessions.firstOrNull() ?: return false
            val wasPlaying = target.playbackState?.isActive == 1
            if (wasPlaying) target.transportControls.pause() else target.transportControls.play()
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Media control failed: ${e.message}")
            false
        }
    }

    fun mediaNext(): Boolean {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = msm.getActiveSessions(null)
            val target: MediaController = sessions.firstOrNull() ?: return false
            target.transportControls.skipToNext(); true
        } catch (e: Throwable) { false }
    }

    fun mediaPrev(): Boolean {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = msm.getActiveSessions(null)
            val target: MediaController = sessions.firstOrNull() ?: return false
            target.transportControls.skipToPrevious(); true
        } catch (e: Throwable) { false }
    }

    // ----------------- Files -----------------

    fun openFiles(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open file")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Throwable) { false }
    }

    /**
     * Search user-visible files. We use MediaStore on Android 10+ where
     * scoped storage forbids global filesystem walks, and a recursive
     * `find` shell command is not available without root. Result is a
     * best-effort list of paths we can actually see.
     */
    fun findFile(name: String): String? {
        val resolver = context.contentResolver
        val n = name.trim().lowercase()
        if (n.isEmpty()) return null

        // 1. Try MediaStore for media files
        val media = mutableListOf<String>()
        val uris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        )
        for (uri in uris) {
            try {
                val cursor: Cursor? = resolver.query(uri, null, null, null, null)
                cursor?.use {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    while (it.moveToNext() && media.size < 5) {
                        if (nameIdx >= 0) {
                            val display = it.getString(nameIdx) ?: continue
                            if (display.lowercase().contains(n)) media += display
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        return media.firstOrNull()
    }

    // ----------------- Contacts -----------------

    fun resolveContactNumber(name: String): String? {
        if (name.isBlank()) return null
        val resolver: ContentResolver = context.contentResolver
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME
        )
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")
        return try {
            resolver.query(uri, projection, selection, selectionArgs, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val displayName = c.getString(1) ?: continue
                    if (!displayName.lowercase().contains(name.lowercase())) continue
                    val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                    val pArgs = arrayOf(id)
                    resolver.query(
                        phoneUri,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        pArgs, null
                    )?.use { pc ->
                        if (pc.moveToFirst()) return pc.getString(0)
                    }
                }
            }
            null
        } catch (e: SecurityException) {
            throw AiraError.PermissionDenied("READ_CONTACTS")
        } catch (e: Throwable) { null }
    }

    companion object { private const val TAG = "DeviceController" }
}