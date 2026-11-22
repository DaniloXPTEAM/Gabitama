package com.novoagentehb.app.agent.models

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.novoagentehb.app.agent.orchestrator.ToolDescriptor
import com.novoagentehb.app.data.prefs.AppPreferences
import com.novoagentehb.app.data.prefs.ProviderId
import com.novoagentehb.app.data.security.SecureKeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

enum class ModelRole(val label: String) {
    CHAT("Conversa"),
    SCREEN_OP("Operação de tela"),
    VISION("Visão"),
    TASK_SYNTHESIS("Síntese de tarefa"),
    MEDIA_GEN("Mídia")
}

enum class FailureCategory(val label: String) {
    AUTH_REJECTED("Chave rejeitada (401)"),
    ACCESS_DENIED("Acesso negado (403)"),
    QUOTA_OR_CREDIT("Crédito ou cota esgotada"),
    PAYLOAD_TOO_LARGE("Requisição muito grande (413)"),
    RATE_LIMITED("Limite de requisições (429)"),
    NETWORK_OR_TIMEOUT("Sem rede ou tempo esgotado"),
    PROVIDER_ERROR("Erro do provedor"),
    NO_KEY_CONFIGURED("Chave não configurada")
}

data class ProviderFailure(
    val providerId: String,
    val modelId: String,
    val httpStatus: Int?,
    val category: FailureCategory,
    val userSummary: String,
    val sanitizedDetail: String
)

data class ModelChatMessage(
    val role: String,
    val text: String
)

data class ModelToolCall(
    val callId: String,
    val name: String,
    val args: Map<String, String>
)

sealed class ModelTurnResult {
    data class TextReply(
        val text: String,
        val providerId: String,
        val modelId: String
    ) : ModelTurnResult()

    data class ToolCallReply(
        val calls: List<ModelToolCall>,
        val providerId: String,
        val modelId: String
    ) : ModelTurnResult()

    data class Failure(val failure: ProviderFailure) : ModelTurnResult()
}

/**
 * Roteador multi-provedor original com foco na pilha ativa **Gemini**
 * e suporte BYOK para Groq, OpenRouter, DeepSeek e Anthropic Claude.
 * - Classifica recusas HTTP (401, 403, 413, 429, cota/crédito, timeout).
 * - Sanitiza chaves/tokens antes de exibir no cartão de detalhes ou logs.
 * - Nunca troca silenciosamente para provedor pago após recusa.
 */
