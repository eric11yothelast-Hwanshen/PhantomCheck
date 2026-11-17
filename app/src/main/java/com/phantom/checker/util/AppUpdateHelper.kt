package com.phantom.checker.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed interface UpdateCheckResult {
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class UpdateAvailable(
        val latestVersion: String,
        val publishedDate: String,
        val apkDownloadUrl: String?,
        val htmlUrl: String
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
                    val msg = when (resp.code) {
                        403, 429 -> "GitHub API 請求次數已達上限，請稍後再試或前往 GitHub 查看。"
                        404 -> "找不到發佈資訊，請前往 GitHub 查看。"
                        else -> "檢查更新失敗（HTTP ${resp.code}），請稍後再試。"
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

                if (compareSemVer(cleanRemote, localVersion) <= 0) {
                    return@withContext UpdateCheckResult.UpToDate(localVersion)
                }

                val assets = json.optJSONArray("assets")
                var releaseApkUrl: String? = null
                var fallbackApkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        val url = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() } ?: continue
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            if (!name.contains("debug", ignoreCase = true) && releaseApkUrl == null) {
                                releaseApkUrl = url
                            } else if (fallbackApkUrl == null) {
                                fallbackApkUrl = url
                            }
                        }
                    }
                }
                val apkUrl = releaseApkUrl ?: fallbackApkUrl
                UpdateCheckResult.UpdateAvailable(
                    latestVersion = cleanRemote,
                    publishedDate = publishedDate,
                    apkDownloadUrl = apkUrl,
                    htmlUrl = htmlUrl
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
}
