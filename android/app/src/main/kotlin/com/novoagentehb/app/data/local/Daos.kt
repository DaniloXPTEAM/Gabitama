package com.novoagentehb.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentDesc(limit: Int = 20): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_items ORDER BY lastUsedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM memory_items ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun getTop(limit: Int = 25): List<MemoryItemEntity>

    @Query("SELECT * FROM memory_items WHERE memoryKey = :key LIMIT 1")
    suspend fun findByKey(key: String): MemoryItemEntity?

    @Query("SELECT * FROM memory_items WHERE value LIKE '%' || :query || '%' OR memoryKey LIKE '%' || :query || '%' ORDER BY lastUsedAt DESC")
    suspend fun search(query: String): List<MemoryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MemoryItemEntity): Long

    @Delete
    suspend fun delete(item: MemoryItemEntity)

    @Query("DELETE FROM memory_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memory_items")
    suspend fun clearAll()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<GoalItemEntity>>

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): GoalItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalItemEntity): Long

    @Update
    suspend fun update(goal: GoalItemEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY triggerAtMillis ASC")
    fun observeAll(): Flow<List<ReminderItemEntity>>

    @Query("SELECT * FROM reminders WHERE triggered = 0 AND triggerAtMillis >= :start AND triggerAtMillis < :end ORDER BY triggerAtMillis ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<ReminderItemEntity>>

    @Query("SELECT * FROM reminders WHERE triggered = 0 ORDER BY triggerAtMillis ASC")
    suspend fun getPending(): List<ReminderItemEntity>

    @Insert
    suspend fun insert(reminder: ReminderItemEntity): Long

    @Query("UPDATE reminders SET triggered = 1 WHERE id = :id")
    suspend fun markTriggered(id: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface FeedEventDao {
    @Query("SELECT * FROM feed_events ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<FeedEventEntity>>

    @Query("SELECT COUNT(*) FROM feed_events WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert
    suspend fun insert(item: FeedEventEntity): Long

    @Query("UPDATE feed_events SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM feed_events")
    suspend fun clearAll()
}

@Dao
interface TaskReceiptDao {
    @Query("SELECT * FROM task_receipts ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<TaskReceiptEntity>>

    @Query("SELECT * FROM task_receipts WHERE idempotencyKey = :key LIMIT 1")
    suspend fun findByIdempotencyKey(key: String): TaskReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(receipt: TaskReceiptEntity)

    @Query("DELETE FROM task_receipts")
    suspend fun clearAll()
}

@Dao
interface SkillRecordDao {
    @Query("SELECT * FROM skill_records ORDER BY name ASC")
    fun observeAll(): Flow<List<SkillRecordEntity>>

    @Query("SELECT * FROM skill_records WHERE enabledByUser = 1 ORDER BY name ASC")
    suspend fun getEnabled(): List<SkillRecordEntity>

    @Query("SELECT * FROM skill_records WHERE skillId = :skillId LIMIT 1")
    suspend fun getById(skillId: String): SkillRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SkillRecordEntity)

    @Query("UPDATE skill_records SET enabledByUser = :enabled, updatedAt = :updatedAt WHERE skillId = :skillId")
    suspend fun setEnabled(skillId: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM skill_records WHERE skillId = :skillId")
    suspend fun deleteById(skillId: String)
}
