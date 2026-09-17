package com.aira.agent.background

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aira.agent.AiraApplication
import com.aira.agent.MainActivity
import com.aira.agent.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Optional, fully user-controlled background assistant. While enabled:
 *   - posts a low-priority persistent notification (required by
 *     Android 8+ for foreground services),
 *   - occasionally surfaces a gentle proactive nudge ("need any help?")
 *     through the system notification channel.
 *
 * Frequency is configurable; default is "normal" (every ~45 minutes)
 * with randomised jitter. The user can turn the toggle off at any
 * time and the service exits within one tick.
 */
class BackgroundAssistantService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = AiraApplication.get()
        val enabled = app.repository.isBgOn()
        if (!enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundWithNotification()

        loopJob?.cancel()
        loopJob = scope.launch {
            val freq = when (app.repository.bgFreq()) {
                "rare" -> 90L
                "frequent" -> 15L
                else -> 45L
            }
            val baseDelay = freq * 60_000L
            while (app.repository.isBgOn()) {
                delay(baseDelay + Random.nextLong(0, 10 * 60_000L))
                if (!app.repository.isBgOn()) break
                if (!app.repository.isProactive()) continue
                if (Random.nextInt(100) < 40) sendProactiveNudge()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, AiraApplication.CH_PROACTIVE)
            .setContentTitle(getString(R.string.bg_notification_title))
            .setContentText(getString(R.string.bg_notification_text))
            .setSmallIcon(R.drawable.ic_aira)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            try { startForeground(NOTIF_ID, notification, type) }
            catch (e: Throwable) { startForeground(NOTIF_ID, notification) }
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun sendProactiveNudge() {
        val lines = listOf(
            "Hi! Anything I can help with right now?",
            "Just checking in — need me to set a reminder or open something?",
            "I'm around if you'd like a hand.",
            "Need any help?",
            "What are you working on?",
            "I'm here if you want to talk through something."
        )
        val text = lines.random()

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("aira_proactive", text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, Random.nextInt(1000), launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, AiraApplication.CH_PROACTIVE)
            .setContentTitle("Aira")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_aira)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val nm = androidx.core.app.NotificationManagerCompat.from(this)
        if (nm.areNotificationsEnabled()) {
            try { nm.notify(Random.nextInt(1000, Int.MAX_VALUE), notif) } catch (_: Throwable) {}
        }
    }

    companion object {
        const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, BackgroundAssistantService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BackgroundAssistantService::class.java))
        }
    }
}