package com.phantom.checker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.phantom.checker.R
import com.phantom.checker.data.GoalEntity
import com.phantom.checker.service.DailySummaryPolicy
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.util.AppLocaleHelper
import com.phantom.checker.util.GoalEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * #439 新增小工具 2：目標進度（GoalProgress）
 * - 顯示最活躍長程目標的進度條、連續期數與下期 due 日（資料源 goalDao，與 #440 語意完全一致）。
 * - 點擊開啟 App 洞察頁夥伴分頁（partner）。
 */
class GoalProgressWidgetProvider : AppWidgetProvider() {

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
                withTimeout(8_500L) {
                    val top = loadMostActiveGoal(appCtx)
                    lastSnapshot = top?.let { Triple(it.goal.id, it.completedCount, it.nextDueDate) }
                    val views = buildRemoteViews(appCtx, top)
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
        val remaining = mgr.getAppWidgetIds(ComponentName(context, GoalProgressWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            lastSnapshot = null
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        lastSnapshot = null
    }

    companion object {
        private const val OPEN_APP_REQUEST_CODE = 2121

        @Volatile
        private var lastSnapshot: Triple<Long, Int, String>? = null

        private suspend fun loadMostActiveGoal(appCtx: Context): GoalEngine.GoalDisplayItem? {
            val items = runCatching { GoalEngine.loadGoalDisplayItems(appCtx) }.getOrDefault(emptyList())
            return items
                .filter { it.goal.status == GoalEntity.STATUS_ACTIVE }
                .sortedWith(
                    compareByDescending<GoalEngine.GoalDisplayItem> { it.completedCount }
                        .thenByDescending { it.streakDays }
                        .thenByDescending { it.goal.createdAt }
                )
                .firstOrNull()
        }

        suspend fun refreshNow(context: Context) {
            val appCtx = context.applicationContext
            val mgr = try {
                AppWidgetManager.getInstance(appCtx)
            } catch (_: Exception) {
                return
            }
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(appCtx, GoalProgressWidgetProvider::class.java))
            } catch (_: Exception) {
                return
            }
            if (ids.isEmpty()) {
                lastSnapshot = null
                return
            }
            val top = loadMostActiveGoal(appCtx)
            val sig = top?.let { Triple(it.goal.id, it.completedCount, it.nextDueDate) }
            if (lastSnapshot == sig) return
            lastSnapshot = sig
            val views = buildRemoteViews(appCtx, top)
            try {
                mgr.updateAppWidget(ids, views)
            } catch (_: Exception) {
            }
        }

        private fun buildRemoteViews(
            context: Context,
            item: GoalEngine.GoalDisplayItem?
        ): RemoteViews {
            val resCtx = AppLocaleHelper.localizedContext(context)
            val openPartnerIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, "partner")
            }
            val openPartnerPending = PendingIntent.getActivity(
                context,
                OPEN_APP_REQUEST_CODE,
                openPartnerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return RemoteViews(context.packageName, R.layout.widget_goal_progress).apply {
                setTextViewText(R.id.widget_goal_header, resCtx.getString(R.string.widget_goal_progress_title))
                setOnClickPendingIntent(R.id.widget_root, openPartnerPending)

                if (item == null) {
                    setViewVisibility(R.id.widget_goal_empty, View.VISIBLE)
                    setTextViewText(R.id.widget_goal_empty, resCtx.getString(R.string.widget_goal_progress_empty))
                    setViewVisibility(R.id.widget_goal_content, View.GONE)
                    setTextViewText(R.id.widget_goal_ratio, "")
                } else {
                    val g = item.goal
                    val target = g.targetCount.coerceAtLeast(1)
                    val completed = item.completedCount.coerceIn(0, target)
                    setViewVisibility(R.id.widget_goal_empty, View.GONE)
                    setViewVisibility(R.id.widget_goal_content, View.VISIBLE)
                    setTextViewText(R.id.widget_goal_ratio, "${item.completedCount}/$target")
                    setTextViewText(R.id.widget_goal_name, g.title)
                    setProgressBar(R.id.widget_goal_progress_bar, target, completed, false)
                    val dueLabel = item.nextDueDate.ifBlank {
                        item.nextMilestoneDate.ifBlank { resCtx.getString(R.string.goal_milestone_reached) }
                    }
                    setTextViewText(
                        R.id.widget_goal_meta,
                        resCtx.getString(R.string.widget_goal_progress_meta, item.streakDays, dueLabel)
                    )
                }
            }
        }
    }
}
