package com.novoagentehb.app.data.migration

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.novoagentehb.app.data.local.GoalDao
import com.novoagentehb.app.data.local.GoalItemEntity
import com.novoagentehb.app.data.local.MemoryDao
import com.novoagentehb.app.data.local.MemoryItemEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class ExportSnapshot(
    val schemaVersion: Int = 1,
    val productId: String = "NOVO_AGENTE_HB",
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val memories: List<MemoryItemEntity> = emptyList(),
    val goals: List<GoalItemEntity> = emptyList()
)

data class ImportOutcome(
    val success: Boolean,
    val importedMemories: Int,
    val importedGoals: Int,
    val message: String
)

/**
 * Exportação e importação explícita e reversível (nunca executada em segundo
 * plano nem copiando Keystore/bancos de apps antigos).
 */
@Singleton
class ExplicitDataExporter @Inject constructor(
    private val memoryDao: MemoryDao,
    private val goalDao: GoalDao
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportToJson(): String {
        val memories = memoryDao.observeAll().first()
        val goals = goalDao.observeAll().first()
        return gson.toJson(ExportSnapshot(memories = memories, goals = goals))
    }

    suspend fun importFromJson(rawJson: String, replaceExisting: Boolean = false): ImportOutcome {
        val parsed = runCatching { gson.fromJson(rawJson, ExportSnapshot::class.java) }.getOrNull()
            ?: return ImportOutcome(false, 0, 0, "JSON inválido para importação.")
        if (parsed.schemaVersion != 1) {
            return ImportOutcome(false, 0, 0, "Versão de esquema incompatível: ${parsed.schemaVersion}")
        }

        // Captura snapshot de rollback antes de alterar qualquer tabela local.
        val rollbackBackup = exportToJson()
        return runCatching {
            if (replaceExisting) {
                memoryDao.clearAll()
            }
            var memCount = 0
            for (mem in parsed.memories) {
                memoryDao.upsert(mem.copy(id = 0, provenance = "explicit_import"))
                memCount++
            }
            var goalCount = 0
            for (goal in parsed.goals) {
                goalDao.upsert(goal.copy(id = 0))
                goalCount++
            }
            ImportOutcome(
                success = true,
                importedMemories = memCount,
                importedGoals = goalCount,
                message = "Importados $memCount registros de memória e $goalCount metas."
            )
        }.getOrElse { err ->
            runCatching {
                val prev = gson.fromJson(rollbackBackup, ExportSnapshot::class.java)
                if (prev != null && replaceExisting) {
                    memoryDao.clearAll()
                    prev.memories.forEach { memoryDao.upsert(it.copy(id = 0)) }
                }
            }
            ImportOutcome(false, 0, 0, "Falha na importação (rollback executado): ${err.message}")
        }
    }
}
