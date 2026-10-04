package com.woderplayer.videodownloader

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object VideoExtractor {

    private val okClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        // Initialize NewPipe Extractor with Custom OkHttp Downloader
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                val okReqBuilder = okhttp3.Request.Builder().url(request.url())
                request.headers().forEach { (k, v) ->
                    v.forEach { okReqBuilder.addHeader(k, it) }
                }
                
                if (request.dataToSend() != null) {
                    okReqBuilder.post(request.dataToSend()!!.toRequestBody())
                }

                val response = okClient.newCall(okReqBuilder.build()).execute()
                val body = response.body?.string() ?: ""
                return Response(response.code, response.message, response.headers.toMultimap(), body, response.request.url.toString())
            }
        })
    }

    fun resolveStreamUrl(webUrl: String, isAudioOnly: Boolean, quality: String): String? {
        val lower = webUrl.lowercase()
        if (lower.endsWith(".mp4") || lower.endsWith(".mp3") || lower.endsWith(".mkv") || lower.endsWith(".m3u8")) {
            return webUrl
        }

        return try {
            val service = ServiceList.YouTube
            val extractor = service.getStreamExtractor(webUrl) as YoutubeStreamExtractor
            extractor.fetchPage()

            if (isAudioOnly) {
                val audioStreams = extractor.audioStreams
                if (audioStreams.isNotEmpty()) {
                    return audioStreams.first().content
                }
            }

            // Progressive video streams (Audio + Video combined MP4)
            val videoStreams = extractor.videoStreams
            for (stream in videoStreams) {
                if (stream.resolution.contains(quality)) {
                    return stream.content
                }
            }

            // Fallback to highest available direct stream
            if (videoStreams.isNotEmpty()) {
                videoStreams.first().content
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
