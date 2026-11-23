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

    sealed interface ApkDownloadState {
        data object Idle : ApkDownloadState
        data class Downloading(
            val downloadId: Long,
            val version: String,
            val file: File,
            val progressPercent: Int? = null
        ) : ApkDownloadState
        data class ReadyToInstall(
            val downloadId: Long,
            val version: String,
            val file: File
        ) : ApkDownloadState
        data class Failed(
            val version: String,
            val reasonText: String
        ) : ApkDownloadState
    }

    fun getApkFileForVersion(ctx: Context, version: String): File? {
        val dir = ctx.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "app-release-${normalizeVersion(version)}.apk")
    }

    /** #408 清理非目標版本或長度為 0 的舊 APK 檔案，以及取消舊的未完成下載 */
    fun cleanupStaleApkDownloads(ctx: Context, keepVersion: String? = null) {
        val appCtx = ctx.applicationContext
        val cleanKeep = keepVersion?.let { normalizeVersion(it) }
        val dir = appCtx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFileName = cleanKeep?.takeIf { it.isNotBlank() }?.let { "app-release-$it.apk" }
        dir?.listFiles()?.forEach { f ->
            if (f.name.startsWith("app-release-") && f.name.endsWith(".apk")) {
                if (targetFileName == null || f.name != targetFileName || f.length() <= 0L) {
                    runCatching { f.delete() }
                }
            }
        }
        val persistedVer = PhantomPrefs.getActiveApkDownloadVersion(appCtx)
        val persistedId = PhantomPrefs.getActiveApkDownloadId(appCtx)
        if (persistedId > 0L && (cleanKeep == null || (persistedVer.isNotBlank() && normalizeVersion(persistedVer) != cleanKeep))) {
            runCatching {
                appCtx.getSystemService(DownloadManager::class.java)?.remove(persistedId)
            }
            PhantomPrefs.clearActiveApkDownload(appCtx)
        }
    }

    /**
     * #408 查詢目前目標版本的下載狀態（全程可於 Dispatchers.IO 呼叫）：
     * - 自動清理與 targetVersion 不符或損毀（length == 0）的舊檔
     * - 若 DownloadManager 記錄為 STATUS_SUCCESSFUL 且檔案有效 → ReadyToInstall
     * - 若無進行中 downloadId 但本地已存在完整目標版本 APK → ReadyToInstall（全域唯一，不再重下）
     * - 若 STATUS_FAILED → 解析 COLUMN_REASON 為繁體中文原因並回傳 Failed
     */
    fun inspectApkDownloadState(ctx: Context, targetVersion: String): ApkDownloadState {
        val appCtx = ctx.applicationContext
        val cleanVer = normalizeVersion(targetVersion)
        if (cleanVer.isBlank()) return ApkDownloadState.Idle

        val localRuntimeVer = normalizeVersion(getRuntimeVersionName(appCtx))
        if (compareSemVer(cleanVer, localRuntimeVer) <= 0) {
            cleanupStaleApkDownloads(appCtx, keepVersion = null)
            return ApkDownloadState.Idle
        }

        // 先清掉其他舊版本的 APK 與過期下載記錄
        val persistedVer = normalizeVersion(PhantomPrefs.getActiveApkDownloadVersion(appCtx))
        if (persistedVer.isNotBlank() && persistedVer != cleanVer) {
            cleanupStaleApkDownloads(appCtx, keepVersion = cleanVer)
        }

        val targetFile = getApkFileForVersion(appCtx, cleanVer) ?: return ApkDownloadState.Idle
        val dm = appCtx.getSystemService(DownloadManager::class.java)
        val downloadId = PhantomPrefs.getActiveApkDownloadId(appCtx)

        if (downloadId > 0L && dm != null) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            try {
                dm.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val soFarIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                        val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                        val soFar = if (soFarIdx >= 0) cursor.getLong(soFarIdx) else 0L
                        val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else -1L

                        when (status) {
                            DownloadManager.STATUS_PENDING,
                            DownloadManager.STATUS_RUNNING,
                            DownloadManager.STATUS_PAUSED -> {
                                val pct = if (total > 0L) ((soFar * 100L) / total).toInt().coerceIn(0, 99) else null
                                return ApkDownloadState.Downloading(
                                    downloadId = downloadId,
                                    version = cleanVer,
                                    file = targetFile,
                                    progressPercent = pct
                                )
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                if (targetFile.exists() && targetFile.length() > 0L) {
                                    return ApkDownloadState.ReadyToInstall(
                                        downloadId = downloadId,
                                        version = cleanVer,
                                        file = targetFile
                                    )
                                } else {
                                    runCatching { targetFile.delete() }
                                    PhantomPrefs.clearActiveApkDownload(appCtx)
                                    return ApkDownloadState.Failed(
                                        version = cleanVer,
                                        reasonText = "下載完成但更新檔不存在或損毀（0 bytes），請重新下載。"
                                    )
                                }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                runCatching { targetFile.delete() }
                                PhantomPrefs.clearActiveApkDownload(appCtx)
                                return ApkDownloadState.Failed(
                                    version = cleanVer,
                                    reasonText = formatDownloadFailureReason(reason)
                                )
                            }
                        }
                    } else {
                        // 使用者在系統下載管理員取消或清除了該筆紀錄
                        PhantomPrefs.clearActiveApkDownload(appCtx)
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 若無進行中的 downloadId，檢查是否已有完整且非空的同版本 APK 檔案
        if (targetFile.exists()) {
            if (targetFile.length() > 0L) {
                return ApkDownloadState.ReadyToInstall(
                    downloadId = downloadId.coerceAtLeast(0L),
                    version = cleanVer,
                    file = targetFile
                )
            } else {
                runCatching { targetFile.delete() }
            }
        }
        return ApkDownloadState.Idle
    }

    fun formatDownloadFailureReason(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "下載失敗：裝置儲存空間不足，請清理空間後重試。"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "下載失敗：找不到外部儲存空間。"
        DownloadManager.ERROR_CANNOT_RESUME -> "下載失敗：連線中斷且無法續傳，請重新下載。"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "下載失敗：網路資料傳輸錯誤，請檢查網路連線後重試。"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "下載失敗：重新導向次數過多。"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "下載失敗：伺服器回應異常（HTTP 錯誤），請稍後重試。"
        DownloadManager.ERROR_FILE_ERROR -> "下載失敗：檔案寫入錯誤，請重試。"
        in 400..599 -> "下載失敗（HTTP $reason），請稍後重試或前往 GitHub 下載。"
        else -> "下載失敗（代碼 $reason），請檢查網路後點擊重新下載，或前往 GitHub 下載。"
    }

    /**
     * #408 全域唯一排入 APK 下載：
     * - 若同版本已在下載中，直接沿用既有 downloadId 不重複建立
     * - 若已有舊下載或舊檔，先清理再排入新下載並持久化至 PhantomPrefs
     */
    fun enqueueApkDownload(ctx: Context, version: String, downloadUrl: String): Pair<Long, File>? {
        val appCtx = ctx.applicationContext
        val cleanVer = normalizeVersion(version)
        val currentState = inspectApkDownloadState(appCtx, cleanVer)
        if (currentState is ApkDownloadState.Downloading) {
            return currentState.downloadId to currentState.file
        }
        val dm = appCtx.getSystemService(DownloadManager::class.java) ?: return null
        val prevId = PhantomPrefs.getActiveApkDownloadId(appCtx)
        if (prevId > 0L) {
            runCatching { dm.remove(prevId) }
        }
        cleanupStaleApkDownloads(appCtx, keepVersion = null)

        val fileName = "app-release-$cleanVer.apk"
        val dir = appCtx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        if (!dir.exists()) dir.mkdirs()
        val destFile = File(dir, fileName)
        if (destFile.exists()) {
            runCatching { destFile.delete() }
        }
        return try {
            val req = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("PhantomCheck v$cleanVer")
                .setDescription("正在下載更新 APK…")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(appCtx, Environment.DIRECTORY_DOWNLOADS, fileName)
            val id = dm.enqueue(req)
            PhantomPrefs.saveActiveApkDownload(appCtx, id, cleanVer, fileName)
            id to destFile
        } catch (_: Exception) {
            null
        }
    }

    fun canRequestPackageInstallsCompat(ctx: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            runCatching { ctx.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }
    }

    /**
     * #408 Android 8+「安裝未知應用程式」一步引導（相容 HyperOS / MIUI、Samsung One UI、OPPO/realme/OnePlus ColorOS、vivo OriginOS）：
     * 優先帶 `package:<pkg>` 直達本 App 專屬開關，失敗降級無 data 之列表頁或應用程式詳情頁。
     */
    fun openUnknownAppSourcesSettings(ctx: Context) {
        val pkgUri = Uri.parse("package:${ctx.packageName}")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val directIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { ctx.startActivity(directIntent) }.isSuccess) return

            val listIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { ctx.startActivity(listIntent) }.isSuccess) return
        }
        runCatching {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
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
