package com.aira.agent.media

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Spotify integration.
 *
 * **Reality:** Spotify does not expose an Android SDK that allows
 * arbitrary "play X" without an OAuth token. We use the documented
 * `spotify:` URI scheme which opens the Spotify app at the right
 * screen. For "play a song", we send the user to Spotify's search
 * with the query pre-filled and they confirm playback.
 */
class SpotifyController(private val context: Context) {

    fun openApp(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("spotify:")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent); true
        } catch (e: Throwable) { false }
    }

    fun searchAndPlay(query: String): Boolean {
        val uri = Uri.parse("spotify:search:${Uri.encode(query)}")
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                `package` = "com.spotify.music"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent); true
        } catch (e: Throwable) {
            // Fallback to Play Store or web.
            try {
                val web = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://open.spotify.com/search/${Uri.encode(query)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(web); true
            } catch (e2: Throwable) { false }
        }
    }
}