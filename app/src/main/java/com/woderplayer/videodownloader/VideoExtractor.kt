package com.woderplayer.videodownloader

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VideoExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun resolveStreamUrl(webUrl: String, isAudioOnly: Boolean, quality: String): String? {
        if (webUrl.endsWith(".mp4", true) || 
            webUrl.endsWith(".mp3", true) || 
            webUrl.endsWith(".mkv", true) || 
            webUrl.endsWith(".m3u8", true)) {
            return webUrl
        }

        return try {
            val apiUrl = "https://api.cobalt.tools/api/json"
            val jsonPayload = if (isAudioOnly) {
                "{\"url\":\"$webUrl\",\"downloadMode\":\"audio\",\"audioFormat\":\"mp3\"}"
            } else {
                "{\"url\":\"$webUrl\",\"videoQuality\":\"$quality\"}"
            }

            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val resString = response.body?.string() ?: return null
                
                // Match url pattern safely without external json library dependencies
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
