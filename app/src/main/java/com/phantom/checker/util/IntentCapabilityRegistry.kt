package com.phantom.checker.util

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.phantom.checker.ui.PermissionHubActivity

/**
 * #380 宣告式 Agentic 能力表與受阻自動改道（不做通用自由變通）：
 * - 全枚舉意圖（REMINDER / MEMO / CALENDAR_ADD / OPEN_APP）× 宣告式優先序路徑表。
 * - AI 只選意圖，絕不發明路徑；受阻時依既定有序表自動下移，全敗才回報人話原因＋PermissionHubActivity 直達鈕。
 * - 確認卡一律附「執行方式」一行；改道成功時產出一次性可滑除透明告知。
 */
object IntentCapabilityRegistry {

    enum class IntentType(val label: String) {
        REMINDER("提醒"),
        MEMO("記下"),
        CALENDAR_ADD("加行程"),
        OPEN_APP("開 App")
    }

    enum class ExecutionPath(val id: String, val label: String) {
        // REMINDER
        REMINDER_EXACT_ALARM("reminder_exact_alarm", "精確鬧鐘提醒（ReminderScheduler）"),
        REMINDER_SYSTEM_NOTIF("reminder_system_notif", "系統通知提醒（非精確排程）"),
        REMINDER_CALENDAR_ALLDAY("reminder_calendar_allday", "本機日曆全天紀念註記"),

        // MEMO
        MEMO_LOCAL_NOTE("memo_local_note", "本機記事庫"),
        MEMO_PROACTIVE_CHAT("memo_proactive_chat", "主動訊息聊天室備份"),

        // CALENDAR_ADD
        CALENDAR_DIRECT_WRITE("calendar_direct_write", "直接寫入已勾選本機日曆"),
        CALENDAR_ACTION_INSERT("calendar_action_insert", "系統日曆預填畫面（ACTION_INSERT）"),
        CALENDAR_LOCAL_TODO("calendar_local_todo", "改存為本機待辦提醒"),

        // OPEN_APP
        OPEN_APP_DIRECT("open_app_direct", "直接開啟目標 App"),
        OPEN_APP_CHOOSER("open_app_chooser", "系統選擇器開啟替代 App")
    }

    /** 全枚舉宣告式路徑優先序表（AI 不可動態發明路徑） */
    val CAPABILITY_TABLE: Map<IntentType, List<ExecutionPath>> = mapOf(
        IntentType.REMINDER to listOf(
            ExecutionPath.REMINDER_EXACT_ALARM,
            ExecutionPath.REMINDER_SYSTEM_NOTIF,
            ExecutionPath.REMINDER_CALENDAR_ALLDAY
        ),
        IntentType.MEMO to listOf(
            ExecutionPath.MEMO_LOCAL_NOTE,
            ExecutionPath.MEMO_PROACTIVE_CHAT
        ),
        IntentType.CALENDAR_ADD to listOf(
            ExecutionPath.CALENDAR_DIRECT_WRITE,
            ExecutionPath.CALENDAR_ACTION_INSERT,
            ExecutionPath.CALENDAR_LOCAL_TODO
        ),
        IntentType.OPEN_APP to listOf(
            ExecutionPath.OPEN_APP_DIRECT,
            ExecutionPath.OPEN_APP_CHOOSER
        )
    )

    data class BlockedStep(
        val path: ExecutionPath,
        val reason: String
    )

    data class RouteResolution(
        val intent: IntentType,
        val selectedPath: ExecutionPath?,
        val reroutedFrom: List<BlockedStep>,
        val allBlockedReason: String?
    ) {
        val isRerouted: Boolean get() = selectedPath != null && reroutedFrom.isNotEmpty()
        val isAllBlocked: Boolean get() = selectedPath == null

        fun executionWayLine(): String {
            val sel = selectedPath
            return if (sel != null) {
                if (isRerouted) {
                    "執行方式：${intent.label} → ${sel.label}（已因系統限制自動改道）"
                } else {
                    "執行方式：${intent.label} → ${sel.label}"
                }
            } else {
                "執行方式：目前所有宣告路徑均受阻（${allBlockedReason ?: "缺少必要權限"}）"
            }
        }

        fun rerouteNoticeText(): String? {
            if (!isRerouted || selectedPath == null) return null
            val firstReason = reroutedFrom.firstOrNull()?.reason ?: "系統權限限制"
            return "系統限制（$firstReason），已改以「${selectedPath.label}」替代。"
        }
    }

