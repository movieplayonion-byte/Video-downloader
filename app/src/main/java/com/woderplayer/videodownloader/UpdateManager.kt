package com.woderplayer.videodownloader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val BASE_URL = "https://raw.githubusercontent.com/movieplayonion-byte/Video-downloader/main/version.json"

    suspend fun checkUpdateStatus(
        context: Context,
        onUpdateFound: (ReleaseInfo) -> Unit,
        onNoUpdate: () -> Unit
    ) {
        val currentCode = BuildConfig.VERSION_CODE.toLong()
        val info = fetchRawVersionInfo()
        withContext(Dispatchers.Main) {
            if (info != null && info.versionCode > currentCode) {
                onUpdateFound(info)
            } else {
                onNoUpdate()
            }
        }
    }

    // Overload for backward compatibility with old activity calls
    suspend fun checkUpdateStatus(
        context: Context,
        unusedCode: Long,
        onUpdateFound: (ReleaseInfo) -> Unit,
        onNoUpdate: () -> Unit
    ) {
        checkUpdateStatus(context, onUpdateFound, onNoUpdate)
    }

    private suspend fun fetchRawVersionInfo(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL?nocache=" + System.currentTimeMillis())
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val content = reader.readText()
                reader.close()

                val json = JSONObject(content)
                val vCode = json.getLong("versionCode")
                val vName = json.getString("versionName")
                val apkUrl = json.getString("apkUrl")
                val log = json.optString("changeLog", "Mandatory update is available.")

                return@withContext ReleaseInfo(vName, vCode, log, apkUrl)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    fun showMandatoryDialog(activity: Activity, info: ReleaseInfo) {
        val builder = AlertDialog.Builder(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update_lock, null)
        builder.setView(view)
        builder.setCancelable(false)

        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        val titleTxt = view.findViewById<TextView>(R.id.update_title)
        val descTxt = view.findViewById<TextView>(R.id.update_desc)
        val pBar = view.findViewById<ProgressBar>(R.id.update_progress)
        val statusTxt = view.findViewById<TextView>(R.id.update_status)
        val actionBtn = view.findViewById<Button>(R.id.update_action_btn)
        val exitBtn = view.findViewById<Button>(R.id.update_exit_btn)

        titleTxt.text = "Update Required (v${info.versionName})"
        descTxt.text = info.changeLog

        exitBtn.setOnClickListener {
            activity.finishAffinity()
        }

        var downloadedApk: File? = null

        actionBtn.setOnClickListener {
            val apk = downloadedApk
            if (apk != null && apk.exists()) {
                handleInstallRequest(activity, apk)
            } else {
                actionBtn.isEnabled = false
                exitBtn.isEnabled = false
                pBar.visibility = View.VISIBLE
                statusTxt.visibility = View.VISIBLE
                statusTxt.text = "Connecting..."

                CoroutineScope(Dispatchers.IO).launch {
                    downloadApkDirect(activity, info.apkUrl, "Update_v${info.versionName}.apk",
                        onProgress = { percent, currentMB, totalMB ->
                            CoroutineScope(Dispatchers.Main).launch {
                                pBar.isIndeterminate = false
                                pBar.progress = percent
                                statusTxt.text = "Downloading: $percent% ($currentMB MB / $totalMB MB)"
                            }
                        },
                        onSuccess = { file ->
                            downloadedApk = file
                            CoroutineScope(Dispatchers.Main).launch {
                                pBar.visibility = View.GONE
                                statusTxt.text = "Download Complete! Ready to install."
                                actionBtn.isEnabled = true
                                actionBtn.text = "Install Update Now"
                                handleInstallRequest(activity, file)
                            }
                        },
                        onError = { errMsg ->
                            CoroutineScope(Dispatchers.Main).launch {
                                pBar.visibility = View.GONE
                                statusTxt.text = "Error: $errMsg"
                                actionBtn.isEnabled = true
                                exitBtn.isEnabled = true
                                actionBtn.text = "Retry Download"
                            }
                        }
                    )
                }
            }
        }

        dialog.show()
    }

    private fun handleInstallRequest(activity: Activity, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(activity, "Allow 'Install unknown apps' permission to continue", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(intent)
                    return
                }
            }
            installApkSafely(activity, apkFile)
        } catch (e: Exception) {
            Toast.makeText(activity, "Install error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun installApkSafely(context: Context, apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadApkDirect(
        context: Context,
        urlString: String,
        fileName: String,
        onProgress: (Int, String, String) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            var currentUrl = urlString
            var connection: HttpURLConnection
            var redirects = 0

            while (true) {
                val u = URL(currentUrl)
                connection = u.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 12000
                connection.readTimeout = 20000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                connection.connect()

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    currentUrl = connection.getHeaderField("Location")
                    redirects++
                    if (redirects > 6) throw Exception("Too many redirects")
                    continue
                }
                break
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}")
            }

            val fileLength = connection.contentLength
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val file = File(dir, fileName)
            if (file.exists()) file.delete()

            val input = connection.inputStream
            val output = FileOutputStream(file)
            val buffer = ByteArray(8192)
            var total: Long = 0
            var count: Int

            val totalMB = String.format("%.1f", fileLength.toFloat() / (1024 * 1024))

            while (input.read(buffer).also { count = it } != -1) {
                total += count.toLong()
                val currentMB = String.format("%.1f", total.toFloat() / (1024 * 1024))
                if (fileLength > 0) {
                    val percent = (total * 100 / fileLength).toInt()
                    onProgress(percent, currentMB, totalMB)
                }
                output.write(buffer, 0, count)
            }

            output.flush()
            output.close()
            input.close()
            onSuccess(file)
        } catch (e: Exception) {
            onError(e.message ?: "Download interrupted")
        }
    }

    data class ReleaseInfo(
        val versionName: String,
        val versionCode: Long,
        val changeLog: String,
        val apkUrl: String
    )
}
