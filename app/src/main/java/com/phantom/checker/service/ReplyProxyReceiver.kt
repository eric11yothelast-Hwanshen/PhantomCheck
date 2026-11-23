package com.phantom.checker.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.phantom.checker.R

/**
 * 接收代理回覆通知的「回覆」、「關閉」、滑掉（deleteIntent）與閒置逾時事件。
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
                        val res = NotificationCollectorService.sendReplyToNotification(
                            context = appCtx,
                            packageName = session.packageName,
                            sender = session.sender,
                            contentSnippet = session.originalSnippet,
                            replyText = replyText
                        )
                        when (res) {
                            is NotificationCollectorService.Companion.ReplySendResult.Success -> {
                                Log.d(TAG, "Proxy reply sent pkg=${session.packageName} sender=${session.sender}")
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
                                        conversationKey = session.sender,
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
                                val isSmsFallback = session.packageName == "sms" ||
                                    TW_PHONE_REGEX.matches(session.sender.trim())
                                if (isSmsFallback) {
                                    try {
                                        @Suppress("DEPRECATION")
                                        SmsManager.getDefault().sendTextMessage(
                                            session.sender.trim(),
                                            null,
                                            replyText,
                                            null,
                                            null
                                        )
                                        NotificationCollectorService.recordOwnReplyEcho(
                                            ctx = appCtx,
                                            packageName = session.packageName,
                                            conversationKey = session.sender,
                                            replyText = replyText
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
                                                conversationKey = session.sender,
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
                                        Log.e(TAG, "Proxy SMS fallback failed sender=${session.sender}", e)
                                        ReplyProxyManager.appendMessageAndNotify(
                                            ctx = appCtx,
                                            sessionKey = sessionKey,
                                            isSelf = false,
                                            text = com.phantom.checker.PhantomApplication.str(R.string.notif_reply_rx_s_46ab77, e.message ?: com.phantom.checker.PhantomApplication.str(R.string.notif_reply_rx_s_df71eb))
                                        )
                                    }
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
        private val TW_PHONE_REGEX = Regex("^09\\d{8}$")
    }
}
