package com.phantom.checker.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,       // 來源 App（LINE / IG / 簡訊…）
    val appLabel: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val riskScore: Int = 0,       // AI 分析結果 0-100
    val riskLabel: String = "",   // safe / suspicious / scam
    val riskReason: String = "",
    val isFiltered: Boolean = false, // 命中關鍵字過濾
    val isRead: Boolean = false,   // 幻訊內的已讀標記（不影響原 App）
    val dedupKey: String? = null,  // 通知去重雜湊鍵
    val isStarred: Boolean = false, // 星標
    val remindAt: Long? = null     // 待辦提醒時間（epoch ms）
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
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val KIND_TAG = "tag"
        const val KIND_WHITELIST = "whitelist"
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
    val sender: String,
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

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun paged(limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isRead = 0 ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun unreadPaged(limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isStarred = 1 ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun starredPaged(limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE riskLabel = 'scam' ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun scamsPaged(limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isFiltered = 1 ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun filteredPaged(limit: Int, offset: Int): List<MessageEntity>

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

    @Query("SELECT * FROM messages WHERE riskLabel = 'scam' ORDER BY timestamp DESC")
    suspend fun scams(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isFiltered = 1 ORDER BY timestamp DESC")
    suspend fun filtered(): List<MessageEntity>

    @Query(
        "SELECT COUNT(*) as total, " +
            "COALESCE(SUM(CASE WHEN timestamp >= :startOfDay THEN 1 ELSE 0 END), 0) as today, " +
            "COALESCE(SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END), 0) as unread, " +
            "COALESCE(SUM(CASE WHEN riskLabel = 'scam' AND timestamp >= :startOfDay THEN 1 ELSE 0 END), 0) as scam " +
            "FROM messages"
    )
    suspend fun statsOverview(startOfDay: Long): MessageStatsRow

    @Query(
        "SELECT COUNT(*) as total, " +
            "COALESCE(SUM(CASE WHEN timestamp >= :startOfDay THEN 1 ELSE 0 END), 0) as today, " +
            "COALESCE(SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END), 0) as unread, " +
            "COALESCE(SUM(CASE WHEN riskLabel = 'scam' AND timestamp >= :startOfDay THEN 1 ELSE 0 END), 0) as scam, " +
            "(SELECT packageName FROM messages ORDER BY timestamp DESC LIMIT 1) as latestPackageName, " +
            "(SELECT GROUP_CONCAT(packageName, '|') FROM (" +
            "SELECT packageName FROM messages WHERE timestamp >= :startOfDay " +
            "GROUP BY packageName ORDER BY MAX(timestamp) DESC LIMIT 5" +
            ")) as recentPackagesCsv " +
            "FROM messages"
    )
    suspend fun statsSnapshot(startOfDay: Long): StatsSnapshotRow

    @Query("SELECT COUNT(*) FROM messages WHERE timestamp >= :startOfDay")
    suspend fun todayCount(startOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0")
    suspend fun unreadCount(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE riskLabel = 'scam' AND timestamp >= :startOfDay")
    suspend fun todayScamCount(startOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE riskLabel = 'scam' AND timestamp >= :sinceTs")
    suspend fun scamCountSince(sinceTs: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE riskLabel = 'scam' AND timestamp >= :startTs AND timestamp < :endTs")
    suspend fun scamCountBetween(startTs: Long, endTs: Long): Int

    @Query("SELECT packageName, appLabel, COUNT(*) as cnt, MAX(timestamp) as lastTs, SUM(CASE WHEN riskLabel = 'scam' THEN 2 WHEN riskLabel = 'suspicious' THEN 1 ELSE 0 END) as riskScore FROM messages WHERE timestamp >= :startOfDay GROUP BY packageName ORDER BY lastTs DESC LIMIT 5")
    suspend fun todayRecentApps(startOfDay: Long): List<AppGroupRow>

    @Query("SELECT packageName FROM messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestPackageName(): String?

    @Query("UPDATE messages SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

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

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE timestamp >= :minTs AND timestamp <= :maxTs AND dedupKey = :dedupKey)")
    suspend fun existsByDedupKeyInWindow(dedupKey: String, minTs: Long, maxTs: Long): Boolean

    @Query("SELECT packageName, appLabel, COUNT(*) as cnt, MAX(timestamp) as lastTs, SUM(CASE WHEN riskLabel = 'scam' THEN 2 WHEN riskLabel = 'suspicious' THEN 1 ELSE 0 END) as riskScore FROM messages GROUP BY packageName ORDER BY lastTs DESC")
    suspend fun groupByApp(): List<AppGroupRow>

    @Query("SELECT packageName, COUNT(*) as cnt FROM messages WHERE timestamp >= :startOfDay GROUP BY packageName")
    suspend fun todayCountsByApp(startOfDay: Long): List<AppCountRow>

    @Query("SELECT packageName, COUNT(*) as cnt FROM messages WHERE isRead = 0 AND timestamp >= :startOfDay GROUP BY packageName")
    suspend fun todayUnreadCountsByApp(startOfDay: Long): List<AppCountRow>

    @Query("SELECT packageName, id AS messageId FROM messages WHERE id IN (SELECT MAX(id) FROM messages m1 WHERE timestamp = (SELECT MAX(timestamp) FROM messages m2 WHERE m2.packageName = m1.packageName) GROUP BY packageName)")
    suspend fun latestMessageIdsByApp(): List<AppLatestMsgRow>

    @Query("SELECT * FROM messages WHERE packageName = :packageName ORDER BY timestamp DESC")
    suspend fun byApp(packageName: String): List<MessageEntity>

    @Query("SELECT sender, COUNT(*) as cnt, SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END) as unreadCnt, MAX(timestamp) as lastTs FROM messages WHERE packageName = :packageName GROUP BY sender ORDER BY lastTs DESC")
    suspend fun groupBySender(packageName: String): List<SenderGroupRow>

    @Query("SELECT * FROM messages WHERE packageName = :packageName AND sender = :sender ORDER BY timestamp ASC")
    suspend fun byConversation(packageName: String, sender: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE packageName = :packageName AND sender = :sender AND (:beforeTs IS NULL OR timestamp < :beforeTs) ORDER BY timestamp DESC LIMIT :limit")
    suspend fun byConversationKeyset(packageName: String, sender: String, beforeTs: Long?, limit: Int): List<MessageEntity>

    @Query("UPDATE messages SET isRead = 1 WHERE packageName = :packageName AND sender = :sender AND isRead = 0")
    suspend fun markConversationRead(packageName: String, sender: String)

    @Query("SELECT * FROM messages WHERE timestamp >= :sinceTs ORDER BY timestamp DESC")
    suspend fun since(sinceTs: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE timestamp >= :startTs AND timestamp < :endTs ORDER BY timestamp DESC")
    suspend fun between(startTs: Long, endTs: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE timestamp >= :startTs AND timestamp <= :endTs AND (:packageName IS NULL OR packageName = :packageName) ORDER BY timestamp DESC LIMIT :limit")
    suspend fun queryRange(startTs: Long, endTs: Long, packageName: String?, limit: Int = 40): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE riskLabel = :label")
    suspend fun countByRiskLabel(label: String): Int

    @Query("SELECT packageName, appLabel, COUNT(*) as cnt FROM messages WHERE riskLabel IN ('scam', 'suspicious') GROUP BY packageName ORDER BY cnt DESC LIMIT 5")
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

    @Query("DELETE FROM messages WHERE packageName = :packageName AND sender = :sender")
    suspend fun deleteByConversation(packageName: String, sender: String)

    @Query("DELETE FROM messages WHERE packageName = :packageName")
    suspend fun deleteByApp(packageName: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("DELETE FROM messages WHERE timestamp < :before AND riskLabel != 'scam'")
    suspend fun prune(before: Long)

    @Query("DELETE FROM messages WHERE riskLabel != 'scam' AND id NOT IN (SELECT id FROM messages ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun deleteOlderThanCount(keepCount: Int)
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

    @Query("DELETE FROM message_tags WHERE messageId IN (SELECT id FROM messages WHERE packageName = :packageName AND sender = :sender)")
    suspend fun deleteCrossRefsByConversation(packageName: String, sender: String)

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
        IntelResultEntity::class
    ],
    version = 8,
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
    }
}
