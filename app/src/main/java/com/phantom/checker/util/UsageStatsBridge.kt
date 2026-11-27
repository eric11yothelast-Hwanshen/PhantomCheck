package com.phantom.checker.util

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * #364 App 使用情況存取（UsageStatsManager）統一橋接器：
 * - 權限檢查（AppOpsManager + PackageManager fallback）
 * - 多廠牌安全授權入口（小米 HyperOS/MIUI、Samsung One UI、OPPO ColorOS、vivo OriginOS/FuntouchOS 皆有 fallback）
 * - 真實前景時間與啟動次數（UsageEvents ACTIVITY_RESUMED）聚合，嚴禁回傳任何推估或編造數字
 */
object UsageStatsBridge {

    data class AppUsageEntry(
        val packageName: String,
        val appLabel: String,
        val foregroundMs: Long,
        val launchCount: Int
    )

    data class UsageSummaryResult(
        val granted: Boolean,
        val days: Int,
        val periodLabel: String,
        val totalForegroundMs: Long,
        val totalLaunches: Int,
        val topApps: List<AppUsageEntry>,
        val markdownTable: String,
        val promptContext: String
    )

    fun hasUsageStatsPermission(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                ctx.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                ctx.packageName
            )
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ctx.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /**
     * 多廠牌安全開啟「應用使用情況存取」設定頁：
     * 1. Android 10+ 優先嘗試帶 package URI 的 ACTION_USAGE_ACCESS_SETTINGS（Pixel / Samsung One UI 直達本 App）
     * 2. 小米 MIUI/HyperOS、OPPO ColorOS、vivo OriginOS 常不支援 package URI，立即 fallback 至標準 ACTION_USAGE_ACCESS_SETTINGS
     * 3. 最終 fallback 至 ACTION_SECURITY_SETTINGS → ACTION_APPLICATION_DETAILS_SETTINGS
     */
    fun openUsageAccessSettings(ctx: Context): Boolean {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(
                    Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS,
                        Uri.parse("package:${ctx.packageName}")
                    )
                )
            }
            add(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            add(Intent(Settings.ACTION_SECURITY_SETTINGS))
            add(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${ctx.packageName}")
                )
            )
        }
        for (intent in candidates) {
            val resolved = try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.resolveActivity(ctx.packageManager) != null
            } catch (_: Exception) {
                false
            }
            if (resolved) {
                try {
                    ctx.startActivity(intent)
                    return true
                } catch (_: Exception) {
                }
            }
        }
        return false
    }

    suspend fun queryUsageSummary(
        ctx: Context,
        days: Int = 1,
        top: Int = 8
    ): UsageSummaryResult = withContext(Dispatchers.IO) {
        val safeDays = days.coerceIn(1, 14)
        val safeTop = top.coerceIn(1, 20)
        if (!hasUsageStatsPermission(ctx)) {
            val msg = "App 使用情況存取權限尚未授權，無法查詢真實使用記錄（請前往「權限中心」開啟「應用使用情況存取」）。"
            return@withContext UsageSummaryResult(
                granted = false,
                days = safeDays,
                periodLabel = if (safeDays == 1) "今日" else "近 $safeDays 天",
                totalForegroundMs = 0L,
                totalLaunches = 0,
                topApps = emptyList(),
                markdownTable = msg,
                promptContext = msg
            )
        }

        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext UsageSummaryResult(
                granted = false,
                days = safeDays,
                periodLabel = if (safeDays == 1) "今日" else "近 $safeDays 天",
                totalForegroundMs = 0L,
                totalLaunches = 0,
                topApps = emptyList(),
                markdownTable = "系統不支援 UsageStatsManager，無法查詢 App 使用情況。",
                promptContext = "系統不支援 UsageStatsManager，無法查詢 App 使用情況。"
            )

        val nowMs = System.currentTimeMillis()
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (safeDays > 1) {
                add(Calendar.DAY_OF_YEAR, -(safeDays - 1))
            }
        }
        val startMs = startCal.timeInMillis
        val dateFmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        val periodLabel = if (safeDays == 1) {
            "今日（${dateFmt.format(Date(startMs))} ～ ${dateFmt.format(Date(nowMs))}）"
        } else {
            "近 $safeDays 天（${dateFmt.format(Date(startMs))} ～ ${dateFmt.format(Date(nowMs))}）"
        }

        val foregroundMap = mutableMapOf<String, Long>()
        val launchMap = mutableMapOf<String, Int>()

        // 1. 以 UsageEvents 計算精確啟動次數（ACTIVITY_RESUMED / MOVE_TO_FOREGROUND）與補充前景時間
        try {
            val events = usm.queryEvents(startMs, nowMs)
            val event = UsageEvents.Event()
            val activeStartByPkg = mutableMapOf<String, Long>()
            var lastResumedPkg: String? = null
            var lastResumedTs = 0L
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                @Suppress("DEPRECATION")
                val isResume = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                @Suppress("DEPRECATION")
                val isPause = event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
                if (isResume) {
                    // 避免同 App 內連續切換 Activity 過度膨脹啟動數：不同 App 或間隔 > 5s 計一次啟動
                    if (pkg != lastResumedPkg || (event.timeStamp - lastResumedTs) > 5_000L) {
                        launchMap[pkg] = (launchMap[pkg] ?: 0) + 1
                    }
                    lastResumedPkg = pkg
                    lastResumedTs = event.timeStamp
                    activeStartByPkg[pkg] = event.timeStamp
                } else if (isPause) {
                    val began = activeStartByPkg.remove(pkg)
                    if (began != null && event.timeStamp > began) {
                        val delta = (event.timeStamp - began).coerceAtMost(4L * 60L * 60L * 1000L)
                        foregroundMap[pkg] = (foregroundMap[pkg] ?: 0L) + delta
                    }
                }
            }
            // 結束時補算仍在前景中（尚未收到 PAUSED）之 App 的區間時長
            for ((pkg, began) in activeStartByPkg) {
                if (nowMs > began) {
                    val delta = (nowMs - began).coerceAtMost(4L * 60L * 60L * 1000L)
                    foregroundMap[pkg] = (foregroundMap[pkg] ?: 0L) + delta
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }

        // 2. 多日 INTERVAL_DAILY bucket 先依 packageName 加總，再與 UsageEvents 取較大者避免漏計
        try {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, nowMs)
            val dailyBucketTotals = mutableMapOf<String, Long>()
            stats?.forEach { st ->
                val pkg = st.packageName ?: return@forEach
                val fg = st.totalTimeInForeground
                if (fg > 0L) {
                    dailyBucketTotals[pkg] = (dailyBucketTotals[pkg] ?: 0L) + fg
                }
            }
            for ((pkg, totalBucketFg) in dailyBucketTotals) {
                val cur = foregroundMap[pkg] ?: 0L
                if (totalBucketFg > cur) {
                    foregroundMap[pkg] = totalBucketFg
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }

        val pm = ctx.packageManager
        val allPkgs = (foregroundMap.keys + launchMap.keys).filter { pkg ->
            pkg != "android" && pkg != "com.android.systemui"
        }

        data class RawUsageEntry(
            val packageName: String,
            val foregroundMs: Long,
            val launchCount: Int
        )

        val rawEntries = allPkgs.mapNotNull { pkg ->
            val fg = foregroundMap[pkg] ?: 0L
            val launches = launchMap[pkg] ?: 0
            if (fg <= 0L && launches <= 0) return@mapNotNull null
            RawUsageEntry(packageName = pkg, foregroundMs = fg, launchCount = launches)
        }.sortedWith(
            compareByDescending<RawUsageEntry> { it.foregroundMs }
                .thenByDescending { it.launchCount }
        )

        val totalFg = rawEntries.sumOf { it.foregroundMs }
        val totalLaunches = rawEntries.sumOf { it.launchCount }
        // 先排序截斷至 safeTop 再查詢 PackageManager 解析 appLabel，避免多餘 Binder IPC
        val topEntries = rawEntries.take(safeTop).map { raw ->
            val label = try {
                val ai = pm.getApplicationInfo(raw.packageName, 0)
                pm.getApplicationLabel(ai).toString().ifBlank { raw.packageName }
            } catch (_: Exception) {
                raw.packageName
            }
            AppUsageEntry(
                packageName = raw.packageName,
                appLabel = label,
                foregroundMs = raw.foregroundMs,
                launchCount = raw.launchCount
            )
        }

        val table = buildString {
            appendLine("**App 使用情況（真實系統數據）**")
            appendLine("- 資料期間：$periodLabel")
            appendLine("- 資料來源：系統使用情況存取（UsageStatsManager）")
            appendLine("- 總前景時間：${formatDuration(totalFg)} · 總啟動次數：$totalLaunches 次")
            if (topEntries.isEmpty()) {
                appendLine("- 此期間查無有效 App 使用記錄。")
            } else {
                appendLine()
                appendLine("| 應用程式 | 前景時間 | 啟動次數 |")
                appendLine("|---|---|---|")
                topEntries.forEach { e ->
                    val safeName = e.appLabel.replace("|", "｜")
                    appendLine("| $safeName | ${formatDuration(e.foregroundMs)} | ${e.launchCount} 次 |")
                }
            }
        }.trim()

        val promptCtx = buildString {
            appendLine("資料來源：系統使用情況存取（UsageStatsManager）")
            appendLine("期間：$periodLabel")
            appendLine("總前景時間：${formatDuration(totalFg)}，總啟動次數：$totalLaunches 次")
            if (topEntries.isEmpty()) {
                appendLine("結果：此期間查無有效 App 使用記錄（請如實告知，嚴禁編造）。")
            } else {
                topEntries.forEachIndexed { idx, e ->
                    appendLine("${idx + 1}. ${e.appLabel} (${e.packageName})：前景 ${formatDuration(e.foregroundMs)}，啟動 ${e.launchCount} 次")
                }
            }
        }.trim()

        UsageSummaryResult(
            granted = true,
            days = safeDays,
            periodLabel = periodLabel,
            totalForegroundMs = totalFg,
            totalLaunches = totalLaunches,
            topApps = topEntries,
            markdownTable = table,
            promptContext = promptCtx
        )
    }

    fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return when {
            hours > 0L -> "${hours} 小時 ${mins} 分"
            mins > 0L -> "${mins} 分 ${secs} 秒"
            else -> "${secs} 秒"
        }
    }
}
