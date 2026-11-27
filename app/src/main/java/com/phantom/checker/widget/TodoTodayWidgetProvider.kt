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
import com.phantom.checker.data.TodoEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.DailySummaryPolicy
import com.phantom.checker.service.ReminderScheduler
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.util.AppLocaleHelper
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * #439 新增小工具 1：今天待辦（TodoToday）
 * - 顯示今日到期（含已逾期）未完成待辦前 4 筆；點擊項目直接透過 provider 自身 broadcast action 勾選完成。
 * - goAsync() 內限時 8.5 秒寫入 DB（複用 ReminderScheduler.completeTodoWithRecurrence，連帶觸發 GoalEngine 歸因與重排）。
 * - 空白態顯示「今日無待辦」；點擊標題／空白區 fallback 進 App 記事待辦分頁。
 */
class TodoTodayWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_TODO) {
            val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1L)
            if (todoId <= 0L) return
            val appCtx = context.applicationContext
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withTimeout(8_500L) {
                        val todo = AppDatabaseHolder.db(appCtx).todoDao().byId(todoId)
                        if (todo != null && !todo.isDone) {
                            ReminderScheduler.completeTodoWithRecurrence(appCtx, todo, true)
                        }
                        lastSnapshot = null
                        refreshNow(appCtx)
                        runCatching { GoalProgressWidgetProvider.refreshNow(appCtx) }
                    }
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

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
                    val data = loadTodayPendingTodos(appCtx)
                    lastSnapshot = computeSnapshotKey(appCtx, data)
                    val views = buildRemoteViews(appCtx, data)
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
        val remaining = mgr.getAppWidgetIds(ComponentName(context, TodoTodayWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            lastSnapshot = null
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        lastSnapshot = null
    }

    companion object {
        const val ACTION_TOGGLE_TODO = "com.phantom.checker.widget.ACTION_TODO_TODAY_TOGGLE"
        const val EXTRA_TODO_ID = "extra_todo_today_id"
        private const val MAX_ITEMS = 4
        private const val OPEN_APP_REQUEST_CODE = 2101
        private const val TOGGLE_REQUEST_CODE_BASE = 2110

        private data class TodayTodosData(
            val totalCount: Int,
            val items: List<TodoEntity>
        )

        @Volatile
        private var lastSnapshot: String? = null

        private suspend fun loadTodayPendingTodos(appCtx: Context): TodayTodosData {
            val zone = ZoneId.systemDefault()
            val endOfTodayMs = LocalDate.now(zone)
                .atTime(LocalTime.MAX)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            val pending = runCatching {
                AppDatabaseHolder.db(appCtx).todoDao().pendingTodos()
            }.getOrDefault(emptyList())
            val todaySorted = pending
                .filter { it.dueAt <= endOfTodayMs }
                .sortedBy { it.dueAt }
            return TodayTodosData(
                totalCount = todaySorted.size,
                items = todaySorted.take(MAX_ITEMS)
            )
        }

        private fun computeSnapshotKey(context: Context, data: TodayTodosData): String {
            val localeTag = AppLocaleHelper.resolveEffectiveLocale(context).toLanguageTag()
            val use24h = PhantomPrefs.is24HourFormat(context)
            val itemsKey = data.items.joinToString("|") { "${it.id}:${it.dueAt}:${it.title}" }
            return "$localeTag|$use24h|${data.totalCount}|$itemsKey"
        }

        suspend fun refreshNow(context: Context) {
            val appCtx = context.applicationContext
            val mgr = try {
                AppWidgetManager.getInstance(appCtx)
            } catch (_: Exception) {
                return
            }
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(appCtx, TodoTodayWidgetProvider::class.java))
            } catch (_: Exception) {
                return
            }
            if (ids.isEmpty()) {
                lastSnapshot = null
                return
            }
            val data = loadTodayPendingTodos(appCtx)
            val sig = computeSnapshotKey(appCtx, data)
            if (lastSnapshot == sig) return
            lastSnapshot = sig
            val views = buildRemoteViews(appCtx, data)
            try {
                mgr.updateAppWidget(ids, views)
            } catch (_: Exception) {
            }
        }

        private fun buildRemoteViews(context: Context, data: TodayTodosData): RemoteViews {
            val items = data.items
            val resCtx = AppLocaleHelper.localizedContext(context)
            val openNotesIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, ReminderWidgetProvider.TAB_NOTES)
            }
            val openNotesPending = PendingIntent.getActivity(
                context,
                OPEN_APP_REQUEST_CODE,
                openNotesIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val rowIds = intArrayOf(
                R.id.widget_todo_today_row1,
                R.id.widget_todo_today_row2,
                R.id.widget_todo_today_row3,
                R.id.widget_todo_today_row4
            )
            val titleIds = intArrayOf(
                R.id.widget_todo_today_title1,
                R.id.widget_todo_today_title2,
                R.id.widget_todo_today_title3,
                R.id.widget_todo_today_title4
            )
            val timeIds = intArrayOf(
                R.id.widget_todo_today_time1,
                R.id.widget_todo_today_time2,
                R.id.widget_todo_today_time3,
                R.id.widget_todo_today_time4
            )

            return RemoteViews(context.packageName, R.layout.widget_todo_today).apply {
                setTextViewText(R.id.widget_todo_today_header, resCtx.getString(R.string.widget_todo_today_title))
                setTextViewText(
                    R.id.widget_todo_today_count,
                    if (data.totalCount <= 0) "" else data.totalCount.toString()
                )
                setOnClickPendingIntent(R.id.widget_root, openNotesPending)

                if (items.isEmpty()) {
                    setViewVisibility(R.id.widget_todo_today_empty, View.VISIBLE)
                    setTextViewText(R.id.widget_todo_today_empty, resCtx.getString(R.string.widget_todo_today_empty))
                    for (i in 0 until MAX_ITEMS) {
                        setViewVisibility(rowIds[i], View.GONE)
                    }
                } else {
                    setViewVisibility(R.id.widget_todo_today_empty, View.GONE)
                    for (i in 0 until MAX_ITEMS) {
                        val item = items.getOrNull(i)
                        if (item != null) {
                            setViewVisibility(rowIds[i], View.VISIBLE)
                            setTextViewText(titleIds[i], item.title)
                            val timeStr = if (item.dueAt > 0L) {
                                PhantomPrefs.formatMessageTime(context, item.dueAt, includeDate = false)
                            } else {
                                ""
                            }
                            setTextViewText(timeIds[i], timeStr)
                            val toggleIntent = Intent(context, TodoTodayWidgetProvider::class.java).apply {
                                action = ACTION_TOGGLE_TODO
                                putExtra(EXTRA_TODO_ID, item.id)
                                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                            }
                            val togglePending = PendingIntent.getBroadcast(
                                context,
                                TOGGLE_REQUEST_CODE_BASE + i,
                                toggleIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            setOnClickPendingIntent(rowIds[i], togglePending)
                        } else {
                            setViewVisibility(rowIds[i], View.GONE)
                        }
                    }
                }
            }
        }
    }
}
