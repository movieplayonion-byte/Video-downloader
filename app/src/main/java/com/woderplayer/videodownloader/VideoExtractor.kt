package com.woderplayer.videodownloader

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VideoExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    // Aapka apna personal Render backend server
    private const val API_BASE = "https://video-downloader-api-s1zd.onrender.com"

    fun resolveStreamUrl(webUrl: String, isAudioOnly: Boolean, quality: String): String? {
        val lower = webUrl.lowercase()
        if (lower.endsWith(".mp4") || lower.endsWith(".mp3") || lower.endsWith(".mkv") || lower.endsWith(".m3u8")) {
            return webUrl
        }

        return try {
            val encodedUrl = URLEncoder.encode(webUrl, "UTF-8")
            val mode = if (isAudioOnly) "audio" else "video"
            val requestUrl = "$API_BASE/extract?url=$encodedUrl&mode=$mode&quality=$quality"

            val request = Request.Builder()
                .url(requestUrl)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null

                val pattern = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"")
                val matcher = pattern.matcher(body)
                if (matcher.find()) {
                    return matcher.group(1)?.replace("\\/", "/")
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
