package com.phantom.checker.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.LruCache
import androidx.core.content.ContextCompat
import com.phantom.checker.data.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 記憶 × 防詐 L1 本地確定性比對器（#328）。
 *
 * 隱私紅線：
 * - 原始通訊錄聯絡人姓名、電話與任何 sensitive 記憶「絕不」外送給 AI API。
 * - 本物件僅在本機完成確定性交叉比對，對外只回傳簡短「矛盾訊號結論字串」（≤150 tokens，省 token 模式 ≤75 tokens）。
 * - READ_CONTACTS 權限未授予或遭拒絕時，自動降級為純記憶庫（category=person）比對，不中斷分析管線、不拋例外。
 */
object ContactContext {

    private const val CACHE_KEY = "contacts_display_names"
    private const val CACHE_TTL_MS = 30L * 60L * 1000L // 30 分鐘 TTL，禁止逐訊息全量重讀

    private data class CachedContacts(
        val normalizedNames: Set<String>,
        val loadedAtMs: Long,
        val permissionGranted: Boolean
    )

    private val contactsCache = LruCache<String, CachedContacts>(1)
    private val cacheGeneration = AtomicLong(1L)

    private val NON_ALNUM_CJK_REGEX = Regex("""[\p{P}\p{S}\s]+""")

    private val URGENCY_SCAM_KEYWORDS = listOf(
        "借錢", "急用", "周轉", "週轉", "驗證碼", "otp", "認證碼", "動態密碼",
        "馬上", "立刻", "緊急", "盡快", "儘快", "帳戶凍結", "帳戶遭凍結", "帳號凍結",
        "帳號異常", "匯款", "轉帳", "轉賬", "代付", "代收", "點數卡", "解除分期",
        "刷卡失敗", "包裹異常", "補件", "臨時改約", "換號碼", "新號碼", "新帳號",
        // #431 English urgency / scam keywords (lowercase for lowerContent.contains)
        "borrow money", "lend me", "need money", "emergency", "wire transfer",
        "bank transfer", "send money", "transfer funds", "pay for me", "gift card",
        "apple gift card", "zelle", "venmo", "verification code", "one-time code",
        "one-time passcode", "security code", "passcode", "seed phrase", "recovery phrase",
        "urgent", "immediately", "right away", "asap", "act now",
        "account suspended", "account locked", "account frozen", "account compromised",
        "unusual activity", "unauthorized charge", "fraud department", "safe account",
        "payment failed", "card declined", "unpaid toll", "delivery failed",
        "shipment on hold", "redelivery fee", "customs fee", "claim your refund",
        "refund department", "new number", "changed my number", "new account"
    )

    private val IMPERSONATION_SELF_REF_KEYWORDS = listOf(
        "我是", "猜猜我是誰", "換新號碼", "換號碼了", "新帳號", "加我新", "這是我新",
        "我手機壞了", "我換手機", "媽我", "爸我", "哥我", "姐我", "老同學", "前同事",
        // #431 English impersonation self-reference phrases (UK Report Fraud "Hi Mum", FTC family/boss impersonation)
        "hi mum", "hi mom", "hey mum", "hey mom", "hi dad", "hey dad",
        "guess who", "this is my new number", "my new number", "got a new number",
        "changed my number", "save my new number", "text me on my new",
        "broke my phone", "broken my phone", "lost my phone", "dropped my phone",
        "phone broke", "new phone", "add my new account", "my new account",
        "it's me", "former colleague", "old classmate", "your boss", "this is the ceo"
    )

    private val KNOWN_APP_KEYWORDS: Map<String, List<String>> = mapOf(
        "line" to listOf("jp.naver.line.android", "line"),
        "messenger" to listOf("com.facebook.orca", "messenger"),
        "whatsapp" to listOf("com.whatsapp", "whatsapp"),
        "telegram" to listOf("org.telegram.messenger", "telegram"),
        "instagram" to listOf("com.instagram.android", "instagram", "ig"),
        "sms" to listOf("sms", "簡訊", "訊息", "com.google.android.apps.messaging", "com.android.mms"),
        "gmail" to listOf("com.google.android.gm", "gmail", "email", "信箱", "郵件")
    )

