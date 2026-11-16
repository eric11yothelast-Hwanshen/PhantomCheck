package com.phantom.checker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.phantom.checker.R
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.NotificationCollectorService
import com.phantom.checker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 桌面 4x1 今日統計小工具（跟隨系統淺/深色主題）：
 * 顯示今日訊息數、未讀數、詐騙警示數，點擊開啟幻訊主畫面。
 * 更新由 StatsForegroundService 差異刷新與系統 APPWIDGET_UPDATE 驅動，不新增高頻鬧鐘。
 */
class StatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val appCtx = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val startOfDay = NotificationCollectorService.startOfTodayMillis()
                val stats = AppDatabaseHolder.db(appCtx).messageDao().statsOverview(startOfDay)
                lastWidgetStats = Triple(stats.today, stats.unread, stats.scam)
                val views = buildRemoteViews(appCtx, stats.today, stats.unread, stats.scam)
                appWidgetManager.updateAppWidget(appWidgetIds, views)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val mgr = AppWidgetManager.getInstance(context)
        val remaining = mgr.getAppWidgetIds(ComponentName(context, StatsWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            lastWidgetStats = null
        }
    }

    companion object {
        private const val WIDGET_CLICK_REQUEST_CODE = 2001

        @Volatile
        private var lastWidgetStats: Triple<Int, Int, Int>? = null

        fun updateAllIfChanged(context: Context, today: Int, unread: Int, scam: Int) {
            val appCtx = context.applicationContext
            val current = Triple(today, unread, scam)
            if (lastWidgetStats == current) return

            val mgr = try {
                AppWidgetManager.getInstance(appCtx)
            } catch (_: Exception) {
                return
            }
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(appCtx, StatsWidgetProvider::class.java))
            } catch (_: Exception) {
                return
            }
            if (ids.isEmpty()) {
                lastWidgetStats = null
                return
            }

            lastWidgetStats = current
            val views = buildRemoteViews(appCtx, today, unread, scam)
            try {
                mgr.updateAppWidget(ids, views)
            } catch (_: Exception) {
            }
        }

        private fun buildRemoteViews(
            context: Context,
            today: Int,
            unread: Int,
            scam: Int
        ): RemoteViews {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                WIDGET_CLICK_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return RemoteViews(context.packageName, R.layout.widget_stats).apply {
                setTextViewText(R.id.widget_today_count, today.toString())
                setTextViewText(R.id.widget_unread_count, unread.toString())
                setTextViewText(R.id.widget_scam_count, scam.toString())
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }
        }
    }
}
