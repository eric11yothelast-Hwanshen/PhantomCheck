package com.phantom.checker.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phantom.checker.network.AiClient
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.util.ContactContext
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 個人長期／短期記憶庫核心引擎（#314 A2 / A3 / A5）。
 *
 * 鐵律遵守：
 * 1. 隱私分級：privacyTier=sensitive 一律禁止進入任何對外 prompt（靜態可驗證：組注入清單處硬排除，僅讀 MemoryEntity.PRIVACY_NORMAL）。
 * 2. 衝突偵測：同 category+subject 且 status=active 而內容實質不同（normalize 去標點/大小寫/空白）→ 不覆蓋，舊筆轉 status=conflict 並保留原值，
 *    新增/候選筆可待使用者確認卡或記憶頁衝突區塊裁定（「更新記憶／保留原樣」）；裁定後 confidence=confirmed、衝突筆封存。
 * 3. 短期記憶 expiresAt 到期 → status=archived：查詢時懶過濾 ＋ WorkManager 每日封存 worker（ExistingPeriodicWorkPolicy.KEEP）。
 * 4. 來源標記（provenance）：每筆記憶可追溯到來源通知或對話（sourceRef）。
 * 5. 確定性檢索評分（無 embedding、離線可用）：score = 關鍵詞重疊 + 新近度 + confirmed 加權 + useCount。
 */
object MemoryEngine {

    val ALLOWED_CATEGORIES = setOf(
        MemoryEntity.CATEGORY_PERSON,
        MemoryEntity.CATEGORY_PLACE,
        MemoryEntity.CATEGORY_SCHEDULE,
        MemoryEntity.CATEGORY_PREFERENCE,
        MemoryEntity.CATEGORY_ROUTINE,
        MemoryEntity.CATEGORY_RELATIONSHIP,
        MemoryEntity.CATEGORY_SCAM_IMMUNITY,
        MemoryEntity.CATEGORY_INTEREST,
        MemoryEntity.CATEGORY_OTHER
    )

    private val PUNCT_AND_SPACE_REGEX = Regex("""[\p{P}\p{S}\s]+""")
    private val CJK_CHAR_REGEX = Regex("""[\u4E00-\u9FFF]""")
    private val WORD_TOKEN_REGEX = Regex("""[a-zA-Z0-9]{2,}|[\u4E00-\u9FFF]{2,}""")

    private val MONEY_URGENCY_OTP_KEYWORDS = listOf(
        "借錢", "急用", "轉帳", "轉賬", "匯款", "驗證碼", "otp", "認證碼", "動態密碼",
        "馬上", "立刻", "緊急", "凍結", "刷卡", "扣款", "包裹", "異常", "補件",
        "解除分期", "投資", "獲利", "提領", "客服", "點數", "罰單", "催繳",
        // #431 English money / urgency / OTP / scam keywords (lowercase for lower.contains)
        "borrow money", "lend me", "wire transfer", "bank transfer", "send money",
        "transfer funds", "safe account", "gift card", "zelle", "venmo", "crypto",
        "verification code", "one-time code", "one-time passcode", "security code",
        "passcode", "seed phrase", "recovery phrase",
        "urgent", "immediately", "act now", "right away",
        "account suspended", "account locked", "account frozen", "account compromised",
        "unauthorized", "fraud department",
        "unpaid toll", "outstanding toll", "package", "parcel", "redelivery",
        "customs fee", "refund", "customer support", "tech support", "overdue",
        "late fee", "arrest warrant",
        "investment", "guaranteed profit", "guaranteed return", "trading group",
        "withdrawal fee", "unlock fee", "margin deposit"
    )

    fun normalizeCategory(raw: String?): String {
        val clean = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return if (clean in ALLOWED_CATEGORIES) clean else MemoryEntity.CATEGORY_OTHER
    }

    fun categoryLabel(category: String): String = when (category.trim().lowercase(Locale.ROOT)) {
        MemoryEntity.CATEGORY_PERSON -> "人物"
        MemoryEntity.CATEGORY_PLACE -> "地點"
        MemoryEntity.CATEGORY_SCHEDULE -> "行程"
        MemoryEntity.CATEGORY_PREFERENCE -> "偏好"
        MemoryEntity.CATEGORY_ROUTINE -> "習慣"
        MemoryEntity.CATEGORY_RELATIONSHIP -> "關係"
        MemoryEntity.CATEGORY_SCAM_IMMUNITY -> "防詐免疫"
        MemoryEntity.CATEGORY_INTEREST -> "興趣"
        else -> "其他"
    }

    fun layerLabel(layer: String): String = when (layer) {
        MemoryEntity.LAYER_SHORT_TERM -> "短期"
        else -> "長期"
    }

    fun statusLabel(status: String): String = when (status) {
        MemoryEntity.STATUS_CONFLICT -> "衝突待確認"
        MemoryEntity.STATUS_ARCHIVED -> "已封存"
        else -> "使用中"
    }

    fun confidenceLabel(confidence: String): String = when (confidence) {
        MemoryEntity.CONFIDENCE_CONFIRMED -> "已確認"
        else -> "AI 推測"
    }

    fun privacyLabel(privacyTier: String): String = when (privacyTier) {
        MemoryEntity.PRIVACY_SENSITIVE -> "敏感（僅本機）"
        else -> "一般"
    }

    fun sourceTypeLabel(sourceType: String): String = when (sourceType) {
        MemoryEntity.SOURCE_NOTIFICATION -> "通知"
        MemoryEntity.SOURCE_CHAT -> "AI 對話"
        MemoryEntity.SOURCE_CONTACTS -> "通訊錄匯入"
        else -> "手動"
    }

