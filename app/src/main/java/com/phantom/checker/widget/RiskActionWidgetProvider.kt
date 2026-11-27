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
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.StatsForegroundService
import com.phantom.checker.ui.ScamCenterActivity
import com.phantom.checker.util.AppLocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 桌面 2x2「高風險處置」小工具：
 * 顯示最新一則高風險訊息預覽，並提供「標記詐騙」與「白名單」RemoteViews 按鈕（goAsync 執行）。
 */
class RiskActionWidgetProvider : AppWidgetProvider() {

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
                    val target = findTargetMessage(appCtx)
                    lastSnapshot = computeSnapshotKey(appCtx, target)
                    val views = buildRemoteViews(appCtx, target)
                    appWidgetManager.updateAppWidget(appWidgetIds, views)
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action != ACTION_MARK_SCAM && action != ACTION_WHITELIST) return
        val msgId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (msgId <= 0L) return

        val appCtx = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                kotlinx.coroutines.withTimeout(8_500L) {
                    val db = AppDatabaseHolder.db(appCtx)
                    val msg = db.messageDao().byId(msgId)
                    if (msg != null) {
                        if (action == ACTION_MARK_SCAM) {
                            db.messageDao().update(
                                msg.copy(
                                    riskLabel = "scam",
                                    riskScore = maxOf(msg.riskScore, 90),
                                    isRead = true
                                )
                            )
                        } else {
                            db.messageDao().update(
                                msg.copy(
                                    riskLabel = "safe",
                                    riskScore = 0,
                                    riskReason = "已由桌面小工具加入白名單", // TODO(R17): 抽離至 strings.xml
                                    isRead = true
                                )
                            )
                            val sender = msg.sender.trim()
                            if (sender.isNotEmpty()) {
                                com.phantom.checker.data.WhitelistRuleHelper.upsertWhitelist(
                                    dao = db.aiRuleDao(),
                                    ruleText = "信任「$sender」的訊息（桌面小工具加入白名單）", // TODO(R17): 抽離至 strings.xml
                                    sender = sender,
                                    packageName = msg.packageName,
                                    onCacheInvalidate = { com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches() }
                                )
                            }
                        }
                    }
                    lastSnapshot = null
                    updateAllIfChanged(appCtx, force = true)
                    WidgetUpdater.scheduleRefresh(appCtx, immediate = true)
                    StatsForegroundService.startOrRefresh(appCtx, force = true)
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
        val remaining = mgr.getAppWidgetIds(ComponentName(context, RiskActionWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            lastSnapshot = null
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        lastSnapshot = null
    }

    companion object {
        private const val ACTION_MARK_SCAM = "com.phantom.checker.widget.ACTION_MARK_SCAM"
        private const val ACTION_WHITELIST = "com.phantom.checker.widget.ACTION_WHITELIST"
        private const val EXTRA_MESSAGE_ID = "extra_message_id"
        private const val REQ_OPEN_CENTER = 2005
        private const val REQ_MARK_SCAM = 2006
        private const val REQ_WHITELIST = 2007

        @Volatile
        private var lastSnapshot: String? = null

        private fun computeSnapshotKey(ctx: Context, target: MessageEntity?): String {
            val localeTag = AppLocaleHelper.resolveEffectiveLocale(ctx).toLanguageTag()
            return if (target == null) {
                "$localeTag|none"
            } else {
                "$localeTag|${target.id}:${target.isRead}:${target.riskLabel}:${target.riskScore}"
            }
        }

        private suspend fun findTargetMessage(ctx: Context): MessageEntity? {
            return AppDatabaseHolder.db(ctx).messageDao().latestUnreadOrRecentScam()
        }

        suspend fun updateAllIfChanged(context: Context, force: Boolean = false) {
            val appCtx = context.applicationContext
            val mgr = try {
                AppWidgetManager.getInstance(appCtx)
            } catch (_: Exception) {
                return
            }
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(appCtx, RiskActionWidgetProvider::class.java))
            } catch (_: Exception) {
                return
            }
            if (ids.isEmpty()) {
                lastSnapshot = null
                return
            }
            val target = findTargetMessage(appCtx)
            val sig = computeSnapshotKey(appCtx, target)
            if (!force && lastSnapshot == sig) return
            lastSnapshot = sig
            val views = buildRemoteViews(appCtx, target)
            try {
                mgr.updateAppWidget(ids, views)
            } catch (_: Exception) {
            }
        }

        private fun buildRemoteViews(context: Context, msg: MessageEntity?): RemoteViews {
            val openIntent = Intent(context, ScamCenterActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPi = PendingIntent.getActivity(
                context,
                REQ_OPEN_CENTER,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return RemoteViews(context.packageName, R.layout.widget_risk_action).apply {
                setOnClickPendingIntent(R.id.widget_root, openPi)
                if (msg == null) {
                    setTextViewText(R.id.widget_risk_sender, com.phantom.checker.PhantomApplication.str(R.string.widget_risk_s_27826a))
                    setTextViewText(R.id.widget_risk_preview, com.phantom.checker.PhantomApplication.str(R.string.widget_risk_s_234a08))
                    setViewVisibility(R.id.widget_risk_actions_row, View.GONE)
                } else {
                    val title = "${msg.sender.ifBlank { msg.appLabel }} · ${msg.appLabel}"
                    setTextViewText(R.id.widget_risk_sender, title)
                    setTextViewText(R.id.widget_risk_preview, msg.content.replace("\n", " ").take(80))
                    setViewVisibility(R.id.widget_risk_actions_row, View.VISIBLE)

                    val scamIntent = Intent(context, RiskActionWidgetProvider::class.java).apply {
                        action = ACTION_MARK_SCAM
                        putExtra(EXTRA_MESSAGE_ID, msg.id)
                    }
                    val scamPi = PendingIntent.getBroadcast(
                        context,
                        REQ_MARK_SCAM,
                        scamIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val wlIntent = Intent(context, RiskActionWidgetProvider::class.java).apply {
                        action = ACTION_WHITELIST
                        putExtra(EXTRA_MESSAGE_ID, msg.id)
                    }
                    val wlPi = PendingIntent.getBroadcast(
                        context,
                        REQ_WHITELIST,
                        wlIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    setOnClickPendingIntent(R.id.widget_btn_mark_scam, scamPi)
                    setOnClickPendingIntent(R.id.widget_btn_whitelist, wlPi)
                }
            }
        }
    }
}
