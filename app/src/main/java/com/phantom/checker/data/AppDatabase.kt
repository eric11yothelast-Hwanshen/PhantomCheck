package com.phantom.checker.data

import androidx.room.*

@Entity(tableName = "messages")
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
    val isRead: Boolean = false   // 幻訊內的已讀標記（不影響原 App）
)

@Entity(tableName = "filter_rules")
data class FilterRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val isActive: Boolean = true
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 200")
    suspend fun recent(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :q || '%' OR sender LIKE '%' || :q || '%' ORDER BY timestamp DESC")
    suspend fun search(q: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE riskLabel = 'scam' ORDER BY timestamp DESC")
    suspend fun scams(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isFiltered = 1 ORDER BY timestamp DESC")
    suspend fun filtered(): List<MessageEntity>

    @Insert
    suspend fun insert(msg: MessageEntity): Long

    @Update
    suspend fun update(msg: MessageEntity)

    @Query("DELETE FROM messages WHERE timestamp < :before")
    suspend fun prune(before: Long)
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

@Database(entities = [MessageEntity::class, FilterRule::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun filterDao(): FilterDao
}
