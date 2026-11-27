package com.phantom.checker.util

import android.content.Context
import androidx.annotation.StringRes
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
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
        val category: String, // "local" | "cloud" | "cross_app"
        val isReadOnly: Boolean,
        val needsPermission: String? = null,
        val needsByokKey: String? = null,
        val evaluateStatus: (Context) -> ActionStatus
    ) {
        val title: String get() = PhantomApplication.str(titleRes)
        val description: String get() = PhantomApplication.str(descriptionRes)
        fun title(ctx: Context): String = AppLocaleHelper.localizedContext(ctx).getString(titleRes)
        fun description(ctx: Context): String = AppLocaleHelper.localizedContext(ctx).getString(descriptionRes)
    }

    val ACTIONS: List<LinkedAppAction> = listOf(
        LinkedAppAction(
            id = "calendar_readonly",
            titleRes = R.string.util_linked_001,
            descriptionRes = R.string.util_linked_002,
            category = "local",
            isReadOnly = true,
            needsPermission = "READ_CALENDAR",
            evaluateStatus = { ctx ->
                val lCtx = AppLocaleHelper.localizedContext(ctx)
                if (CalendarBridge.isCalendarEnabledForAi(ctx)) {
                    ActionStatus(Availability.READY, lCtx.getString(R.string.util_linked_018))
                } else {
                    ActionStatus(Availability.NEEDS_PERMISSION, lCtx.getString(R.string.util_linked_019))
                }
            }
        ),
        LinkedAppAction(
            id = "usage_stats_readonly",
            titleRes = R.string.util_linked_003,
            descriptionRes = R.string.util_linked_004,
            category = "local",
            isReadOnly = true,
            needsPermission = "PACKAGE_USAGE_STATS",
            evaluateStatus = { ctx ->
                val lCtx = AppLocaleHelper.localizedContext(ctx)
                if (UsageStatsBridge.hasUsageStatsPermission(ctx)) {
                    ActionStatus(Availability.READY, lCtx.getString(R.string.util_linked_020))
                } else {
                    ActionStatus(Availability.NEEDS_PERMISSION, lCtx.getString(R.string.util_linked_021))
                }
            }
        ),
        LinkedAppAction(
            id = "github_monitor_readonly",
            titleRes = R.string.util_linked_005,
            descriptionRes = R.string.util_linked_006,
            category = "cloud",
            isReadOnly = true,
            needsByokKey = "github_pat",
            evaluateStatus = { ctx ->
                val lCtx = AppLocaleHelper.localizedContext(ctx)
                if (!EncryptedByokStore.isGitHubConfigured(ctx)) {
                    ActionStatus(Availability.NEEDS_BYOK, lCtx.getString(R.string.util_linked_022))
                } else {
                    val lastSync = PhantomPrefs.getLinkedAppLastSyncAt(ctx, "github")
                    val err = PhantomPrefs.getLinkedAppLastError(ctx, "github")
                    val intervalMs = PhantomPrefs.getGitHubPollIntervalMinutes(ctx).toLong() * 60_000L
                    val nextSync = if (lastSync > 0L) lastSync + intervalMs else 0L
                    if (err.isNotBlank()) {
                        ActionStatus(Availability.WARNING, lCtx.getString(R.string.util_linked_023, err), lastSync, nextSync, err)
                    } else {
                        ActionStatus(Availability.READY, lCtx.getString(R.string.util_linked_024), lastSync, nextSync)
                    }
                }
            }
        ),
        LinkedAppAction(
            id = "discord_digest_stats_readonly",
            titleRes = R.string.util_linked_007,
            descriptionRes = R.string.util_linked_008,
            category = "cloud",
            isReadOnly = true,
            needsByokKey = "discord_webhook",
            evaluateStatus = { ctx ->
                val lCtx = AppLocaleHelper.localizedContext(ctx)
                if (!EncryptedByokStore.isDiscordConfigured(ctx)) {
                    ActionStatus(Availability.NEEDS_BYOK, lCtx.getString(R.string.util_linked_025))
                } else {
                    val fwdOn = PhantomPrefs.isDiscordForwardEnabled(ctx)
                    val lastSync = PhantomPrefs.getLinkedAppLastSyncAt(ctx, "discord")
                    ActionStatus(
                        Availability.READY,
                        if (fwdOn) lCtx.getString(R.string.util_linked_026) else lCtx.getString(R.string.util_linked_027),
                        lastSyncAtMs = lastSync
                    )
                }
            }
        ),
        LinkedAppAction(
            id = "discord_webhook_forward",
            titleRes = R.string.util_linked_009,
            descriptionRes = R.string.util_linked_010,
            category = "cloud",
            isReadOnly = false,
            needsByokKey = "discord_webhook",
            evaluateStatus = { ctx ->
                val lCtx = AppLocaleHelper.localizedContext(ctx)
                if (!EncryptedByokStore.isDiscordConfigured(ctx)) {
                    ActionStatus(Availability.NEEDS_BYOK, lCtx.getString(R.string.util_linked_028))
                } else {
                    ActionStatus(Availability.READY, lCtx.getString(R.string.util_linked_029))
                }
            }
        ),
        LinkedAppAction(
            id = "cross_app_intents",
            titleRes = R.string.util_linked_011,
            descriptionRes = R.string.util_linked_012,
            category = "cross_app",
            isReadOnly = false,
            evaluateStatus = { ctx ->
                ActionStatus(Availability.READY, AppLocaleHelper.localizedContext(ctx).getString(R.string.util_linked_013))
            }
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
        val lCtx = AppLocaleHelper.localizedContext(ctx)
        appendLine(lCtx.getString(R.string.util_linked_017))
        ACTIONS.forEach { act ->
            val st = act.evaluateStatus(ctx)
            val rw = if (act.isReadOnly) lCtx.getString(R.string.linked_apps_s_8351a4) else lCtx.getString(R.string.linked_apps_s_736b95)
            appendLine("- ${act.id}（${act.title(ctx)}，$rw）：${st.statusText}")
        }
    }.trim()
}
