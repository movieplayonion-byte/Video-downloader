package com.woderplayer.videodownloader

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VideoExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val instances = listOf(
        "https://inv.tux.pizza",
        "https://invidious.nerdvpn.de",
        "https://yewtu.be",
        "https://invidious.jing.rocks"
    )

    fun resolveStreamUrl(webUrl: String, isAudioOnly: Boolean, quality: String): String? {
        val lower = webUrl.lowercase()
        if (lower.endsWith(".mp4") || lower.endsWith(".mp3") || lower.endsWith(".mkv") || lower.endsWith(".m3u8")) {
            return webUrl
        }

        val videoId = extractYouTubeId(webUrl) ?: return null

        for (host in instances) {
            try {
                val apiUrl = "$host/api/v1/videos/$videoId"
                val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use

                    if (isAudioOnly) {
                        val audioRegex = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"[^}]*\"container\"\\s*:\\s*\"m4a\"")
                        val m = audioRegex.matcher(body)
                        if (m.find()) return cleanUrl(m.group(1), host)
                    }

                    // Extract progressive MP4 streams
                    val streamRegex = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"[^}]*\"qualityLabel\"\\s*:\\s*\"(\\d+p)\"")
                    val m = streamRegex.matcher(body)
                    var bestUrl: String? = null
                    while (m.find()) {
                        val streamUrl = m.group(1)
                        val q = m.group(2)
                        if (q.contains(quality)) {
                            return cleanUrl(streamUrl, host)
                        }
                        if (bestUrl == null) bestUrl = streamUrl
                    }
                    if (bestUrl != null) return cleanUrl(bestUrl, host)
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun cleanUrl(raw: String?, host: String): String? {
        if (raw == null) return null
        val decoded = raw.replace("\\/", "/").replace("\\u0026", "&")
        return if (decoded.startsWith("http")) decoded else "$host$decoded"
    }

    private fun extractYouTubeId(url: String): String? {
        val p = Pattern.compile("(?:youtu\\.be\\/|youtube\\.com\\/(?:watch\\?v=|embed\\/|v\\/|shorts\\/))([a-zA-Z0-9_-]{11})")
        val m = p.matcher(url)
        return if (m.find()) m.group(1) else null
    }
}
