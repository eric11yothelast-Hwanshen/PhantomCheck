package com.phantom.checker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import androidx.core.app.NotificationManagerCompat
import com.phantom.checker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 自動回覆分兩種：
 * 1. SMS：完全可行，直接回傳簡訊（時段控制）
 * 2. LINE/IG 等通知：不可代送（會已讀），改用「通知快速回覆 RemoteInput」
 *    → 見 QuickReplyHelper
 */
object AutoReplyConfig {
    private const val PREFS_NAME = "auto_reply_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_START_MINUTE = "start_minute"
    private const val KEY_END_MINUTE = "end_minute"
    private const val KEY_SMS_TEMPLATE = "sms_template"

    private const val DEFAULT_START_MINUTE = 22 * 60   // 22:00 起
    private const val DEFAULT_END_MINUTE = 8 * 60      // 08:00 止
    private const val DEFAULT_SMS_TEMPLATE = "目前在忙，稍後回覆你 🙂"

    @Volatile private var loaded = false

    var enabled = false
    var startMinute = DEFAULT_START_MINUTE
    var endMinute = DEFAULT_END_MINUTE
    var smsTemplate = DEFAULT_SMS_TEMPLATE

    @Synchronized
    fun load(ctx: Context) {
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_ENABLED, false)
        startMinute = prefs.getInt(KEY_START_MINUTE, DEFAULT_START_MINUTE)
        endMinute = prefs.getInt(KEY_END_MINUTE, DEFAULT_END_MINUTE)
        smsTemplate = prefs.getString(KEY_SMS_TEMPLATE, DEFAULT_SMS_TEMPLATE) ?: DEFAULT_SMS_TEMPLATE
        loaded = true
    }

    fun ensureLoaded(ctx: Context) {
        if (!loaded) load(ctx)
    }

    @Synchronized
    fun save(
        ctx: Context,
        enabled: Boolean = this.enabled,
        startMinute: Int = this.startMinute,
        endMinute: Int = this.endMinute,
        smsTemplate: String = this.smsTemplate
    ) {
        this.enabled = enabled
        this.startMinute = startMinute
        this.endMinute = endMinute
        this.smsTemplate = smsTemplate
        this.loaded = true
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_START_MINUTE, startMinute)
            .putInt(KEY_END_MINUTE, endMinute)
            .putString(KEY_SMS_TEMPLATE, smsTemplate)
            .apply()
    }
}

class SmsAutoReplyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AutoReplyConfig.ensureLoaded(ctx)
        if (!AutoReplyConfig.enabled) return
        val now = java.util.Calendar.getInstance()
        val mins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val inWindow = if (AutoReplyConfig.startMinute <= AutoReplyConfig.endMinute)
            mins in AutoReplyConfig.startMinute..AutoReplyConfig.endMinute
        else mins >= AutoReplyConfig.startMinute || mins <= AutoReplyConfig.endMinute
        if (!inWindow) return

        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = msgs.firstOrNull()?.displayOriginatingAddress?.trim() ?: return
        val body = msgs.joinToString("") { it.displayMessageBody ?: "" }

        val containsSensitiveKw = SENSITIVE_SMS_KEYWORDS.any { kw ->
            body.contains(kw, ignoreCase = true)
        }
        val containsShortUrl = SHORT_URL_REGEX.containsMatchIn(body)
        val template = AutoReplyConfig.smsTemplate.trim()
        val isSelfTemplate = template.isNotEmpty() && body.trim() == template
        val shouldSkipReply = containsSensitiveKw ||
            containsShortUrl ||
            isSelfTemplate ||
            !TW_MOBILE_REGEX.matches(sender)

        if (shouldSkipReply) return

        CoroutineScope(Dispatchers.IO).launch {
            if (canReplyAndRecord(ctx, sender)) {
                android.telephony.SmsManager.getDefault().sendTextMessage(
                    sender, null, AutoReplyConfig.smsTemplate, null, null
                )
            }
        }
    }

    companion object {
        private const val COOLDOWN_PREFS = "auto_reply_cooldown_prefs"
        private const val COOLDOWN_MS = 4L * 60L * 60L * 1000L
        private const val MAX_REPLIES_PER_DAY = 3

        private val SENSITIVE_SMS_KEYWORDS = listOf(
            "驗證碼", "OTP", "一次性密碼", "信用卡", "銀行", "帳單"
        )
        private val SHORT_URL_REGEX = Regex(
            "(https?://)?(bit\\.ly|tinyurl\\.com|reurl\\.cc|pse\\.is|is\\.gd|t\\.co|goo\\.gl|shorturl\\.at|ppt\\.cc|risu\\.io|lihi1\\.com|lihi\\.cc)/\\S+",
            RegexOption.IGNORE_CASE
        )
        private val TW_MOBILE_REGEX = Regex("^09\\d{8}$")

        @Synchronized
        internal fun canReplyAndRecord(
            ctx: Context,
            sender: String,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            val prefs = ctx.applicationContext.getSharedPreferences(COOLDOWN_PREFS, Context.MODE_PRIVATE)
            val tsKey = "last_reply_ts_$sender"
            val dateKey = "daily_reply_date_$sender"
            val countKey = "daily_reply_count_$sender"

            val lastRepliedAt = prefs.getLong(tsKey, 0L)
            if (lastRepliedAt > 0L && (nowMs - lastRepliedAt) < COOLDOWN_MS) {
                return false
            }

            val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                .format(java.util.Date(nowMs))
            val savedDate = prefs.getString(dateKey, null)
            val savedCount = if (savedDate == today) prefs.getInt(countKey, 0) else 0
            if (savedCount >= MAX_REPLIES_PER_DAY) {
                return false
            }

            prefs.edit()
                .putLong(tsKey, nowMs)
                .putString(dateKey, today)
                .putInt(countKey, savedCount + 1)
                .apply()
            return true
        }
    }
}

/** 讓使用者在原通知上快速回覆（不開啟原 App、不觸發已讀的替代方案） */
object QuickReplyHelper {
    fun canReply(sbn: Notification): Boolean =
        sbn.actions.any { it.remoteInputs?.isNotEmpty() == true }

    fun buildQuickReplyAction(ctx: Context, sbn: Notification): Notification.Action? =
        sbn.actions.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
}
