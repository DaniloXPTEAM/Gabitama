package com.novoagentehb.app.agent.skills

import com.novoagentehb.app.agent.orchestrator.ToolExecutionResult
import com.novoagentehb.app.agent.orchestrator.ToolExecutionSemantics
import com.novoagentehb.app.bridges.SafLibraryManager
import com.novoagentehb.app.bridges.WorkspaceWriteOutcome
import com.novoagentehb.app.data.local.SkillRecordEntity
import javax.inject.Inject
import javax.inject.Singleton

enum class SkillExecutionStatus {
    TASK_EXECUTED,
    ARTIFACT_PREPARED,
    BLOCKED_OPT_IN,
    BLOCKED_QUARANTINE,
    INVALID_TYPE,
    FAILED
}

data class SkillExecutionOutcome(
    val status: SkillExecutionStatus,
    val artifactCreated: Boolean,
    val artifactPath: String?,
    val taskExecuted: Boolean,
    val verifiedPostCondition: Boolean,
    val sha256: String = "",
    val summary: String,
    val errorCode: String? = null,
    val semantics: ToolExecutionSemantics = ToolExecutionSemantics.ARTIFACT_CREATED_ONLY
)

/**
 * Contrato honesto de Skills locais no workspace privado:
 * - Nunca interpreta instruções livres nem executa shell/código externo.
 * - Separa explicitamente `ARTIFACT_PREPARED` (manifesto Markdown salvo, sem execução de ações)
 *   de `TASK_EXECUTED` (somente adaptador determinístico `workspace_action_write_verify` com
 *   gravação + releitura do disco + conferência SHA-256).
 */