@Singleton
class ModelRouter @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val prefs: AppPreferences,
    private val keyVault: SecureKeyVault
) {
    suspend fun generateTurn(
        role: ModelRole,
        systemPrompt: String,
        history: List<ModelChatMessage>,
        tools: List<ToolDescriptor>,
        explicitProvider: ProviderId? = null
    ): ModelTurnResult {
        val provider = explicitProvider ?: prefs.primaryProvider.first()
        val model = resolveModelForRole(provider, role)
        val apiKey = resolveApiKey(provider)

        if (apiKey.isBlank()) {
            return ModelTurnResult.Failure(
                ProviderFailure(
                    providerId = provider.id,
                    modelId = model,
                    httpStatus = null,
                    category = FailureCategory.NO_KEY_CONFIGURED,
                    userSummary = "Nenhuma chave configurada para ${provider.displayName}. Configure em Ajustes.",
                    sanitizedDetail = "Chave ausente para provedor ${provider.id}."
                )
            )
        }

        return withContext(Dispatchers.IO) {
            when (provider) {
                ProviderId.GEMINI -> callGeminiNative(apiKey, model, systemPrompt, history, tools)
                ProviderId.GROQ -> callOpenAiCompatible(
                    providerId = provider.id,
                    baseUrl = "https://api.groq.com/openai/v1/chat/completions",
                    apiKey = apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    history = history,
                    tools = tools
                )
                ProviderId.OPENROUTER -> callOpenAiCompatible(
                    providerId = provider.id,
                    baseUrl = "https://openrouter.ai/api/v1/chat/completions",
                    apiKey = apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    history = history,
                    tools = tools
                )
                ProviderId.DEEPSEEK -> callOpenAiCompatible(
                    providerId = provider.id,
                    baseUrl = "https://api.deepseek.com/v1/chat/completions",
                    apiKey = apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    history = history,
                    tools = tools
                )
                ProviderId.ANTHROPIC -> callAnthropicMessages(apiKey, model, systemPrompt, history)
            }
        }
    }

    suspend fun resolveModelForRole(provider: ProviderId, role: ModelRole): String {
        if (role == ModelRole.MEDIA_GEN) return "UNAVAILABLE_IN_CATALOGUE"
        val fallback = when (provider) {
            ProviderId.GEMINI -> "gemini-2.5-flash"
            ProviderId.GROQ -> "llama-3.3-70b-versatile"
            ProviderId.OPENROUTER -> "openrouter/free"
            ProviderId.DEEPSEEK -> "deepseek-chat"
            ProviderId.ANTHROPIC -> "claude-sonnet-4-20250514"
        }
        // Explicitly requesting a different provider must not reuse a model ID saved for the active provider.
        if (provider != prefs.primaryProvider.first()) return fallback
        return when (role) {
            ModelRole.CHAT, ModelRole.TASK_SYNTHESIS -> prefs.chatModel.first()
            ModelRole.SCREEN_OP -> prefs.screenOpModel.first()
            ModelRole.VISION -> prefs.visionModel.first()
            ModelRole.MEDIA_GEN -> "UNAVAILABLE_IN_CATALOGUE"
        }.ifBlank { fallback }
    }

    fun resolveApiKey(provider: ProviderId): String = when (provider) {
        ProviderId.GEMINI -> keyVault.get(SecureKeyVault.KEY_GEMINI)
        ProviderId.GROQ -> keyVault.get(SecureKeyVault.KEY_GROQ)
        ProviderId.OPENROUTER -> keyVault.get(SecureKeyVault.KEY_OPENROUTER)
        ProviderId.DEEPSEEK -> keyVault.get(SecureKeyVault.KEY_DEEPSEEK)
        ProviderId.ANTHROPIC -> keyVault.get(SecureKeyVault.KEY_ANTHROPIC)
    }

    /**
     * Consulta real na conta do usuário (`GET /v1beta/models`) quais modelos suportam
     * `bidiGenerateContent` (Gemini Live API), sem assumir disponibilidade cegamente.
     */
    suspend fun discoverGeminiLiveModels(apiKey: String = resolveApiKey(ProviderId.GEMINI)): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching { GeminiLiveDiscovery.list(okHttpClient, apiKey) }
        }

    private fun callGeminiNative(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<ModelChatMessage>,
        tools: List<ToolDescriptor>
    ): ModelTurnResult {
        val body = buildGeminiRequestJson(systemPrompt, history, tools)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val req = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use ModelTurnResult.Failure(classifyHttpFailure("gemini", model, resp.code, raw))
                }
                parseGeminiResponse("gemini", model, raw)
            }
        }.getOrElse { err ->
            ModelTurnResult.Failure(
                ProviderFailure(
                    providerId = "gemini",
                    modelId = model,
                    httpStatus = null,
                    category = FailureCategory.NETWORK_OR_TIMEOUT,
                    userSummary = "Não consegui conectar ao Google Gemini agora.",
                    sanitizedDetail = SecureKeyVault.redactSecrets(err.message ?: "Erro de rede")
                )
            )
        }
    }

    private fun callOpenAiCompatible(
        providerId: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<ModelChatMessage>,
        tools: List<ToolDescriptor>
    ): ModelTurnResult {
        val body = buildOpenAiRequestJson(model, systemPrompt, history, tools)
        val req = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use ModelTurnResult.Failure(classifyHttpFailure(providerId, model, resp.code, raw))
                }
                parseOpenAiResponse(providerId, model, raw)
            }
        }.getOrElse { err ->
            ModelTurnResult.Failure(
                ProviderFailure(
                    providerId = providerId,
                    modelId = model,
                    httpStatus = null,
                    category = FailureCategory.NETWORK_OR_TIMEOUT,
                    userSummary = "Falha de rede ao consultar $providerId.",
                    sanitizedDetail = SecureKeyVault.redactSecrets(err.message ?: "Erro de rede")
                )
            )
        }
    }

    private fun callAnthropicMessages(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<ModelChatMessage>
    ): ModelTurnResult {
        val msgs = JsonArray()
        history.takeLast(12).forEach { m ->
            msgs.add(JsonObject().apply {
                addProperty("role", if (m.role == "assistant") "assistant" else "user")
                addProperty("content", m.text)
            })
        }
        val root = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", 1024)
            addProperty("system", systemPrompt)
            add("messages", msgs)
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(root.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use ModelTurnResult.Failure(classifyHttpFailure("anthropic", model, resp.code, raw))
                }
                val parsed = JsonParser.parseString(raw).asJsonObject
                val content = parsed.getAsJsonArray("content")
                    ?.firstOrNull()?.asJsonObject
                    ?.get("text")?.asString
                    .orEmpty()
                if (content.isBlank()) {
                    ModelTurnResult.Failure(
                        ProviderFailure("anthropic", model, resp.code, FailureCategory.PROVIDER_ERROR, "Resposta vazia do Claude.", "")
                    )
                } else {
                    ModelTurnResult.TextReply(content.trim(), "anthropic", model)
                }
            }
        }.getOrElse { err ->
            ModelTurnResult.Failure(
                ProviderFailure(
                    "anthropic", model, null, FailureCategory.NETWORK_OR_TIMEOUT,
                    "Falha de rede ao consultar Claude.",
                    SecureKeyVault.redactSecrets(err.message ?: "Erro de rede")
                )
            )
        }
    }

    companion object {
        fun classifyHttpFailure(
            providerId: String,
            modelId: String,
            statusCode: Int,
            rawBody: String
        ): ProviderFailure {
            val clean = SecureKeyVault.redactSecrets(rawBody).take(320)
            val lower = clean.lowercase()
            val category = when {
                statusCode == 401 -> FailureCategory.AUTH_REJECTED
                statusCode == 403 && (lower.contains("credit") || lower.contains("billing") || lower.contains("quota")) ->
                    FailureCategory.QUOTA_OR_CREDIT
                statusCode == 403 -> FailureCategory.ACCESS_DENIED
                statusCode == 402 -> FailureCategory.QUOTA_OR_CREDIT
                statusCode == 413 -> FailureCategory.PAYLOAD_TOO_LARGE
                statusCode == 429 && (lower.contains("insufficient_quota") || lower.contains("billing")) ->
                    FailureCategory.QUOTA_OR_CREDIT
                statusCode == 429 -> FailureCategory.RATE_LIMITED
                else -> FailureCategory.PROVIDER_ERROR
            }
            val summary = when (category) {
                FailureCategory.AUTH_REJECTED -> "Chave rejeitada pelo provedor ($providerId). Verifique em Ajustes."
                FailureCategory.ACCESS_DENIED -> "Acesso negado para o modelo $modelId ($providerId HTTP 403)."
                FailureCategory.QUOTA_OR_CREDIT -> "Cota ou saldo insuficiente reportado por $providerId."
                FailureCategory.PAYLOAD_TOO_LARGE -> "Contexto/prompt excedeu o limite do modelo (HTTP 413)."
                FailureCategory.RATE_LIMITED -> "Limite temporário de requisições atingido em $providerId (HTTP 429)."
                else -> "Provedor $providerId retornou HTTP $statusCode."
            }
            return ProviderFailure(providerId, modelId, statusCode, category, summary, clean)
        }

        fun buildGeminiRequestJson(
            systemPrompt: String,
            history: List<ModelChatMessage>,
            tools: List<ToolDescriptor>
        ): String {
            val contents = JsonArray()
            history.takeLast(16).forEach { msg ->
                contents.add(JsonObject().apply {
                    addProperty("role", if (msg.role == "assistant") "model" else "user")
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", msg.text) })
                    })
                })
            }
            val root = JsonObject().apply {
                add("systemInstruction", JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", systemPrompt) })
                    })
                })
                add("contents", contents)
                if (tools.isNotEmpty()) {
                    val decls = JsonArray()
                    tools.forEach { t ->
                        decls.add(JsonObject().apply {
                            addProperty("name", t.name)
                            addProperty("description", t.description)
                            if (t.params.isNotEmpty()) {
                                val props = JsonObject()
                                val req = JsonArray()
                                t.params.forEach { p ->
                                    props.add(p.name, JsonObject().apply {
                                        addProperty("type", "STRING")
                                        addProperty("description", p.description)
                                    })
                                    if (p.required) req.add(p.name)
                                }
                                add("parameters", JsonObject().apply {
                                    addProperty("type", "OBJECT")
                                    add("properties", props)
                                    if (req.size() > 0) add("required", req)
                                })
                            }
                        })
                    }
                    add("tools", JsonArray().apply {
                        add(JsonObject().apply { add("functionDeclarations", decls) })
                    })
                }
            }
            return root.toString()
        }

        fun parseGeminiResponse(providerId: String, modelId: String, rawJson: String): ModelTurnResult {
            val root = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull()
                ?: return ModelTurnResult.Failure(
                    ProviderFailure(providerId, modelId, 200, FailureCategory.PROVIDER_ERROR, "JSON inválido.", "")
                )
            val parts = root.getAsJsonArray("candidates")
                ?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?: return ModelTurnResult.Failure(
                    ProviderFailure(providerId, modelId, 200, FailureCategory.PROVIDER_ERROR, "Sem conteúdo retornado pelo Gemini.", "")
                )

            val toolCalls = mutableListOf<ModelToolCall>()
            val textParts = mutableListOf<String>()
            parts.forEachIndexed { idx, elem ->
                val p = elem.asJsonObject
                p.getAsJsonObject("functionCall")?.let { fc ->
                    val name = fc.get("name")?.asString.orEmpty()
                    val args = mutableMapOf<String, String>()
                    fc.getAsJsonObject("args")?.entrySet()?.forEach { (k, v) ->
                        args[k] = if (v.isJsonPrimitive) v.asString else v.toString()
                    }
                    if (name.isNotBlank()) toolCalls.add(ModelToolCall("gemini_call_$idx", name, args))
                }
                p.get("text")?.asString?.takeIf { it.isNotBlank() }?.let { textParts.add(it) }
            }

            return if (toolCalls.isNotEmpty()) {
                ModelTurnResult.ToolCallReply(toolCalls, providerId, modelId)
            } else {
                ModelTurnResult.TextReply(textParts.joinToString("\n").trim(), providerId, modelId)
            }
        }

        fun buildOpenAiRequestJson(
            model: String,
            systemPrompt: String,
            history: List<ModelChatMessage>,
            tools: List<ToolDescriptor>
        ): String {
            val msgs = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemPrompt)
                })
                history.takeLast(14).forEach { m ->
                    add(JsonObject().apply {
                        addProperty("role", if (m.role == "assistant") "assistant" else "user")
                        addProperty("content", m.text)
                    })
                }
            }
            val root = JsonObject().apply {
                addProperty("model", model)
                add("messages", msgs)
                addProperty("temperature", 0.6)
                if (tools.isNotEmpty()) {
                    val arr = JsonArray()
                    tools.forEach { t ->
                        val props = JsonObject()
                        val req = JsonArray()
                        t.params.forEach { p ->
                            props.add(p.name, JsonObject().apply {
                                addProperty("type", "string")
                                addProperty("description", p.description)
                            })
                            if (p.required) req.add(p.name)
                        }
                        arr.add(JsonObject().apply {
                            addProperty("type", "function")
                            add("function", JsonObject().apply {
                                addProperty("name", t.name)
                                addProperty("description", t.description)
                                add("parameters", JsonObject().apply {
                                    addProperty("type", "object")
                                    add("properties", props)
                                    if (req.size() > 0) add("required", req)
                                })
                            })
                        })
                    }
                    add("tools", arr)
                    addProperty("tool_choice", "auto")
                }
            }
            return root.toString()
        }

        fun parseOpenAiResponse(providerId: String, modelId: String, rawJson: String): ModelTurnResult {
            val root = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull()
                ?: return ModelTurnResult.Failure(
                    ProviderFailure(providerId, modelId, 200, FailureCategory.PROVIDER_ERROR, "JSON inválido.", "")
                )
            val msg = root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject?.getAsJsonObject("message")
                ?: return ModelTurnResult.Failure(
                    ProviderFailure(providerId, modelId, 200, FailureCategory.PROVIDER_ERROR, "Resposta sem choices.", "")
                )

            val calls = mutableListOf<ModelToolCall>()
            msg.getAsJsonArray("tool_calls")?.forEach { elem ->
                val obj = elem.asJsonObject
                val id = obj.get("id")?.asString ?: "call"
                val fn = obj.getAsJsonObject("function") ?: return@forEach
                val name = fn.get("name")?.asString.orEmpty()
                val rawArgs = fn.get("arguments")?.asString.orEmpty()
                val parsedArgs = mutableMapOf<String, String>()
                runCatching {
                    JsonParser.parseString(rawArgs).asJsonObject.entrySet().forEach { (k, v) ->
                        parsedArgs[k] = if (v.isJsonPrimitive) v.asString else v.toString()
                    }
                }
                if (name.isNotBlank()) calls.add(ModelToolCall(id, name, parsedArgs))
            }
            if (calls.isNotEmpty()) return ModelTurnResult.ToolCallReply(calls, providerId, modelId)

            val content = msg.get("content")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            // Alguns modelos via Groq/DeepSeek colocam texto somente em reasoning_content
            val reasoningFallback = msg.get("reasoning_content")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            val finalReply = content.ifBlank { reasoningFallback }
            return if (finalReply.isNotBlank()) {
                ModelTurnResult.TextReply(finalReply, providerId, modelId)
            } else {
                ModelTurnResult.Failure(
                    ProviderFailure(providerId, modelId, 200, FailureCategory.PROVIDER_ERROR, "Resposta vazia do provedor.", "")
                )
            }
        }

        fun parseGeminiLiveModels(rawJson: String): List<String> {
            val root = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull() ?: return emptyList()
            val models = root.getAsJsonArray("models") ?: return emptyList()
            val out = mutableListOf<String>()
            models.forEach { elem ->
                val obj = elem.asJsonObject
                val name = obj.get("name")?.asString?.removePrefix("models/").orEmpty()
                val methods = obj.getAsJsonArray("supportedGenerationMethods")
                    ?.mapNotNull { m -> runCatching { m.asString }.getOrNull() }
                    .orEmpty()
                if (name.isNotBlank() && methods.any { it.equals("bidiGenerateContent", ignoreCase = true) }) {
                    out.add(name)
                }
            }
            return out
        }
    }
}