    /** 正規化 subject 作為衝突偵測鍵（去首尾空白與多餘空白，小寫化） */
    fun normalizeSubjectKey(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    /** 正規化內容以比對是否「實質不同」（去標點、符號、空白與大小寫差異） */
    fun normalizeContentForComparison(raw: String): String =
        raw.trim().lowercase(Locale.ROOT).replace(PUNCT_AND_SPACE_REGEX, "")

    fun isSubstantiallySameContent(a: String, b: String): Boolean {
        val na = normalizeContentForComparison(a)
        val nb = normalizeContentForComparison(b)
        return na.isNotEmpty() && na == nb
    }

    /** 格式化來源標記（provenance）：app+寄件者+時間 或 對話句摘要 */
    fun formatNotificationSourceRef(
        messageId: Long,
        appLabel: String,
        sender: String,
        timestampMs: Long,
        timeFormatted: String
    ): String {
        val cleanApp = appLabel.trim().ifEmpty { "通知" }
        val cleanSender = sender.trim().ifEmpty { cleanApp }
        return "通知 #$messageId｜$cleanApp・$cleanSender（$timeFormatted）"
    }

    private val NOTIFICATION_SOURCE_ID_REGEX = Regex("""通知\s*#(\d+)""")

    /** 從記憶 provenance（sourceRef）解析來源通知 messageId，供記憶頁直接跳轉 MessageDetailActivity */
    fun extractRelatedMessageId(sourceRef: String?): Long? {
        if (sourceRef.isNullOrBlank()) return null
        return NOTIFICATION_SOURCE_ID_REGEX.find(sourceRef)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    fun formatChatSourceRef(sessionId: Long?, userSnippet: String, timeFormatted: String): String {
        val snippet = userSnippet.replace("\n", " ").trim().take(36)
        val sessionPart = sessionId?.let { "對話 #$it" } ?: "對話"
        return if (snippet.isNotEmpty()) {
            "$sessionPart（$timeFormatted）：「$snippet」"
        } else {
            "$sessionPart（$timeFormatted）"
        }
    }

    /**
     * 懶過濾與自動封存已過期的短期記憶（查詢前呼叫，確保過期短期記憶轉為 archived）。
     */
    suspend fun lazyArchiveExpiredShortTerm(
        db: AppDatabase,
        nowMs: Long = System.currentTimeMillis()
    ): Int = withContext(Dispatchers.IO) {
        db.memoryDao().archiveExpiredShortTerm(nowMs)
    }

    sealed class WriteOutcome {
        /** 直接新增成功（無同 category+subject 的 active 舊筆） */
        data class Inserted(val memory: MemoryEntity) : WriteOutcome()
        /** 既有同 category+subject 的 active 記憶內容實質相同，僅更新時間與確認狀態，不產生重複或假衝突 */
        data class Deduplicated(val memory: MemoryEntity) : WriteOutcome()
        /**
         * 偵測到同 category+subject 且內容實質不同：
         * - 舊筆轉為 status=conflict 並保留原值（不覆蓋）
         * - 新筆同樣以 status=conflict 寫入（或供確認卡/記憶頁衝突區塊顯示雙版本比對與「更新記憶／保留原樣」裁定）
         */
        data class ConflictDetected(
            val existingMemory: MemoryEntity,
            val incomingMemory: MemoryEntity
        ) : WriteOutcome()
    }

    /**
     * 檢查同 category+subject 是否存在內容實質不同的 active 記憶（供聊天確認卡在寫入前預先提示衝突）。
     */
    suspend fun previewConflict(
        db: AppDatabase,
        category: String,
        subject: String,
        proposedContent: String,
        nowMs: Long = System.currentTimeMillis()
    ): MemoryEntity? = withContext(Dispatchers.IO) {
        lazyArchiveExpiredShortTerm(db, nowMs)
        val normCat = normalizeCategory(category)
        val normSubj = subject.trim().ifEmpty { proposedContent.take(16).trim() }
        val targetSubjKey = normalizeSubjectKey(normSubj)
        val activeCandidates = db.memoryDao().findActiveNonExpiredByCategory(normCat, nowMs)
            .filter { normalizeSubjectKey(it.subject) == targetSubjKey }
        activeCandidates.firstOrNull { existing ->
            !isSubstantiallySameContent(existing.content, proposedContent)
        }
    }

    /**
     * 寫入記憶管線（含衝突偵測，不靜默覆蓋既有 active 記憶）。
     * - 若 forceReplaceConflict == true（例如使用者已在聊天確認卡點選「更新記憶」直接裁定）：
     *   將舊筆封存（status=archived），新筆以 status=active, confidence=confirmed 寫入。
     * - 若 forceKeepExisting == true（使用者在確認卡點選「保留原樣」）：
     *   舊筆升級為 confidence=confirmed，不寫入新值。
     * - 若未預先裁定且發生衝突：
     *   舊筆轉 status=conflict 並保留原值，新候選筆也以 status=conflict 寫入，雙入口（聊天確認卡＋記憶頁衝突區塊）皆可裁定。
     */
    suspend fun writeMemoryWithConflictDetection(
        db: AppDatabase,
        category: String,
        subject: String,
        content: String,
        layer: String = MemoryEntity.LAYER_LONG_TERM,
        expiresAt: Long? = null,
        privacyTier: String = MemoryEntity.PRIVACY_NORMAL,
        confidence: String = MemoryEntity.CONFIDENCE_INFERRED,
        sourceType: String = MemoryEntity.SOURCE_CHAT,
        sourceRef: String? = null,
        forceReplaceConflict: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): WriteOutcome = withContext(Dispatchers.IO) {
        lazyArchiveExpiredShortTerm(db, nowMs)
        val normCat = normalizeCategory(category)
        val cleanSubject = subject.trim().ifEmpty { content.trim().take(16) }
        val cleanContent = content.trim()
        val normLayer = if (layer == MemoryEntity.LAYER_SHORT_TERM) {
            MemoryEntity.LAYER_SHORT_TERM
        } else {
            MemoryEntity.LAYER_LONG_TERM
        }
        val effectiveExpiresAt = if (normLayer == MemoryEntity.LAYER_SHORT_TERM) {
            expiresAt ?: (nowMs + TimeUnit.DAYS.toMillis(7))
        } else {
            null
        }
        val normPrivacy = if (privacyTier == MemoryEntity.PRIVACY_SENSITIVE) {
            MemoryEntity.PRIVACY_SENSITIVE
        } else {
            MemoryEntity.PRIVACY_NORMAL
        }

        db.withTransaction {
            val dao = db.memoryDao()
            val targetSubjKey = normalizeSubjectKey(cleanSubject)
            val matchingActive = dao.findActiveNonExpiredByCategory(normCat, nowMs).filter {
                normalizeSubjectKey(it.subject) == targetSubjKey
            }
            val sameContentExisting = matchingActive.firstOrNull {
                isSubstantiallySameContent(it.content, cleanContent)
            }
            if (sameContentExisting != null) {
                val upgradedConf = if (confidence == MemoryEntity.CONFIDENCE_CONFIRMED) {
                    MemoryEntity.CONFIDENCE_CONFIRMED
                } else {
                    sameContentExisting.confidence
                }
                val updated = sameContentExisting.copy(
                    updatedAt = nowMs,
                    confidence = upgradedConf,
                    expiresAt = effectiveExpiresAt ?: sameContentExisting.expiresAt
                )
                dao.update(updated)
                return@withTransaction WriteOutcome.Deduplicated(updated)
            }

            val conflictingExisting = matchingActive.firstOrNull()
            if (conflictingExisting != null) {
                if (forceReplaceConflict) {
                    // 使用者已明確選擇「更新記憶」：舊筆封存，新筆 confirmed + active
                    dao.update(
                        conflictingExisting.copy(
                            status = MemoryEntity.STATUS_ARCHIVED,
                            updatedAt = nowMs
                        )
                    )
                    val newEntity = MemoryEntity(
                        category = normCat,
                        subject = cleanSubject,
                        content = cleanContent,
                        layer = normLayer,
                        status = MemoryEntity.STATUS_ACTIVE,
                        expiresAt = effectiveExpiresAt,
                        privacyTier = normPrivacy,
                        confidence = MemoryEntity.CONFIDENCE_CONFIRMED,
                        sourceType = sourceType,
                        sourceRef = sourceRef,
                        createdAt = nowMs,
                        updatedAt = nowMs
                    )
                    val newId = dao.insert(newEntity)
                    return@withTransaction WriteOutcome.Inserted(newEntity.copy(id = newId))
                } else {
                    // 不覆蓋！舊筆轉 status=conflict 並保留原值，新筆以 status=conflict 進入衝突待確認
                    val conflictOld = conflictingExisting.copy(
                        status = MemoryEntity.STATUS_CONFLICT,
                        updatedAt = nowMs
                    )
                    dao.update(conflictOld)
                    val conflictNew = MemoryEntity(
                        category = normCat,
                        subject = conflictingExisting.subject,
                        content = cleanContent,
                        layer = normLayer,
                        status = MemoryEntity.STATUS_CONFLICT,
                        expiresAt = effectiveExpiresAt,
                        privacyTier = normPrivacy,
                        confidence = confidence,
                        sourceType = sourceType,
                        sourceRef = sourceRef,
                        createdAt = nowMs,
                        updatedAt = nowMs
                    )
                    val newId = dao.insert(conflictNew)
                    return@withTransaction WriteOutcome.ConflictDetected(
                        existingMemory = conflictOld,
                        incomingMemory = conflictNew.copy(id = newId)
                    )
                }
            }

            val entity = MemoryEntity(
                category = normCat,
                subject = cleanSubject,
                content = cleanContent,
                layer = normLayer,
                status = MemoryEntity.STATUS_ACTIVE,
                expiresAt = effectiveExpiresAt,
                privacyTier = normPrivacy,
                confidence = confidence,
                sourceType = sourceType,
                sourceRef = sourceRef,
                createdAt = nowMs,
                updatedAt = nowMs
            )
            val id = dao.insert(entity)
            WriteOutcome.Inserted(entity.copy(id = id))
        }
    }

    /**
     * 裁定衝突記憶（記憶頁衝突區塊與聊天衝突確認卡共用）：
     * - keepMemoryId：被選為保留/更新的記憶 → status=active, confidence=confirmed
     * - 同組（同 category + 正規化 subject 且 status=conflict）其餘記憶 → status=archived
     */
    suspend fun resolveConflictGroup(
        db: AppDatabase,
        chosenMemoryId: Long,
        nowMs: Long = System.currentTimeMillis()
    ): MemoryEntity? = withContext(Dispatchers.IO) {
        db.withTransaction {
            val dao = db.memoryDao()
            val chosen = dao.byId(chosenMemoryId) ?: return@withTransaction null
            val subjKey = normalizeSubjectKey(chosen.subject)
            val sameGroupCandidates = dao.findConflictOrActiveByCategory(chosen.category).filter {
                it.id != chosen.id && normalizeSubjectKey(it.subject) == subjKey
            }
            sameGroupCandidates.forEach { item ->
                dao.update(
                    item.copy(
                        status = MemoryEntity.STATUS_ARCHIVED,
                        updatedAt = nowMs
                    )
                )
            }
            val resolved = chosen.copy(
                status = MemoryEntity.STATUS_ACTIVE,
                confidence = MemoryEntity.CONFIDENCE_CONFIRMED,
                updatedAt = nowMs
            )
            dao.update(resolved)
            resolved
        }
    }

    /** 從已封存恢復記憶為 active（若為已過期短期記憶，自動延長 7 天或轉回有效狀態） */
    suspend fun restoreArchivedMemory(
        db: AppDatabase,
        memoryId: Long,
        nowMs: Long = System.currentTimeMillis()
    ): MemoryEntity? = withContext(Dispatchers.IO) {
        val dao = db.memoryDao()
        val target = dao.byId(memoryId) ?: return@withContext null
        val newExpires = if (target.layer == MemoryEntity.LAYER_SHORT_TERM &&
            (target.expiresAt == null || target.expiresAt <= nowMs)
        ) {
            nowMs + TimeUnit.DAYS.toMillis(7)
        } else {
            target.expiresAt
        }
        val restored = target.copy(
            status = MemoryEntity.STATUS_ACTIVE,
            expiresAt = newExpires,
            updatedAt = nowMs
        )
        dao.update(restored)
        restored
    }

    /** 封存指定記憶 */
    suspend fun archiveMemory(
        db: AppDatabase,
        memoryId: Long,
        nowMs: Long = System.currentTimeMillis()
    ): MemoryEntity? = withContext(Dispatchers.IO) {
        val dao = db.memoryDao()
        val target = dao.byId(memoryId) ?: return@withContext null
        val archived = target.copy(
            status = MemoryEntity.STATUS_ARCHIVED,
            updatedAt = nowMs
        )
        dao.update(archived)
        archived
    }

    // ==================== A3 確定性檢索評分與 Prompt 注入（含敏感記憶硬閘門） ====================

    data class RetrievedMemoryPrompt(
        val promptBlock: String,
        val hitMemoryIds: List<Long>,
        val estimatedTokens: Int
    )

    private fun extractQueryTerms(text: String): Set<String> {
        val lower = text.lowercase(Locale.ROOT)
        val out = LinkedHashSet<String>()
        WORD_TOKEN_REGEX.findAll(lower).forEach { m ->
            val w = m.value
            out.add(w)
            // CJK 雙字元切分（2-gram）提升中文無空格短句召回率
            if (w.length >= 2 && CJK_CHAR_REGEX.containsMatchIn(w)) {
                for (i in 0 until w.length - 1) {
                    out.add(w.substring(i, i + 2))
                }
            }
        }
        // 單字元 CJK 備援（僅在無雙字詞時）
        if (out.isEmpty()) {
            CJK_CHAR_REGEX.findAll(lower).forEach { out.add(it.value) }
        }
        return out
    }

    /**
     * 純確定性記憶評分（離線可用、零 embedding）：
     * score = 關鍵詞重疊(使用者訊息 vs subject+content) + 新近度 + confirmed 加權 + useCount
     */
    fun scoreMemory(
        memory: MemoryEntity,
        queryTerms: Set<String>,
        rawQueryLower: String,
        nowMs: Long
    ): Double {
        val subjectLower = memory.subject.lowercase(Locale.ROOT)
        val contentLower = memory.content.lowercase(Locale.ROOT)
        val haystack = "$subjectLower $contentLower"

        var overlapScore = 0.0
        if (subjectLower.isNotBlank() && rawQueryLower.contains(subjectLower)) {
            overlapScore += 35.0
        }
        queryTerms.forEach { term ->
            if (subjectLower.contains(term)) {
                overlapScore += if (term.length >= 2) 12.0 else 4.0
            } else if (haystack.contains(term)) {
                overlapScore += if (term.length >= 2) 6.0 else 2.0
            }
        }

        val ageDays = ((nowMs - memory.updatedAt).coerceAtLeast(0L) / (24.0 * 3600_000.0))
        val recencyScore = when {
            ageDays <= 1.0 -> 15.0
            ageDays <= 7.0 -> 10.0
            ageDays <= 30.0 -> 5.0
            else -> 2.0
        }

        val confirmedBonus = if (memory.confidence == MemoryEntity.CONFIDENCE_CONFIRMED) 10.0 else 0.0
        val useBonus = (memory.useCount.coerceIn(0, 20)) * 0.8

        return overlapScore + recencyScore + confirmedBonus + useBonus
    }

    /**
     * 估算中英混排 token 數（中文字約 1.5 token/字，英數約 0.25 token/字，保守取 max）。
     */
    fun estimateMemoryTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var cjkCount = 0
        var otherCount = 0
        for (ch in text) {
            if (ch.code in 0x4E00..0x9FFF || ch.code in 0x3000..0x303F || ch.code in 0xFF00..0xFFEF) {
                cjkCount++
            } else {
                otherCount++
            }
        }
        val est = (cjkCount * 1.4 + otherCount * 0.3).toInt().coerceAtLeast(1)
        return maxOf(est, AiClient.estimateTokens(text))
    }

    /**
     * 組裝注入 chatAssistant systemPrompt 的【長期記憶】區塊。
     *
     * 鐵律 3 靜態可驗證敏感閘門：
     * 1. DAO 查詢 `activeNormalMemoriesForPrompt(nowMs)` 的 SQL 已硬寫 `privacyTier = 'normal'`
     * 2. Kotlin 層再做二次硬過濾 `.filter { it.privacyTier == MemoryEntity.PRIVACY_NORMAL && it.status == MemoryEntity.STATUS_ACTIVE }`
     *    確保 `privacyTier == "sensitive"` 的記憶在任何情況下都不可能進入 prompt！
     *
     * @param degradeFactor 降載倍率（1.0 = 正常預算；0.5 = onRebuildSystemPromptForRetry 第一次降載；0 = 極限降載省略）
     * @param recordHitUsage 是否更新命中記憶的 lastUsedAt 與 useCount（初次組裝為 true，retry 重組為 false 避免重複計數）
     */
    suspend fun buildMemoryInjectionBlock(
        ctx: Context,
        db: AppDatabase,
        userQuery: String,
        degradeFactor: Float = 1.0f,
        recordHitUsage: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ): RetrievedMemoryPrompt = withContext(Dispatchers.IO) {
        if (degradeFactor <= 0.05f) {
            return@withContext RetrievedMemoryPrompt("", emptyList(), 0)
        }
        lazyArchiveExpiredShortTerm(db, nowMs)

        // 雙重硬排除 privacyTier=sensitive 與過期短期記憶（靜態可驗證）
        val rawCandidates = db.memoryDao().activeNormalMemoriesForPrompt(nowMs)
            .filter { mem ->
                mem.privacyTier == MemoryEntity.PRIVACY_NORMAL &&
                    mem.status == MemoryEntity.STATUS_ACTIVE &&
                    (mem.layer != MemoryEntity.LAYER_SHORT_TERM || mem.expiresAt == null || mem.expiresAt > nowMs)
            }

        val portraitText = PhantomPrefs.getPortraitCardText(ctx)
        if (rawCandidates.isEmpty() && portraitText.isBlank()) {
            return@withContext RetrievedMemoryPrompt("", emptyList(), 0)
        }

        val tokenSaver = PhantomPrefs.isTokenSaverEnabled(ctx)
        val baseTopK = PhantomPrefs.getEffectiveMemoryInjectionTopK(ctx)
        // #337 畫像卡優先注入，其後 top-K 減半（省 token 模式僅畫像卡 + 最多 2 筆）
        val adjustedTopK = if (portraitText.isNotBlank()) {
            if (tokenSaver) minOf(2, (baseTopK / 2).coerceAtLeast(1)) else (baseTopK / 2).coerceAtLeast(1)
        } else {
            baseTopK
        }
        val effectiveTopK = (adjustedTopK * degradeFactor).toInt().coerceAtLeast(1)
        val perItemCharCap = PhantomPrefs.getEffectiveMemoryPerItemMaxChars(ctx)
        val baseTokenBudget = PhantomPrefs.getEffectiveMemoryTotalTokenBudget(ctx)
        val effectiveTokenBudget = (baseTokenBudget * degradeFactor).toInt().coerceAtLeast(30)

        val queryLower = userQuery.trim().lowercase(Locale.ROOT)
        val queryTerms = extractQueryTerms(userQuery)

        val ranked = rawCandidates
            .map { mem -> mem to scoreMemory(mem, queryTerms, queryLower, nowMs) }
            .sortedWith(
                compareByDescending<Pair<MemoryEntity, Double>> { it.second }
                    .thenByDescending { it.first.updatedAt }
            )
            .take(effectiveTopK)

        val header = "【長期記憶】"
        var usedTokens = estimateMemoryTokens(header)
        val lines = mutableListOf<String>()
        val selectedIds = mutableListOf<Long>()

        if (portraitText.isNotBlank()) {
            val safePortrait = AiClient.sanitizeForPrompt(portraitText.replace("\n", " ").trim()).take(if (tokenSaver) 140 else 240)
            val portraitLine = "【我對你的瞭解】$safePortrait"
            lines.add(portraitLine)
            usedTokens += estimateMemoryTokens(portraitLine)
        }

        for ((mem, _) in ranked) {
            // 再次靜態斷言：絕不允許 sensitive 記憶進入
            if (mem.privacyTier != MemoryEntity.PRIVACY_NORMAL) continue

            val catTag = categoryLabel(mem.category)
            val layerTag = if (mem.layer == MemoryEntity.LAYER_SHORT_TERM) "短期" else "長期"
            val confTag = if (mem.confidence == MemoryEntity.CONFIDENCE_CONFIRMED) "已確認" else "推測"
            val safeSubj = AiClient.sanitizeForPrompt(mem.subject.replace("\n", " ").trim()).take(18)
            val safeContent = AiClient.sanitizeForPrompt(mem.content.replace("\n", " ").trim()).take(perItemCharCap)
            val line = "- [#${mem.id}][$catTag/$layerTag/$confTag] $safeSubj：$safeContent"
            val lineTokens = estimateMemoryTokens(line)
            if (usedTokens + lineTokens > effectiveTokenBudget && lines.isNotEmpty()) {
                break
            }
            if (usedTokens + lineTokens <= effectiveTokenBudget || lines.isEmpty()) {
                val boundedLine = if (usedTokens + lineTokens > effectiveTokenBudget) {
                    line.take((effectiveTokenBudget * 2).coerceAtLeast(24))
                } else {
                    line
                }
                lines.add(boundedLine)
                usedTokens += estimateMemoryTokens(boundedLine)
                selectedIds.add(mem.id)
            }
        }

        if (lines.isEmpty()) {
            return@withContext RetrievedMemoryPrompt("", emptyList(), 0)
        }

        if (recordHitUsage && selectedIds.isNotEmpty()) {
            runCatching {
                db.memoryDao().recordUsage(selectedIds, nowMs)
            }
        }

        val block = buildString {
            appendLine(header)
            lines.forEach { appendLine(it) }
        }.trimEnd()

        RetrievedMemoryPrompt(
            promptBlock = block,
            hitMemoryIds = selectedIds,
            estimatedTokens = usedTokens
        )
    }

    // ==================== #337 「我對你的瞭解」畫像卡（≤200 tokens，敏感記憶絕不進入） ====================

    private const val PORTRAIT_REFRESH_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L
    private const val PORTRAIT_NEW_CONFIRMED_THRESHOLD = 20
    private const val PORTRAIT_DAILY_MAX_GENS = 3

    suspend fun shouldRefreshPortrait(
        ctx: Context,
        db: AppDatabase,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val normals = db.memoryDao().activeNormalMemoriesForPrompt(nowMs)
            .filter { it.privacyTier == MemoryEntity.PRIVACY_NORMAL && it.status == MemoryEntity.STATUS_ACTIVE }
        if (normals.isEmpty()) return@withContext false
        val existing = PhantomPrefs.getPortraitCardText(ctx)
        if (existing.isBlank()) return@withContext true
        val lastAt = PhantomPrefs.getPortraitCardUpdatedAt(ctx)
        if (nowMs - lastAt >= PORTRAIT_REFRESH_INTERVAL_MS) return@withContext true
        val confirmedNow = normals.count { it.confidence == MemoryEntity.CONFIDENCE_CONFIRMED }
        val confirmedAtGen = PhantomPrefs.getPortraitConfirmedCountAtGen(ctx)
        (confirmedNow - confirmedAtGen) >= PORTRAIT_NEW_CONFIRMED_THRESHOLD
    }

    /**
     * 生成「我對你的瞭解」畫像卡（≤200 tokens）：
     * - 嚴格雙重排除 privacyTier == sensitive（沿用 #319 敏感雙閘門）。
     * - 走 AiPurpose.BRIEFING，獨立每日小額上限（3 次/日），不吃訊息分析額度。
     * - 未設 AI 金鑰時降級為本機精簡摘要。
     */
    suspend fun generatePortraitCard(
        ctx: Context,
        db: AppDatabase,
        forceManual: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): String? = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        lazyArchiveExpiredShortTerm(db, nowMs)
        val normals = db.memoryDao().activeNormalMemoriesForPrompt(nowMs)
            .filter { mem ->
                mem.privacyTier == MemoryEntity.PRIVACY_NORMAL &&
                    mem.status == MemoryEntity.STATUS_ACTIVE &&
                    (mem.layer != MemoryEntity.LAYER_SHORT_TERM || mem.expiresAt == null || mem.expiresAt > nowMs)
            }
        if (normals.isEmpty()) return@withContext null

        val dayKey = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(nowMs))
        if (!forceManual && PhantomPrefs.getPortraitDailyGenCount(appCtx, dayKey) >= PORTRAIT_DAILY_MAX_GENS) {
            return@withContext PhantomPrefs.getPortraitCardText(appCtx).takeIf { it.isNotBlank() }
        }

