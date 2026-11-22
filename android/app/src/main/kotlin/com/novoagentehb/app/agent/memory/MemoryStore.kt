package com.novoagentehb.app.agent.memory

import com.novoagentehb.app.data.local.MemoryCategory
import com.novoagentehb.app.data.local.MemoryDao
import com.novoagentehb.app.data.local.MemoryItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fonte única e durável de memória (Room SQLite), com proveniência,
 * deduplicação por chave canônica e esquecimento explícito.
 */
@Singleton
class MemoryStore @Inject constructor(
    private val dao: MemoryDao
) {
    fun observeAll(): Flow<List<MemoryItemEntity>> = dao.observeAll()

    suspend fun getTopContext(limit: Int = 15): List<MemoryItemEntity> = dao.getTop(limit)

    suspend fun search(query: String): List<MemoryItemEntity> =
        if (query.isBlank()) emptyList() else dao.search(query.trim())

    suspend fun upsertFact(
        rawKey: String,
        value: String,
        category: MemoryCategory = MemoryCategory.FACT,
        provenance: String = "user_conversation"
    ): MemoryItemEntity {
        val canonicalKey = rawKey.trim().lowercase().replace(Regex("[^a-z0-9_]+"), "_").ifBlank { "fato" }
        val existing = dao.findByKey(canonicalKey)
        val now = System.currentTimeMillis()
        val entity = if (existing != null) {
            existing.copy(
                category = category.name,
                value = value.trim(),
                provenance = provenance,
                useCount = existing.useCount + 1,
                lastUsedAt = now
            )
        } else {
            MemoryItemEntity(
                category = category.name,
                memoryKey = canonicalKey,
                value = value.trim(),
                provenance = provenance,
                confidence = 1.0f,
                useCount = 1,
                lastUsedAt = now,
                createdAt = now
            )
        }
        val rowId = dao.upsert(entity)
        return entity.copy(id = if (entity.id == 0L) rowId else entity.id)
    }

    suspend fun forget(item: MemoryItemEntity) = dao.delete(item)

    suspend fun forgetById(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()
}
