package com.phantom.checker.service

import android.Manifest
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
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
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val appCtx = ctx.applicationContext
        if (ContextCompat.checkSelfPermission(
                appCtx,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "SEND_SMS permission not granted; skipping auto-reply")
            return
        }

        AutoReplyConfig.ensureLoaded(appCtx)
        if (!AutoReplyConfig.enabled) return
        val template = AutoReplyConfig.smsTemplate.trim()
        if (template.isEmpty()) return

        val now = java.util.Calendar.getInstance()
        val mins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val inWindow = if (AutoReplyConfig.startMinute <= AutoReplyConfig.endMinute)
            mins in AutoReplyConfig.startMinute..AutoReplyConfig.endMinute
        else mins >= AutoReplyConfig.startMinute || mins <= AutoReplyConfig.endMinute
        if (!inWindow) return

        val msgs = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "getMessagesFromIntent failed", e)
            null
        } ?: return
        val sender = msgs.firstOrNull()?.displayOriginatingAddress?.trim() ?: return
        val body = msgs.joinToString("") { it.displayMessageBody ?: "" }

        val containsSensitiveKw = SENSITIVE_SMS_KEYWORDS.any { kw ->
            body.contains(kw, ignoreCase = true)
        }
        val containsShortUrl = SHORT_URL_REGEX.containsMatchIn(body)
        val isSelfTemplate = body.trim() == template
        val shouldSkipReply = containsSensitiveKw ||
            containsShortUrl ||
            isSelfTemplate ||
            !TW_MOBILE_REGEX.matches(sender)

        if (shouldSkipReply) return

        val subId = intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            .takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                intent.getIntExtra(
                    SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID
                )
            } else {
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!canReply(appCtx, sender)) return@launch
                val smsManager = resolveSmsManager(appCtx, subId) ?: return@launch
                val parts = smsManager.divideMessage(template)
                if (parts.isNullOrEmpty()) return@launch
                smsManager.sendMultipartTextMessage(sender, null, parts, null, null)
                recordReply(appCtx, sender)
            } catch (e: SecurityException) {
                Log.w(TAG, "Auto-reply SEND_SMS SecurityException sender=$sender", e)
            } catch (e: Exception) {
                Log.e(TAG, "Auto-reply sendMultipartTextMessage failed sender=$sender", e)
            } finally {
                try {
                    pendingResult.finish()
                } catch (e: Exception) {
                    Log.e(TAG, "pendingResult.finish() failed", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "SmsAutoReply"
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

        private fun resolveSmsManager(ctx: Context, subId: Int): SmsManager? {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val base = ctx.getSystemService(SmsManager::class.java)
                    if (base != null && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        base.createForSubscriptionId(subId)
                    } else {
                        base
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        SmsManager.getSmsManagerForSubscriptionId(subId)
                    } else {
                        SmsManager.getDefault()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "resolveSmsManager failed subId=$subId", e)
                null
            }
        }

        @Synchronized
        internal fun canReply(
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
            return savedCount < MAX_REPLIES_PER_DAY
        }

        @Synchronized
        internal fun recordReply(
            ctx: Context,
            sender: String,
            nowMs: Long = System.currentTimeMillis()
        ) {
            val prefs = ctx.applicationContext.getSharedPreferences(COOLDOWN_PREFS, Context.MODE_PRIVATE)
            val tsKey = "last_reply_ts_$sender"
            val dateKey = "daily_reply_date_$sender"
            val countKey = "daily_reply_count_$sender"

            val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                .format(java.util.Date(nowMs))
            val savedDate = prefs.getString(dateKey, null)
            val savedCount = if (savedDate == today) prefs.getInt(countKey, 0) else 0

            prefs.edit()
                .putLong(tsKey, nowMs)
                .putString(dateKey, today)
                .putInt(countKey, savedCount + 1)
                .apply()
        }

        @Synchronized
        internal fun canReplyAndRecord(
            ctx: Context,
            sender: String,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            if (!canReply(ctx, sender, nowMs)) return false
            recordReply(ctx, sender, nowMs)
            return true
        }
    }
}

/** 讓使用者在原通知上快速回覆（不開啟原 App、不觸發已讀的替代方案） */
object QuickReplyHelper {
    fun canReply(sbn: Notification): Boolean =
        sbn.actions?.any { it.remoteInputs?.isNotEmpty() == true } == true

    fun buildQuickReplyAction(ctx: Context, sbn: Notification): Notification.Action? =
        sbn.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
}
