package com.phantom.checker.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["timestamp"]),
        Index(value = ["packageName", "conversationKey"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,       // 來源 App（LINE / IG / 簡訊…）
    val appLabel: String,
    val sender: String,
    val conversationKey: String = "", // 對話鍵（群組為正規化 conversationTitle，1:1/非通訊類為 sender）
    val content: String,
    val timestamp: Long,
    val riskScore: Int = 0,       // AI 分析結果 0-100
    val riskLabel: String = "",   // safe / suspicious / scam
    val riskReason: String = "",
    val isFiltered: Boolean = false, // 命中關鍵字過濾
    val isRead: Boolean = false,   // 幻訊內的已讀標記（不影響原 App）
    val dedupKey: String? = null,  // 通知去重雜湊鍵
    val isStarred: Boolean = false, // 星標
    val remindAt: Long? = null,    // 待辦提醒時間（epoch ms）
    val isOwn: Boolean = false     // 使用者自己送出的回覆
)

@Entity(tableName = "filter_rules")
data class FilterRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val isActive: Boolean = true,
    val packageName: String? = null, // null = 全部 App 生效
    val action: String = ACTION_FILTER // filter / drop / scam
) {
    companion object {
        const val ACTION_FILTER = "filter"
        const val ACTION_DROP = "drop"
        const val ACTION_SCAM = "scam"
    }
}

@Entity(
    tableName = "ai_summaries",
    indices = [Index(value = ["date"], unique = true)]
)
data class AiSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,               // yyyyMMdd 格式（例如 20261115）
    val summary: String,
    val generatedAt: Long
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val sortOrder: Int = 0,
    val aiHint: String = "",
    val isAiManaged: Boolean = true,
    val isAppCategory: Boolean = false
)

@Entity(
    tableName = "message_tags",
    primaryKeys = ["messageId", "tagId"],
    indices = [Index(value = ["tagId"])]
)
data class MessageTagCrossRef(
    val messageId: Long,
    val tagId: Long,
    val source: String = SOURCE_MANUAL
) {
    companion object {
        const val SOURCE_AUTO = "auto"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_USER_RULE = "user_rule"
    }
}

@Entity(
    tableName = "user_tag_rules",
    indices = [Index(value = ["targetTagId"]), Index(value = ["isEnabled"])]
)
data class UserTagRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val appPackage: String? = null,
    val appLabel: String? = null,
    val senderPattern: String? = null,
    val contentKeyword: String? = null,
    val contentRegex: String? = null,
    val semanticCategory: String? = null,
    val targetTagId: Long,
    val isEnabled: Boolean = true,
    val priority: Int = 100,
    val source: String = "chat",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "todos",
    indices = [
        Index(value = ["isDone", "dueAt"]),
        Index(value = ["dueAt"])
    ]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueAt: Long,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val relatedMessageId: Long? = null,
    val sourceSessionId: Long? = null,
    val completedAt: Long? = null,
    val priority: Int = PRIORITY_MEDIUM,
    val notes: String = "",
    val repeatRule: String = REPEAT_NONE
) {
    companion object {
        const val PRIORITY_LOW = 0
        const val PRIORITY_MEDIUM = 1
        const val PRIORITY_HIGH = 2

        const val REPEAT_NONE = ""
        const val REPEAT_DAILY = "daily"
        const val REPEAT_WEEKLY = "weekly"
    }
}

@Entity(
    tableName = "app_category_rules",
    indices = [Index(value = ["packageName"])]
)
data class AppCategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val tagId: Long,
    val isEnabled: Boolean = true
)

@Entity(tableName = "ai_rules")
data class AiRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleText: String,
    val targetTagId: Long? = null,
    val kind: String = KIND_TAG,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val sender: String? = null,
    val packageName: String? = null
) {
    companion object {
        const val KIND_TAG = "tag"
        const val KIND_WHITELIST = "whitelist"
    }
}

@Entity(tableName = "memo_rules")
data class MemoRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String? = null,
    val sender: String? = null,
    val keyword: String? = null,
    val action: String, // "note" | "todo" | "never"
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = SOURCE_MANUAL // "manual" | "chat" | "detail"
) {
    companion object {
        const val ACTION_NOTE = "note"
        const val ACTION_TODO = "todo"
        const val ACTION_NEVER = "never"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_CHAT = "chat"
        const val SOURCE_DETAIL = "detail"
    }
}

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

@Entity(tableName = "intel_cache")
data class IntelCacheEntity(
    @PrimaryKey val cacheKey: String,
    val isHit: Boolean,
    val sourceLabel: String,
    val detail: String,
    val checkedAt: Long
)

@Entity(tableName = "intel_results")
data class IntelResultEntity(
    @PrimaryKey val messageId: Long,
    val indicatorsSummary: String,
    val isHit: Boolean,
    val sourceLabel: String,
    val detail: String,
    val checkedAt: Long
)

data class AiTagCountRow(
    val tagId: Long,
    val name: String,
    val colorHex: String,
    val cnt: Int
)

data class AppGroupRow(
    val packageName: String,
    val appLabel: String,
    val cnt: Int,
    val lastTs: Long,
    val riskScore: Int = 0
)

data class SenderGroupRow(
    val conversationKey: String,
    val cnt: Int,
    val unreadCnt: Int,
    val lastTs: Long
)

