package com.woderplayer.videodownloader

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.woderplayer.videodownloader.databinding.DialogShareDownloadBinding
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class ShareDialogActivity : AppCompatActivity() {

    private lateinit var binding: DialogShareDownloadBinding
    private var extractedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogShareDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleShareIntent(intent)

        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.btnStartDownload.setOnClickListener {
            val url = extractedUrl
            if (!url.isNullOrEmpty()) {
                val isAudio = binding.rbMp3.isChecked
                val quality = when {
                    binding.rb1080p.isChecked -> "1080"
                    binding.rb720p.isChecked -> "720"
                    else -> "480"
                }

                binding.btnStartDownload.isEnabled = false
                binding.btnStartDownload.text = "Fetching Stream..."
                Toast.makeText(this, "Extracting real video stream...", Toast.LENGTH_SHORT).show()

                lifecycleScope.launch {
                    val streamUrl = VideoExtractor.resolveStreamUrl(url, isAudio, quality)
                    if (!streamUrl.isNullOrEmpty()) {
                        startBackgroundDownload(streamUrl, isAudio)
                        finish()
                    } else {
                        Toast.makeText(this@ShareDialogActivity, "Failed to extract video stream. Try another link.", Toast.LENGTH_LONG).show()
                        binding.btnStartDownload.isEnabled = true
                        binding.btnStartDownload.text = "Download Now"
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun handleShareIntent(intent: Intent) {
        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val url = extractUrlFromString(sharedText)
            if (url != null) {
                extractedUrl = url
                binding.tvSharedLink.text = url
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
}
