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
import com.phantom.checker.service.DailySummaryPolicy
import com.phantom.checker.service.WeeklySummaryPolicy
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.ui.stripMarkdown
import com.phantom.checker.util.AppLocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val WHITESPACE_REGEX = Regex("\\s+")


/**
 * 桌面 2x2「最新 AI 摘要」小工具：
 * 顯示最近一則每日／每週 AI 摘要節錄，點擊開啟統計頁摘要卡。
 */
class AiSummaryWidgetProvider : AppWidgetProvider() {

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
                    val latest = AppDatabaseHolder.db(appCtx).aiSummaryDao().latest()
                    val dateText = formatSummaryDate(latest?.date)
                    val bodyText = if (latest == null || latest.summary.isBlank()) {
                        com.phantom.checker.PhantomApplication.str(R.string.widget_ai_s_8eb256)
                    } else {
                        stripMarkdown(latest.summary)
                            .replace(WHITESPACE_REGEX, " ")
                            .trim()
                            .take(140)
                    }
                    val localeTag = AppLocaleHelper.resolveEffectiveLocale(appCtx).toLanguageTag()
                    lastSnapshot = Triple(dateText, bodyText, localeTag)
                    val views = buildRemoteViews(appCtx, dateText, bodyText)
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
        val remaining = mgr.getAppWidgetIds(ComponentName(context, AiSummaryWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            lastSnapshot = null
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        lastSnapshot = null
    }

    companion object {
        private const val WIDGET_CLICK_REQUEST_CODE = 2004

        @Volatile
        private var lastSnapshot: Triple<String, String, String>? = null

        private fun formatSummaryDate(dateKey: Long?): String {
            if (dateKey == null) return ""
            return if (dateKey >= WeeklySummaryPolicy.WEEKLY_KEY_OFFSET) {
                WeeklySummaryPolicy.formatWeekLabel(dateKey)
            } else {
                dateKey.toString()
            }
        }

        suspend fun updateAllIfChanged(context: Context) {
            val appCtx = context.applicationContext
            val mgr = try {
                AppWidgetManager.getInstance(appCtx)
            } catch (_: Exception) {
                return
            }
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(appCtx, AiSummaryWidgetProvider::class.java))
            } catch (_: Exception) {
                return
            }
            if (ids.isEmpty()) {
                lastSnapshot = null
                return
            }
            val latest = AppDatabaseHolder.db(appCtx).aiSummaryDao().latest()
            val dateText = formatSummaryDate(latest?.date)
            val bodyText = if (latest == null || latest.summary.isBlank()) {
                com.phantom.checker.PhantomApplication.str(R.string.widget_ai_s_8eb256)
            } else {
                stripMarkdown(latest.summary)
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
                    .take(140)
            }
            val localeTag = AppLocaleHelper.resolveEffectiveLocale(appCtx).toLanguageTag()
            val snapshot = Triple(dateText, bodyText, localeTag)
            if (lastSnapshot == snapshot) return
            lastSnapshot = snapshot
            val views = buildRemoteViews(appCtx, dateText, bodyText)
            try {
                mgr.updateAppWidget(ids, views)
            } catch (_: Exception) {
            }
        }

        private fun buildRemoteViews(
            context: Context,
            dateText: String,
            bodyText: String
        ): RemoteViews {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, "stats")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                WIDGET_CLICK_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return RemoteViews(context.packageName, R.layout.widget_ai_summary).apply {
                setTextViewText(R.id.widget_summary_date, dateText)
                setTextViewText(R.id.widget_summary_body, bodyText)
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }
        }
    }
}