@Singleton
class LocalSkillExecutor @Inject constructor(
    private val safLibraryManager: SafLibraryManager
) {
    fun execute(skill: SkillRecordEntity, inputContext: String): SkillExecutionOutcome =
        executeWithStore(
            skill = skill,
            inputContext = inputContext,
            writeFile = { path, text -> safLibraryManager.writeWorkspaceFile(path, text) },
            readFile = { path -> safLibraryManager.readWorkspaceFile(path) }
        )

    companion object {
        const val SCOPE_WORKSPACE_ONLY = "workspace_only"
        const val SCOPE_WORKSPACE_WRITE_VERIFY = "workspace_action_write_verify"

        /**
         * Converte o desfecho da skill no resultado da ferramenta `run_local_skill`.
         * `ARTIFACT_PREPARED` (manifesto) nunca vira "ação executada": `verified` segue
         * o pós-condição real reportada pelo executor.
         */
        fun toToolResult(out: SkillExecutionOutcome): ToolExecutionResult = when (out.status) {
            SkillExecutionStatus.TASK_EXECUTED,
            SkillExecutionStatus.ARTIFACT_PREPARED -> ToolExecutionResult(
                success = true,
                verified = out.verifiedPostCondition,
                summary = out.summary,
                payload = out.artifactPath.orEmpty(),
                errorCode = null,
                semantics = out.semantics
            )
            SkillExecutionStatus.BLOCKED_OPT_IN,
            SkillExecutionStatus.BLOCKED_QUARANTINE -> ToolExecutionResult(
                success = false,
                verified = false,
                summary = out.summary,
                errorCode = out.errorCode ?: "SKILL_BLOCKED",
                semantics = out.semantics
            )
            SkillExecutionStatus.INVALID_TYPE,
            SkillExecutionStatus.FAILED -> ToolExecutionResult(
                success = false,
                verified = false,
                summary = out.summary,
                errorCode = out.errorCode ?: "SKILL_FAILED",
                semantics = out.semantics
            )
        }

        fun buildManifestRelativePath(skillId: String): String {
            val safe = sanitizeSlug(skillId)
            return "skills/$safe-manifesto.md"
        }

        fun buildVerifiedOutputRelativePath(skillId: String): String {
            val safe = sanitizeSlug(skillId)
            return "skills/$safe-documento-verificado.md"
        }

        private fun sanitizeSlug(skillId: String): String =
            skillId.lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-').ifBlank { "skill" }

        fun containsQuarantinedInjection(text: String): Boolean {
            val cleaned = SkillAndMcpRegistry.sanitizeUntrustedDescription(text)
            return cleaned.contains("[filtrado]")
        }

        fun executeWithStore(
            skill: SkillRecordEntity,
            inputContext: String,
            writeFile: (relativePath: String, content: String) -> WorkspaceWriteOutcome,
            readFile: (relativePath: String) -> String?
        ): SkillExecutionOutcome {
            if (!skill.enabledByUser) {
                return SkillExecutionOutcome(
                    status = SkillExecutionStatus.BLOCKED_OPT_IN,
                    artifactCreated = false,
                    artifactPath = null,
                    taskExecuted = false,
                    verifiedPostCondition = false,
                    summary = "Skill \"${skill.name}\" está desativada. Ative o opt-in antes de prosseguir.",
                    errorCode = "SKILL_DISABLED",
                    semantics = ToolExecutionSemantics.NOT_IMPLEMENTED
                )
            }
            if (skill.sourceType != "LOCAL_SKILL") {
                return SkillExecutionOutcome(
                    status = SkillExecutionStatus.INVALID_TYPE,
                    artifactCreated = false,
                    artifactPath = null,
                    taskExecuted = false,
                    verifiedPostCondition = false,
                    summary = "Registro \"${skill.name}\" não é uma Skill local.",
                    errorCode = "INVALID_SKILL_TYPE",
                    semantics = ToolExecutionSemantics.NOT_IMPLEMENTED
                )
            }

            if (containsQuarantinedInjection(skill.summary) || containsQuarantinedInjection(inputContext)) {
                return SkillExecutionOutcome(
                    status = SkillExecutionStatus.BLOCKED_QUARANTINE,
                    artifactCreated = false,
                    artifactPath = null,
                    taskExecuted = false,
                    verifiedPostCondition = false,
                    summary = "Skill \"${skill.name}\" retida em quarentena: padrão suspeito/injeção detectado.",
                    errorCode = "SKILL_QUARANTINED",
                    semantics = ToolExecutionSemantics.NOT_IMPLEMENTED
                )
            }

            // Caminho 1: adaptador determinístico permitido (grava documento + relê + valida SHA-256)
            if (skill.scope == SCOPE_WORKSPACE_WRITE_VERIFY) {
                val targetPath = buildVerifiedOutputRelativePath(skill.skillId)
                val body = buildExecutionArtifactContent(skill, inputContext, modeLabel = "TASK_EXECUTED_WORKSPACE_WRITE")
                val written = writeFile(targetPath, body)
                if (!written.success) {
                    return SkillExecutionOutcome(
                        status = SkillExecutionStatus.FAILED,
                        artifactCreated = false,
                        artifactPath = targetPath,
                        taskExecuted = false,
                        verifiedPostCondition = false,
                        summary = "Falha ao gravar documento da skill \"${skill.name}\": ${written.error}",
                        errorCode = "SKILL_WRITE_FAILED",
                        semantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED
                    )
                }
                val readBack = readFile(written.relativePath)
                val readSha = readBack?.let { SafLibraryManager.sha256Hex(it.toByteArray(Charsets.UTF_8)) }.orEmpty()
                val verified = readBack != null && readSha == written.sha256 && readBack.contains(skill.skillId)
                return if (verified) {
                    SkillExecutionOutcome(
                        status = SkillExecutionStatus.TASK_EXECUTED,
                        artifactCreated = true,
                        artifactPath = written.relativePath,
                        taskExecuted = true,
                        verifiedPostCondition = true,
                        sha256 = readSha,
                        summary = "Ação local da skill \"${skill.name}\" executada e verificada (gravação + releitura SHA-256=${readSha.take(12)}… em ${written.relativePath}).",
                        errorCode = null,
                        semantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED
                    )
                } else {
                    SkillExecutionOutcome(
                        status = SkillExecutionStatus.FAILED,
                        artifactCreated = true,
                        artifactPath = written.relativePath,
                        taskExecuted = false,
                        verifiedPostCondition = false,
                        sha256 = written.sha256,
                        summary = "Arquivo criado em ${written.relativePath}, mas a releitura SHA-256 falhou na pós-condição.",
                        errorCode = "POST_CONDITION_MISMATCH",
                        semantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED
                    )
                }
            }

            // Caminho 2: skill de instrução livre -> NÃO interpreta/executa; apenas prepara manifesto honesto
            val manifestPath = buildManifestRelativePath(skill.skillId)
            val manifestContent = buildExecutionArtifactContent(skill, inputContext, modeLabel = "MANIFEST_PREPARED_ONLY")
            val written = writeFile(manifestPath, manifestContent)
            return if (written.success) {
                SkillExecutionOutcome(
                    status = SkillExecutionStatus.ARTIFACT_PREPARED,
                    artifactCreated = true,
                    artifactPath = written.relativePath,
                    taskExecuted = false,
                    verifiedPostCondition = false,
                    sha256 = written.sha256,
                    summary = "Manifesto/artefato da skill preparado em ${written.relativePath} (sha256=${written.sha256.take(12)}…). Nenhuma ação automática foi executada: interpretador de instruções livres NOT_IMPLEMENTED.",
                    errorCode = "SKILL_INTERPRETER_NOT_IMPLEMENTED",
                    semantics = ToolExecutionSemantics.ARTIFACT_CREATED_ONLY
                )
            } else {
                SkillExecutionOutcome(
                    status = SkillExecutionStatus.FAILED,
                    artifactCreated = false,
                    artifactPath = manifestPath,
                    taskExecuted = false,
                    verifiedPostCondition = false,
                    summary = "Falha ao preparar manifesto da skill \"${skill.name}\": ${written.error}",
                    errorCode = "SKILL_MANIFEST_WRITE_FAILED",
                    semantics = ToolExecutionSemantics.ARTIFACT_CREATED_ONLY
                )
            }
        }

        fun buildExecutionArtifactContent(
            skill: SkillRecordEntity,
            inputContext: String,
            modeLabel: String = "MANIFEST_PREPARED_ONLY"
        ): String {
            val cleanInput = SkillAndMcpRegistry.sanitizeUntrustedDescription(
                inputContext.ifBlank { "Registro via Computador & Skills" }
            )
            return buildString {
                appendLine("# Skill Local: ${skill.name}")
                appendLine("- skill_id: `${skill.skillId}`")
                appendLine("- mode: `$modeLabel`")
                appendLine("- version: `${skill.version}`")
                appendLine("- scope: `${skill.scope}`")
                appendLine("- untrusted_quarantine_flag: `${skill.untrustedContentFlag}`")
                appendLine()
                appendLine("## Regra / Instrução Sanitizada")
                appendLine(skill.summary)
                appendLine()
                appendLine("## Contexto Informado")
                appendLine(cleanInput)
            }
        }
    }
}
