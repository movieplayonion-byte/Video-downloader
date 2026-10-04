package com.woderplayer.videodownloader

import com.chaquo.python.Python

object VideoExtractor {

    @Synchronized
    fun resolveStreamUrl(webUrl: String, isAudioOnly: Boolean, quality: String): String {
        return try {
            val py = Python.getInstance()
            
            // Module ko safely load / reload karna taaki attribute drop na ho
            val importlib = py.getModule("importlib")
            val pyModule = try {
                val mod = py.getModule("extractor")
                importlib.callAttr("reload", mod)
            } catch (e: Exception) {
                py.getModule("extractor")
            }

            val mode = if (isAudioOnly) "audio" else "video"
            val result = pyModule.callAttr("get_stream", webUrl, mode, quality)
            result?.toString() ?: "ERR_KT: Null returned from python"
        } catch (e: Exception) {
            "ERR_KT: ${e.localizedMessage ?: e.message}"
        }
    }
}
