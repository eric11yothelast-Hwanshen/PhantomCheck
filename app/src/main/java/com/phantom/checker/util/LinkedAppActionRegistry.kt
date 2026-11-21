package com.phantom.checker.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * #368 連結應用程式中心：宣告式動作登錄檔（Action Registry）單一來源：
 * - 同時驅動 LinkedAppsActivity 卡片列表與 ChatScreenParts AI 能力宣告（AI 不再編造不存在的能力）
 * - 鐵律編碼：新連接器一律唯讀先上（isReadOnly = true），寫入一律走 Confirm-before-execute 卡
 * - BYOK 未設定 → 狀態卡灰色待設定誠實跳過；輪詢異常靜默記於狀態卡黃色提示
 */
object LinkedAppActionRegistry {

    enum class Availability { READY, NEEDS_PERMISSION, NEEDS_BYOK, WARNING }

    data class ActionStatus(
        val availability: Availability,
        val statusText: String,
        val lastSyncAtMs: Long = 0L,
        val nextSyncAtMs: Long = 0L,
        val warningDetail: String? = null
    )

    data class LinkedAppAction(
        val id: String,
        val title: String,
        val description: String,
        val category: String, // "local" | "cloud" | "cross_app"
        val isReadOnly: Boolean,
        val needsPermission: String? = null,
        val needsByokKey: String? = null,
        val evaluateStatus: (Context) -> ActionStatus
    )

    val ACTIONS: List<LinkedAppAction> = listOf(
        LinkedAppAction(
            id = "calendar_readonly",
            title = "本機日曆讀取",
            description = "唯讀查詢已勾選的本機日曆行程與空檔",
            category = "local",
            isReadOnly = true,
            needsPermission = "READ_CALENDAR",
            evaluateStatus = { ctx ->
                if (CalendarBridge.isCalendarEnabledForAi(ctx)) {
                    ActionStatus(Availability.READY, "已授權且已勾選日曆")
                } else {
                    ActionStatus(Availability.NEEDS_PERMISSION, "待授權或尚未勾選日曆")
                }
            }
        ),
        LinkedAppAction(
            id = "usage_stats_readonly",
            title = "App 使用情況統計",
            description = "唯讀查詢系統 UsageStatsManager 真實前景時間與啟動次數",
            category = "local",
            isReadOnly = true,
            needsPermission = "PACKAGE_USAGE_STATS",
            evaluateStatus = { ctx ->
                if (UsageStatsBridge.hasUsageStatsPermission(ctx)) {
                    ActionStatus(Availability.READY, "已授權系統使用情況存取")
                } else {
                    ActionStatus(Availability.NEEDS_PERMISSION, "待授權（PACKAGE_USAGE_STATS）")
                }
            }
        ),
        LinkedAppAction(
            id = "github_monitor_readonly",
            title = "GitHub CI / PR 唯讀監控",
            description = "每 15 分鐘零 AI 輪詢指定倉庫建置失敗與待審 PR（BYOK PAT）",
            category = "cloud",
            isReadOnly = true,
            needsByokKey = "github_pat",
            evaluateStatus = { ctx ->
                if (!EncryptedByokStore.isGitHubConfigured(ctx)) {
                    ActionStatus(Availability.NEEDS_BYOK, "未設定 GitHub PAT（灰色待設定）")
                } else {
                    val lastSync = PhantomPrefs.getLinkedAppLastSyncAt(ctx, "github")
                    val err = PhantomPrefs.getLinkedAppLastError(ctx, "github")
                    val intervalMs = PhantomPrefs.getGitHubPollIntervalMinutes(ctx).toLong() * 60_000L
                    val nextSync = if (lastSync > 0L) lastSync + intervalMs else 0L
                    if (err.isNotBlank()) {
                        ActionStatus(Availability.WARNING, "上次同步異常：$err", lastSync, nextSync, err)
                    } else {
                        ActionStatus(Availability.READY, "已設定並啟用唯讀輪詢", lastSync, nextSync)
                    }
                }
            }
        ),
        LinkedAppAction(
            id = "discord_digest_stats_readonly",
            title = "Discord 轉發狀態與統計（唯讀示範連接器）",
            description = "唯讀檢視 Discord Webhook 連線狀態與近期簡報轉發設定（透過 Registry 零核心 UI 修改擴展）",
            category = "cloud",
            isReadOnly = true,
            needsByokKey = "discord_webhook",
            evaluateStatus = { ctx ->
                if (!EncryptedByokStore.isDiscordConfigured(ctx)) {
                    ActionStatus(Availability.NEEDS_BYOK, "未設定 Discord Webhook（灰色待設定）")
                } else {
                    val fwdOn = PhantomPrefs.isDiscordForwardEnabled(ctx)
                    val lastSync = PhantomPrefs.getLinkedAppLastSyncAt(ctx, "discord")
                    ActionStatus(
                        Availability.READY,
                        if (fwdOn) "Webhook 就緒 · 簡報轉發已開啟" else "Webhook 就緒 · 轉發未開啟",
                        lastSyncAtMs = lastSync
                    )
                }
            }
        ),
        LinkedAppAction(
            id = "discord_webhook_forward",
            title = "Discord Webhook 轉發（寫入需確認）",
            description = "將早晚簡報或指定訊息轉發至自訂 Discord 頻道（寫入動作必經確認／開關）",
            category = "cloud",
            isReadOnly = false,
            needsByokKey = "discord_webhook",
            evaluateStatus = { ctx ->
                if (!EncryptedByokStore.isDiscordConfigured(ctx)) {
                    ActionStatus(Availability.NEEDS_BYOK, "未設定 Discord Webhook")
                } else {
                    ActionStatus(Availability.READY, "已設定（轉發開關控制）")
                }
            }
        ),
        LinkedAppAction(
            id = "cross_app_intents",
            title = "跨 App 行動卡（Gmail／地圖／鬧鐘／開 App）",
            description = "一律彈出確認卡由使用者手動點擊執行，絕不自動觸發外部 App",
            category = "cross_app",
            isReadOnly = false,
            evaluateStatus = { _ -> ActionStatus(Availability.READY, "即用（必經確認卡）") }
        )
    )

    fun formatSyncWindow(lastSyncMs: Long, nextSyncMs: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val lastStr = if (lastSyncMs > 0L) fmt.format(Date(lastSyncMs)) else "尚未同步"
        val nextStr = if (nextSyncMs > 0L) fmt.format(Date(nextSyncMs)) else "待排程"
        return "最後同步 $lastStr · 下輪 $nextStr"
    }

    /** 供 ChatScreenParts 注入系統提示，確保 AI 只宣稱並呼叫 Registry 內存在的能力 */
    fun buildCapabilityPromptSummary(ctx: Context): String = buildString {
        appendLine("【連結應用動作登錄檔（僅限以下已註冊能力）】")
        ACTIONS.forEach { act ->
            val st = act.evaluateStatus(ctx)
            val rw = if (act.isReadOnly) "唯讀" else "寫入(必確認)"
            appendLine("- ${act.id}（${act.title}，$rw）：${st.statusText}")
        }
    }.trim()
}
