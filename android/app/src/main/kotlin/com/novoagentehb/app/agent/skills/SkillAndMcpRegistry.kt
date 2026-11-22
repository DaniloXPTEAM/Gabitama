package com.novoagentehb.app.agent.skills

import com.novoagentehb.app.data.local.SkillRecordDao
import com.novoagentehb.app.data.local.SkillRecordEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registro de Skills e servidores MCP com quarentena de conteúdo externo:
 * - Instalação/ativação exige opt-in explícito da pessoa (`enabledByUser`).
 * - Instruções de terceiros são rotuladas como não confiáveis e nunca executam
 *   automaticamente só por estarem presentes em disco.
 */
@Singleton
class SkillAndMcpRegistry @Inject constructor(
    private val dao: SkillRecordDao
) {
    fun observeAll(): Flow<List<SkillRecordEntity>> = dao.observeAll()

    suspend fun getById(skillId: String): SkillRecordEntity? = dao.getById(skillId)

    suspend fun registerDescriptor(
        skillId: String,
        name: String,
        sourceType: String,
        version: String,
        summary: String,
        scope: String,
        manifestOrigin: String
    ): SkillRecordEntity {
        val cleanId = skillId.trim().lowercase().replace(Regex("[^a-z0-9._-]+"), "-")
        val entity = SkillRecordEntity(
            skillId = cleanId,
            name = name.trim(),
            sourceType = sourceType,
            version = version.trim().ifBlank { "0.1.0" },
            summary = sanitizeUntrustedDescription(summary),
            scope = scope.trim(),
            manifestOrigin = manifestOrigin.trim(),
            enabledByUser = false,
            untrustedContentFlag = true
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun updateVerifiedSummary(skillId: String, summary: String) {
        val cur = dao.getById(skillId) ?: return
        dao.upsert(
            cur.copy(
                summary = sanitizeUntrustedDescription(summary),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setEnabled(skillId: String, enabled: Boolean) {
        dao.setEnabled(skillId, enabled)
    }

    suspend fun remove(skillId: String) {
        dao.deleteById(skillId)
    }

    suspend fun activeSkillsPromptFragment(maxChars: Int = 1200): String {
        val active = dao.getEnabled()
        if (active.isEmpty()) return ""
        val sb = StringBuilder("Habilidades/MCP ativas (somente metadados aprovados):\n")
        for (s in active) {
            val line = "- ${s.name} [${s.sourceType} v${s.version}] (${s.scope}): ${s.summary}\n"
            if (sb.length + line.length > maxChars) break
            sb.append(line)
        }
        return sb.toString().trim()
    }

    companion object {
        fun sanitizeUntrustedDescription(raw: String): String =
            raw.replace(Regex("[\\r\\n]+"), " ")
                .replace(Regex("(?i)(ignore previous|\\bsystem prompt\\b|\\bexec\\b|\\bshell_execute\\b)"), "[filtrado]")
                .trim()
                .take(220)
    }
}
