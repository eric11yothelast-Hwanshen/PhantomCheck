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
                                    "對話已結束，請開啟原 App 回覆",
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
                                            text = "⚠️ 無法送出：${e.message ?: "簡訊發送失敗"}，請開啟原 App 回覆"
                                        )
                                    }
                                } else {
                                    ReplyProxyManager.appendMessageAndNotify(
                                        ctx = appCtx,
                                        sessionKey = sessionKey,
                                        isSelf = false,
                                        text = "⚠️ 無法送出：原始通知已移除，請開啟原 App 回覆"
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
                                "回覆失敗，請開啟原 App",
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
