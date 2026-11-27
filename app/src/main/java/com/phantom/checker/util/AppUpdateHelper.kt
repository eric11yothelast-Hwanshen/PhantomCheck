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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
        val releaseNotes: String = "",
        val sha256DownloadUrl: String? = null
    ) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

object AppUpdateHelper {

    const val REPO_RELEASES_URL = "https://github.com/eric11yothelast-Hwanshen/PhantomCheck/releases"
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/eric11yothelast-Hwanshen/PhantomCheck/releases/latest"
    private const val REPO_PATH_PREFIX = "/eric11yothelast-Hwanshen/PhantomCheck/"
    private const val SHA256_PREFS_NAME = "phantom_apk_sha256_cache"
    private const val KEY_SHA256_URL_PREFIX = "sha256_url_"
    private const val KEY_SHA256_HEX_PREFIX = "sha256_hex_"

    private val ALLOWED_GITHUB_DOWNLOAD_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )
    private val SHA256_HEX_REGEX = Regex("""\b([0-9a-fA-F]{64})\b""")

    private val verifiedFileCache = ConcurrentHashMap<String, Boolean>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** 驗證下載網址必須為 HTTPS 且屬於 GitHub 官方發佈網域（github.com 並限本倉庫路徑）。 */
    internal fun isAllowedGitHubDownloadUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        if (host !in ALLOWED_GITHUB_DOWNLOAD_HOSTS) return false
        if (host == "github.com") {
            val path = uri.path.orEmpty()
            if (!path.startsWith(REPO_PATH_PREFIX, ignoreCase = true)) return false
        }
        return true
    }

    internal fun parseSha256Hex(rawContent: String): String? =
        SHA256_HEX_REGEX.find(rawContent)?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT)

    private fun computeFileSha256Hex(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(8192)
            FileInputStream(file).use { fis ->
                while (true) {
                    val read = fis.read(buf)
                    if (read <= 0) break
                    md.update(buf, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256Prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(SHA256_PREFS_NAME, Context.MODE_PRIVATE)

    private fun persistSha256Url(ctx: Context, cleanVersion: String, sha256Url: String?) {
        val prefs = sha256Prefs(ctx)
        if (sha256Url != null && isAllowedGitHubDownloadUrl(sha256Url)) {
            prefs.edit().putString("$KEY_SHA256_URL_PREFIX$cleanVersion", sha256Url).apply()
        } else {
            prefs.edit().remove("$KEY_SHA256_URL_PREFIX$cleanVersion").apply()
        }
    }

    private fun fetchExpectedSha256Hex(ctx: Context, cleanVersion: String): String? {
        val prefs = sha256Prefs(ctx)
        val cachedHex = prefs.getString("$KEY_SHA256_HEX_PREFIX$cleanVersion", null)
        if (!cachedHex.isNullOrBlank() && cachedHex.length == 64) {
            return cachedHex.lowercase(Locale.ROOT)
        }
        val sha256Url = prefs.getString("$KEY_SHA256_URL_PREFIX$cleanVersion", null)
        if (sha256Url.isNullOrBlank() || !isAllowedGitHubDownloadUrl(sha256Url)) {
            return null
        }
        val request = Request.Builder()
            .url(sha256Url)
            .header("User-Agent", "PhantomCheck-Android")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                val hex = parseSha256Hex(body) ?: return null
                prefs.edit().putString("$KEY_SHA256_HEX_PREFIX$cleanVersion", hex).apply()
                hex
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 校驗已下載 APK 的 SHA-256：
     * - 若本次 Release 附有 `.sha256` 資產，強制比對並於不符時拒絕安裝。
     * - 若為尚未附 `.sha256` 的舊版 Release（無 sha256Url），誠實放行以維持相容。
     */
    internal suspend fun verifyDownloadedApkIntegrity(
        ctx: Context,
        cleanVersion: String,
        apkFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val cacheKey = "${apkFile.absolutePath}:${apkFile.length()}:${apkFile.lastModified()}"
        if (verifiedFileCache[cacheKey] == true) return@withContext true

        val prefs = sha256Prefs(ctx)
        val hasSha256Asset = !prefs.getString("$KEY_SHA256_URL_PREFIX$cleanVersion", null).isNullOrBlank() ||
            !prefs.getString("$KEY_SHA256_HEX_PREFIX$cleanVersion", null).isNullOrBlank()
        if (!hasSha256Asset) {
            return@withContext true
        }
        val expectedHex = fetchExpectedSha256Hex(ctx, cleanVersion) ?: return@withContext false
        val actualHex = computeFileSha256Hex(apkFile) ?: return@withContext false
        val matched = MessageDigest.isEqual(
            expectedHex.toByteArray(Charsets.US_ASCII),
            actualHex.toByteArray(Charsets.US_ASCII)
        )
        if (matched) {
            verifiedFileCache[cacheKey] = true
        }
        matched
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
                        403, 429 -> com.phantom.checker.PhantomApplication.str(R.string.update_s_77657f)
                        404 -> com.phantom.checker.PhantomApplication.str(R.string.update_s_f552e8)
                        else -> com.phantom.checker.PhantomApplication.str(R.string.update_s_b791c7, code)
                    }
                    return@withContext UpdateCheckResult.Error(msg)
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext UpdateCheckResult.Error(com.phantom.checker.PhantomApplication.str(R.string.update_s_38b21c))
                }
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "").trim()
                val cleanRemote = normalizeVersion(tagName)
                if (cleanRemote.isBlank()) {
                    return@withContext UpdateCheckResult.Error(com.phantom.checker.PhantomApplication.str(R.string.update_s_6aa1c4))
                }
                val publishedRaw = json.optString("published_at", "").trim()
                val publishedDate = if (publishedRaw.length >= 10) publishedRaw.take(10) else com.phantom.checker.PhantomApplication.str(R.string.update_s_be1965)
                val rawHtmlUrl = json.optString("html_url", REPO_RELEASES_URL).ifBlank { REPO_RELEASES_URL }
                val htmlUrl = if (isAllowedGitHubDownloadUrl(rawHtmlUrl)) rawHtmlUrl else REPO_RELEASES_URL
                val releaseNotes = json.optString("body", "").trim()

                if (compareSemVer(cleanRemote, localVersion) <= 0) {
                    return@withContext UpdateCheckResult.UpToDate(
                        currentVersion = localVersion,
                        publishedDate = publishedDate,
                        releaseNotes = releaseNotes
                    )
                }

                val assets = json.optJSONArray("assets")
                var releaseApkName: String? = null
                var releaseApkUrl: String? = null
                var exactSha256Url: String? = null
                var fallbackSha256Url: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "").trim()
                        val url = asset.optString("browser_download_url", "").trim()
                            .takeIf { isAllowedGitHubDownloadUrl(it) } ?: continue
                        if (name.endsWith(".apk", ignoreCase = true) &&
                            !name.contains("debug", ignoreCase = true) &&
                            releaseApkUrl == null
                        ) {
                            releaseApkName = name
                            releaseApkUrl = url
                        }
                    }
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "").trim()
                        val url = asset.optString("browser_download_url", "").trim()
                            .takeIf { isAllowedGitHubDownloadUrl(it) } ?: continue
                        if (releaseApkName != null && name.equals("$releaseApkName.sha256", ignoreCase = true)) {
                            exactSha256Url = url
                            break
                        }
                        if (name.endsWith(".sha256", ignoreCase = true) &&
                            !name.contains("debug", ignoreCase = true) &&
                            fallbackSha256Url == null
                        ) {
                            fallbackSha256Url = url
                        }
                    }
                }
                val sha256Url = exactSha256Url ?: fallbackSha256Url
                persistSha256Url(ctx, cleanRemote, sha256Url)
                UpdateCheckResult.UpdateAvailable(
                    latestVersion = cleanRemote,
                    publishedDate = publishedDate,
                    apkDownloadUrl = releaseApkUrl,
                    htmlUrl = htmlUrl,
                    releaseNotes = releaseNotes,
                    sha256DownloadUrl = sha256Url
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SocketTimeoutException) {
            UpdateCheckResult.Error(com.phantom.checker.PhantomApplication.str(R.string.update_s_650ea3))
        } catch (_: UnknownHostException) {
            UpdateCheckResult.Error(com.phantom.checker.PhantomApplication.str(R.string.update_s_948b34))
        } catch (_: ConnectException) {
            UpdateCheckResult.Error(com.phantom.checker.PhantomApplication.str(R.string.update_s_948b34))
        } catch (e: Exception) {
            UpdateCheckResult.Error(com.phantom.checker.PhantomApplication.str(R.string.update_s_9e07c4, e.message ?: com.phantom.checker.PhantomApplication.str(R.string.common_unknown_error)))
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
     * #408 查詢目前目標版本的下載狀態（固定切換至 Dispatchers.IO）：
     * - 自動清理與 targetVersion 不符或損毀（length == 0）的舊檔
     * - 若 DownloadManager 記錄為 STATUS_SUCCESSFUL 且檔案通過 SHA-256 校驗 → ReadyToInstall
     * - 若無進行中 downloadId 但本地已存在完整且通過校驗的目標版本 APK → ReadyToInstall
     * - 若 STATUS_FAILED 或校驗失敗 → 回傳 Failed
     */
    suspend fun inspectApkDownloadState(ctx: Context, targetVersion: String): ApkDownloadState = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val cleanVer = normalizeVersion(targetVersion)
        if (cleanVer.isBlank()) return@withContext ApkDownloadState.Idle

        val localRuntimeVer = normalizeVersion(getRuntimeVersionName(appCtx))
        if (compareSemVer(cleanVer, localRuntimeVer) <= 0) {
            cleanupStaleApkDownloads(appCtx, keepVersion = null)
            return@withContext ApkDownloadState.Idle
        }

        // 先清掉其他舊版本的 APK 與過期下載記錄
        val persistedVer = normalizeVersion(PhantomPrefs.getActiveApkDownloadVersion(appCtx))
        if (persistedVer.isNotBlank() && persistedVer != cleanVer) {
            cleanupStaleApkDownloads(appCtx, keepVersion = cleanVer)
        }

        val targetFile = getApkFileForVersion(appCtx, cleanVer) ?: return@withContext ApkDownloadState.Idle
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
                                return@withContext ApkDownloadState.Downloading(
                                    downloadId = downloadId,
                                    version = cleanVer,
                                    file = targetFile,
                                    progressPercent = pct
                                )
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                if (targetFile.exists() && targetFile.length() > 0L) {
                                    if (!verifyDownloadedApkIntegrity(appCtx, cleanVer, targetFile)) {
                                        runCatching { targetFile.delete() }
                                        PhantomPrefs.clearActiveApkDownload(appCtx)
                                        return@withContext ApkDownloadState.Failed(
                                            version = cleanVer,
                                            reasonText = com.phantom.checker.PhantomApplication.str(R.string.update_s_93dea7)
                                        )
                                    }
                                    return@withContext ApkDownloadState.ReadyToInstall(
                                        downloadId = downloadId,
                                        version = cleanVer,
                                        file = targetFile
                                    )
                                } else {
                                    runCatching { targetFile.delete() }
                                    PhantomPrefs.clearActiveApkDownload(appCtx)
                                    return@withContext ApkDownloadState.Failed(
                                        version = cleanVer,
                                        reasonText = com.phantom.checker.PhantomApplication.str(R.string.update_s_93dea7)
                                    )
                                }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                runCatching { targetFile.delete() }
                                PhantomPrefs.clearActiveApkDownload(appCtx)
                                return@withContext ApkDownloadState.Failed(
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        // 若無進行中的 downloadId，檢查是否已有完整且通過校驗的同版本 APK 檔案
        if (targetFile.exists()) {
            if (targetFile.length() > 0L) {
                if (!verifyDownloadedApkIntegrity(appCtx, cleanVer, targetFile)) {
                    runCatching { targetFile.delete() }
                    PhantomPrefs.clearActiveApkDownload(appCtx)
                    return@withContext ApkDownloadState.Failed(
                        version = cleanVer,
                        reasonText = com.phantom.checker.PhantomApplication.str(R.string.update_s_93dea7)
                    )
                }
                return@withContext ApkDownloadState.ReadyToInstall(
                    downloadId = downloadId.coerceAtLeast(0L),
                    version = cleanVer,
                    file = targetFile
                )
            } else {
                runCatching { targetFile.delete() }
            }
        }
        ApkDownloadState.Idle
    }

    fun formatDownloadFailureReason(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> com.phantom.checker.PhantomApplication.str(R.string.update_s_ef2be7)
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> com.phantom.checker.PhantomApplication.str(R.string.update_s_248599)
        DownloadManager.ERROR_CANNOT_RESUME -> com.phantom.checker.PhantomApplication.str(R.string.update_s_db2afc)
        DownloadManager.ERROR_HTTP_DATA_ERROR -> com.phantom.checker.PhantomApplication.str(R.string.update_s_787b67)
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> com.phantom.checker.PhantomApplication.str(R.string.update_s_bea682)
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> com.phantom.checker.PhantomApplication.str(R.string.update_s_0a2345)
        DownloadManager.ERROR_FILE_ERROR -> com.phantom.checker.PhantomApplication.str(R.string.update_s_ebfe53)
        in 400..599 -> com.phantom.checker.PhantomApplication.str(R.string.update_s_c0343e, reason)
        else -> com.phantom.checker.PhantomApplication.str(R.string.update_s_8138a1, reason)
    }

    /**
     * #408 全域唯一排入 APK 下載：
     * - 嚴格驗證下載網址為 GitHub 官方網域，非白名單直接拒絕
     * - 若同版本已在下載中，直接沿用既有 downloadId 不重複建立
     * - 若已有舊下載或舊檔，先清理再排入新下載並持久化至 PhantomPrefs
     */
    suspend fun enqueueApkDownload(
        ctx: Context,
        version: String,
        downloadUrl: String,
        sha256DownloadUrl: String? = null
    ): Pair<Long, File>? = withContext(Dispatchers.IO) {
        if (!isAllowedGitHubDownloadUrl(downloadUrl)) {
            return@withContext null
        }
        val appCtx = ctx.applicationContext
        val cleanVer = normalizeVersion(version)
        if (!sha256DownloadUrl.isNullOrBlank() && isAllowedGitHubDownloadUrl(sha256DownloadUrl)) {
            persistSha256Url(appCtx, cleanVer, sha256DownloadUrl)
        }
        val currentState = inspectApkDownloadState(appCtx, cleanVer)
        if (currentState is ApkDownloadState.Downloading) {
            return@withContext currentState.downloadId to currentState.file
        }
        val dm = appCtx.getSystemService(DownloadManager::class.java) ?: return@withContext null
        val prevId = PhantomPrefs.getActiveApkDownloadId(appCtx)
        if (prevId > 0L) {
            runCatching { dm.remove(prevId) }
        }
        cleanupStaleApkDownloads(appCtx, keepVersion = null)

        val fileName = "app-release-$cleanVer.apk"
        val dir = appCtx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return@withContext null
        if (!dir.exists()) dir.mkdirs()
        val destFile = File(dir, fileName)
        if (destFile.exists()) {
            runCatching { destFile.delete() }
        }
        try {
            val req = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("PhantomCheck v$cleanVer")
                .setDescription(com.phantom.checker.PhantomApplication.str(R.string.update_s_a76912))
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(appCtx, Environment.DIRECTORY_DOWNLOADS, fileName)
            val id = dm.enqueue(req)
            PhantomPrefs.saveActiveApkDownload(appCtx, id, cleanVer, fileName)
            id to destFile
        } catch (e: CancellationException) {
            throw e
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
            return Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.update_s_814a6b)))
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
                    com.phantom.checker.PhantomApplication.str(R.string.update_s_ca9576),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = com.phantom.checker.PhantomApplication.str(R.string.update_s_c810ba)
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
            .setContentTitle(com.phantom.checker.PhantomApplication.str(R.string.update_s_d4cf93, update.latestVersion))
            .setContentText(com.phantom.checker.PhantomApplication.str(R.string.update_s_bda843, update.publishedDate))
            .setStyle(
                Notification.BigTextStyle().bigText(
                    com.phantom.checker.PhantomApplication.str(R.string.update_s_86fb4b, update.latestVersion, update.publishedDate)
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
