package com.woderplayer.videodownloader

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors
import java.util.regex.Pattern

class ShareDialogActivity : AppCompatActivity() {

    private var extractedUrl: String? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_share_download)

        val tvSharedLink = findViewById<TextView>(R.id.tvSharedLink)
        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val btnStartDownload = findViewById<Button>(R.id.btnStartDownload)
        val rb1080p = findViewById<RadioButton>(R.id.rb1080p)
        val rb720p = findViewById<RadioButton>(R.id.rb720p)
        val rbMp3 = findViewById<RadioButton>(R.id.rbMp3)

        handleShareIntent(intent, tvSharedLink)

        btnCancel.setOnClickListener {
            finish()
        }

        btnStartDownload.setOnClickListener {
            val url = extractedUrl
            if (!url.isNullOrEmpty()) {
                val isAudio = rbMp3.isChecked
                val quality = when {
                    rb1080p.isChecked -> "1080"
                    rb720p.isChecked -> "720"
                    else -> "480"
                }

                btnStartDownload.isEnabled = false
                btnStartDownload.text = "Fetching Stream..."
                Toast.makeText(this, "Extracting real video stream...", Toast.LENGTH_SHORT).show()

                executor.execute {
                    val streamUrl = VideoExtractor.resolveStreamUrl(url, isAudio, quality)
                    runOnUiThread {
                        if (!streamUrl.isNullOrEmpty()) {
                            startBackgroundDownload(streamUrl, isAudio)
                            finish()
                        } else {
                            Toast.makeText(this@ShareDialogActivity, "Failed to extract video stream. Try another link.", Toast.LENGTH_LONG).show()
                            btnStartDownload.isEnabled = true
                            btnStartDownload.text = "Download Now"
                        }
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun handleShareIntent(intent: Intent, tvSharedLink: TextView) {
        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val url = extractUrlFromString(sharedText)
            if (url != null) {
                extractedUrl = url
                tvSharedLink.text = url
            } else {
                Toast.makeText(this, "No valid link found", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }
    }

    private fun extractUrlFromString(text: String): String? {
        val matcher = Pattern.compile("https?://\\S+").matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    private fun startBackgroundDownload(streamUrl: String, isAudio: Boolean) {
        try {
            val ext = if (isAudio) "mp3" else "mp4"
            val subDir = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
            val fileName = "Video_${System.currentTimeMillis()}.$ext"

            val request = DownloadManager.Request(Uri.parse(streamUrl)).apply {
                setTitle("Downloading ${if (isAudio) "Audio" else "Video"}")
                setDescription(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(subDir, "DownloadedVideos/$fileName")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "Real Video downloading in background!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
