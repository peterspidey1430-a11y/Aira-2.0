package com.aira.agent.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/**
 * YouTube integration via official deep links / intents. We do NOT
 * scrape or call private APIs. The user types "play X" and we send
 * them to the YouTube app's search results with the query pre-filled.
 */
class YouTubeController(private val context: Context) {

    fun openApp(): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent); true
        } catch (e: Throwable) { false }
    }

    fun searchAndPlay(query: String): Boolean {
        val q = URLEncoder.encode(query, "UTF-8")
        // Try YouTube app first; fall back to browser.
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://results?search_query=$q")).apply {
                `package` = "com.google.android.youtube"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent); true
        } catch (e: Throwable) {
            try {
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com/results?search_query=$q")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(web); true
            } catch (e2: Throwable) { false }
        }
    }

    fun play(videoId: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://$videoId")).apply {
                `package` = "com.google.android.youtube"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent); true
        } catch (e: Throwable) { false }
    }
}