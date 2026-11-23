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
 * 桌面 2x1 迷你統計小工具（跟隨系統淺/深色主題）：
 * 精簡顯示今日訊息數與詐騙警示數，點擊開啟幻訊主畫面。
 */
class StatsMiniWidgetProvider : AppWidgetProvider() {

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
                kotlinx.coroutines.withTimeout(8_500L) {
                    val startOfDay = NotificationCollectorService.startOfTodayMillis()
                    val stats = AppDatabaseHolder.db(appCtx).messageDao().statsOverview(startOfDay)
                    lastWidgetStats = stats.today to stats.scam
                    val views = buildRemoteViews(appCtx, stats.today, stats.scam)
                    appWidgetManager.updateAppWidget(appWidgetIds, views)
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val mgr = AppWidgetManager.getInstance(context)
        val remaining = mgr.getAppWidgetIds(ComponentName(context, StatsMiniWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            lastWidgetStats = null
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        lastWidgetStats = null
    }

    companion object {
        private const val WIDGET_CLICK_REQUEST_CODE = 2002

        @Volatile
        private var lastWidgetStats: Pair<Int, Int>? = null

        fun updateAllIfChanged(context: Context, today: Int, scam: Int) {
            val appCtx = context.applicationContext
            val current = today to scam
            if (lastWidgetStats == current) return

            val mgr = try {
                AppWidgetManager.getInstance(appCtx)
            } catch (_: Exception) {
                return
            }
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(appCtx, StatsMiniWidgetProvider::class.java))
            } catch (_: Exception) {
                return
            }
            if (ids.isEmpty()) {
                lastWidgetStats = null
                return
            }

            lastWidgetStats = current
            val views = buildRemoteViews(appCtx, today, scam)
            try {
                mgr.updateAppWidget(ids, views)
            } catch (_: Exception) {
            }
        }

        private fun buildRemoteViews(
            context: Context,
            today: Int,
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

            return RemoteViews(context.packageName, R.layout.widget_stats_mini).apply {
                setTextViewText(R.id.widget_mini_today_count, today.toString())
                setTextViewText(R.id.widget_mini_scam_count, scam.toString())
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }
        }
    }
}
