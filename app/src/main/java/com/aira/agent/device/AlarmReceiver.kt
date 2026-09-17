package com.aira.agent.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Triggers when an internal AlarmManager alarm fires. The intent is a
 * simple wake-up; we launch the app so the user sees the alarm.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        launch?.let { context.startActivity(it) }
    }
}