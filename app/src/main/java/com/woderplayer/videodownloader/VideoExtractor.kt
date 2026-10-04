package com.woderplayer.videodownloader

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VideoExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    fun resolveStreamUrl(webUrl: String, isAudioOnly: Boolean, quality: String): String? {
        val lower = webUrl.lowercase()
        if (lower.endsWith(".mp4") || lower.endsWith(".mp3") || lower.endsWith(".mkv") || lower.endsWith(".m3u8")) {
            return webUrl
        }

        return try {
            val endpoint = "https://api.cobalt.tools/"
            val jsonPayload = if (isAudioOnly) {
                "{\"url\":\"$webUrl\",\"downloadMode\":\"audio\",\"audioFormat\":\"mp3\"}"
            } else {
                "{\"url\":\"$webUrl\",\"videoQuality\":\"$quality\"}"
            }

            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val resString = response.body?.string() ?: return null
                
                // Parse direct media URL from JSON response
                val pattern = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"")
                val matcher = pattern.matcher(resString)
                if (matcher.find()) {
                    return matcher.group(1)?.replace("\\/", "/")
                }
                
                val audioPattern = Pattern.compile("\"audio\"\\s*:\\s*\"([^\"]+)\"")
                val audioMatcher = audioPattern.matcher(resString)
                if (audioMatcher.find()) {
                    return audioMatcher.group(1)?.replace("\\/", "/")
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