        val confirmedCount = normals.count { it.confidence == MemoryEntity.CONFIDENCE_CONFIRMED }
        val prioritized = normals
            .sortedWith(
                compareByDescending<MemoryEntity> { it.confidence == MemoryEntity.CONFIDENCE_CONFIRMED }
                    .thenByDescending { it.useCount }
                    .thenByDescending { it.updatedAt }
            )
            .take(20)

        val memLines = prioritized.joinToString("\n") { mem ->
            "- [${categoryLabel(mem.category)}] ${AiClient.sanitizeForPrompt(mem.subject).take(18)}：${AiClient.sanitizeForPrompt(mem.content).take(60)}"
        }
        val assistantName = PhantomPrefs.getAssistantName(appCtx)
        val portraitSystemPrompt = if (AiClient.isZhTwLocale()) {
            "你是「$assistantName」，請根據以下已過濾的一般記憶，用繁體中文第一人稱寫一段溫暖精準的「我對你的瞭解」個人畫像摘要（嚴格 ≤140 字／≤200 tokens，分 2–3 句，不要列點、不要編造未提供的事實）。"
        } else {
            "You are \"$assistantName\". Based only on the filtered normal memories below, write a warm, accurate first-person \"What I know about you\" profile summary in English (locale: ${AiClient.resolveLocaleTag()}). Keep it concise (within 100 words / ≤200 tokens, 2–3 sentences, no bullet points, and never fabricate facts not provided)."
        }
        val aiText = AiClient.generateBriefingText(
            context = appCtx,
            systemPrompt = portraitSystemPrompt,
            userPrompt = memLines,
            maxTokens = 200
        )