    fun hasContactsPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** 供 AiClient riskCache 納入版本戳記，避免新授權或快取更新後仍命中舊快取 */
    fun getCacheGeneration(): Long = cacheGeneration.get()

    fun invalidateCache() {
        synchronized(contactsCache) {
            contactsCache.evictAll()
        }
        cacheGeneration.incrementAndGet()
    }

    private fun normalizeName(raw: String): String =
        raw.trim().lowercase(Locale.ROOT).replace(NON_ALNUM_CJK_REGEX, "")

    /**
     * 於 Dispatchers.IO 讀取本機通訊錄 displayName Set（含 30 分鐘 LruCache 快取）。
     * 權限未授予或發生任何例外皆回傳 emptySet()，絕不崩潰。
     */
    suspend fun getNormalizedContactNames(ctx: Context, nowMs: Long = System.currentTimeMillis()): Pair<Set<String>, Boolean> {
        val granted = hasContactsPermission(ctx)
        synchronized(contactsCache) {
            val cached = contactsCache.get(CACHE_KEY)
            if (cached != null &&
                cached.permissionGranted == granted &&
                (nowMs - cached.loadedAtMs) < CACHE_TTL_MS
            ) {
                return cached.normalizedNames to cached.permissionGranted
            }
        }
        if (!granted) {
            val empty = CachedContacts(emptySet(), nowMs, permissionGranted = false)
            synchronized(contactsCache) { contactsCache.put(CACHE_KEY, empty) }
            return emptySet<String>() to false
        }
        return withContext(Dispatchers.IO) {
            val names = LinkedHashSet<String>()
            try {
                val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                ctx.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                    if (idx >= 0) {
                        while (cursor.moveToNext()) {
                            val raw = cursor.getString(idx) ?: continue
                            val norm = normalizeName(raw)
                            if (norm.length >= 2) {
                                names.add(norm)
                            }
                        }
                    }
                }
            } catch (_: SecurityException) {
                // 權限在執行期間遭撤銷：降級純記憶比對
            } catch (_: Exception) {
                // 裝置通訊錄異常：安全降級
            }
            val entry = CachedContacts(names, nowMs, permissionGranted = true)
            synchronized(contactsCache) {
                contactsCache.put(CACHE_KEY, entry)
            }
            names to true
        }
    }

    /**
     * 離線確定性比對：結合本機通訊錄（若已授權）與一般分級之 person 記憶，產出「矛盾訊號結論字串」。
     * 不含任何原始通訊錄姓名／電話／sensitive 記憶。
     */
    suspend fun evaluateContradictionSignals(
        ctx: Context,
        sender: String,
        content: String,
        packageName: String,
        appLabel: String,
        timestampMs: Long,
        normalPersonMemories: List<MemoryEntity>,
        maxTokens: Int
    ): String {
        if (maxTokens <= 0) return ""
        val safePersonMemories = normalPersonMemories.filter {
            it.privacyTier == MemoryEntity.PRIVACY_NORMAL &&
                it.status == MemoryEntity.STATUS_ACTIVE &&
                it.category == MemoryEntity.CATEGORY_PERSON
        }
        val (contactNames, contactsGranted) = getNormalizedContactNames(ctx)

        val normSender = normalizeName(sender)
        val normContent = normalizeName(content)
        val lowerContent = content.lowercase(Locale.ROOT)
        val lowerPkg = packageName.lowercase(Locale.ROOT)
        val lowerApp = appLabel.lowercase(Locale.ROOT)

        val senderInContacts = normSender.length >= 2 && contactNames.any { it == normSender }
        val senderMatchedAlias = if (normSender.length >= 2) {
            runCatching {
                withContext(Dispatchers.IO) {
                    val aliases = com.phantom.checker.service.AppDatabaseHolder.db(ctx).contactAliasDao().all()
                    aliases.any { entry ->
                        val pkgMatch = entry.packageName.isNullOrBlank() ||
                            entry.packageName.equals(packageName, ignoreCase = true)
                        pkgMatch && normalizeName(entry.alias) == normSender
                    }
                }
            }.getOrDefault(false)
        } else {
            false
        }
        val matchedPersonMem = if (normSender.length >= 2) {
            safePersonMemories.firstOrNull { mem ->
                val subj = normalizeName(mem.subject)
                subj.length >= 2 && (subj == normSender || normSender.contains(subj) || subj.contains(normSender))
            }
        } else {
            null
        }
        val isKnownSender = senderInContacts || senderMatchedAlias || matchedPersonMem != null

        val hasUrgencyKeyword = URGENCY_SCAM_KEYWORDS.any { lowerContent.contains(it) }
        val hasImpersonationPhrase = IMPERSONATION_SELF_REF_KEYWORDS.any { lowerContent.contains(it) }

        // 檢查訊息內文是否提及已知聯絡人或 person 記憶中的人物稱呼（僅記錄「是否命中」，絕不輸出原始姓名）
        val mentionsKnownPersonInBody = if (!isKnownSender && normContent.length >= 4) {
            val hitMemoryPerson = safePersonMemories.any { mem ->
                val subj = normalizeName(mem.subject)
                subj.length >= 2 && normContent.contains(subj)
            }
            val hitContact = if (!hitMemoryPerson && contactNames.isNotEmpty()) {
                contactNames.any { name -> name.length >= 2 && normContent.contains(name) }
            } else {
                false
            }
            hitMemoryPerson || hitContact
        } else {
            false
        }

        val signals = mutableListOf<String>()

        // 訊號 1：陌生帳號＋自稱熟人＋急迫語氣（高風險冒充）
        if (!isKnownSender && (mentionsKnownPersonInBody || hasImpersonationPhrase)) {
            if (hasUrgencyKeyword) {
                signals.add("高風險矛盾：寄件者屬陌生帳號（不在通訊錄／熟人記憶），但內文疑似冒充熟人且含急迫借錢、匯款或索取驗證碼語氣")
            } else {
                signals.add("熟人冒充疑點：寄件者不在本機通訊錄或熟人記憶，但內文自稱熟人或提及熟人稱呼")
            }
        } else if (!isKnownSender && contactsGranted && hasUrgencyKeyword) {
            signals.add("陌生寄件者警示：寄件者不在本機通訊錄且訊息含高風險急迫或金錢／驗證碼話術")
        }

        // 訊號 2：已知人物的慣用 App 與實際來源 App 不符
        if (matchedPersonMem != null && (lowerPkg.isNotBlank() || lowerApp.isNotBlank())) {
            val memTextLower = "${matchedPersonMem.subject} ${matchedPersonMem.content}".lowercase(Locale.ROOT)
            val expectedChannels = KNOWN_APP_KEYWORDS.entries
                .filter { (_, tokens) -> tokens.any { memTextLower.contains(it) } }
                .map { it.key }
            if (expectedChannels.isNotEmpty()) {
                val actualMatchesExpected = expectedChannels.any { channel ->
                    val tokens = KNOWN_APP_KEYWORDS[channel].orEmpty()
                    tokens.any { tok -> lowerPkg.contains(tok) || lowerApp.contains(tok) }
                }
                if (!actualMatchesExpected) {
                    signals.add("通訊管道矛盾：此聯絡人本次來源 App 與記憶中記載的慣用聯絡 App 不符")
                }
            }
        }

        // 訊號 3：凌晨異常時段聯絡（00:00–05:30）
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val isLateNight = hour in 0..4 || (hour == 5 && minute <= 30)
        if (isLateNight && (hasUrgencyKeyword || matchedPersonMem != null)) {
            val memAllowsNight = matchedPersonMem?.content?.let { c ->
                c.contains("夜班") || c.contains("凌晨") || c.contains("時差") || c.contains("半夜") ||
                    c.contains("night shift", ignoreCase = true) ||
                    c.contains("time difference", ignoreCase = true) ||
                    c.contains("timezone", ignoreCase = true) ||
                    c.contains("late night", ignoreCase = true)
            } ?: false
            if (!memAllowsNight) {
                val hhmm = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
                signals.add("聯絡時段異常：訊息於凌晨 $hhmm 寄達，與此聯絡人一般慣用互動時段不符")
            }
        }

        if (signals.isEmpty()) return ""

        val charBudget = (maxTokens * 2).coerceAtLeast(60)
        return buildString {
            for (sig in signals) {
                val line = "- $sig\n"
                if (length + line.length > charBudget && isNotEmpty()) break
                append(line)
            }
        }.trimEnd()
    }
}
