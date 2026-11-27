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
            return PolicyDecision.RequireConfirmation("Enviar o pedido ao FLUX.1-schnell (NVIDIA NIM) para gerar uma imagem? Descrição: ${request.args["prompt"].orEmpty().take(4000)}. Rota de teste sem custo autorizado; se a conta exigir cobrança ou atingir o limite, a chamada falha sem cobrar.")
        }

        // 3. Portão de custo: nunca presume gratuidade em chamadas pagas/ambíguas
        if (descriptor.riskLevel == RiskLevel.PAID_API_CALL && !request.paidConsentGranted && !allowPaidWithoutPrompt) {
            return PolicyDecision.RequireConfirmation(
                "Esta ação (${descriptor.name}) pode consumir cota ou créditos de API externa. Autorizar execução?"
            )
        }

        // 4. Portão de efeito externo (WhatsApp, ligação, calendário)
        if ((descriptor.riskLevel == RiskLevel.EXTERNAL_SIDE_EFFECT || descriptor.name == "execute_workspace_program" || descriptor.name == "run_local_skill") && !request.userConfirmed) {
            val detail = when (descriptor.name) {
                "run_local_skill" ->
                    "Executar a skill local \"${request.args["skill_id"].orEmpty().take(100)}\" no workspace privado? Ela só grava o arquivo verificado da própria skill; não executa shell."
                "execute_workspace_program" ->
                    "Executar localmente a expressão aritmética \"${request.args["expression"].orEmpty().take(256)}\" nos números fornecidos e criar um novo CSV privado? Não é shell Linux."
                "send_whatsapp_message" ->
                    "Enviar WhatsApp para ${request.args["contact_name"] ?: "contato"}: \"${request.args["message"].orEmpty()}\"?"
                "call_contact", "call_number" ->
                    "Realizar ligação para ${request.args["contact_name"] ?: request.args["phone_number"] ?: "destinatário"}?"
                "browser_page_interact" -> {
                    val alvo = request.args["selector"].orEmpty().take(200)
                    if (request.args["action"].equals("fill", ignoreCase = true)) {
                        "Preencher o campo “$alvo” na página do navegador com o texto “${request.args["value"].orEmpty().take(200)}”? O texto pode ser enviado ao site se o formulário for enviado depois."
                    } else {
                        "Clicar no elemento “$alvo” na página do navegador? Isso pode enviar formulário, abrir link ou executar ação no site."
                    }
                }
                "mcp_call_tool" ->
                    "Chamar a ferramenta MCP '${request.args["tool_name"].orEmpty().take(64)}' no servidor '${request.args["server_id"].orEmpty().take(100)}'? Os argumentos saem do aparelho; a resposta do servidor não é verificada pelo app."
                "create_calendar_event" ->
                    "Criar compromisso \"${request.args["title"].orEmpty()}\" em ${request.args["start_at"].orEmpty()}?"
                else -> "Autorizar execução externa de ${descriptor.name}?"
            }
            return PolicyDecision.RequireConfirmation(detail)
        }

        return PolicyDecision.Allowed
    }
}
