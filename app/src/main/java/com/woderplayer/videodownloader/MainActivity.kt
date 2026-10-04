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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.woderplayer.videodownloader.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val videoNames = ArrayList<String>()
    private val videoUris = ArrayList<Uri>()
    private lateinit var adapter: ArrayAdapter<String>

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        loadDownloadedVideos()
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == intent?.action) {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "Download Finished!"
                Toast.makeText(this@MainActivity, "Video downloaded successfully!", Toast.LENGTH_SHORT).show()
                binding.root.postDelayed({ loadDownloadedVideos() }, 1500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, videoNames)
        binding.lvDownloadedVideos.adapter = adapter

        requestAppPermissions()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        binding.btnDownload.setOnClickListener {
            val url = binding.etVideoUrl.text.toString().trim()
            if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                startVideoDownload(url)
            } else {
                Toast.makeText(this, "Please enter a valid video link (http/https)", Toast.LENGTH_SHORT).show()
            }
        }

        binding.lvDownloadedVideos.setOnItemClickListener { _, _, position, _ ->
            val videoUri = videoUris[position]
            playVideo(videoUri)
        }
    }

    private fun startVideoDownload(url: String) {
        try {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvStatus.text = "Downloading video..."

            val fileName = "Video_${System.currentTimeMillis()}.mp4"
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("Downloading Video")
                setDescription(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "DownloadedVideos/$fileName")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            binding.etVideoUrl.text?.clear()
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            binding.tvStatus.text = ""
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playVideo(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No supported video player found", Toast.LENGTH_SHORT).show()
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
        try {
            unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {}
    }
}
