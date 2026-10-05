package com.woderplayer.videodownloader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class ShareDialogActivity : AppCompatActivity() {

    private var targetUrl: String = ""
    private val CURRENT_VERSION_CODE = 18L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_dialog)

        val formatGroup = findViewById<RadioGroup>(R.id.dialog_format_group)
        val downloadBtn = findViewById<Button>(R.id.dialog_download_btn)
        val cancelBtn = findViewById<Button>(R.id.dialog_cancel_btn)
        val progressBar = findViewById<ProgressBar>(R.id.dialog_progress)

        cancelBtn.setOnClickListener { finish() }

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            targetUrl = extractUrl(sharedText)
        }

        if (targetUrl.isEmpty()) {
            Toast.makeText(this, "No valid video URL detected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Mandatory version check
        progressBar.visibility = View.VISIBLE
        downloadBtn.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            UpdateManager.checkUpdateStatus(
                context = this@ShareDialogActivity,
                currentVersionCode = CURRENT_VERSION_CODE,
                onUpdateFound = { updateInfo ->
                    progressBar.visibility = View.GONE
                    UpdateManager.showMandatoryDialog(this@ShareDialogActivity, updateInfo)
                },
                onNoUpdate = {
                    progressBar.visibility = View.GONE
                    downloadBtn.isEnabled = true
                }
            )
        }

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        downloadBtn.setOnClickListener {
            val isAudio = formatGroup.checkedRadioButtonId == R.id.dialog_radio_audio
            progressBar.visibility = View.VISIBLE
            downloadBtn.isEnabled = false
            cancelBtn.isEnabled = false

            lifecycleScope.launch(Dispatchers.IO) {
                val streamUrl = VideoExtractor.resolveStreamUrl(targetUrl, isAudio, "720")

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) {
                        val fileName = if (isAudio) "audio_${System.currentTimeMillis()}.m4a" else "video_${System.currentTimeMillis()}.mp4"
                        DownloadService.startDownload(this@ShareDialogActivity, streamUrl, fileName, isAudio)
                        finish()
                    } else {
                        downloadBtn.isEnabled = true
                        cancelBtn.isEnabled = true
                        Toast.makeText(this@ShareDialogActivity, streamUrl, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun extractUrl(text: String): String {
        val pattern = Pattern.compile("https?://\\S+")
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group() else text.trim()
    }
}
