package com.phantom.checker.util

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.phantom.checker.R
import com.phantom.checker.service.NotifIds
import com.phantom.checker.ui.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface UpdateCheckResult {
    data class UpToDate(
        val currentVersion: String,
        val publishedDate: String = "",
        val releaseNotes: String = ""
    ) : UpdateCheckResult
    data class UpdateAvailable(
        val latestVersion: String,
        val publishedDate: String,
        val apkDownloadUrl: String?,
        val htmlUrl: String,
        val releaseNotes: String = ""
    ) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

object AppUpdateHelper {

    const val REPO_RELEASES_URL = "https://github.com/eric11yothelast-Hwanshen/PhantomCheck/releases"
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/eric11yothelast-Hwanshen/PhantomCheck/releases/latest"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun getRuntimeVersionName(ctx: Context): String {
        return try {
            val pkgInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            pkgInfo.versionName?.trim().orEmpty().ifBlank { "0.0.0" }
        } catch (_: Exception) {
            "0.0.0"
        }
    }

    suspend fun checkLatestRelease(ctx: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val localVersion = getRuntimeVersionName(ctx)
        val request = Request.Builder()
            .url(LATEST_RELEASE_API)
            .header("User-Agent", "PhantomCheck-Android")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val code = resp.code
                    val msg = when (code) {
                        403, 429 -> "GitHub API 請求次數已達上限，請稍後再試或前往 GitHub 查看。"
                        404 -> "找不到發佈資訊，請前往 GitHub 查看。"
                        else -> "檢查更新失敗（HTTP $code），請稍後再試。"
                    }
                    return@withContext UpdateCheckResult.Error(msg)
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext UpdateCheckResult.Error("GitHub 回傳內容為空，請稍後再試。")
                }
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "").trim()
                val cleanRemote = normalizeVersion(tagName)
                if (cleanRemote.isBlank()) {
                    return@withContext UpdateCheckResult.Error("無法解析遠端版本號，請前往 GitHub 查看。")
                }
                val publishedRaw = json.optString("published_at", "").trim()
                val publishedDate = if (publishedRaw.length >= 10) publishedRaw.take(10) else "未知日期"
                val htmlUrl = json.optString("html_url", REPO_RELEASES_URL).ifBlank { REPO_RELEASES_URL }
                val releaseNotes = json.optString("body", "").trim()

                if (compareSemVer(cleanRemote, localVersion) <= 0) {
                    return@withContext UpdateCheckResult.UpToDate(
                        currentVersion = localVersion,
                        publishedDate = publishedDate,
                        releaseNotes = releaseNotes
                    )
                }

                val assets = json.optJSONArray("assets")
                var releaseApkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        val url = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() } ?: continue
                        if (name.endsWith(".apk", ignoreCase = true) &&
                            !name.contains("debug", ignoreCase = true) &&
                            releaseApkUrl == null
                        ) {
                            releaseApkUrl = url
                        }
                    }
                }
                val apkUrl = releaseApkUrl
                UpdateCheckResult.UpdateAvailable(
                    latestVersion = cleanRemote,
                    publishedDate = publishedDate,
                    apkDownloadUrl = apkUrl,
                    htmlUrl = htmlUrl,
                    releaseNotes = releaseNotes
                )
            }
        } catch (_: SocketTimeoutException) {
            UpdateCheckResult.Error("連線逾時（10 秒），請檢查網路後再試。")
        } catch (_: UnknownHostException) {
            UpdateCheckResult.Error("無法連線至 GitHub，請檢查網路連線。")
        } catch (_: ConnectException) {
            UpdateCheckResult.Error("無法連線至 GitHub，請檢查網路連線。")
        } catch (e: Exception) {
            UpdateCheckResult.Error("檢查更新失敗：${e.message ?: "未知錯誤"}")
        }
    }

    fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").substringBefore("-")

    fun compareSemVer(remote: String, local: String): Int {
        val rParts = normalizeVersion(remote).split(".").map { it.toIntOrNull() ?: 0 }
        val lParts = normalizeVersion(local).split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, lParts.size, 3)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }

    fun enqueueApkDownload(ctx: Context, version: String, downloadUrl: String): Pair<Long, File>? {
        val appCtx = ctx.applicationContext
        val dm = appCtx.getSystemService(DownloadManager::class.java) ?: return null
        val fileName = "app-release-$version.apk"
        val dir = appCtx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        if (!dir.exists()) dir.mkdirs()
        val destFile = File(dir, fileName)
        if (destFile.exists()) {
            destFile.delete()
        }
        val req = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("PhantomCheck v$version")
            .setDescription("正在下載更新 APK…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appCtx, Environment.DIRECTORY_DOWNLOADS, fileName)
        val id = dm.enqueue(req)
        return id to destFile
    }

    fun launchApkInstaller(ctx: Context, apkFile: File): Result<Unit> {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return Result.failure(IllegalStateException("更新檔不存在或已被移除，請重新下載。"))
        }
        return try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private const val UPDATE_CHANNEL_ID = "app_updates"
    const val DEFAULT_UPDATE_SNOOZE_MS = 60L * 60L * 1000L

    sealed interface AutoUpdateCheckOutcome {
        data class Available(val update: UpdateCheckResult.UpdateAvailable) : AutoUpdateCheckOutcome
        data object UpToDate : AutoUpdateCheckOutcome
        data object Skipped : AutoUpdateCheckOutcome
        data object Failed : AutoUpdateCheckOutcome
    }

    fun isNetworkAvailable(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    fun isSnoozed(ctx: Context, nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs < PhantomPrefs.getUpdateSnoozeUntilMs(ctx.applicationContext)

    /**
     * #374 更新提醒必現狀態機：
     * - 關閉開關、無網路或仍在 snooze 期間 → Skipped（不消耗進程檢查旗標）
     * - 發現新版本且未 snooze → 必發系統通知並回傳 Available（已移除 version|day 每日 gate）
     * - 檢查失敗（403/429/逾時/網路錯誤）→ 回傳 Failed（呼叫端於進程內最多重試一次）
     */
    suspend fun checkAndNotifyWithOutcome(
        ctx: Context,
        nowMs: Long = System.currentTimeMillis()
    ): AutoUpdateCheckOutcome {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.isUpdateAutoCheckEnabled(appCtx)) return AutoUpdateCheckOutcome.Skipped
        if (!isNetworkAvailable(appCtx)) return AutoUpdateCheckOutcome.Skipped
        if (isSnoozed(appCtx, nowMs)) return AutoUpdateCheckOutcome.Skipped

        return when (val res = checkLatestRelease(appCtx)) {
            is UpdateCheckResult.UpdateAvailable -> {
                postUpdateAvailableNotification(appCtx, res)
                AutoUpdateCheckOutcome.Available(res)
            }
            is UpdateCheckResult.UpToDate -> AutoUpdateCheckOutcome.UpToDate
            is UpdateCheckResult.Error -> AutoUpdateCheckOutcome.Failed
        }
    }

    /** 相容舊呼叫點：回傳 UpdateAvailable 或 null */
    suspend fun checkAndNotifyIfNeeded(ctx: Context): UpdateCheckResult.UpdateAvailable? =
        (checkAndNotifyWithOutcome(ctx) as? AutoUpdateCheckOutcome.Available)?.update

    private fun postUpdateAvailableNotification(
        ctx: Context,
        update: UpdateCheckResult.UpdateAvailable
    ) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(UPDATE_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "版本更新",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "偵測到新版本發佈時提醒；選擇稍後更新將於 snooze 間隔後再提醒"
                }
            )
        }
        val intent = Intent(ctx, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("highlight_card_key", "about")
        }
        val pi = PendingIntent.getActivity(
            ctx,
            NotifIds.UPDATE_AVAILABLE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(ctx, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_phantom)
            .setContentTitle("幻訊有新版本 v${update.latestVersion}")
            .setContentText("發佈於 ${update.publishedDate}・點擊前往下載。可至設定關閉自動檢查更新。")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "已推出新版本 v${update.latestVersion}（發佈於 ${update.publishedDate}），點擊前往設定 → 關於下載更新。可至設定關閉自動檢查更新。"
                )
            )
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NotifIds.UPDATE_AVAILABLE, notif)
        } catch (_: SecurityException) {
        }
    }
}
