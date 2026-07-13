package com.jing.sakura.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.google.gson.JsonParser
import com.jing.sakura.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class TvUpdate(
    val version: String,
    val downloadUrl: String,
    val notes: String
)

class TvUpdateManager(private val activity: Activity) {
    private val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private var installPermissionRequested = false

    suspend fun checkForUpdate(): TvUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Aulama-Anime-TV/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val root = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            if (root.get("draft")?.asBoolean == true) return@withContext null
            val version = root.get("tag_name")?.asString.orEmpty().removePrefix("v")
            if (!isNewerVersion(version, BuildConfig.VERSION_NAME)) return@withContext null
            val asset = root.getAsJsonArray("assets")
                ?.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject }
                ?.firstOrNull { item ->
                    item.get("name")?.asString.orEmpty().endsWith(".apk", ignoreCase = true)
                }
                ?: return@withContext null
            TvUpdate(
                version = version,
                downloadUrl = asset.get("browser_download_url")?.asString.orEmpty(),
                notes = root.get("body")?.asString.orEmpty().trim().take(360)
            ).takeIf { it.downloadUrl.isNotBlank() }
        }
    }

    fun download(update: TvUpdate): Long {
        val fileName = "aulama-anime-tv-v${update.version}.apk"
        activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.resolve(fileName)
            ?.delete()
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("Aulama Anime TV ${update.version}")
            .setDescription("下載完成後即可安裝")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)
        return downloadManager.enqueue(request).also { downloadId ->
            preferences.edit().putLong(PENDING_DOWNLOAD_ID, downloadId).apply()
        }
    }

    fun handleDownloadComplete(downloadId: Long) {
        if (downloadId == preferences.getLong(PENDING_DOWNLOAD_ID, -1L)) {
            installPendingUpdate()
        }
    }

    fun installPendingUpdate(): Boolean {
        val downloadId = preferences.getLong(PENDING_DOWNLOAD_ID, -1L)
        if (downloadId < 0) return false
        val status = queryStatus(downloadId) ?: return false
        if (status != DownloadManager.STATUS_SUCCESSFUL) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            if (installPermissionRequested) return false
            installPermissionRequested = true
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
            )
            return true
        }

        installPermissionRequested = false

        val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return false
        return runCatching {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME_TYPE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            preferences.edit().remove(PENDING_DOWNLOAD_ID).apply()
            true
        }.getOrDefault(false)
    }

    private fun queryStatus(downloadId: Long): Int? {
        val cursor: Cursor = downloadManager.query(
            DownloadManager.Query().setFilterById(downloadId)
        ) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return@use null
            it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.toVersionParts()
        val localParts = local.toVersionParts()
        val maxSize = maxOf(remoteParts.size, localParts.size)
        repeat(maxSize) { index ->
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val localPart = localParts.getOrElse(index) { 0 }
            if (remotePart != localPart) return remotePart > localPart
        }
        return false
    }

    private fun String.toVersionParts(): List<Int> =
        substringBefore('-')
            .split('.')
            .map { part -> part.filter(Char::isDigit).toIntOrNull() ?: 0 }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/siumiu1968/ciyuanbox-tv/releases/latest"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val PREFERENCES = "tv_update"
        private const val PENDING_DOWNLOAD_ID = "pending_download_id"
    }
}
