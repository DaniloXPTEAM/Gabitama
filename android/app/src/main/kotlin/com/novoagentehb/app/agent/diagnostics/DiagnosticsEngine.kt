package com.novoagentehb.app.agent.diagnostics

import android.os.Build
import com.novoagentehb.app.BuildConfig
import com.novoagentehb.app.agent.models.ProviderFailure
import com.novoagentehb.app.data.prefs.ProviderId
import com.novoagentehb.app.data.prefs.VoiceModeId
import com.novoagentehb.app.data.security.SecureKeyVault
import javax.inject.Inject
import javax.inject.Singleton

data class DiagnosticsInput(
    val primaryProvider: ProviderId,
    val chatModel: String,
    val voiceMode: VoiceModeId,
    val prosodyProfile: String,
    val lastFailure: ProviderFailure? = null,
    val petComponentsPresent: Boolean = false
)

/**
 * Gerador do relatório `/diagnostico` com lista fechada de campos:
 * - Nunca inclui histórico de conversa, chaves de API, nomes pessoais,
 *   números de telefone ou payloads crus de erro não sanitizados.
 */
@Singleton
class DiagnosticsEngine @Inject constructor() {

    fun isExactCommand(input: String): Boolean {
        val normalized = input.trim().lowercase()
        return normalized == "/diagnostico" || normalized == "/diagnóstico"
    }

    fun buildReport(input: DiagnosticsInput): String {
        val failureSummary = input.lastFailure?.let {
            "${it.providerId} | HTTP=${it.httpStatus ?: "UNAVAILABLE"} | ${it.category.name}"
        } ?: "NONE"

        val rawReport = buildString {
            appendLine("=== DIAGNÓSTICO LOCAL (NOVO_AGENTE_HB) ===")
            appendLine("produto_provisorio: ${BuildConfig.PROVISIONAL_INTERNAL_ID}")
            appendLine("application_id: ${BuildConfig.APPLICATION_ID}")
            appendLine("versao: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("build_time: ${BuildConfig.BUILD_TIME}")
            appendLine("android_api: ${Build.VERSION.SDK_INT}")
            appendLine("abi_principal: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "UNAVAILABLE"}")
            appendLine("provedor_primario: ${input.primaryProvider.id}")
            appendLine("modelo_chat: ${input.chatModel.ifBlank { "UNAVAILABLE" }}")
            appendLine("motor_voz: ${input.voiceMode.id} (perfil=${input.prosodyProfile})")
            appendLine("ultima_falha_rede: $failureSummary")
            appendLine("camada_pet_excluida: ${!input.petComponentsPresent}")
        }.trim()

        return SecureKeyVault.redactSecrets(rawReport)
    }
}
