package com.woderplayer.videodownloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun resolveStreamUrl(webUrl: String, isAudioOnly: Boolean, quality: String): String? {
        return withContext(Dispatchers.IO) {
            // Agar pehle se direct media URL hai
            if (webUrl.endsWith(".mp4", true) || 
                webUrl.endsWith(".mp3", true) || 
                webUrl.endsWith(".mkv", true) || 
                webUrl.endsWith(".m3u8", true)) {
                return@withContext webUrl
            }

            try {
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
                    val resString = response.body?.string() ?: return@withContext null
                    val json = JSONObject(resString)
                    
                    if (json.has("url")) {
                        return@withContext json.getString("url")
                    } else if (json.has("audio")) {
                        return@withContext json.getString("audio")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
    }
}
