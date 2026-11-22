package com.novoagentehb.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ChatRole { USER, ASSISTANT, SYSTEM, RECEIPT }

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,
    val content: String,
    val taskId: String? = null,
    val providerUsed: String? = null,
    val modelUsed: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class MemoryCategory {
    USER_NAME, PREFERENCE, HABIT, ROUTINE, FACT, FAVORITE_APP, EPISODE, TASK_NOTE
}

@Entity(tableName = "memory_items")
data class MemoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val memoryKey: String,
    val value: String,
    val provenance: String = "user_conversation",
    val confidence: Float = 1.0f,
    val useCount: Int = 0,
    val lastUsedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class GoalStatus { ACTIVE, IN_PROGRESS, COMPLETED, ARCHIVED }

@Entity(tableName = "goals")
data class GoalItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val category: String = "Pessoal",
    val status: String = GoalStatus.ACTIVE.name,
    val linkedTaskIds: String = "",
    val progressPercent: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerAtMillis: Long,
    val triggered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "feed_events")
data class FeedEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePackage: String,
    val sourceLabel: String,
    val title: String,
    val body: String,
    val kind: String,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "task_receipts")
data class TaskReceiptEntity(
    @PrimaryKey val taskId: String,
    val idempotencyKey: String,
    val goalId: Long? = null,
    val capabilityName: String,
    val riskLevel: String,
    val status: String,
    val summary: String,
    val verifiedOutcome: Boolean,
    val errorCode: String? = null,
    val providerUsed: String? = null,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "skill_records")
data class SkillRecordEntity(
    @PrimaryKey val skillId: String,
    val name: String,
    val sourceType: String,
    val version: String,
    val summary: String,
    val scope: String,
    val manifestOrigin: String,
    val enabledByUser: Boolean = false,
    val untrustedContentFlag: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
