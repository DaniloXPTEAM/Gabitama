package com.novoagentehb.app.agent.skills

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.novoagentehb.app.data.security.SecureKeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class McpRemoteTool(
    val name: String,
    val description: String
)

data class McpEndpointValidation(
    val allowed: Boolean,
    val normalizedUrl: String = "",
    val isPrivateOrLoopback: Boolean = false,
    val error: String? = null
)

data class McpHandshakeResult(
    val reachable: Boolean,
    val serverName: String = "",
    val protocolVersion: String = "",
    val sessionId: String? = null,
    val initializedNotificationSent: Boolean = false,
    val tools: List<McpRemoteTool> = emptyList(),
    val negotiationLog: List<String> = emptyList(),
    val error: String? = null
)

data class McpToolCallResult(
    val success: Boolean,
    val outputText: String,
    val sessionId: String? = null,
    val negotiationLog: List<String> = emptyList(),
    val error: String? = null
)

/**
 * Cliente MCP JSON-RPC 2.0 sobre HTTP/HTTPS (subconjunto Streamable HTTP 2024-11-05):
 * - Fluxo suportado: `POST initialize` -> `Mcp-Session-Id` (se fornecido) ->
 *   `POST notifications/initialized` -> `POST tools/list` -> `POST tools/call`.
 * - Aceita respostas `application/json` e `text/event-stream` (SSE `data:`).
 * - Transportes `stdio` e `WebSocket` não são implementados nesta versão (`NOT_IMPLEMENTED`).
 * - Bloqueia redirect automático, credenciais em URL, metadados cloud (169.254.169.254) e,
 *   salvo opt-in explícito (`allowLocalLan`), bloqueia hosts loopback/LAN privados.
 */
