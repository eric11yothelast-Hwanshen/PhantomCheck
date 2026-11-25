package com.phantom.checker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.NotificationCollectorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * #503 事件驅動＋低耗電混合式小工具即時更新協調器：
 * - 資料變動（新通知、已讀同步、待辦／記事新增編輯刪除）時以 1.5 秒防抖觸發，確保 5 秒內刷新所有已放置之小工具。
 * - 僅針對有實際放置（appWidgetIds 非空）之小工具觸發更新，避免無效喚醒。
 * - 搭配 30 分鐘 WorkManager 週期兜底（遵守 Android ≥15 分鐘最低間隔規範），兼顧即時性與省電。
 */
object WidgetUpdater {
    private const val DEBOUNCE_MS = 1_500L
    private const val PERIODIC_WORK_NAME = "phantom_widget_periodic_refresh"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var debounceJob: Job? = null

    fun scheduleRefresh(ctx: Context, immediate: Boolean = false) {
        val appCtx = ctx.applicationContext
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            refreshAllActiveWidgets(appCtx)
        }
    }

    fun ensurePeriodicBackupScheduled(ctx: Context) {
        runCatching {
            val req = PeriodicWorkRequestBuilder<WidgetPeriodicRefreshWorker>(30L, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }

    suspend fun refreshAllActiveWidgets(appCtx: Context) {
        val mgr = runCatching { AppWidgetManager.getInstance(appCtx) }.getOrNull() ?: return
        val db = runCatching { AppDatabaseHolder.db(appCtx) }.getOrNull() ?: return

        val hasStats = hasActiveIds(mgr, appCtx, StatsWidgetProvider::class.java)
        val hasStatsMini = hasActiveIds(mgr, appCtx, StatsMiniWidgetProvider::class.java)
        val hasStatsDetail = hasActiveIds(mgr, appCtx, StatsDetailWidgetProvider::class.java)

        if (hasStats || hasStatsMini || hasStatsDetail) {
            runCatching {
                val startOfDay = NotificationCollectorService.startOfTodayMillis()
                val snap = db.messageDao().statsSnapshot(startOfDay)
                if (hasStats) {
                    StatsWidgetProvider.updateAllIfChanged(appCtx, snap.today, snap.unread, snap.scam)
                }
                if (hasStatsMini) {
                    StatsMiniWidgetProvider.updateAllIfChanged(appCtx, snap.today, snap.scam)
                }
                if (hasStatsDetail) {
                    StatsDetailWidgetProvider.updateAllIfChanged(
                        appCtx,
                        snap.today,
                        snap.unread,
                        snap.scam,
                        snap.recentPackages()
                    )
                }
            }
        }

        if (hasActiveIds(mgr, appCtx, RiskActionWidgetProvider::class.java)) {
            runCatching { RiskActionWidgetProvider.updateAllIfChanged(appCtx) }
        }
        if (hasActiveIds(mgr, appCtx, ReminderWidgetProvider::class.java)) {
            runCatching { ReminderWidgetProvider.updateAllIfChanged(appCtx) }
        }
        if (hasActiveIds(mgr, appCtx, NotesWidgetProvider::class.java)) {
            runCatching { NotesWidgetProvider.updateAllIfChanged(appCtx) }
        }
        if (hasActiveIds(mgr, appCtx, AiSummaryWidgetProvider::class.java)) {
            runCatching { AiSummaryWidgetProvider.updateAllIfChanged(appCtx) }
        }

        // Broadcast update for remaining providers if actively placed on home screen
        listOf(
            TodoTodayWidgetProvider::class.java,
            GoalProgressWidgetProvider::class.java,
            QuickNoteWidgetProvider::class.java,
            HealthSnapWidgetProvider::class.java
        ).forEach { cls ->
            val ids = runCatching { mgr.getAppWidgetIds(ComponentName(appCtx, cls)) }.getOrNull()
            if (ids != null && ids.isNotEmpty()) {
                runCatching {
                    val intent = Intent(appCtx, cls).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    appCtx.sendBroadcast(intent)
                }
            }
        }
    }

    private fun hasActiveIds(mgr: AppWidgetManager, ctx: Context, cls: Class<*>): Boolean =
        runCatching { mgr.getAppWidgetIds(ComponentName(ctx, cls)).isNotEmpty() }.getOrDefault(false)
}

class WidgetPeriodicRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        runCatching { WidgetUpdater.refreshAllActiveWidgets(applicationContext) }
        return Result.success()
    }
}
