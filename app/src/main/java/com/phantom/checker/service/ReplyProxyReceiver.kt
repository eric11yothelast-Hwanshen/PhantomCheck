package com.phantom.checker.service

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.phantom.checker.R

/**
 * 接收代理回覆通知的「回覆」、「確認送出簡訊」、「關閉」、滑掉（deleteIntent）與閒置逾時事件。
 */
class ReplyProxyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext
        val sessionKey = intent.getStringExtra(ReplyProxyManager.EXTRA_SESSION_KEY) ?: return
        when (intent.action) {
            ReplyProxyManager.ACTION_CLOSE,
            ReplyProxyManager.ACTION_TIMEOUT -> {
                try {
                    ReplyProxyManager.closeSession(appCtx, sessionKey, cancelNotification = true)
                } catch (e: Exception) {
                    Log.e(TAG, "closeSession(${intent.action}) failed sessionKey=$sessionKey", e)
                }
            }
            ReplyProxyManager.ACTION_DISMISSED -> {
                try {
                    ReplyProxyManager.closeSession(appCtx, sessionKey, cancelNotification = false)
                } catch (e: Exception) {
                    Log.e(TAG, "closeSession(ACTION_DISMISSED) failed sessionKey=$sessionKey", e)
                }
            }
            ReplyProxyManager.ACTION_CONFIRM_SMS -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val pair = ReplyProxyManager.consumePendingSms(sessionKey)
                        if (pair == null) {
                            Log.w(TAG, "ACTION_CONFIRM_SMS no pending sms sessionKey=$sessionKey")
                            return@launch
                        }
                        val (session, replyText) = pair
                        if (!isGenuineSmsFallbackAllowed(appCtx, session)) {
                            Log.w(TAG, "ACTION_CONFIRM_SMS rejected: not genuine SMS source or missing permission pkg=${session.packageName}")
                            ReplyProxyManager.appendMessageAndNotify(
                                ctx = appCtx,
                                sessionKey = sessionKey,
                                isSelf = false,
                                text = com.phantom.checker.PhantomApplication.str(R.string.notif_reply_rx_s_74cc37)
                            )
                            return@launch
                        }
                        val recipient = session.sender.trim()
                        val effectiveConvKey = session.conversationKey.ifBlank { session.sender }
                        try {
                            val smsManager = resolveSmsManager(appCtx)
                                ?: throw IllegalStateException("SmsManager unavailable")
                            val parts = smsManager.divideMessage(replyText)
                            if (parts.isNullOrEmpty()) {
                                throw IllegalStateException("Empty SMS parts")
                            }
                            NotificationCollectorService.recordOwnReplyEcho(
                                ctx = appCtx,
                                packageName = session.packageName,
                                conversationKey = effectiveConvKey,
                                replyText = replyText
                            )
                            smsManager.sendMultipartTextMessage(
                                recipient,
                                null,
                                parts,
                                null,
                                null
                            )
                            AppDatabaseHolder.db(appCtx).messageDao().insert(
                                com.phantom.checker.data.MessageEntity(
                                    packageName = session.packageName,
                                    appLabel = session.appLabel,
                                    sender = "我",
                                    content = replyText,
                                    timestamp = System.currentTimeMillis(),
                                    riskScore = 0,
                                    riskLabel = "",
                                    riskReason = "",
                                    isRead = true,
                                    conversationKey = effectiveConvKey,
                                    dedupKey = null,
                                    isOwn = true
                                )
                            )
                            ReplyProxyManager.appendMessageAndNotify(
                                ctx = appCtx,
                                sessionKey = sessionKey,
                                isSelf = true,
                                text = replyText
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Confirmed proxy SMS send failed sender=${session.sender}", e)
                            ReplyProxyManager.appendMessageAndNotify(
                                ctx = appCtx,
                                sessionKey = sessionKey,
                                isSelf = false,
                                text = com.phantom.checker.PhantomApplication.str(
                                    R.string.notif_reply_rx_s_46ab77,
                                    e.message ?: com.phantom.checker.PhantomApplication.str(R.string.notif_reply_rx_s_df71eb)
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "ACTION_CONFIRM_SMS unexpected failure sessionKey=$sessionKey", e)
                    } finally {
                        try {
                            pendingResult.finish()
                        } catch (e: Exception) {
                            Log.e(TAG, "pendingResult.finish() failed", e)
                        }
                    }
                }
            }
            ReplyProxyManager.ACTION_REPLY -> {
                val replyText = try {
                    RemoteInput.getResultsFromIntent(intent)
                        ?.getCharSequence(ReplyProxyManager.REMOTE_INPUT_KEY)
                        ?.toString()
                        ?.trim()
                        .orEmpty()
                } catch (e: Exception) {
                    Log.e(TAG, "RemoteInput.getResultsFromIntent failed sessionKey=$sessionKey", e)
                    ""
                }
                if (replyText.isEmpty()) {
                    Log.w(TAG, "ACTION_REPLY empty replyText sessionKey=$sessionKey")
                    return
                }

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val session = ReplyProxyManager.getSession(sessionKey)
                        if (session == null) {
                            Log.w(TAG, "ACTION_REPLY session expired/missing sessionKey=$sessionKey")
                            val notifId = NotifIds.replyProxy(sessionKey.hashCode())
                            try {
                                appCtx.getSystemService(NotificationManager::class.java)
                                    ?.cancel(notifId)
                            } catch (_: Exception) {
                            }
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    appCtx,
                                    com.phantom.checker.PhantomApplication.str(R.string.notif_reply_rx_s_ce3721),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return@launch
                        }
                        val effectiveConvKey = session.conversationKey.ifBlank { session.sender }
                        val res = NotificationCollectorService.sendReplyToNotification(
                            context = appCtx,
                            packageName = session.packageName,
                            sender = session.sender,
                            contentSnippet = session.originalSnippet,
                            replyText = replyText,
                            conversationKey = effectiveConvKey
                        )
                        when (res) {
                            is NotificationCollectorService.Companion.ReplySendResult.Success -> {
                                Log.d(TAG, "Proxy reply sent pkg=${session.packageName} sender=${session.sender} convKey=$effectiveConvKey")
                                AppDatabaseHolder.db(appCtx).messageDao().insert(
                                    com.phantom.checker.data.MessageEntity(
                                        packageName = session.packageName,
                                        appLabel = session.appLabel,
                                        sender = "我",
                                        content = replyText,
                                        timestamp = System.currentTimeMillis(),
                                        riskScore = 0,
                                        riskLabel = "",
                                        riskReason = "",
                                        isRead = true,
                                        conversationKey = effectiveConvKey,
                                        dedupKey = null,
                                        isOwn = true
                                    )
                                )
                                ReplyProxyManager.appendMessageAndNotify(
                                    ctx = appCtx,
                                    sessionKey = sessionKey,
                                    isSelf = true,
                                    text = replyText
                                )
                            }
                            is NotificationCollectorService.Companion.ReplySendResult.Unavailable -> {
                                Log.w(TAG, "Proxy reply unavailable pkg=${session.packageName} reason=${res.reason}")
                                if (isGenuineSmsFallbackAllowed(appCtx, session)) {
                                    // 僅真正簡訊來源才提供付費簡訊替代選項，且須經使用者點擊確認按鈕才發送
                                    ReplyProxyManager.requestSmsFallbackConfirmation(
                                        ctx = appCtx,
                                        sessionKey = sessionKey,
                                        pendingReplyText = replyText,
                                        noticeText = com.phantom.checker.PhantomApplication.str(R.string.notif_reply_rx_s_74cc37)
                                    )
                                } else {
                                    ReplyProxyManager.appendMessageAndNotify(
                                        ctx = appCtx,
                                        sessionKey = sessionKey,
                                        isSelf = false,
                                        text = com.phantom.checker.PhantomApplication.str(R.string.notif_reply_rx_s_74cc37)
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "ACTION_REPLY unexpected failure sessionKey=$sessionKey", e)
                        val notifId = NotifIds.replyProxy(sessionKey.hashCode())
                        try {
                            appCtx.getSystemService(NotificationManager::class.java)?.cancel(notifId)
                        } catch (_: Exception) {
                        }
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                appCtx,
                                com.phantom.checker.PhantomApplication.str(R.string.notif_reply_rx_s_74000d),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } finally {
                        try {
                            pendingResult.finish()
                        } catch (e: Exception) {
                            Log.e(TAG, "pendingResult.finish() failed", e)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "PhantomReply"
        private val PHONE_NUMBER_REGEX = Regex("^(?:\\+?[0-9]{7,15}|09\\d{8})$")

        internal fun isGenuineSmsSource(ctx: Context, packageName: String): Boolean {
            if (packageName == "sms") return true
            val defaultSmsPkg = try {
                Telephony.Sms.getDefaultSmsPackage(ctx)
            } catch (_: Exception) {
                null
            }
            return !defaultSmsPkg.isNullOrBlank() && packageName == defaultSmsPkg
        }

        private fun isGenuineSmsFallbackAllowed(
            ctx: Context,
            session: ReplyProxyManager.ProxySession
        ): Boolean {
            if (!isGenuineSmsSource(ctx, session.packageName)) return false
            if (ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.SEND_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            val normalizedPhone = session.sender.trim().replace(" ", "").replace("-", "")
            return PHONE_NUMBER_REGEX.matches(normalizedPhone)
        }

        private fun resolveSmsManager(ctx: Context): SmsManager? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }
}
