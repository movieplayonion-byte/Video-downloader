package com.woderplayer.videodownloader

import com.chaquo.python.Python

object VideoExtractor {

    fun resolveStreamUrl(webUrl: String, isAudioOnly: Boolean, quality: String): String? {
        val lower = webUrl.lowercase()
        if (lower.endsWith(".mp4") || lower.endsWith(".mp3") || lower.endsWith(".mkv") || lower.endsWith(".m3u8")) {
            return webUrl
        }

        return try {
            val py = Python.getInstance()
            val pyModule = py.getModule("extractor")
            val mode = if (isAudioOnly) "audio" else "video"
            
            val result = pyModule.callAttr("get_stream", webUrl, mode, quality).toString()
            if (result.isNotBlank() && result != "None") result else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
