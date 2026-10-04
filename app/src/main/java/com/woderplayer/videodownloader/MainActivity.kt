package com.woderplayer.videodownloader

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val videoNames = ArrayList<String>()
    private val videoUris = ArrayList<Uri>()
    private lateinit var adapter: ArrayAdapter<String>
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var etVideoUrl: EditText
    private lateinit var btnPlayOnline: Button
    private lateinit var btnDownload: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var lvDownloadedVideos: ListView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        loadDownloadedVideos()
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == intent?.action) {
                progressBar.visibility = View.GONE
                tvStatus.text = "Download Finished!"
                Toast.makeText(this@MainActivity, "Video downloaded successfully!", Toast.LENGTH_SHORT).show()
                lvDownloadedVideos.postDelayed({ loadDownloadedVideos() }, 1500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etVideoUrl = findViewById(R.id.etVideoUrl)
        btnPlayOnline = findViewById(R.id.btnPlayOnline)
        btnDownload = findViewById(R.id.btnDownload)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        lvDownloadedVideos = findViewById(R.id.lvDownloadedVideos)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, videoNames)
        lvDownloadedVideos.adapter = adapter

        requestAppPermissions()

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }

        // Play Online Button
        btnPlayOnline.setOnClickListener {
            val url = etVideoUrl.text.toString().trim()
            if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                progressBar.visibility = View.VISIBLE
                tvStatus.text = "Resolving video stream..."
                
                executor.execute {
                    val streamUrl = VideoExtractor.resolveStreamUrl(url, false, "720")
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        tvStatus.text = ""
                        
                        if (!streamUrl.isNullOrEmpty()) {
                            val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                                putExtra("EXTRA_VIDEO_URL", streamUrl)
                            }
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@MainActivity, "Could not stream video. Trying direct open...", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                                putExtra("EXTRA_VIDEO_URL", url)
                            }
                            startActivity(intent)
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Please enter a valid video link", Toast.LENGTH_SHORT).show()
            }
        }

        // Download Button
        btnDownload.setOnClickListener {
            val url = etVideoUrl.text.toString().trim()
            if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                progressBar.visibility = View.VISIBLE
                tvStatus.text = "Extracting real video..."

                executor.execute {
                    val streamUrl = VideoExtractor.resolveStreamUrl(url, false, "1080")
                    runOnUiThread {
                        if (!streamUrl.isNullOrEmpty()) {
                            startVideoDownload(streamUrl)
                        } else {
                            progressBar.visibility = View.GONE
                            tvStatus.text = ""
                            Toast.makeText(this@MainActivity, "Extraction failed. Check URL.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Please enter a valid link", Toast.LENGTH_SHORT).show()
            }
        }

        // Play Downloaded Video
        lvDownloadedVideos.setOnItemClickListener { _, _, position, _ ->
            val videoUri = videoUris[position]
            val intent = Intent(this, PlayerActivity::class.java).apply {
                data = videoUri
                putExtra("EXTRA_VIDEO_URL", videoUri.toString())
            }
            startActivity(intent)
        }
    }

    private fun startVideoDownload(directStreamUrl: String) {
        try {
            tvStatus.text = "Downloading real video..."

            val fileName = "Video_${System.currentTimeMillis()}.mp4"
            val request = DownloadManager.Request(Uri.parse(directStreamUrl)).apply {
                setTitle("Downloading Video")
                setDescription(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "DownloadedVideos/$fileName")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            etVideoUrl.text?.clear()
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            tvStatus.text = ""
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDownloadedVideos() {
        videoNames.clear()
        videoUris.clear()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%DownloadedVideos%")

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                videoNames.add(name)
                videoUris.add(uri)
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        loadDownloadedVideos()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {}
    }
}
