package com.phantom.checker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.JsonParser
import com.phantom.checker.R
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.util.EncryptedByokStore
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * #339 雲端擴充 BYOK（GitHub PAT 輪詢 Build 失敗／PR 通知 ＋ Discord Webhook 測試／轉發）：
 * - 憑證全程讀取自 EncryptedByokStore（EncryptedSharedPreferences），絕不進 log/BuildConfig/repo。
 * - 通知一律 PendingIntent.getActivity 直達，絕不用 trampoline。
 */
object CloudExtensionsClient {

    private const val GITHUB_WORK_NAME = "phantom_github_monitor"
    private const val CHANNEL_CLOUD_EXT = "cloud_extensions"
    private const val REPO_ALERT_KEYS_PREFS = "phantom_github_repo_alert_keys"

    fun scheduleGitHubPolling(ctx: Context) {
        val appCtx = ctx.applicationContext
        val wm = WorkManager.getInstance(appCtx)
        val enabled = PhantomPrefs.isGitHubMonitorEnabled(appCtx) &&
            EncryptedByokStore.isGitHubConfigured(appCtx) &&
            EncryptedByokStore.getGitHubRepos(appCtx).isNotBlank()
        if (!enabled) {
            wm.cancelUniqueWork(GITHUB_WORK_NAME)
            return
        }
        val mins = PhantomPrefs.getGitHubPollIntervalMinutes(appCtx).coerceIn(15, 180).toLong()
        val req = PeriodicWorkRequestBuilder<GitHubMonitorWorker>(mins, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(GITHUB_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /** 驗證 GitHub PAT（呼叫 GET /user，不記錄任何 token） */
    suspend fun testGitHubPat(ctx: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val pat = EncryptedByokStore.getGitHubPat(ctx)
        if (pat.isBlank()) return@withContext false to com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_d70c30)
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("https://api.github.com/user").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Authorization", "Bearer $pat")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "PhantomCheck")
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val login = runCatching {
                    JsonParser.parseString(body).asJsonObject.get("login")?.asString
                }.getOrNull().orEmpty()
                true to if (login.isNotBlank()) com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_395907, login) else com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_adda8c)
            } else {
                false to com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_3b8ff2, code)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false to com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_1f6021)
        } finally {
            conn?.disconnect()
        }
    }

    /** 測試或轉發訊息至 Discord Webhook（URL 存於 EncryptedByokStore，不寫 log） */
    suspend fun sendDiscordMessage(ctx: Context, content: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val url = EncryptedByokStore.getDiscordWebhookUrl(ctx)
        if (!url.startsWith("https://")) return@withContext false to "請先輸入有效的 Discord Webhook HTTPS 網址"
        var conn: HttpURLConnection? = null
        try {
            val payload = com.google.gson.JsonObject().apply {
                addProperty("content", content.take(1500))
            }.toString()
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "PhantomCheck")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
            val code = conn.responseCode
            if (code in 200..299) {
                true to com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_05b2a7)
            } else {
                false to com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_cf550c, code)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false to com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_cb2d1b)
        } finally {
            conn?.disconnect()
        }
    }

    private fun getLastRepoKey(ctx: Context, prefix: String, repo: String): String {
        val prefs = ctx.applicationContext.getSharedPreferences(REPO_ALERT_KEYS_PREFS, Context.MODE_PRIVATE)
        return prefs.getString("${prefix}_$repo", "").orEmpty()
    }

    private fun setLastRepoKey(ctx: Context, prefix: String, repo: String, value: String) {
        ctx.applicationContext.getSharedPreferences(REPO_ALERT_KEYS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("${prefix}_$repo", value)
            .apply()
    }

    internal suspend fun pollGitHubRepos(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val pat = EncryptedByokStore.getGitHubPat(ctx)
        val repos = EncryptedByokStore.getGitHubRepos(ctx)
            .split(',', '\n', ' ')
            .map { it.trim() }
            .filter { it.contains('/') }
            .distinct()
            .take(5)
        if (pat.isBlank() || repos.isEmpty()) return@withContext true

        val alerts = mutableListOf<String>()
        var anySuccess = false
        for (repo in repos) {
            // 1. 僅檢查最新一筆 Actions run（runs[0]）是否失敗，避免後續已修復仍對舊失敗重複告警
            fetchJson(pat, "https://api.github.com/repos/$repo/actions/runs?per_page=1")?.let { body ->
                anySuccess = true
                runCatching {
                    val runs = JsonParser.parseString(body).asJsonObject.getAsJsonArray("workflow_runs")
                    val latest = if (runs != null && runs.size() > 0) runs[0].asJsonObject else null
                    val conclusion = latest?.get("conclusion")?.takeIf { !it.isJsonNull }?.asString
                    if (latest != null && conclusion == "failure") {
                        val wfName = latest.get("name")?.asString ?: "Workflow"
                        val runId = latest.get("id")?.asLong ?: 0L
                        val key = "gh_fail_${repo}_$runId"
                        val prevPerRepo = getLastRepoKey(ctx, "fail", repo)
                        if (prevPerRepo != key && PhantomPrefs.getLastGitHubAlertKey(ctx) != key) {
                            setLastRepoKey(ctx, "fail", repo, key)
                            PhantomPrefs.setLastGitHubAlertKey(ctx, key)
                            alerts.add(com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_6bca43, repo, wfName))
                        } else if (prevPerRepo != key) {
                            setLastRepoKey(ctx, "fail", repo, key)
                        }
                    }
                }
            }
            // 2. 檢查最新開啟的 PR（告警 key 以 repo 為鍵分別持久化）
            fetchJson(pat, "https://api.github.com/repos/$repo/pulls?state=open&per_page=1")?.let { body ->
                anySuccess = true
                runCatching {
                    val prs = JsonParser.parseString(body).asJsonArray
                    if (prs.size() > 0) {
                        val first = prs[0].asJsonObject
                        val prNum = first.get("number")?.asInt ?: 0
                        val title = first.get("title")?.asString?.take(40).orEmpty()
                        val key = "gh_pr_${repo}_$prNum"
                        val prevPerRepo = getLastRepoKey(ctx, "pr", repo)
                        if (prevPerRepo != key && PhantomPrefs.getLastGitHubPrKey(ctx) != key) {
                            setLastRepoKey(ctx, "pr", repo, key)
                            PhantomPrefs.setLastGitHubPrKey(ctx, key)
                            alerts.add("🔀 [$repo] PR #$prNum：$title")
                        } else if (prevPerRepo != key) {
                            setLastRepoKey(ctx, "pr", repo, key)
                        }
                    }
                }
            }
        }
        if (alerts.isNotEmpty()) {
            postCloudAlert(ctx, com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_641a53), alerts.joinToString("\n"))
        }
        anySuccess
    }

    private fun fetchJson(pat: String, urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Authorization", "Bearer $pat")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "PhantomCheck")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    internal fun postCloudAlert(ctx: Context, title: String, body: String) {
        if (!ProactiveEngine.areNotificationsEnabled(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CLOUD_EXT, com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_2ec0e4), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pi = PendingIntent.getActivity(
            ctx,
            NotifIds.GITHUB_ALERT,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_CLOUD_EXT)
            .setSmallIcon(R.drawable.ic_stat_phantom)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(NotifIds.GITHUB_ALERT, notif)
        } catch (_: SecurityException) {
        }
    }
}

class GitHubMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("phantom_github_health", Context.MODE_PRIVATE)
        return try {
            val ok = CloudExtensionsClient.pollGitHubRepos(applicationContext)
            PhantomPrefs.setLinkedAppLastSyncAt(applicationContext, "github", System.currentTimeMillis())
            if (ok) {
                prefs.edit().putInt("consecutive_failures", 0).apply()
                PhantomPrefs.setLinkedAppLastError(applicationContext, "github", "")
            } else {
                val fails = prefs.getInt("consecutive_failures", 0) + 1
                prefs.edit().putInt("consecutive_failures", fails).apply()
                PhantomPrefs.setLinkedAppLastError(applicationContext, "github", "HTTP/網路輪詢失敗（連續 $fails 次）")
                if (fails == 3) {
                    android.util.Log.w("PhantomCloudExt", "GitHub monitor consecutive failures reached 3")
                    CloudExtensionsClient.postCloudAlert(
                        applicationContext,
                        com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_388df5),
                        com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_ff1595)
                    )
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val fails = prefs.getInt("consecutive_failures", 0) + 1
            prefs.edit().putInt("consecutive_failures", fails).apply()
            if (fails <= 3) {
                android.util.Log.w("PhantomCloudExt", "GitHub monitor worker failed (#$fails): ${e.javaClass.simpleName}")
            }
            PhantomPrefs.setLinkedAppLastSyncAt(applicationContext, "github", System.currentTimeMillis())
            PhantomPrefs.setLinkedAppLastError(applicationContext, "github", e.javaClass.simpleName)
            if (fails == 3) {
                CloudExtensionsClient.postCloudAlert(
                    applicationContext,
                    com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_388df5),
                    com.phantom.checker.PhantomApplication.str(R.string.notif_github_s_9c579c, e.javaClass.simpleName)
                )
            }
            Result.success()
        }
    }
}
