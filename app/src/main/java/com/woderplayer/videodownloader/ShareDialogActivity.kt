package com.woderplayer.videodownloader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShareDialogActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var downloadBtn: Button
    private lateinit var audioRadioBtn: RadioButton
    private var sharedUrl: String = ""

    private val currentVersionCode: Long
        get() = try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            24L
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_dialog)

        progressBar = findViewById(R.id.share_progress)
        downloadBtn = findViewById(R.id.share_download_btn)
        audioRadioBtn = findViewById(R.id.share_audio_radio)

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
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
                val extension = if (isAudio) "mp3" else "mp4"
                val fileName = "download_${System.currentTimeMillis()}.$extension"

                DownloadService.startDownload(
                    context = this,
                    url = sharedUrl,
                    fileName = fileName,
                    isAudio = isAudio
                )
                finish()
            } else {
                Toast.makeText(this, "Invalid Link", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
