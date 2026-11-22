package com.novoagentehb.app.agent.policy

import com.novoagentehb.app.agent.orchestrator.RiskLevel
import com.novoagentehb.app.agent.orchestrator.ToolDescriptor
import com.novoagentehb.app.agent.orchestrator.ToolExecutionRequest
import javax.inject.Inject
import javax.inject.Singleton

sealed class PolicyDecision {
    object Allowed : PolicyDecision()
    data class RequireConfirmation(val reasonPrompt: String) : PolicyDecision()
    data class Denied(val reason: String) : PolicyDecision()
}

/**
 * Portão de segurança e custos do NOVO_AGENTE_HB:
 * - Exige confirmação explícita antes de efeitos externos (WhatsApp, ligações, eventos, arquivos externos).
 * - Bloqueia automação autônoma de credenciais/senhas/OTP.
 * - Bloqueia chamadas pagas ou de custo ambíguo quando o consentimento não foi concedido.
 */
@Singleton
class PolicyGate @Inject constructor() {

    private val forbiddenSensitiveKeywords = listOf(
        "otp", "codigo de verificacao", "código de verificação", "2fa",
        "token bancario", "senha do banco", "cvv", "pin do cartao"
    )

    fun evaluate(
        descriptor: ToolDescriptor,
        request: ToolExecutionRequest,
        allowPaidWithoutPrompt: Boolean
    ): PolicyDecision {
        // 1. Bloqueia qualquer tentativa de preencher/capturar credenciais ou OTP automaticamente
        val combinedArgs = request.args.values.joinToString(" ").lowercase()
        if (forbiddenSensitiveKeywords.any { combinedArgs.contains(it) }) {
            return PolicyDecision.Denied(
                "Ação bloqueada pela política de segurança: o agente não manipula senhas, PIN ou códigos OTP."
            )
        }

        // 2. Nunca herda ou executa comandos da antiga camada Pet/Tamagotchi
        if (descriptor.name.startsWith("tama.pet") || descriptor.name.contains("dragon", ignoreCase = true)) {
            return PolicyDecision.Denied("Capacidade removida da arquitetura deste produto.")
        }

        // Geração de imagem exige confirmação por pedido, inclusive se o opt-in genérico estiver ativo.
        if (descriptor.name == "generate_image" && (!request.userConfirmed || !request.paidConsentGranted)) {
            return PolicyDecision.RequireConfirmation("Enviar ao Gemini para gerar uma imagem? Modelo: ${request.args["model"].orEmpty().take(100)}. Descrição: ${request.args["prompt"].orEmpty().take(4000)}. Pode consumir créditos; confira preço e disponibilidade antes de autorizar.")
        }

        // 3. Portão de custo: nunca presume gratuidade em chamadas pagas/ambíguas
        if (descriptor.riskLevel == RiskLevel.PAID_API_CALL && !request.paidConsentGranted && !allowPaidWithoutPrompt) {
            return PolicyDecision.RequireConfirmation(
                "Esta ação (${descriptor.name}) pode consumir cota ou créditos de API externa. Autorizar execução?"
            )
        }

        // 4. Portão de efeito externo (WhatsApp, ligação, calendário)
        if ((descriptor.riskLevel == RiskLevel.EXTERNAL_SIDE_EFFECT || descriptor.name == "execute_workspace_program") && !request.userConfirmed) {
            val detail = when (descriptor.name) {
                "execute_workspace_program" ->
                    "Executar localmente a expressão aritmética \"${request.args["expression"].orEmpty().take(256)}\" nos números fornecidos e criar um novo CSV privado? Não é shell Linux."
                "send_whatsapp_message" ->
                    "Enviar WhatsApp para ${request.args["contact_name"] ?: "contato"}: \"${request.args["message"].orEmpty()}\"?"
                "call_contact", "call_number" ->
                    "Realizar ligação para ${request.args["contact_name"] ?: request.args["phone_number"] ?: "destinatário"}?"
                "create_calendar_event" ->
                    "Criar compromisso \"${request.args["title"].orEmpty()}\" em ${request.args["start_at"].orEmpty()}?"
                else -> "Autorizar execução externa de ${descriptor.name}?"
            }
            return PolicyDecision.RequireConfirmation(detail)
        }

        return PolicyDecision.Allowed
    }
}