data class AppCountRow(
    val packageName: String,
    val cnt: Int
)

data class AppRiskRow(
    val packageName: String,
    val appLabel: String,
    val cnt: Int
)

data class MessageStatsRow(
    val total: Int,
    val today: Int,
    val unread: Int,
    val scam: Int
)

data class StatsSnapshotRow(
    val total: Int,
    val today: Int,
    val unread: Int,
    val scam: Int,
    val latestPackageName: String?,
    val recentPackagesCsv: String?
) {
    fun recentPackages(): List<String> =
        recentPackagesCsv?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
}

data class MessageTagRow(
    val messageId: Long,
    val tagId: Long,
    val name: String,
    val colorHex: String,
    val source: String = MessageTagCrossRef.SOURCE_MANUAL
)

data class TagTotalCountRow(
    val tagId: Long,
    val name: String,
    val colorHex: String,
    val cnt: Int
)

data class AppLatestMsgRow(
    val packageName: String,
    val messageId: Long
)

data class DayCountRow(
    val dayKey: String,
    val cnt: Int
)

data class HourCountRow(
    val hour: Int,
    val cnt: Int
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun paged(limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isRead = 0 ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun unreadPaged(limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isStarred = 1 ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun starredPaged(limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isOwn = 0 AND riskLabel = 'scam' ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun scamsPaged(limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isFiltered = 1 ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun filteredPaged(limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id IN (SELECT relatedMessageId FROM notes WHERE relatedMessageId IS NOT NULL) ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun notedPaged(limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id IN (SELECT relatedMessageId FROM todos WHERE relatedMessageId IS NOT NULL) ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun todoedPaged(limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT DISTINCT relatedMessageId FROM notes WHERE relatedMessageId IS NOT NULL")
    suspend fun notedMessageIds(): List<Long>

    @Query("SELECT DISTINCT relatedMessageId FROM todos WHERE relatedMessageId IS NOT NULL")
    suspend fun todoedMessageIds(): List<Long>

    @Query("SELECT m.* FROM messages m INNER JOIN message_tags mt ON m.id = mt.messageId WHERE mt.tagId = :tagId ORDER BY m.timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun byTagPaged(tagId: Long, limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 200")
    suspend fun recent(): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    suspend fun all(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE (:packageName IS NULL OR packageName = :packageName) AND (:sinceTs IS NULL OR timestamp >= :sinceTs) ORDER BY timestamp DESC")
    suspend fun exportQuery(packageName: String?, sinceTs: Long?): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :q || '%' OR sender LIKE '%' || :q || '%' ORDER BY timestamp DESC LIMIT 200")
    suspend fun search(q: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isOwn = 0 AND riskLabel = 'scam' ORDER BY timestamp DESC")
    suspend fun scams(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isFiltered = 1 ORDER BY timestamp DESC")
    suspend fun filtered(): List<MessageEntity>

    @Query(
        "SELECT COUNT(*) as total, " +
            "COALESCE(SUM(CASE WHEN timestamp >= :startOfDay THEN 1 ELSE 0 END), 0) as today, " +
            "COALESCE(SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END), 0) as unread, " +
            "COALESCE(SUM(CASE WHEN riskLabel = 'scam' AND timestamp >= :startOfDay THEN 1 ELSE 0 END), 0) as scam " +
            "FROM messages WHERE isOwn = 0"
    )
    suspend fun statsOverview(startOfDay: Long): MessageStatsRow

    @Query(
        "SELECT COUNT(*) as total, " +
            "COALESCE(SUM(CASE WHEN timestamp >= :startOfDay THEN 1 ELSE 0 END), 0) as today, " +
            "COALESCE(SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END), 0) as unread, " +
            "COALESCE(SUM(CASE WHEN riskLabel = 'scam' AND timestamp >= :startOfDay THEN 1 ELSE 0 END), 0) as scam, " +
            "(SELECT packageName FROM messages WHERE isOwn = 0 ORDER BY timestamp DESC LIMIT 1) as latestPackageName, " +
            "(SELECT GROUP_CONCAT(packageName, '|') FROM (" +
            "SELECT packageName FROM messages WHERE isOwn = 0 AND timestamp >= :startOfDay " +
            "GROUP BY packageName ORDER BY MAX(timestamp) DESC LIMIT 5" +
            ")) as recentPackagesCsv " +
            "FROM messages WHERE isOwn = 0"
    )
    suspend fun statsSnapshot(startOfDay: Long): StatsSnapshotRow

    @Query("SELECT COUNT(*) FROM messages WHERE isOwn = 0 AND timestamp >= :startOfDay")
    suspend fun todayCount(startOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE isOwn = 0 AND isRead = 0")
    suspend fun unreadCount(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE isOwn = 0 AND riskLabel = 'scam' AND timestamp >= :startOfDay")
    suspend fun todayScamCount(startOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE isOwn = 0 AND riskLabel = 'scam' AND timestamp >= :sinceTs")
    suspend fun scamCountSince(sinceTs: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE isOwn = 0 AND riskLabel = 'scam' AND timestamp >= :startTs AND timestamp < :endTs")
    suspend fun scamCountBetween(startTs: Long, endTs: Long): Int

    @Query("SELECT packageName, appLabel, COUNT(*) as cnt, MAX(timestamp) as lastTs, SUM(CASE WHEN riskLabel = 'scam' THEN 2 WHEN riskLabel = 'suspicious' THEN 1 ELSE 0 END) as riskScore FROM messages WHERE isOwn = 0 AND timestamp >= :startOfDay GROUP BY packageName ORDER BY lastTs DESC LIMIT 5")
    suspend fun todayRecentApps(startOfDay: Long): List<AppGroupRow>

    @Query("SELECT packageName FROM messages WHERE isOwn = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestPackageName(): String?

    @Query("UPDATE messages SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

    @Query("UPDATE messages SET isRead = 1 WHERE isRead = 0 AND riskLabel != 'scam'")
    suspend fun markAllNonScamRead(): Int

    @Query("UPDATE messages SET isRead = 1 WHERE isRead = 0 AND riskLabel != 'scam' AND timestamp >= :sinceTs")
    suspend fun markNonScamReadSince(sinceTs: Long): Int

    @Query("UPDATE messages SET isRead = 1 WHERE isRead = 0 AND riskLabel != 'scam' AND packageName = :packageName")
    suspend fun markNonScamReadByApp(packageName: String): Int

    @Query(
        """
        SELECT * FROM messages
        WHERE timestamp >= :sinceTs
          AND (:packageName IS NULL OR packageName = :packageName)
          AND (:sender IS NULL OR sender LIKE '%' || :sender || '%' COLLATE NOCASE)
          AND (:query IS NULL OR content LIKE '%' || :query || '%' COLLATE NOCASE OR sender LIKE '%' || :query || '%' COLLATE NOCASE)
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun searchFiltered(
        sinceTs: Long,
        packageName: String?,
        sender: String?,
        query: String?,
        limit: Int = 5
    ): List<MessageEntity>

    @Query("UPDATE messages SET isRead = 1 WHERE id IN (:ids)")
    suspend fun markReadByIds(ids: List<Long>)

    @Query("UPDATE messages SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE messages SET isStarred = :starred WHERE id IN (:ids)")
    suspend fun setStarredByIds(ids: List<Long>, starred: Boolean)

    @Query("UPDATE messages SET remindAt = :remindAt WHERE id = :id")
    suspend fun setRemindAt(id: Long, remindAt: Long?)

    @Query("SELECT * FROM messages WHERE remindAt IS NOT NULL AND remindAt > :now ORDER BY remindAt ASC")
    suspend fun pendingReminders(now: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE remindAt IS NOT NULL ORDER BY remindAt ASC")
    suspend fun allScheduledReminders(): List<MessageEntity>

    @Query(
        "SELECT strftime('%Y%m%d', timestamp / 1000, 'unixepoch', 'localtime') AS dayKey, COUNT(*) AS cnt " +
            "FROM messages WHERE isOwn = 0 AND timestamp >= :sinceTs GROUP BY dayKey"
    )
    suspend fun countByDaySince(sinceTs: Long): List<DayCountRow>

    @Query(
        "SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, COUNT(*) AS cnt " +
            "FROM messages WHERE isOwn = 0 AND timestamp >= :sinceTs GROUP BY hour"
    )
    suspend fun countByHourSince(sinceTs: Long): List<HourCountRow>

    @Query(
        "SELECT strftime('%Y%m%d', timestamp / 1000, 'unixepoch', 'localtime') AS dayKey, COUNT(*) AS cnt " +
            "FROM messages WHERE isOwn = 0 AND timestamp >= :sinceTs AND riskLabel IN ('suspicious', 'scam') GROUP BY dayKey"
    )
    suspend fun riskyCountByDaySince(sinceTs: Long): List<DayCountRow>

    @Query("SELECT COUNT(*) FROM messages WHERE isOwn = 0 AND isRead = 0")
    suspend fun totalUnreadCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE isOwn = 0 AND timestamp >= :minTs AND timestamp <= :maxTs AND dedupKey = :dedupKey)")
    suspend fun existsByDedupKeyInWindow(dedupKey: String, minTs: Long, maxTs: Long): Boolean

    @Query("SELECT packageName, appLabel, COUNT(*) as cnt, MAX(timestamp) as lastTs, SUM(CASE WHEN riskLabel = 'scam' THEN 2 WHEN riskLabel = 'suspicious' THEN 1 ELSE 0 END) as riskScore FROM messages WHERE isOwn = 0 GROUP BY packageName ORDER BY lastTs DESC")
    suspend fun groupByApp(): List<AppGroupRow>

    @Query("SELECT packageName, COUNT(*) as cnt FROM messages WHERE isOwn = 0 AND timestamp >= :startOfDay GROUP BY packageName")
    suspend fun todayCountsByApp(startOfDay: Long): List<AppCountRow>

    @Query("SELECT packageName, COUNT(*) as cnt FROM messages WHERE isOwn = 0 AND isRead = 0 AND timestamp >= :startOfDay GROUP BY packageName")
    suspend fun todayUnreadCountsByApp(startOfDay: Long): List<AppCountRow>

    @Query("SELECT packageName, COUNT(*) as cnt FROM messages WHERE isOwn = 0 AND isRead = 0 GROUP BY packageName")
    suspend fun unreadCountsByApp(): List<AppCountRow>

    @Query("SELECT packageName, id AS messageId FROM messages WHERE isOwn = 0 AND id IN (SELECT MAX(id) FROM messages m1 WHERE isOwn = 0 AND timestamp = (SELECT MAX(timestamp) FROM messages m2 WHERE isOwn = 0 AND m2.packageName = m1.packageName) GROUP BY packageName)")
    suspend fun latestMessageIdsByApp(): List<AppLatestMsgRow>

    @Query("SELECT * FROM messages WHERE packageName = :packageName ORDER BY timestamp DESC")
    suspend fun byApp(packageName: String): List<MessageEntity>

    @Query("SELECT conversationKey, COUNT(*) as cnt, SUM(CASE WHEN isRead = 0 AND isOwn = 0 THEN 1 ELSE 0 END) as unreadCnt, MAX(timestamp) as lastTs FROM messages WHERE packageName = :packageName GROUP BY conversationKey ORDER BY lastTs DESC")
    suspend fun groupByConversation(packageName: String): List<SenderGroupRow>

    @Query("SELECT * FROM messages WHERE packageName = :packageName AND conversationKey = :conversationKey ORDER BY timestamp ASC")
    suspend fun byConversation(packageName: String, conversationKey: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE packageName = :packageName AND conversationKey = :conversationKey AND (:beforeTs IS NULL OR timestamp < :beforeTs) ORDER BY timestamp DESC LIMIT :limit")
    suspend fun byConversationKeyset(packageName: String, conversationKey: String, beforeTs: Long?, limit: Int): List<MessageEntity>

    @Query("UPDATE messages SET isRead = 1 WHERE packageName = :packageName AND conversationKey = :conversationKey AND isRead = 0")
    suspend fun markConversationRead(packageName: String, conversationKey: String)

    @Query("SELECT * FROM messages WHERE isOwn = 0 AND timestamp >= :sinceTs ORDER BY timestamp DESC")
    suspend fun since(sinceTs: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isOwn = 0 AND timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp DESC")
    suspend fun between(startTs: Long, endTs: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE timestamp >= :startTs AND timestamp <= :endTs AND (:packageName IS NULL OR packageName = :packageName) ORDER BY timestamp DESC LIMIT :limit")
    suspend fun queryRange(startTs: Long, endTs: Long, packageName: String?, limit: Int = 40): List<MessageEntity>

    @Query("SELECT DISTINCT sender FROM messages WHERE isOwn = 0 AND sender != '' ORDER BY timestamp DESC LIMIT 200")
    suspend fun recentSenders(): List<String>

    @Query("SELECT COUNT(*) FROM messages WHERE isOwn = 0 AND riskLabel = :label")
    suspend fun countByRiskLabel(label: String): Int

    @Query("SELECT packageName, appLabel, COUNT(*) as cnt FROM messages WHERE isOwn = 0 AND riskLabel IN ('scam', 'suspicious') GROUP BY packageName ORDER BY cnt DESC LIMIT 5")
    suspend fun topRiskyApps(): List<AppRiskRow>

    @Insert
    suspend fun insert(msg: MessageEntity): Long

    @Update
    suspend fun update(msg: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM messages WHERE isRead = 1")
    suspend fun deleteReadMessages()

    @Query("DELETE FROM messages WHERE packageName = :packageName AND conversationKey = :conversationKey")
    suspend fun deleteByConversation(packageName: String, conversationKey: String)

    @Query("DELETE FROM messages WHERE packageName = :packageName")
    suspend fun deleteByApp(packageName: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("DELETE FROM messages WHERE timestamp < :before AND riskLabel != 'scam'")
    suspend fun prune(before: Long)

    @Query("DELETE FROM messages WHERE riskLabel != 'scam' AND id NOT IN (SELECT id FROM messages ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun deleteOlderThanCount(keepCount: Int)

    @Query("SELECT id FROM messages WHERE id IN (:ids) AND riskLabel != 'scam'")
    suspend fun nonScamIdsFrom(ids: List<Long>): List<Long>

    @Query("DELETE FROM messages WHERE id IN (:ids) AND riskLabel != 'scam'")
    suspend fun deleteNonScamByIds(ids: List<Long>): Int

    @Query("UPDATE messages SET isRead = 1 WHERE packageName = :packageName AND conversationKey IN (:conversationKeys) AND isRead = 0")
    suspend fun markConversationsRead(packageName: String, conversationKeys: List<String>)

    @Query("SELECT COUNT(*) FROM messages WHERE packageName = :packageName AND conversationKey IN (:conversationKeys) AND riskLabel = 'scam'")
    suspend fun countScamByConversations(packageName: String, conversationKeys: List<String>): Int

    @Query("DELETE FROM messages WHERE packageName = :packageName AND conversationKey IN (:conversationKeys) AND riskLabel != 'scam'")
    suspend fun deleteNonScamByConversations(packageName: String, conversationKeys: List<String>): Int
}

@Dao
interface FilterDao {
    @Query("SELECT * FROM filter_rules WHERE isActive = 1")
    suspend fun active(): List<FilterRule>

    @Query("SELECT * FROM filter_rules")
    suspend fun all(): List<FilterRule>

    @Insert
    suspend fun insert(rule: FilterRule)

    @Update
    suspend fun update(rule: FilterRule)

    @Delete
    suspend fun delete(rule: FilterRule)
}

@Dao
interface AiSummaryDao {
    @Query("SELECT * FROM ai_summaries WHERE date < 1000000000 ORDER BY date DESC LIMIT 1")
    suspend fun latest(): AiSummaryEntity?

    @Query("SELECT * FROM ai_summaries WHERE date >= 1000000000 ORDER BY date DESC LIMIT 1")
    suspend fun latestWeekly(): AiSummaryEntity?

    @Query("SELECT * FROM ai_summaries WHERE date >= 1000000000 ORDER BY date DESC LIMIT 12")
    suspend fun weeklyHistory(): List<AiSummaryEntity>

    @Query("SELECT * FROM ai_summaries WHERE date = :date LIMIT 1")
    suspend fun byDate(date: Long): AiSummaryEntity?

    @Query("SELECT * FROM ai_summaries WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun byDateRange(startDate: Long, endDate: Long): List<AiSummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(summary: AiSummaryEntity)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY sortOrder ASC, id ASC")
    suspend fun all(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE isAiManaged = 1 ORDER BY sortOrder ASC, id ASC")
    suspend fun aiManagedTags(): List<TagEntity>

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteById(tagId: Long)

    @Query("DELETE FROM message_tags WHERE tagId = :tagId")
    suspend fun deleteCrossRefsByTag(tagId: Long)

    @Query("DELETE FROM message_tags WHERE messageId = :messageId")
    suspend fun deleteCrossRefsByMessage(messageId: Long)

    @Query("DELETE FROM message_tags WHERE messageId IN (:ids)")
    suspend fun deleteCrossRefsByMessageIds(ids: List<Long>)

    @Query("DELETE FROM message_tags WHERE messageId IN (SELECT id FROM messages WHERE packageName = :packageName)")
    suspend fun deleteCrossRefsByApp(packageName: String)

    @Query("DELETE FROM message_tags WHERE messageId IN (SELECT id FROM messages WHERE packageName = :packageName AND conversationKey = :conversationKey)")
    suspend fun deleteCrossRefsByConversation(packageName: String, conversationKey: String)

    @Query("DELETE FROM message_tags WHERE messageId IN (SELECT id FROM messages WHERE packageName = :packageName AND conversationKey IN (:conversationKeys) AND riskLabel != 'scam')")
    suspend fun deleteNonScamCrossRefsByConversations(packageName: String, conversationKeys: List<String>)

    @Query("DELETE FROM message_tags WHERE messageId IN (SELECT id FROM messages WHERE isRead = 1)")
    suspend fun deleteCrossRefsForReadMessages()

    @Query("DELETE FROM message_tags")
    suspend fun deleteAllCrossRefs()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToMessage(ref: MessageTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagsToMessageInternal(refs: List<MessageTagCrossRef>)

    @Transaction
    suspend fun addTagsToMessageBatch(refs: List<MessageTagCrossRef>) {
        if (refs.isNotEmpty()) addTagsToMessageInternal(refs)
    }

    @Query("DELETE FROM message_tags WHERE messageId = :messageId AND tagId = :tagId")
    suspend fun removeTagFromMessage(messageId: Long, tagId: Long)

    @Query("SELECT t.* FROM tags t INNER JOIN message_tags mt ON t.id = mt.tagId WHERE mt.messageId = :messageId ORDER BY t.sortOrder ASC, t.id ASC")
    suspend fun tagsForMessage(messageId: Long): List<TagEntity>

    @Query("SELECT * FROM message_tags WHERE messageId = :messageId")
    suspend fun crossRefsForMessage(messageId: Long): List<MessageTagCrossRef>

    @Query("SELECT * FROM message_tags")
    suspend fun allCrossRefs(): List<MessageTagCrossRef>

    @Query(
        "SELECT t.id as tagId, t.name as name, t.colorHex as colorHex, " +
            "COUNT(DISTINCT CASE WHEN m.id IS NOT NULL THEN mt.messageId END) as cnt " +
            "FROM tags t " +
            "LEFT JOIN message_tags mt ON t.id = mt.tagId " +
            "LEFT JOIN messages m ON mt.messageId = m.id AND m.timestamp >= :startOfDay " +
            "WHERE t.isAiManaged = 1 " +
            "GROUP BY t.id ORDER BY t.sortOrder ASC, t.id ASC"
    )
    suspend fun todayAiTagCounts(startOfDay: Long): List<AiTagCountRow>

    @Query(
        "SELECT mt.messageId AS messageId, t.id AS tagId, t.name AS name, t.colorHex AS colorHex, mt.source AS source " +
            "FROM message_tags mt INNER JOIN tags t ON mt.tagId = t.id " +
            "WHERE mt.messageId IN (:messageIds) " +
            "ORDER BY t.sortOrder ASC, t.id ASC"
    )
    suspend fun tagsForMessages(messageIds: List<Long>): List<MessageTagRow>

    @Query(
        "SELECT t.id AS tagId, t.name AS name, t.colorHex AS colorHex, " +
            "COUNT(DISTINCT CASE WHEN m.id IS NOT NULL THEN mt.messageId END) AS cnt " +
            "FROM tags t " +
            "LEFT JOIN message_tags mt ON t.id = mt.tagId " +
            "LEFT JOIN messages m ON mt.messageId = m.id " +
            "GROUP BY t.id ORDER BY t.sortOrder ASC, t.id ASC"
    )
    suspend fun allTagCounts(): List<TagTotalCountRow>

    @Query("DELETE FROM message_tags WHERE messageId NOT IN (SELECT id FROM messages)")
    suspend fun deleteOrphanCrossRefs()
}

@Dao
interface AppCategoryRuleDao {
    @Query("SELECT * FROM app_category_rules ORDER BY id ASC")
    suspend fun all(): List<AppCategoryRuleEntity>

    @Query("SELECT * FROM app_category_rules WHERE packageName = :packageName AND isEnabled = 1")
    suspend fun activeByPackage(packageName: String): List<AppCategoryRuleEntity>

    @Insert
    suspend fun insert(rule: AppCategoryRuleEntity): Long

    @Update
    suspend fun update(rule: AppCategoryRuleEntity)

    @Query("DELETE FROM app_category_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM app_category_rules WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("DELETE FROM app_category_rules WHERE tagId = :tagId")
    suspend fun deleteByTagId(tagId: Long)
}

@Dao
interface AiRuleDao {
    @Query("SELECT * FROM ai_rules ORDER BY createdAt DESC, id DESC")
    suspend fun all(): List<AiRuleEntity>

    @Query("SELECT * FROM ai_rules WHERE isEnabled = 1 ORDER BY createdAt ASC, id ASC LIMIT 20")
    suspend fun active(): List<AiRuleEntity>

    @Query("SELECT * FROM ai_rules WHERE kind = :kind AND isEnabled = 1 ORDER BY packageName ASC, id ASC")
    suspend fun activeByKind(kind: String): List<AiRuleEntity>

    @Query("SELECT * FROM ai_rules WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): AiRuleEntity?

    @Insert
    suspend fun insert(rule: AiRuleEntity): Long

    @Update
    suspend fun update(rule: AiRuleEntity)

    @Query("DELETE FROM ai_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC, id DESC")
    suspend fun allSessions(): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun sessionById(id: Long): ChatSessionEntity?

    @Insert
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Query("UPDATE chat_sessions SET title = :title WHERE id = :id")
    suspend fun updateSessionTitle(id: Long, title: String)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun messagesForSession(sessionId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: Long)

    @Transaction
    suspend fun deleteSessionWithMessages(sessionId: Long) {
        deleteMessagesBySession(sessionId)
        deleteSessionById(sessionId)
    }

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()

    @Transaction
    suspend fun clearAllSessions() {
        deleteAllMessages()
        deleteAllSessions()
    }
}

@Dao
interface IntelDao {
    @Query("SELECT * FROM intel_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getCache(key: String): IntelCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCache(entry: IntelCacheEntity)

    @Query("DELETE FROM intel_cache")
    suspend fun clearCache()

    @Query("DELETE FROM intel_cache WHERE checkedAt < :beforeTs")
    suspend fun pruneCache(beforeTs: Long)

    @Query("SELECT * FROM intel_results WHERE messageId = :messageId LIMIT 1")
    suspend fun getResult(messageId: Long): IntelResultEntity?

    @Query("SELECT * FROM intel_results WHERE messageId IN (:messageIds)")
    suspend fun getResults(messageIds: List<Long>): List<IntelResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putResult(result: IntelResultEntity)

    @Query("DELETE FROM intel_results WHERE messageId = :messageId")
    suspend fun deleteResult(messageId: Long)

    @Query("DELETE FROM intel_results")
    suspend fun deleteAllResults()
}

@Dao
interface UserTagRuleDao {
    @Query("SELECT * FROM user_tag_rules ORDER BY priority DESC, createdAt DESC")
    suspend fun all(): List<UserTagRuleEntity>

    @Query("SELECT * FROM user_tag_rules WHERE isEnabled = 1 ORDER BY priority DESC, createdAt DESC")
    suspend fun active(): List<UserTagRuleEntity>

    @Insert
    suspend fun insert(rule: UserTagRuleEntity): Long

    @Update
    suspend fun update(rule: UserTagRuleEntity)

    @Query("DELETE FROM user_tag_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

data class TodoCompletionRow(
    val total: Int,
    val done: Int
)

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE isDone = 0 ORDER BY dueAt ASC, id ASC")
    suspend fun pendingTodos(): List<TodoEntity>

    @Query("SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN isDone = 1 THEN 1 ELSE 0 END), 0) AS done FROM todos")
    suspend fun completionStats(): TodoCompletionRow

    @Query("SELECT * FROM todos ORDER BY isDone ASC, dueAt ASC, id DESC")
    suspend fun all(): List<TodoEntity>

    @Query("SELECT * FROM todos ORDER BY dueAt ASC")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): TodoEntity?

    @Query("SELECT * FROM todos WHERE isDone = 0 AND dueAt > :now ORDER BY dueAt ASC")
    suspend fun futurePending(now: Long): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE relatedMessageId = :messageId LIMIT 1")
    suspend fun findByRelatedMessageId(messageId: Long): TodoEntity?

    @Query("SELECT * FROM todos WHERE isDone = 0 AND createdAt >= :since ORDER BY createdAt DESC")
    suspend fun findRecentPendingSince(since: Long): List<TodoEntity>

    @Query("""
        SELECT t.* FROM todos t
        INNER JOIN messages m ON t.relatedMessageId = m.id
        WHERE t.isDone = 0
          AND t.createdAt >= :since
          AND (CASE WHEN m.appLabel != '' THEN m.appLabel ELSE m.packageName END) = :appLabel
        ORDER BY t.createdAt DESC
    """)
    suspend fun findRecentPendingByAppLabel(appLabel: String, since: Long): List<TodoEntity>

    @Insert
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Query("UPDATE todos SET isDone = :done, completedAt = :completedAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, completedAt: Long? = if (done) System.currentTimeMillis() else null)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Entity(tableName = "pending_ai_analysis")
data class PendingAiAnalysisEntity(
    @PrimaryKey val messageId: Long,
    val queuedAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0
)

@Dao
interface PendingAiAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(item: PendingAiAnalysisEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueAll(items: List<PendingAiAnalysisEntity>)

    @Query("SELECT * FROM pending_ai_analysis WHERE attempts < 3 ORDER BY queuedAt ASC LIMIT :limit")
    suspend fun nextBatch(limit: Int = 20): List<PendingAiAnalysisEntity>

    @Query("SELECT COUNT(*) FROM pending_ai_analysis WHERE attempts < 3")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM pending_ai_analysis WHERE attempts < 3")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM pending_ai_analysis WHERE messageId IN (:messageIds)")
    suspend fun deleteByIds(messageIds: List<Long>)

    @Query("UPDATE pending_ai_analysis SET attempts = attempts + 1 WHERE messageId IN (:messageIds)")
    suspend fun incrementAttempts(messageIds: List<Long>)

    @Query("SELECT * FROM pending_ai_analysis WHERE attempts >= 3")
    suspend fun exhaustedItems(): List<PendingAiAnalysisEntity>

    @Query("DELETE FROM pending_ai_analysis WHERE attempts >= 3")
    suspend fun deleteExhausted()

    @Query("DELETE FROM pending_ai_analysis")
    suspend fun clearAll()
}

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["pinned", "updatedAt"]),
        Index(value = ["relatedMessageId", "source"])
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val colorHex: String? = null,
    val pinned: Boolean = false,
    val source: String = SOURCE_MANUAL,
    val relatedMessageId: Long? = null,
    val relatedAppLabel: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_AUTO = "auto"
    }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    suspend fun all(): List<NoteEntity>

    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE relatedMessageId = :messageId AND source = 'auto' LIMIT 1")
    suspend fun findAutoByMessageId(messageId: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE source = 'auto' AND relatedAppLabel = :label AND updatedAt >= :since ORDER BY updatedAt DESC")
    suspend fun findRecentAutoByAppLabel(label: String, since: Long): List<NoteEntity>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MemoRuleDao {
    @Query("SELECT * FROM memo_rules ORDER BY id DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<MemoRuleEntity>>

    @Query("SELECT * FROM memo_rules ORDER BY id DESC")
    suspend fun all(): List<MemoRuleEntity>

    @Query("SELECT * FROM memo_rules WHERE isEnabled = 1 ORDER BY id ASC")
    suspend fun active(): List<MemoRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: MemoRuleEntity): Long

    @Update
    suspend fun update(rule: MemoRuleEntity)

    @Query("DELETE FROM memo_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Entity(
    tableName = "own_reply_echo",
    indices = [
        Index(value = ["packageName", "conversationKey"]),
        Index(value = ["sentAt"])
    ]
)
data class OwnReplyEchoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val conversationKey: String,
    val normalizedText: String,
    val sentAt: Long = System.currentTimeMillis()
)

@Dao
interface OwnReplyEchoDao {
    @Query("SELECT * FROM own_reply_echo WHERE packageName = :packageName AND sentAt >= :since ORDER BY sentAt DESC")
    suspend fun recentByPkg(packageName: String, since: Long): List<OwnReplyEchoEntity>

    @Insert
    suspend fun insert(item: OwnReplyEchoEntity): Long

    @Query("DELETE FROM own_reply_echo WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM own_reply_echo WHERE sentAt < :cutoff")
    suspend fun deleteExpired(cutoff: Long)
}

@Entity(
    tableName = "sender_avatars",
    primaryKeys = ["packageName", "sender"]
)
data class SenderAvatarEntity(
    val packageName: String,
    val sender: String,
    val icon: ByteArray,
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SenderAvatarEntity) return false
        return packageName == other.packageName &&
            sender == other.sender &&
            icon.contentEquals(other.icon) &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + sender.hashCode()
        result = 31 * result + icon.contentHashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

@Dao
interface SenderAvatarDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(avatar: SenderAvatarEntity)

    @Query("SELECT * FROM sender_avatars WHERE packageName = :packageName AND sender = :sender LIMIT 1")
    suspend fun get(packageName: String, sender: String): SenderAvatarEntity?

    @Query("SELECT * FROM sender_avatars WHERE packageName = :packageName")
    suspend fun byPackage(packageName: String): List<SenderAvatarEntity>

    @Query("DELETE FROM sender_avatars WHERE updatedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Database(
    entities = [
        MessageEntity::class,
        FilterRule::class,
        AiSummaryEntity::class,
        TagEntity::class,
        MessageTagCrossRef::class,
        AppCategoryRuleEntity::class,
        AiRuleEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        IntelCacheEntity::class,
        IntelResultEntity::class,
        UserTagRuleEntity::class,
        TodoEntity::class,
        PendingAiAnalysisEntity::class,
        NoteEntity::class,
        MemoRuleEntity::class,
        OwnReplyEchoEntity::class,
        SenderAvatarEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun filterDao(): FilterDao
    abstract fun aiSummaryDao(): AiSummaryDao
    abstract fun tagDao(): TagDao
    abstract fun appCategoryRuleDao(): AppCategoryRuleDao
    abstract fun aiRuleDao(): AiRuleDao
    abstract fun chatDao(): ChatDao
    abstract fun intelDao(): IntelDao
    abstract fun userTagRuleDao(): UserTagRuleDao
    abstract fun todoDao(): TodoDao
    abstract fun pendingAiAnalysisDao(): PendingAiAnalysisDao
    abstract fun noteDao(): NoteDao
    abstract fun memoRuleDao(): MemoRuleDao
    abstract fun ownReplyEchoDao(): OwnReplyEchoDao
    abstract fun senderAvatarDao(): SenderAvatarDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE filter_rules ADD COLUMN packageName TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN dedupKey TEXT DEFAULT NULL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_summaries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `summary` TEXT NOT NULL,
                        `generatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_summaries_date` ON `ai_summaries` (`date`)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_packageName` ON `messages` (`packageName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_timestamp` ON `messages` (`timestamp`)")
                db.execSQL("ALTER TABLE filter_rules ADD COLUMN action TEXT NOT NULL DEFAULT 'filter'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tags` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `message_tags` (
                        `messageId` INTEGER NOT NULL,
                        `tagId` INTEGER NOT NULL,
                        PRIMARY KEY(`messageId`, `tagId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_tags_tagId` ON `message_tags` (`tagId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN aiHint TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tags ADD COLUMN isAiManaged INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE message_tags ADD COLUMN source TEXT NOT NULL DEFAULT 'auto'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN isAppCategory INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_category_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `tagId` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_category_rules_packageName` ON `app_category_rules` (`packageName`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ruleText` TEXT NOT NULL,
                        `targetTagId` INTEGER,
                        `kind` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `intel_cache` (
                        `cacheKey` TEXT NOT NULL,
                        `isHit` INTEGER NOT NULL,
                        `sourceLabel` TEXT NOT NULL,
                        `detail` TEXT NOT NULL,
                        `checkedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `intel_results` (
                        `messageId` INTEGER NOT NULL,
                        `indicatorsSummary` TEXT NOT NULL,
                        `isHit` INTEGER NOT NULL,
                        `sourceLabel` TEXT NOT NULL,
                        `detail` TEXT NOT NULL,
                        `checkedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`messageId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN remindAt INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_rules ADD COLUMN sender TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE ai_rules ADD COLUMN packageName TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_tag_rules` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`appPackage` TEXT DEFAULT NULL, " +
                        "`appLabel` TEXT DEFAULT NULL, " +
                        "`senderPattern` TEXT DEFAULT NULL, " +
                        "`contentKeyword` TEXT DEFAULT NULL, " +
                        "`contentRegex` TEXT DEFAULT NULL, " +
                        "`semanticCategory` TEXT DEFAULT NULL, " +
                        "`targetTagId` INTEGER NOT NULL, " +
                        "`isEnabled` INTEGER NOT NULL DEFAULT 1, " +
                        "`priority` INTEGER NOT NULL DEFAULT 100, " +
                        "`source` TEXT NOT NULL DEFAULT 'chat', " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_tag_rules_targetTagId` ON `user_tag_rules` (`targetTagId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_tag_rules_isEnabled` ON `user_tag_rules` (`isEnabled`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN conversationKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE messages SET conversationKey = sender WHERE conversationKey = ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_packageName_conversationKey` ON `messages` (`packageName`, `conversationKey`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `todos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `dueAt` INTEGER NOT NULL,
                        `isDone` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `relatedMessageId` INTEGER DEFAULT NULL,
                        `sourceSessionId` INTEGER DEFAULT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_isDone_dueAt` ON `todos` (`isDone`, `dueAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_dueAt` ON `todos` (`dueAt`)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_ai_analysis` (
                        `messageId` INTEGER NOT NULL,
                        `queuedAt` INTEGER NOT NULL,
                        `attempts` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`messageId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `content` TEXT NOT NULL,
                        `colorHex` TEXT DEFAULT NULL,
                        `pinned` INTEGER NOT NULL DEFAULT 0,
                        `source` TEXT NOT NULL DEFAULT 'manual',
                        `relatedMessageId` INTEGER DEFAULT NULL,
                        `relatedAppLabel` TEXT DEFAULT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_pinned_updatedAt` ON `notes` (`pinned`, `updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_relatedMessageId_source` ON `notes` (`relatedMessageId`, `source`)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN completedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE todos ADD COLUMN priority INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE todos ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE todos ADD COLUMN repeatRule TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isOwn INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memo_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT,
                        `sender` TEXT,
                        `keyword` TEXT,
                        `action` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        `source` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `own_reply_echo` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `conversationKey` TEXT NOT NULL,
                        `normalizedText` TEXT NOT NULL,
                        `sentAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_own_reply_echo_packageName_conversationKey` ON `own_reply_echo` (`packageName`, `conversationKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_own_reply_echo_sentAt` ON `own_reply_echo` (`sentAt`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sender_avatars` (
                        `packageName` TEXT NOT NULL,
                        `sender` TEXT NOT NULL,
                        `icon` BLOB NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`packageName`, `sender`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
