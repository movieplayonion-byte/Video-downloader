package com.woderplayer.videodownloader

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    // GitHub Raw URL (No Rate Limit, Super Fast)
    private const val RAW_VERSION_URL = "https://raw.githubusercontent.com/movieplayonion-byte/Video-downloader/main/version.json"

    // Check updates and execute callback: onMandatoryUpdate (agar update zaroori ho), onProceed (agar app latest ho)
    suspend fun checkUpdateStatus(
        context: Context,
        currentVersionCode: Long,
        onUpdateFound: (ReleaseInfo) -> Unit,
        onNoUpdate: () -> Unit
    ) {
        val info = fetchRawVersionInfo()
        withContext(Dispatchers.Main) {
            if (info != null && info.versionCode > currentVersionCode) {
                onUpdateFound(info)
            } else {
                onNoUpdate()
            }
        }
    }

    private suspend fun fetchRawVersionInfo(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(RAW_VERSION_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                useCaches = false
            }

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val content = reader.readText()
                reader.close()

                val json = JSONObject(content)
                val vCode = json.getLong("versionCode")
                val vName = json.getString("versionName")
                val apkUrl = json.getString("apkUrl")
                val log = json.optString("changeLog", "Please update to continue using the app.")

                return@withContext ReleaseInfo(vName, vCode, log, apkUrl)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    fun showMandatoryDialog(context: Context, info: ReleaseInfo, onCancelAction: () -> Unit = {}) {
        AlertDialog.Builder(context)
            .setTitle("Update Required (v${info.versionName})")
            .setMessage(info.changeLog + "\n\nYou must update to continue using Video Downloader.")
            .setCancelable(false)
            .setPositiveButton("Update Now") { _, _ ->
                downloadAndInstall(context, info.apkUrl, "app_update_v${info.versionName}.apk")
            }
            .setNegativeButton("Exit") { _, _ ->
                onCancelAction()
            }
            .show()
    }

    private fun downloadAndInstall(context: Context, downloadUrl: String, fileName: String) {
        try {
            val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (destination.exists()) destination.delete()

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("Downloading App Update...")
                setDescription("Please wait while update finishes.")
                setDestinationUri(Uri.fromFile(destination))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            }

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)
            Toast.makeText(context, "Update downloading in background...", Toast.LENGTH_SHORT).show()

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctxt: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        installApk(ctxt, destination)
                        try {
                            ctxt.unregisterReceiver(this)
                        } catch (e: Exception) {}
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    data class ReleaseInfo(
        val versionName: String,
        val versionCode: Long,
        val changeLog: String,
        val apkUrl: String
    )
}
