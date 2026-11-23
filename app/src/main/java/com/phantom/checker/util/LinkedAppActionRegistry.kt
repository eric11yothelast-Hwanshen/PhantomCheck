package com.phantom.checker.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.phantom.checker.PhantomApplication
import com.phantom.checker.R

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
            title = PhantomApplication.str(R.string.util_linked_001),
            description = PhantomApplication.str(R.string.util_linked_002),
            category = "local",
            isReadOnly = true,
            needsPermission = "READ_CALENDAR",
            evaluateStatus = { ctx ->
                if (CalendarBridge.isCalendarEnabledForAi(ctx)) {
                    ActionStatus(Availability.READY, ctx.getString(R.string.util_linked_018))
                } else {
                    ActionStatus(Availability.NEEDS_PERMISSION, ctx.getString(R.string.util_linked_019))
                }
            }
        ),
        LinkedAppAction(
            id = "usage_stats_readonly",
            title = PhantomApplication.str(R.string.util_linked_003),
            description = PhantomApplication.str(R.string.util_linked_004),
            category = "local",
            isReadOnly = true,
            needsPermission = "PACKAGE_USAGE_STATS",
            evaluateStatus = { ctx ->
                if (UsageStatsBridge.hasUsageStatsPermission(ctx)) {
                    ActionStatus(Availability.READY, ctx.getString(R.string.util_linked_020))
                } else {
                    ActionStatus(Availability.NEEDS_PERMISSION, ctx.getString(R.string.util_linked_021))
                }
            }
        ),
        LinkedAppAction(
            id = "github_monitor_readonly",
            title = PhantomApplication.str(R.string.util_linked_005),
            description = PhantomApplication.str(R.string.util_linked_006),
            category = "cloud",
            isReadOnly = true,
            needsByokKey = "github_pat",
            evaluateStatus = { ctx ->
                if (!EncryptedByokStore.isGitHubConfigured(ctx)) {
                    ActionStatus(Availability.NEEDS_BYOK, ctx.getString(R.string.util_linked_022))
                } else {
                    val lastSync = PhantomPrefs.getLinkedAppLastSyncAt(ctx, "github")
                    val err = PhantomPrefs.getLinkedAppLastError(ctx, "github")
                    val intervalMs = PhantomPrefs.getGitHubPollIntervalMinutes(ctx).toLong() * 60_000L
                    val nextSync = if (lastSync > 0L) lastSync + intervalMs else 0L
                    if (err.isNotBlank()) {
                        ActionStatus(Availability.WARNING, ctx.getString(R.string.util_linked_023, err), lastSync, nextSync, err)
                    } else {
                        ActionStatus(Availability.READY, ctx.getString(R.string.util_linked_024), lastSync, nextSync)
                    }
                }
            }
        ),
        LinkedAppAction(
            id = "discord_digest_stats_readonly",
            title = PhantomApplication.str(R.string.util_linked_007),
            description = PhantomApplication.str(R.string.util_linked_008),
            category = "cloud",
            isReadOnly = true,
            needsByokKey = "discord_webhook",
            evaluateStatus = { ctx ->
                if (!EncryptedByokStore.isDiscordConfigured(ctx)) {
                    ActionStatus(Availability.NEEDS_BYOK, ctx.getString(R.string.util_linked_025))
                } else {
                    val fwdOn = PhantomPrefs.isDiscordForwardEnabled(ctx)
                    val lastSync = PhantomPrefs.getLinkedAppLastSyncAt(ctx, "discord")
                    ActionStatus(
                        Availability.READY,
                        if (fwdOn) ctx.getString(R.string.util_linked_026) else ctx.getString(R.string.util_linked_027),
                        lastSyncAtMs = lastSync
                    )
                }
            }
        ),
        LinkedAppAction(
            id = "discord_webhook_forward",
            title = PhantomApplication.str(R.string.util_linked_009),
            description = PhantomApplication.str(R.string.util_linked_010),
            category = "cloud",
            isReadOnly = false,
            needsByokKey = "discord_webhook",
            evaluateStatus = { ctx ->
                if (!EncryptedByokStore.isDiscordConfigured(ctx)) {
                    ActionStatus(Availability.NEEDS_BYOK, ctx.getString(R.string.util_linked_028))
                } else {
                    ActionStatus(Availability.READY, ctx.getString(R.string.util_linked_029))
                }
            }
        ),
        LinkedAppAction(
            id = "cross_app_intents",
            title = PhantomApplication.str(R.string.util_linked_011),
            description = PhantomApplication.str(R.string.util_linked_012),
            category = "cross_app",
            isReadOnly = false,
            evaluateStatus = { _ -> ActionStatus(Availability.READY, PhantomApplication.str(R.string.util_linked_013)) }
        )
    )

    fun formatSyncWindow(lastSyncMs: Long, nextSyncMs: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val lastStr = if (lastSyncMs > 0L) fmt.format(Date(lastSyncMs)) else PhantomApplication.str(R.string.util_linked_014)
        val nextStr = if (nextSyncMs > 0L) fmt.format(Date(nextSyncMs)) else PhantomApplication.str(R.string.util_linked_015)
        return PhantomApplication.str(R.string.util_linked_016, lastStr, nextStr)
    }

    /** 供 ChatScreenParts 注入系統提示，確保 AI 只宣稱並呼叫 Registry 內存在的能力 */
    fun buildCapabilityPromptSummary(ctx: Context): String = buildString {
        appendLine(PhantomApplication.str(R.string.util_linked_017))
        ACTIONS.forEach { act ->
            val st = act.evaluateStatus(ctx)
            val rw = if (act.isReadOnly) PhantomApplication.str(R.string.linked_apps_s_8351a4) else PhantomApplication.str(R.string.linked_apps_s_736b95)
            appendLine("- ${act.id}（${act.title}，$rw）：${st.statusText}")
        }
    }.trim()
}
