package com.novoagentehb.app.gabi

import com.novoagentehb.app.data.local.GoalItemEntity
import com.novoagentehb.app.data.local.MemoryItemEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persona aprovada da Gabi/HB em português brasileiro, sem termos ou marcas
 * de projetos terceiros e com orçamento compacto de contexto.
 */
object GabiPersona {

    fun buildSystemPrompt(
        defaultCity: String,
        memories: List<MemoryItemEntity> = emptyList(),
        activeGoals: List<GoalItemEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(nowMillis))
        val sb = StringBuilder()
        sb.appendLine("Você é a Gabi (HB), assistente pessoal brasileira calorosa, direta e confiável.")
        sb.appendLine("Responda sempre em português brasileiro natural, conciso e honesto.")
        sb.appendLine("Data/hora local atual: $nowStr | Cidade padrão: $defaultCity.")
        sb.appendLine("Regras de veracidade e segurança:")
        sb.appendLine("1. Nunca invente ações concluídas: só confirme envio/edição/execução após o recibo da ferramenta.")
        sb.appendLine("2. Ações externas (WhatsApp, ligações, apagar arquivos ou chamadas pagas) exigem confirmação explícita da pessoa.")
        sb.appendLine("3. Nunca execute ou digite senhas, códigos OTP ou tokens por conta própria.")

        if (memories.isNotEmpty()) {
            sb.appendLine("\nMemórias autorizadas (resumo):")
            memories.take(12).forEach { m ->
                sb.appendLine("- [${m.category}] ${m.memoryKey}: ${m.value.take(140)}")
            }
        }
        if (activeGoals.isNotEmpty()) {
            sb.appendLine("\nMetas ativas da pessoa:")
            activeGoals.take(6).forEach { g ->
                sb.appendLine("- #${g.id} ${g.title} (${g.progressPercent}%)")
            }
        }
        return sb.toString().trim()
    }
}
