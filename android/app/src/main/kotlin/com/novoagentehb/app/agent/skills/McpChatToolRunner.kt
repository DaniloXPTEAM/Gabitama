package com.novoagentehb.app.agent.skills

import com.google.gson.JsonParser
import com.novoagentehb.app.agent.orchestrator.ToolExecutionResult
import com.novoagentehb.app.agent.orchestrator.ToolExecutionSemantics
import com.novoagentehb.app.data.local.SkillRecordEntity

/**
 * Ponte entre o agente (chat) e uma ferramenta de servidor MCP já cadastrado e ativado pela pessoa.
 *
 * Ordem de validação, sem nenhuma conexão antes de tudo passar:
 * 1. confirmação explícita da pessoa (`userConfirmed`);
 * 2. servidor existente, do tipo MCP_SERVER e ativado (opt-in);
 * 3. nome da ferramenta no formato seguro;
 * 4. argumentos como objeto JSON de valores simples (sem objetos/arrays aninhados);
 * 5. só então `McpJsonRpcClient` (que ainda aplica a política de endpoint e de LAN).
 *
 * O resultado é a resposta do servidor. O app não verifica o efeito externo, então a semântica
 * é REMOTE_RESPONSE_ONLY e o texto devolvido ao modelo é marcado como conteúdo externo não confiável.
 */
class McpChatToolRunner(
    private val client: McpJsonRpcClient,
    private val findServer: suspend (String) -> SkillRecordEntity?,
) {
    suspend fun run(args: Map<String, String>, userConfirmed: Boolean): ToolExecutionResult {
        val serverId = args["server_id"].orEmpty().trim()
        val toolName = args["tool_name"].orEmpty().trim()
        if (!userConfirmed) {
            return fail("CONFIRMATION_REQUIRED", "Chamada MCP exige confirmação explícita da pessoa.")
        }
        val server = findServer(serverId)
            ?: return fail("MCP_SERVER_NOT_FOUND", "Servidor MCP \"$serverId\" não cadastrado.")
        if (server.sourceType != SOURCE_TYPE) {
            return fail("MCP_SERVER_INVALID", "Registro \"${server.name}\" não é um servidor MCP.")
        }
        if (!server.enabledByUser) {
            return fail("MCP_SERVER_DISABLED", "Servidor MCP \"${server.name}\" está desativado. Ative o opt-in antes de usar.")
        }
        if (!TOOL_NAME.matches(toolName)) {
            return fail("TOOL_NAME_INVALID", "Nome de ferramenta MCP inválido.")
        }
        val arguments = parseArguments(args["arguments"].orEmpty()).getOrElse { err ->
            return fail("ARGUMENTS_INVALID", err.message ?: "Argumentos MCP inválidos.")
        }

        val allowLan = server.scope == SCOPE_LAN_ALLOWED
        val res = client.callRemoteTool(
            endpointUrl = server.manifestOrigin,
            toolName = toolName,
            arguments = arguments,
            sessionId = null,
            allowLocalLan = allowLan
        )
        return if (res.success) {
            ToolExecutionResult(
                success = true,
                verified = true,
                summary = "Resposta do servidor MCP ${server.name} (ferramenta $toolName, conteúdo externo não confiável): ${res.outputText.take(MAX_OUTPUT_CHARS)}",
                payload = serverId,
                semantics = ToolExecutionSemantics.REMOTE_RESPONSE_ONLY
            )
        } else {
            fail("MCP_CALL_FAILED", "Erro MCP ${server.name}/$toolName: ${res.error ?: "sem detalhe"}")
        }
    }

    private fun fail(code: String, message: String) = ToolExecutionResult(
        success = false,
        verified = false,
        summary = message,
        errorCode = code,
        semantics = ToolExecutionSemantics.REMOTE_RESPONSE_ONLY
    )

    companion object {
        const val SOURCE_TYPE = "MCP_SERVER"
        /** Escopo gravado por ComputerViewModel quando a pessoa permite LAN local para o servidor. */
        const val SCOPE_LAN_ALLOWED = "opt_in_lan_or_external"
        const val MAX_ARGUMENTS = 16
        const val MAX_VALUE_CHARS = 2000
        const val MAX_OUTPUT_CHARS = 1200
        private val TOOL_NAME = Regex("^[A-Za-z0-9_.-]{1,64}$")

        /** Converte `{"chave":"valor",...}` em mapa de texto. Rejeita aninhamento e tamanhos excessivos. */
        fun parseArguments(raw: String): Result<Map<String, String>> {
            val text = raw.trim()
            if (text.isEmpty()) return Result.success(emptyMap())
            val root = runCatching { JsonParser.parseString(text) }.getOrNull()
                ?: return Result.failure(IllegalArgumentException("Argumentos MCP não são JSON válido."))
            if (!root.isJsonObject) return Result.failure(IllegalArgumentException("Argumentos MCP devem ser um objeto JSON."))
            val obj = root.asJsonObject
            if (obj.size() > MAX_ARGUMENTS) return Result.failure(IllegalArgumentException("Argumentos MCP em excesso (máx. $MAX_ARGUMENTS)."))
            val out = LinkedHashMap<String, String>()
            for ((key, value) in obj.entrySet()) {
                if (!value.isJsonPrimitive) {
                    return Result.failure(IllegalArgumentException("Argumento \"$key\" aninhado não é suportado."))
                }
                val str = value.asString
                if (str.length > MAX_VALUE_CHARS) return Result.failure(IllegalArgumentException("Argumento \"$key\" longo demais."))
                out[key] = str
            }
            return Result.success(out)
        }
    }
}