    /**
     * 依宣告表由上而下評估每條路徑可用性；受阻即自動下移下一條。
     * [simulatedBlocked] 供回歸驗證注入模擬受阻狀態。
     */
    fun resolve(
        ctx: Context,
        intent: IntentType,
        targetPackage: String? = null,
        hasSelectedWritableCalendar: Boolean = false,
        simulatedBlocked: Set<ExecutionPath> = emptySet()
    ): RouteResolution {
        val appCtx = ctx.applicationContext
        val paths = CAPABILITY_TABLE[intent].orEmpty()
        val blocked = mutableListOf<BlockedStep>()

        for (path in paths) {
            val blockReason = if (path in simulatedBlocked) {
                "模擬受阻"
            } else {
                checkPathBlockedReason(appCtx, path, targetPackage, hasSelectedWritableCalendar)
            }
            if (blockReason == null) {
                return RouteResolution(
                    intent = intent,
                    selectedPath = path,
                    reroutedFrom = blocked,
                    allBlockedReason = null
                )
            }
            blocked.add(BlockedStep(path, blockReason))
        }

        val summaryReason = blocked.joinToString("；") { "${it.path.label}：${it.reason}" }
        return RouteResolution(
            intent = intent,
            selectedPath = null,
            reroutedFrom = blocked,
            allBlockedReason = summaryReason.ifBlank { "尚未授予必要系統權限" }
        )
    }

    private fun checkPathBlockedReason(
        ctx: Context,
        path: ExecutionPath,
        targetPackage: String?,
        hasSelectedWritableCalendar: Boolean
    ): String? = when (path) {
        ExecutionPath.REMINDER_EXACT_ALARM -> {
            // 對齊 ReminderScheduler 實際排程策略（AlarmManager.setAndAllowWhileIdle，不依賴 SCHEDULE_EXACT_ALARM，
            // 但需具備 AlarmManager 服務與系統通知權限以便到期顯示提醒）
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            when {
                am == null -> "系統不支援鬧鐘排程服務（AlarmManager）"
                !NotificationManagerCompat.from(ctx).areNotificationsEnabled() -> "系統通知權限已關閉"
                else -> null
            }
        }
        ExecutionPath.REMINDER_SYSTEM_NOTIF -> {
            if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                "系統通知權限已關閉"
            } else null
        }
        ExecutionPath.REMINDER_CALENDAR_ALLDAY,
        ExecutionPath.CALENDAR_DIRECT_WRITE -> {
            when {
                !CalendarBridge.hasWritePermission(ctx) -> "未授予日曆寫入權限（WRITE_CALENDAR）"
                !hasSelectedWritableCalendar && PhantomPrefs.getSelectedCalendarIds(ctx).isEmpty() -> "尚未勾選可寫入的本機日曆"
                else -> null
            }
        }
        ExecutionPath.CALENDAR_ACTION_INSERT -> {
            val testIntent = CalendarBridge.buildInsertEventIntent("測試", System.currentTimeMillis(), System.currentTimeMillis() + 3600_000L)
            if (testIntent.resolveActivity(ctx.packageManager) == null) "裝置未安裝系統日曆 App" else null
        }
        ExecutionPath.MEMO_LOCAL_NOTE,
        ExecutionPath.MEMO_PROACTIVE_CHAT,
        ExecutionPath.CALENDAR_LOCAL_TODO -> null
        ExecutionPath.OPEN_APP_DIRECT -> {
            val pkg = targetPackage?.trim().orEmpty()
            if (pkg.isBlank()) {
                "未指定目標 App 套件名"
            } else if (ctx.packageManager.getLaunchIntentForPackage(pkg) == null) {
                "裝置未安裝目標 App（$pkg）"
            } else null
        }
        ExecutionPath.OPEN_APP_CHOOSER -> null
    }

    fun buildPermissionHubIntent(ctx: Context): Intent =
        Intent(ctx, PermissionHubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