        val finalText = aiText?.trim()?.take(360) ?: buildString {
            val topCats = prioritized.groupBy { categoryLabel(it.category) }
                .entries.sortedByDescending { it.value.size }.take(3)
            append("目前共記住 ${normals.size} 筆一般記憶（已確認 $confirmedCount 筆），主要涵蓋")
            append(topCats.joinToString("、") { "${it.key}（${it.value.size} 筆）" })
            append("。")
        }

        PhantomPrefs.savePortraitCard(appCtx, finalText, nowMs, confirmedCount)
        PhantomPrefs.incrementPortraitDailyGenCount(appCtx, dayKey)
        finalText
    }

    // ==================== A5 整合既有資料彙總查詢與統一操作介面（不搬表、零遷移） ====================

    data class IntegratedMemoryDataSnapshot(
        val autoNotes: List<NoteEntity>,
        val whitelistRules: List<AiRuleEntity>,
        val aiTagRules: List<AiRuleEntity>,
        val userTagRules: List<UserTagRuleEntity>,
        val memoRules: List<MemoRuleEntity>,
        val tagsById: Map<Long, TagEntity>
    ) {
        val totalCount: Int
            get() = autoNotes.size + whitelistRules.size + aiTagRules.size + userTagRules.size + memoRules.size
    }

    /**
     * 統一查詢既有自動備忘錄（notes source=auto）、白名單（ai_rules kind=whitelist）、
     * 標籤規則（ai_rules kind=tag 與 user_tag_rules）、備忘規則（memo_rules），不搬表、零遷移。
     */
    suspend fun loadIntegratedSnapshot(db: AppDatabase): IntegratedMemoryDataSnapshot =
        withContext(Dispatchers.IO) {
            val autoNotes = db.noteDao().all().filter { it.source == NoteEntity.SOURCE_AUTO }
            val allAiRules = db.aiRuleDao().all()
            val whitelistRules = allAiRules.filter { it.kind == AiRuleEntity.KIND_WHITELIST }
            val aiTagRules = allAiRules.filter { it.kind == AiRuleEntity.KIND_TAG }
            val userTagRules = db.userTagRuleDao().all()
            val memoRules = db.memoRuleDao().all()
            val tagsById = db.tagDao().all().associateBy { it.id }
            IntegratedMemoryDataSnapshot(
                autoNotes = autoNotes,
                whitelistRules = whitelistRules,
                aiTagRules = aiTagRules,
                userTagRules = userTagRules,
                memoRules = memoRules,
                tagsById = tagsById
            )
        }

    suspend fun deleteAutoNote(ctx: Context, db: AppDatabase, noteId: Long) = withContext(Dispatchers.IO) {
        db.noteDao().deleteById(noteId)
        com.phantom.checker.widget.NotesWidgetProvider.updateAllIfChanged(ctx.applicationContext)
    }

    suspend fun toggleAiRuleEnabled(db: AppDatabase, rule: AiRuleEntity, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.aiRuleDao().update(rule.copy(isEnabled = enabled))
        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
    }

    suspend fun deleteAiRule(db: AppDatabase, ruleId: Long) = withContext(Dispatchers.IO) {
        db.aiRuleDao().deleteById(ruleId)
        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
    }

    suspend fun toggleUserTagRuleEnabled(db: AppDatabase, rule: UserTagRuleEntity, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.userTagRuleDao().update(rule.copy(isEnabled = enabled))
        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
    }

    suspend fun deleteUserTagRule(db: AppDatabase, ruleId: Long) = withContext(Dispatchers.IO) {
        db.userTagRuleDao().deleteById(ruleId)
        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
    }

    suspend fun toggleMemoRuleEnabled(db: AppDatabase, rule: MemoRuleEntity, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.memoRuleDao().update(rule.copy(isEnabled = enabled))
        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
    }

    suspend fun deleteMemoRule(db: AppDatabase, ruleId: Long) = withContext(Dispatchers.IO) {
        db.memoRuleDao().deleteById(ruleId)
        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
    }

    // ==================== #328 記憶 × 防詐三件套 ====================

    fun containsMoneyUrgencyOtpKeyword(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return MONEY_URGENCY_OTP_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * 計算記憶庫與通訊錄版本戳記，供 AiClient.analyzeMessage 的 riskCache key 納入，
     * 確保新增免疫記憶、人物記憶或通訊錄授權變更後，不被 24h 舊快取遮蔽。
     */
    suspend fun getAntiFraudCacheVersion(
        ctx: Context,
        db: AppDatabase,
        nowMs: Long = System.currentTimeMillis()
    ): String = withContext(Dispatchers.IO) {
        val normals = db.memoryDao().activeNormalMemoriesForPrompt(nowMs)
            .filter { it.privacyTier == MemoryEntity.PRIVACY_NORMAL && it.status == MemoryEntity.STATUS_ACTIVE }
        val maxUpdated = normals.maxOfOrNull { it.updatedAt } ?: 0L
        val contactGen = ContactContext.getCacheGeneration()
        val hasContacts = if (ContactContext.hasContactsPermission(ctx)) 1 else 0
        val saver = if (PhantomPrefs.isTokenSaverEnabled(ctx)) 1 else 0
        "${normals.size}:$maxUpdated:$contactGen:$hasContacts:$saver"
    }

    /**
     * 組裝注入風險分析 systemPrompt 的【個人矛盾情報】【個人近期活動摘要】【防詐免疫紀錄】區塊（#328）。
     *
     * 隱私鐵律：
     * - 嚴格雙重排除 privacyTier == sensitive（B1 語意零回退）。
     * - 原始通訊錄絕不外送，僅送 ContactContext 產出之匿名結論字串。
     * - 總開關關閉時直接回傳空字串，行為與 token 用量與 v3.0.0 完全一致。
     * - 省 token 模式預算減半。
     */
    suspend fun buildAntiFraudPromptBlock(
        ctx: Context,
        db: AppDatabase,
        sender: String,
        content: String,
        packageName: String = "",
        appLabel: String = "",
        timestampMs: Long = System.currentTimeMillis(),
        nowMs: Long = System.currentTimeMillis()
    ): String = withContext(Dispatchers.IO) {
        if (!PhantomPrefs.isAntiFraudCrossCheckEnabled(ctx)) return@withContext ""
        lazyArchiveExpiredShortTerm(db, nowMs)

        // 雙重硬排除 sensitive 與過期短期記憶（靜態可驗證）
        val normalMemories = db.memoryDao().activeNormalMemoriesForPrompt(nowMs)
            .filter { mem ->
                mem.privacyTier == MemoryEntity.PRIVACY_NORMAL &&
                    mem.status == MemoryEntity.STATUS_ACTIVE &&
                    (mem.layer != MemoryEntity.LAYER_SHORT_TERM || mem.expiresAt == null || mem.expiresAt > nowMs)
            }

        val tokenSaver = PhantomPrefs.isTokenSaverEnabled(ctx)
        val signalBudgetTokens = if (tokenSaver) 75 else 150
        val activityBudgetTokens = if (tokenSaver) 100 else 200
        val activityTopK = if (tokenSaver) 3 else 6
        val perItemCap = if (tokenSaver) 40 else 65

        val personMemories = normalMemories.filter { it.category == MemoryEntity.CATEGORY_PERSON }
        val contradictionSignals = ContactContext.evaluateContradictionSignals(
            ctx = ctx,
            sender = sender,
            content = content,
            packageName = packageName,
            appLabel = appLabel,
            timestampMs = timestampMs,
            normalPersonMemories = personMemories,
            maxTokens = signalBudgetTokens
        )

        val queryLower = "$sender $content".trim().lowercase(Locale.ROOT)
        val queryTerms = extractQueryTerms("$sender $content")

        // B. 情境矛盾交叉檢查：schedule / routine / preference 類 normal tier 記憶 top ≤6（省 token ≤3）
        val contextCategories = setOf(
            MemoryEntity.CATEGORY_SCHEDULE,
            MemoryEntity.CATEGORY_ROUTINE,
            MemoryEntity.CATEGORY_PREFERENCE
        )
        val rankedActivity = normalMemories
            .filter { it.category in contextCategories }
            .map { mem -> mem to scoreMemory(mem, queryTerms, queryLower, nowMs) }
            .sortedWith(
                compareByDescending<Pair<MemoryEntity, Double>> { it.second }
                    .thenByDescending { it.first.updatedAt }
            )
            .take(activityTopK)

        val activityLines = mutableListOf<String>()
        var activityTokens = 0
        for ((mem, _) in rankedActivity) {
            if (mem.privacyTier != MemoryEntity.PRIVACY_NORMAL) continue
            val catTag = categoryLabel(mem.category)
            val safeSubj = AiClient.sanitizeForPrompt(mem.subject.replace("\n", " ").trim()).take(16)
            val safeContent = AiClient.sanitizeForPrompt(mem.content.replace("\n", " ").trim()).take(perItemCap)
            val line = "- [$catTag] $safeSubj：$safeContent"
            val lineTok = estimateMemoryTokens(line)
            if (activityTokens + lineTok > activityBudgetTokens && activityLines.isNotEmpty()) break
            if (activityTokens + lineTok <= activityBudgetTokens || activityLines.isEmpty()) {
                activityLines.add(line)
                activityTokens += lineTok
            }
        }

        // C. 個人化詐騙免疫紀錄：金錢／催促／驗證碼類訊息優先帶入 scam_immunity top-1
        val immunityLine = if (containsMoneyUrgencyOtpKeyword(content)) {
            normalMemories
                .filter { it.category == MemoryEntity.CATEGORY_SCAM_IMMUNITY && it.privacyTier == MemoryEntity.PRIVACY_NORMAL }
                .map { mem -> mem to scoreMemory(mem, queryTerms, queryLower, nowMs) }
                .sortedWith(
                    compareByDescending<Pair<MemoryEntity, Double>> { it.second }
                        .thenByDescending { it.first.updatedAt }
                )
                .firstOrNull()
                ?.first
                ?.let { mem ->
                    val safeSubj = AiClient.sanitizeForPrompt(mem.subject.replace("\n", " ").trim()).take(18)
                    val safeContent = AiClient.sanitizeForPrompt(mem.content.replace("\n", " ").trim()).take(perItemCap)
                    "- [防詐免疫] $safeSubj：$safeContent"
                }
        } else {
            null
        }

        if (contradictionSignals.isBlank() && activityLines.isEmpty() && immunityLine == null) {
            return@withContext ""
        }

        buildString {
            if (contradictionSignals.isNotBlank()) {
                appendLine("【個人矛盾情報（本機離線比對結論）】")
                appendLine(contradictionSignals)
            }
            if (activityLines.isNotEmpty()) {
                appendLine("【個人近期活動摘要】")
                activityLines.forEach { appendLine(it) }
            }
            if (immunityLine != null) {
                appendLine("【曾遇過之詐騙免疫紀錄】")
                appendLine(immunityLine)
            }
            append(
                "防詐交叉判讀要求：若訊息與上述個人矛盾情報衝突（例如陌生帳號冒充熟人且急迫借錢／要驗證碼、慣用 App 或聯絡時段異常），" +
                    "或與個人近期活動摘要出現邏輯破綻（例如未曾訂購卻稱包裹異常、刷卡失敗、臨時改約），或吻合曾遇過之詐騙免疫手法，" +
                    "必須顯著升高 risk_score 並在 risk_reason 中明確指出矛盾或破綻。"
            )
        }.trim()
    }

    /** 從單則詐騙訊息濃縮為「防詐免疫」記憶草稿（App、話術特徵、風險理由），供確認卡預填 */
    fun buildScamImmunityDraftFromMessage(msg: MessageEntity): Pair<String, String> {
        val appName = msg.appLabel.trim().ifEmpty { msg.packageName.ifEmpty { "通知" } }
        val lower = msg.content.lowercase(Locale.ROOT)
        val matchedTactics = MONEY_URGENCY_OTP_KEYWORDS.filter { lower.contains(it) }.distinct().take(4)
        val tacticPart = if (matchedTactics.isNotEmpty()) {
            "關鍵話術：${matchedTactics.joinToString("、")}"
        } else {
            "風險分數 ${msg.riskScore}"
        }
        val reasonPart = msg.riskReason.replace("\n", " ").trim().take(60)
        val subject = "${appName} 詐騙手法"
        val content = buildString {
            append("來源 $appName；$tacticPart")
            if (reasonPart.isNotEmpty()) {
                append("；特徵：$reasonPart")
            }
        }.take(140)
        return subject to content
    }
}

