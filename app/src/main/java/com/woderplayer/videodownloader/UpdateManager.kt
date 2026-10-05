package com.woderplayer.videodownloader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
            val url = URL("$BASE_URL?t=" + System.currentTimeMillis())
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                useCaches = false
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val content = reader.readText()
                reader.close()

                val json = JSONObject(content)
                val vCode = json.getLong("versionCode")
                val vName = json.getString("versionName")
                val apkUrl = json.getString("apkUrl")
                val log = json.optString("changeLog", "Mandatory security and engine update.")

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
                checkPermissionAndInstall(activity, apk)
            } else {
                actionBtn.isEnabled = false
                exitBtn.isEnabled = false
                pBar.visibility = View.VISIBLE
                statusTxt.visibility = View.VISIBLE
                statusTxt.text = "Connecting to server..."

                CoroutineScope(Dispatchers.IO).launch {
                    downloadApkDirect(activity, info.apkUrl, "Update_v${info.versionName}.apk",
                        onProgress = { percent ->
                            CoroutineScope(Dispatchers.Main).launch {
                                pBar.isIndeterminate = false
                                pBar.progress = percent
                                statusTxt.text = "Downloading: $percent%"
                            }
                        },
                        onSuccess = { file ->
                            downloadedApk = file
                            CoroutineScope(Dispatchers.Main).launch {
                                pBar.visibility = View.GONE
                                statusTxt.text = "Download Complete!"
                                actionBtn.isEnabled = true
                                actionBtn.text = "Install Update Now"
                                checkPermissionAndInstall(activity, file)
                            }
                        },
                        onError = { errMsg ->
                            CoroutineScope(Dispatchers.Main).launch {
                                pBar.visibility = View.GONE
                                statusTxt.text = "Download Failed: $errMsg"
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

    private fun checkPermissionAndInstall(activity: Activity, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(activity, "Please allow 'Install unknown apps' permission to update", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
                return
            }
        }
        triggerSystemInstall(activity, apkFile)
    }

    private fun triggerSystemInstall(context: Context, apkFile: File) {
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

    private fun downloadApkDirect(
        context: Context,
        urlString: String,
        fileName: String,
        onProgress: (Int) -> Unit,
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
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connect()

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    currentUrl = connection.getHeaderField("Location")
                    redirects++
                    if (redirects > 5) throw Exception("Too many redirects")
                    continue
                }
                break
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned code ${connection.responseCode}")
            }

            val fileLength = connection.contentLength
            val file = File(context.getExternalFilesDir(null), fileName)
            if (file.exists()) file.delete()

            val input = connection.inputStream
            val output = FileOutputStream(file)
            val buffer = ByteArray(4096)
            var total: Long = 0
            var count: Int

            while (input.read(buffer).also { count = it } != -1) {
                total += count.toLong()
                if (fileLength > 0) {
                    val percent = (total * 100 / fileLength).toInt()
                    onProgress(percent)
                }
                output.write(buffer, 0, count)
            }

            output.flush()
            output.close()
            input.close()
            onSuccess(file)
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }

    data class ReleaseInfo(
        val versionName: String,
        val versionCode: Long,
        val changeLog: String,
        val apkUrl: String
    )
}