@Singleton
class McpJsonRpcClient @Inject constructor(
    okHttpClient: OkHttpClient
) {
    private val noRedirectClient: OkHttpClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun handshakeAndListTools(
        endpointUrl: String,
        allowLocalLan: Boolean = false
    ): McpHandshakeResult = withContext(Dispatchers.IO) {
        val log = mutableListOf<String>()
        val check = validateEndpoint(endpointUrl, allowLocalLan = allowLocalLan)
        if (!check.allowed) {
            log.add("ENDPOINT_REJECTED: ${check.error}")
            return@withContext McpHandshakeResult(
                reachable = false,
                negotiationLog = log,
                error = check.error
            )
        }
        val clean = check.normalizedUrl
        log.add("1. POST initialize -> $clean")

        val initHttp = postJsonRpc(clean, buildInitializeRequestJson(id = 1), sessionId = null)
        if (initHttp.transportError != null) {
            log.add("1. ERR initialize: ${initHttp.transportError}")
            return@withContext McpHandshakeResult(reachable = false, negotiationLog = log, error = initHttp.transportError)
        }
        val initBody = extractJsonPayloadFromHttpBody(initHttp.rawBody, initHttp.contentType)
        val initError = extractJsonRpcError(initBody)
        if (initError != null) {
            log.add("1. JSON-RPC ERR initialize: $initError")
            return@withContext McpHandshakeResult(reachable = false, negotiationLog = log, error = initError)
        }

        val serverName = extractServerName(initBody)
        val protocolVersion = extractProtocolVersion(initBody)
        val sessionId = initHttp.sessionId
        log.add("1. OK initialize (server=${serverName.ifBlank { "desconhecido" }}, proto=${protocolVersion.ifBlank { "?" }}, session=${sessionId ?: "none"})")

        // Etapa obrigatória MCP: notifications/initialized sem id
        val notifBody = buildInitializedNotificationJson()
        val notifHttp = postJsonRpc(clean, notifBody, sessionId = sessionId)
        val notifOk = notifHttp.transportError == null && notifHttp.httpCode in 200..299
        log.add("2. POST notifications/initialized -> HTTP ${notifHttp.httpCode} (ok=$notifOk)")
        if (!notifOk) {
            return@withContext McpHandshakeResult(
                reachable = false,
                serverName = serverName,
                protocolVersion = protocolVersion,
                sessionId = sessionId,
                initializedNotificationSent = false,
                negotiationLog = log,
                error = notifHttp.transportError ?: "Servidor rejeitou notifications/initialized (HTTP ${notifHttp.httpCode})."
            )
        }

        val listHttp = postJsonRpc(clean, buildToolsListRequestJson(id = 2), sessionId = sessionId)
        if (listHttp.transportError != null) {
            log.add("3. ERR tools/list: ${listHttp.transportError}")
            return@withContext McpHandshakeResult(
                reachable = false,
                serverName = serverName,
                protocolVersion = protocolVersion,
                sessionId = sessionId,
                initializedNotificationSent = true,
                negotiationLog = log,
                error = listHttp.transportError
            )
        }
        val listBody = extractJsonPayloadFromHttpBody(listHttp.rawBody, listHttp.contentType)
        val listError = extractJsonRpcError(listBody)
        if (listError != null) {
            log.add("3. JSON-RPC ERR tools/list: $listError")
            return@withContext McpHandshakeResult(
                reachable = false,
                serverName = serverName,
                protocolVersion = protocolVersion,
                sessionId = sessionId,
                initializedNotificationSent = true,
                negotiationLog = log,
                error = listError
            )
        }

        val tools = parseToolsListResponse(listBody)
        log.add("3. OK tools/list -> ${tools.size} ferramenta(s)")
        McpHandshakeResult(
            reachable = true,
            serverName = serverName,
            protocolVersion = protocolVersion,
            sessionId = sessionId,
            initializedNotificationSent = true,
            tools = tools,
            negotiationLog = log
        )
    }

    suspend fun callRemoteTool(
        endpointUrl: String,
        toolName: String,
        arguments: Map<String, String>,
        sessionId: String? = null,
        allowLocalLan: Boolean = false
    ): McpToolCallResult = withContext(Dispatchers.IO) {
        val log = mutableListOf<String>()
        val check = validateEndpoint(endpointUrl, allowLocalLan = allowLocalLan)
        if (!check.allowed) {
            return@withContext McpToolCallResult(false, "", negotiationLog = listOf("REJECTED: ${check.error}"), error = check.error)
        }
        var activeSessionId = sessionId
        if (activeSessionId == null) {
            val hs = handshakeAndListTools(check.normalizedUrl, allowLocalLan = allowLocalLan)
            log.addAll(hs.negotiationLog)
            if (!hs.reachable) {
                return@withContext McpToolCallResult(false, "", negotiationLog = log, error = hs.error)
            }
            activeSessionId = hs.sessionId
        }

        log.add("4. POST tools/call ($toolName)")
        val payload = buildToolsCallRequestJson(id = 3, toolName = toolName, arguments = arguments)
        val callHttp = postJsonRpc(check.normalizedUrl, payload, sessionId = activeSessionId)
        if (callHttp.transportError != null) {
            log.add("4. ERR tools/call: ${callHttp.transportError}")
            return@withContext McpToolCallResult(false, "", activeSessionId, log, callHttp.transportError)
        }
        val jsonPayload = extractJsonPayloadFromHttpBody(callHttp.rawBody, callHttp.contentType)
        val parsed = parseToolsCallResponse(jsonPayload)
        log.add("4. ${if (parsed.success) "OK" else "ERR"} tools/call (HTTP ${callHttp.httpCode})")
        parsed.copy(sessionId = activeSessionId, negotiationLog = log)
    }

    private data class HttpRpcReply(
        val httpCode: Int = 0,
        val contentType: String = "",
        val sessionId: String? = null,
        val rawBody: String = "",
        val transportError: String? = null
    )

    private fun postJsonRpc(url: String, bodyJson: String, sessionId: String?): HttpRpcReply {
        val builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")
            .addHeader("MCP-Protocol-Version", SUPPORTED_PROTOCOL_VERSION)
        if (!sessionId.isNullOrBlank()) {
            builder.addHeader("Mcp-Session-Id", sessionId)
        }
        val req = builder.post(bodyJson.toRequestBody("application/json".toMediaType())).build()
        return runCatching {
            noRedirectClient.newCall(req).execute().use { resp ->
                if (resp.isRedirect) {
                    return@use HttpRpcReply(
                        httpCode = resp.code,
                        transportError = "Redirecionamento HTTP (${resp.code}) bloqueado por segurança MCP."
                    )
                }
                val txt = resp.body?.string().orEmpty()
                val ct = resp.header("Content-Type").orEmpty()
                val sid = resp.header("Mcp-Session-Id")?.takeIf { it.isNotBlank() } ?: sessionId
                if (!resp.isSuccessful && txt.isBlank()) {
                    HttpRpcReply(
                        httpCode = resp.code,
                        contentType = ct,
                        sessionId = sid,
                        rawBody = "{\"error\":{\"code\":${resp.code},\"message\":\"HTTP ${resp.code}\"}}"
                    )
                } else if (!resp.isSuccessful && runCatching { JsonParser.parseString(txt).isJsonObject }.getOrDefault(false).not()) {
                    HttpRpcReply(
                        httpCode = resp.code,
                        contentType = ct,
                        sessionId = sid,
                        transportError = "HTTP ${resp.code}: ${SecureKeyVault.redactSecrets(txt.take(180))}"
                    )
                } else {
                    HttpRpcReply(httpCode = resp.code, contentType = ct, sessionId = sid, rawBody = txt)
                }
            }
        }.getOrElse { err ->
            HttpRpcReply(transportError = SecureKeyVault.redactSecrets(err.message ?: "Erro de transporte HTTP MCP"))
        }
    }

    companion object {
        const val SUPPORTED_PROTOCOL_VERSION = "2024-11-05"

        fun validateEndpoint(url: String, allowLocalLan: Boolean = false): McpEndpointValidation {
            val clean = url.trim()
            if (clean.length < 11) {
                return McpEndpointValidation(false, error = "URL MCP muito curta ou vazia.")
            }
            val uri = runCatching { URI(clean) }.getOrNull()
                ?: return McpEndpointValidation(false, error = "URL MCP malformada.")

            val scheme = uri.scheme?.lowercase().orEmpty()
            if (scheme != "http" && scheme != "https") {
                return McpEndpointValidation(false, error = "Esquema '$scheme' bloqueado; use apenas http:// ou https://.")
            }
            if (!uri.userInfo.isNullOrBlank()) {
                return McpEndpointValidation(false, error = "Credenciais embutidas na URL (user:pass@) são proibidas.")
            }
            val host = uri.host?.lowercase()?.trim('.').orEmpty()
            if (host.isBlank()) {
                return McpEndpointValidation(false, error = "Host ausente na URL MCP.")
            }
            if (host == "169.254.169.254" || host == "metadata.google.internal" || host == "0.0.0.0") {
                return McpEndpointValidation(false, error = "Host reservado/metadados ($host) sempre bloqueado.")
            }

            val isPrivateOrLoopback = isPrivateOrLocalHost(host)
            if (isPrivateOrLoopback && !allowLocalLan) {
                return McpEndpointValidation(
                    allowed = false,
                    normalizedUrl = clean,
                    isPrivateOrLoopback = true,
                    error = "Host local/LAN ($host) bloqueado por padrão. Ative 'Permitir LAN local (opt-in)' apenas para servidor MCP autorizado."
                )
            }
            return McpEndpointValidation(allowed = true, normalizedUrl = clean, isPrivateOrLoopback = isPrivateOrLoopback)
        }

        private fun isPrivateOrLocalHost(host: String): Boolean {
            if (host == "localhost" || host.endsWith(".local") || host == "::1" || host == "[::1]") return true
            val ipv4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$").matchEntire(host)
            if (ipv4 != null) {
                val a = ipv4.groupValues[1].toIntOrNull() ?: return true
                val b = ipv4.groupValues[2].toIntOrNull() ?: return true
                return a == 127 || a == 10 || a == 0 ||
                    (a == 169 && b == 254) ||
                    (a == 192 && b == 168) ||
                    (a == 172 && b in 16..31)
            }
            return runCatching {
                val addr = InetAddress.getByName(host)
                addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress
            }.getOrDefault(false)
        }

        fun isAllowedEndpoint(url: String, allowLocalLan: Boolean = false): Boolean =
            validateEndpoint(url, allowLocalLan).allowed

        fun buildInitializeRequestJson(id: Int = 1): String = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", id)
            addProperty("method", "initialize")
            add("params", JsonObject().apply {
                addProperty("protocolVersion", SUPPORTED_PROTOCOL_VERSION)
                add("capabilities", JsonObject())
                add("clientInfo", JsonObject().apply {
                    addProperty("name", "novo-agente-hb")
                    addProperty("version", "0.2.1")
                })
            })
        }.toString()

        fun buildInitializedNotificationJson(): String = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", "notifications/initialized")
        }.toString()

        fun buildToolsListRequestJson(id: Int = 2): String = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", id)
            addProperty("method", "tools/list")
            add("params", JsonObject())
        }.toString()

        fun buildToolsCallRequestJson(id: Int = 3, toolName: String, arguments: Map<String, String>): String =
            JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("id", id)
                addProperty("method", "tools/call")
                add("params", JsonObject().apply {
                    addProperty("name", toolName)
                    add("arguments", JsonObject().apply {
                        arguments.forEach { (k, v) -> addProperty(k, v) }
                    })
                })
            }.toString()

        fun extractJsonPayloadFromHttpBody(rawBody: String, contentType: String = ""): String {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) return ""
            val isSse = contentType.lowercase().contains("text/event-stream") ||
                trimmed.lineSequence().any { it.startsWith("data:") }
            if (!isSse) return trimmed

            val dataEvents = mutableListOf<String>()
            val cur = StringBuilder()
            for (line in trimmed.lineSequence()) {
                when {
                    line.startsWith("data:") -> {
                        val part = line.removePrefix("data:").trimStart()
                        if (cur.isNotEmpty()) cur.append('\n')
                        cur.append(part)
                    }
                    line.isBlank() && cur.isNotEmpty() -> {
                        dataEvents.add(cur.toString())
                        cur.clear()
                    }
                }
            }
            if (cur.isNotEmpty()) dataEvents.add(cur.toString())
            return dataEvents.lastOrNull { it.trim().startsWith("{") } ?: trimmed
        }

        fun extractJsonRpcError(rawJson: String): String? {
            if (rawJson.isBlank()) return "Resposta MCP vazia."
            val obj = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull()
                ?: return "Resposta MCP não é JSON válido."
            val err = obj.getAsJsonObject("error") ?: return null
            val msg = err.get("message")?.asString ?: "Erro MCP"
            return SecureKeyVault.redactSecrets(msg)
        }

        fun extractServerName(rawJson: String): String {
            val obj = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull() ?: return ""
            return obj.getAsJsonObject("result")
                ?.getAsJsonObject("serverInfo")
                ?.get("name")?.asString
                .orEmpty()
        }

        fun extractProtocolVersion(rawJson: String): String {
            val obj = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull() ?: return ""
            return obj.getAsJsonObject("result")
                ?.get("protocolVersion")?.asString
                .orEmpty()
        }

        fun parseToolsListResponse(rawJson: String): List<McpRemoteTool> {
            val obj = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull() ?: return emptyList()
            val arr = obj.getAsJsonObject("result")?.getAsJsonArray("tools") ?: return emptyList()
            return arr.mapNotNull { elem ->
                val t = runCatching { elem.asJsonObject }.getOrNull() ?: return@mapNotNull null
                val name = t.get("name")?.asString?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                val desc = SkillAndMcpRegistry.sanitizeUntrustedDescription(
                    t.get("description")?.asString.orEmpty()
                )
                McpRemoteTool(name = name, description = desc)
            }
        }

        fun parseToolsCallResponse(rawJson: String): McpToolCallResult {
            val err = extractJsonRpcError(rawJson)
            if (err != null) return McpToolCallResult(false, "", error = err)
            val obj = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull()
                ?: return McpToolCallResult(false, "", error = "JSON inválido na resposta tools/call.")
            val result = obj.getAsJsonObject("result")
                ?: return McpToolCallResult(false, "", error = "Resposta tools/call sem campo result.")
            val isError = result.get("isError")?.asBoolean == true
            val contentText = result.getAsJsonArray("content")
                ?.mapNotNull { c ->
                    runCatching { c.asJsonObject.get("text")?.asString }.getOrNull()
                }
                ?.joinToString("\n")
                ?.trim()
                .orEmpty()
            val safeText = SecureKeyVault.redactSecrets(contentText.ifBlank { result.toString().take(800) })
            return if (isError) {
                McpToolCallResult(false, "", error = safeText)
            } else {
                McpToolCallResult(true, safeText)
            }
        }
    }
}
