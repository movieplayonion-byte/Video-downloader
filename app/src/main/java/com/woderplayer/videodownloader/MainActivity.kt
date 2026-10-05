package com.woderplayer.videodownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var playBtn: Button
    private lateinit var downloadBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var formatGroup: RadioGroup
    private val CURRENT_VERSION_CODE = 30L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Notification permission Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Background Raw Auto-Update Check
        lifecycleScope.launch(Dispatchers.IO) {
            UpdateManager.checkUpdateStatus(
                context = this@MainActivity,
                currentVersionCode = CURRENT_VERSION_CODE,
                onUpdateFound = { info ->
                    UpdateManager.showMandatoryDialog(this@MainActivity, info)
                },
                onNoUpdate = {}
            )
        }

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        urlInput = findViewById(R.id.url_input)
        playBtn = findViewById(R.id.play_btn)
        downloadBtn = findViewById(R.id.download_btn)
        progressBar = findViewById(R.id.progress_bar)
        formatGroup = findViewById(R.id.format_group)

        playBtn.setOnClickListener { processMedia(isDownload = false) }
        downloadBtn.setOnClickListener { processMedia(isDownload = true) }
    }

    private fun processMedia(isDownload: Boolean) {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
            return
        }

        val isAudio = formatGroup.checkedRadioButtonId == R.id.radio_mp3
        setLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            val result = VideoExtractor.resolveStreamUrl(url, isAudio, "720")

            withContext(Dispatchers.Main) {
                setLoading(false)
                if (result.startsWith("http://") || result.startsWith("https://")) {
                    if (isDownload) {
                        val fileName = if (isAudio) "audio_${System.currentTimeMillis()}.m4a" else "video_${System.currentTimeMillis()}.mp4"
                        DownloadService.startDownload(this@MainActivity, result, fileName, isAudio)
                    } else {
                        val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                            putExtra("video_url", result)
                        }
                        startActivity(intent)
                    }
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Diagnostic Output")
                        .setMessage(result)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        playBtn.isEnabled = !isLoading
        downloadBtn.isEnabled = !isLoading
    }
}