/**
 * 每日自動封存已過期短期記憶的 WorkManager Worker（#314 A2-3）。
 */
class MemoryExpiryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabaseHolder.db(applicationContext)
            MemoryEngine.lazyArchiveExpiredShortTerm(db, System.currentTimeMillis())
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("MemoryExpiryWorker", "doWork failed (attempt=$runAttemptCount)", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

object MemoryExpiryPolicy {
    private const val UNIQUE_WORK_NAME = "phantom_memory_expiry_daily"

    fun schedule(ctx: Context) {
        val appCtx = ctx.applicationContext
        val request = PeriodicWorkRequestBuilder<MemoryExpiryWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(appCtx).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

/**
 * #328 每週定期彙總近 30 天詐騙訊息手法，產出「防詐免疫」待確認草稿。
 * 鐵律：不直接寫入 memories 表，而是存入 PhantomPrefs 待確認草稿，由使用者於記憶頁確認卡核准後才落庫。
 */
class ScamImmunitySummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val appCtx = applicationContext
            if (!PhantomPrefs.isAntiFraudCrossCheckEnabled(appCtx)) {
                return Result.success()
            }
            val db = AppDatabaseHolder.db(appCtx)
            val now = System.currentTimeMillis()
            val since30d = now - TimeUnit.DAYS.toMillis(30)
            val recentScams = db.messageDao().scamsSince(since30d, limit = 500)
            if (recentScams.isEmpty()) {
                return Result.success()
            }
            val topApps = recentScams
                .groupBy { it.appLabel.ifBlank { it.packageName }.ifBlank { "未知App" } }
                .entries
                .sortedByDescending { it.value.size }
                .take(3)
                .joinToString("、") { "${it.key}(${it.value.size}則)" }

            val tacticKeywords = listOf(
                "驗證碼", "OTP", "借錢", "匯款", "轉帳", "包裹異常", "刷卡失敗",
                "投資", "解除分期", "帳戶凍結", "罰單", "客服", "點數",
                // #431 English scam tactics
                "verification code", "one-time passcode", "wire transfer", "bank transfer",
                "account suspended", "account locked", "account compromised", "fraud department",
                "unpaid toll", "redelivery fee", "customs fee", "shipment on hold",
                "guaranteed return", "guaranteed profit", "trading group", "crypto platform",
                "refund", "gift card", "remote access", "arrest warrant", "seed phrase"
            )
            val hitTactics = tacticKeywords.filter { kw ->
                recentScams.any { m ->
                    m.content.contains(kw, ignoreCase = true) || m.riskReason.contains(kw, ignoreCase = true)
                }
            }.take(5)

            val summary = buildString {
                append("近 30 天攔截 ${recentScams.size} 則高風險詐騙，主要來源：$topApps")
                if (hitTactics.isNotEmpty()) {
                    append("；常見手法關鍵詞：${hitTactics.joinToString("、")}")
                }
            }.take(160)

            PhantomPrefs.savePendingScamImmunityDraft(
                ctx = appCtx,
                subject = "近 30 天曾遇過之詐騙手法彙總",
                content = summary,
                nowMs = now
            )
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("ScamImmunitySummaryWorker", "doWork failed (attempt=$runAttemptCount)", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

object ScamImmunityPolicy {
    private const val UNIQUE_WORK_NAME = "phantom_scam_immunity_weekly"

    fun schedule(ctx: Context) {
        val appCtx = ctx.applicationContext
        val request = PeriodicWorkRequestBuilder<ScamImmunitySummaryWorker>(7, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(appCtx).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
