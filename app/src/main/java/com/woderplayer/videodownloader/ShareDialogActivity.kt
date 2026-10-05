package com.woderplayer.videodownloader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareDialogActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var downloadBtn: Button
    private lateinit var audioRadioBtn: RadioButton
    private var sharedUrl: String = ""
    private val currentVersionCode: Long = 29L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_dialog)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        progressBar = findViewById(R.id.share_progress)
        downloadBtn = findViewById(R.id.share_download_btn)
        audioRadioBtn = findViewById(R.id.share_audio_radio)

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val regex = "(https?://[\\S]+)".toRegex()
            sharedUrl = regex.find(text)?.value ?: text.trim()
        }

        progressBar.visibility = View.VISIBLE
        downloadBtn.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            UpdateManager.checkUpdateStatus(
                context = this@ShareDialogActivity,
                currentVersionCode = currentVersionCode,
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

        downloadBtn.setOnClickListener {
            if (sharedUrl.isNotEmpty()) {
                val isAudio = audioRadioBtn.isChecked
                progressBar.visibility = View.VISIBLE
                downloadBtn.isEnabled = false

                lifecycleScope.launch(Dispatchers.IO) {
                    val result = VideoExtractor.resolveStreamUrl(sharedUrl, isAudio, "720")
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        downloadBtn.isEnabled = true

                        if (result != null && (result.startsWith("http://") || result.startsWith("https://"))) {
                            val ext = if (isAudio) "mp3" else "mp4"
                            val fileName = "download_${System.currentTimeMillis()}.$ext"

                            DownloadService.startDownload(
                                context = this@ShareDialogActivity,
                                url = result,
                                fileName = fileName,
                                isAudio = isAudio
                            )
                            finish()
                        } else {
                            Toast.makeText(
                                this@ShareDialogActivity,
                                "Error resolving link: ${result ?: "Unknown error"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Invalid Link", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
