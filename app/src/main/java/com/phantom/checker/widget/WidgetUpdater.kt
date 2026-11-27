package com.phantom.checker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
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

    private val stateLock = Any()
    private var latestCtx: Context? = null
    private var pendingRefresh: Boolean = false
    private var pendingImmediate: Boolean = false
    private var workerRunning: Boolean = false
    private var debounceWaitJob: Job? = null

    fun scheduleRefresh(ctx: Context, immediate: Boolean = false) {
        val appCtx = ctx.applicationContext
        val shouldStart = synchronized(stateLock) {
            latestCtx = appCtx
            pendingRefresh = true
            if (immediate) pendingImmediate = true
            if (workerRunning) {
                // 僅在等待防抖延遲期間收到 immediate 請求時提前喚醒，絕不中斷執行中的 refreshAllActiveWidgets
                if (immediate) debounceWaitJob?.cancel()
                false
            } else {
                workerRunning = true
                true
            }
        }
        if (shouldStart) {
            scope.launch { drainRefreshLoop() }
        }
    }

    private suspend fun drainRefreshLoop() {
        while (true) {
            val (ctx, isImmediate) = synchronized(stateLock) {
                if (!pendingRefresh) {
                    workerRunning = false
                    return
                }
                val c = latestCtx ?: run {
                    pendingRefresh = false
                    pendingImmediate = false
                    workerRunning = false
                    return
                }
                val imm = pendingImmediate
                pendingRefresh = false
                pendingImmediate = false
                c to imm
            }
            if (!isImmediate) {
                val waitJob = scope.launch { delay(DEBOUNCE_MS) }
                synchronized(stateLock) { debounceWaitJob = waitJob }
                try {
                    waitJob.join()
                } finally {
                    synchronized(stateLock) {
                        if (debounceWaitJob === waitJob) debounceWaitJob = null
                    }
                }
                // 防抖期間合併到的請求將由緊接著的這次刷新一次涵蓋
                synchronized(stateLock) {
                    pendingRefresh = false
                    pendingImmediate = false
                }
            }
            val targetCtx = synchronized(stateLock) { latestCtx } ?: ctx
            runCatching { refreshAllActiveWidgets(targetCtx) }
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
        if (hasActiveIds(mgr, appCtx, TodoTodayWidgetProvider::class.java)) {
            runCatching { TodoTodayWidgetProvider.refreshNow(appCtx) }
        }
        if (hasActiveIds(mgr, appCtx, GoalProgressWidgetProvider::class.java)) {
            runCatching { GoalProgressWidgetProvider.refreshNow(appCtx) }
        }
        if (hasActiveIds(mgr, appCtx, QuickNoteWidgetProvider::class.java)) {
            runCatching { QuickNoteWidgetProvider.refreshNow(appCtx) }
        }
        if (hasActiveIds(mgr, appCtx, HealthSnapWidgetProvider::class.java)) {
            runCatching { HealthSnapWidgetProvider.refreshNow(appCtx) }
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
