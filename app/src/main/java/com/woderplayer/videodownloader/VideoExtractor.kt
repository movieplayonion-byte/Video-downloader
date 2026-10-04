package com.woderplayer.videodownloader

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
            val jsonPayload = JSONObject().apply {
                put("url", webUrl)
                if (isAudioOnly) {
                    put("downloadMode", "audio")
                    put("audioFormat", "mp3")
                } else {
                    put("videoQuality", quality)
                }
            }

            val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val resString = response.body?.string() ?: return null
                val json = JSONObject(resString)
                
                if (json.has("url")) {
                    json.getString("url")
                } else if (json.has("audio")) {
                    json.getString("audio")
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
