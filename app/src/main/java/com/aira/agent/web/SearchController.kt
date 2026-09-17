package com.aira.agent.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/**
 * Web search. We compose the right URL for the chosen engine and
 * hand it to the default browser. We do NOT scrape results inside
 * the app — Gemini already produces a textual answer; if the user
 * wants to "see for themselves", we send them to the search page.
 */
class SearchController(private val context: Context) {

    fun search(query: String, engine: String = "google"): Boolean {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = when (engine.lowercase()) {
            "duckduckgo" -> "https://duckduckgo.com/?q=$q"
            "bing" -> "https://www.bing.com/search?q=$q"
            else -> "https://www.google.com/search?q=$q"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { context.startActivity(intent); true } catch (e: Throwable) { false }
    }
}