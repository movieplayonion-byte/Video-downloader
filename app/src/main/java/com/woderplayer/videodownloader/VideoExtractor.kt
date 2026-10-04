package com.woderplayer.videodownloader

import com.chaquo.python.Python

object VideoExtractor {

    fun resolveStreamUrl(webUrl: String, isAudioOnly: Boolean, quality: String): String {
        return try {
            val py = Python.getInstance()
            val pyModule = py.getModule("extractor")
            val mode = if (isAudioOnly) "audio" else "video"
            
            pyModule.callAttr("get_stream", webUrl, mode, quality).toString()
        } catch (e: Exception) {
            "ERR_KT: ${e.localizedMessage ?: e.message}"
        }
    }
}
