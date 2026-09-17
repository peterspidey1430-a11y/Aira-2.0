package com.aira.agent.web

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Open URLs in the user's default browser.
 */
class BrowserController(private val context: Context) {

    fun open(url: String): Boolean {
        val safe = if (url.startsWith("http")) url else "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safe)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { context.startActivity(intent); true } catch (e: Throwable) { false }
    }
}