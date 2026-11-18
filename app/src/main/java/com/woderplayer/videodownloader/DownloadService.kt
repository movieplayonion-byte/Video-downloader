package com.woderplayer.videodownloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

class DownloadService {

    companion object {
        fun startDownload(context: Context, url: String, fileName: String, isAudio: Boolean) {
            try {
                val parts = url.split("|COOKIE=", limit = 2)
                val cleanUrl = parts[0]
                val cookie = if (parts.size > 1) parts[1] else null

                val mimeType = if (isAudio) "audio/mp4" else "video/mp4"

                val request = DownloadManager.Request(Uri.parse(cleanUrl)).apply {
                    setTitle(fileName)
                    setDescription(if (isAudio) "Downloading audio..." else "Downloading video...")
                    setMimeType(mimeType)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)

                    if (cookie != null) {
                        addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        addRequestHeader("Referer", "https://www.tiktok.com/")
                        addRequestHeader("Cookie", cookie)
                    } else {
                        addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    }
                }

                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                Toast.makeText(context, "Download started! Full file will be saved in Downloads.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
