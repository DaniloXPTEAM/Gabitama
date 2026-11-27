package com.novoagentehb.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatMessageEntity::class,
        MemoryItemEntity::class,
        GoalItemEntity::class,
        ReminderItemEntity::class,
        FeedEventEntity::class,
        TaskReceiptEntity::class,
        SkillRecordEntity::class,
        BrowserHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun goalDao(): GoalDao
    abstract fun reminderDao(): ReminderDao
    abstract fun feedEventDao(): FeedEventDao
    abstract fun taskReceiptDao(): TaskReceiptDao
    abstract fun skillRecordDao(): SkillRecordDao
    abstract fun browserHistoryDao(): BrowserHistoryDao

    companion object {
        const val DATABASE_NAME = "novo_agente_hb.db"
    }
}
