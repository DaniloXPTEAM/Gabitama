package com.novoagentehb.app.gabi

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.novoagentehb.app.agent.orchestrator.ToolDescriptor
import com.novoagentehb.app.data.prefs.AppPreferences
import com.novoagentehb.app.data.security.SecureKeyVault
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.Base64
import java.io.IOException
import java.io.InterruptedIOException
import javax.net.ssl.SSLException
import javax.inject.Inject
import javax.inject.Singleton

sealed class GeminiLiveEvent {
    object Ready : GeminiLiveEvent()
    data class AudioOutput(val pcm24k: ByteArray) : GeminiLiveEvent()
    data class InputTranscript(val text: String) : GeminiLiveEvent()
    data class OutputTranscript(val text: String) : GeminiLiveEvent()
    object Interrupted : GeminiLiveEvent()
    object TurnComplete : GeminiLiveEvent()
    data class ToolInvocation(val callId: String, val name: String, val argsJson: Map<String, String>) : GeminiLiveEvent()
    data class Usage(val inputTokens: Int, val outputTokens: Int) : GeminiLiveEvent()
    object GoingAway : GeminiLiveEvent()
    data class ConnectionFailed(val reason: String, val retryable: Boolean = false) : GeminiLiveEvent()
    data class Closed(val code: Int, val reason: String) : GeminiLiveEvent()
}

/**
 * Cliente WebSocket original para a Gemini Multimodal Live API (`BidiGenerateContent`).
 * - Cabeçalho `x-goog-api-key` (nunca expõe chave na URL).
 * - PCM16 LE 16 kHz na entrada; PCM16 LE 24 kHz na saída.
 * - Suporta barge-in (`serverContent.interrupted`), transcrição bidirecional e `toolCall`/`toolResponse`.
 * - Modelo candidato configurável; validar disponibilidade real na conta via `ModelRouter.discoverGeminiLiveModels`.
 */
@Singleton
class GeminiLiveVoiceClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    internal var webSocketFactory: WebSocket.Factory = okHttpClient
    private var activeSocket: WebSocket? = null
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _events = MutableSharedFlow<GeminiLiveEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<GeminiLiveEvent> = _events.asSharedFlow()

    @Synchronized
    fun connect(
        apiKey: String,
        model: String = DEFAULT_LIVE_MODEL,
        systemInstruction: String,
        voiceName: String = DEFAULT_VOICE_NAME,
        tools: List<ToolDescriptor> = emptyList()
    ): Boolean {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            _events.tryEmit(GeminiLiveEvent.ConnectionFailed("Chave Gemini não configurada."))
            return false
        }
        disconnect()

        val request = Request.Builder()
            .url(BIDI_WS_URL)
            .addHeader("x-goog-api-key", cleanKey)
            .build()

        val setupJson = buildSetupJson(model, systemInstruction, voiceName, tools)
        activeSocket = webSocketFactory.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(this@GeminiLiveVoiceClient) {
                if (activeSocket === webSocket && !webSocket.send(setupJson)) {
                    _events.tryEmit(GeminiLiveEvent.ConnectionFailed("Falha ao enviar setup Gemini Live."))
                    webSocket.cancel()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) = onServerText(webSocket, text)

            // The Live service may deliver its JSON as binary frames. Treat them exactly like text frames
            // (UTF-8); otherwise setupComplete is never parsed and the handshake times out.
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = onServerText(webSocket, bytes.utf8())

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = synchronized(this@GeminiLiveVoiceClient) {
                if (activeSocket !== webSocket) return@synchronized
                val wasReady = _connected.value
                activeSocket = null
                _connected.value = false
                val msg = LiveErrorText.mask(LiveErrorText.failure(t, response?.code), cleanKey)
                _events.tryEmit(GeminiLiveEvent.ConnectionFailed(msg, retryable = wasReady && response == null && t is java.io.IOException))
                Unit
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = synchronized(this@GeminiLiveVoiceClient) {
                if (activeSocket !== webSocket) return@synchronized
                _connected.value = false
                if (code != 1000) {
                    _events.tryEmit(GeminiLiveEvent.ConnectionFailed(LiveErrorText.closedByServer(code)))
                }
                webSocket.close(code, null)
                Unit
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = synchronized(this@GeminiLiveVoiceClient) {
                if (activeSocket !== webSocket) return@synchronized
                activeSocket = null
                _connected.value = false
                _events.tryEmit(GeminiLiveEvent.Closed(code, LiveErrorText.closedByServer(code)))
                Unit
            }
        })
        return true
    }

    @Synchronized
    private fun onServerText(webSocket: WebSocket, text: String) {
        if (activeSocket !== webSocket) return
        val parsed = parseServerMessage(text)
        val failure = parsed.filterIsInstance<GeminiLiveEvent.ConnectionFailed>().firstOrNull()
        if (failure != null) {
            _connected.value = false
            activeSocket = null
            _events.tryEmit(failure)
            webSocket.cancel()
            return
        }
        parsed.forEach {
            if (it is GeminiLiveEvent.Ready) _connected.value = true
            if (_connected.value || it is GeminiLiveEvent.GoingAway) _events.tryEmit(it)
        }
    }

    @Synchronized
    fun sendPcm16AudioChunk(pcm16k: ByteArray): Boolean {
        if (!_connected.value) return false
        val ws = activeSocket ?: return false
        return ws.send(buildAudioChunkJson(pcm16k))
    }

    @Synchronized
    fun sendTextTurn(text: String): Boolean {
        if (!_connected.value) return false
        val ws = activeSocket ?: return false
        return ws.send(buildTextTurnJson(text))
    }

    @Synchronized
    fun sendToolResponse(callId: String, name: String, outputText: String): Boolean {
        if (!_connected.value) return false
        val ws = activeSocket ?: return false
        return ws.send(buildToolResponseJson(callId, name, outputText))
    }

    @Synchronized
    fun disconnect() {
        runCatching { activeSocket?.close(1000, "client_disconnect") }
        activeSocket = null
        _connected.value = false
    }

    companion object {
        const val BIDI_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        const val DEFAULT_LIVE_MODEL = AppPreferences.DEFAULT_GEMINI_LIVE_MODEL
        const val DEFAULT_VOICE_NAME = "Kore"
        const val INPUT_SAMPLE_RATE = 16_000
        const val OUTPUT_SAMPLE_RATE = 24_000

        fun buildSetupJson(
            model: String,
            systemInstruction: String,
            voiceName: String,
            tools: List<ToolDescriptor>
        ): String {
            val root = JsonObject()
            val setup = JsonObject().apply {
                addProperty("model", if (model.startsWith("models/")) model else "models/$model")
                add("generationConfig", JsonObject().apply {
                    add("responseModalities", JsonArray().apply { add("AUDIO") })
                    add("speechConfig", JsonObject().apply {
                        add("voiceConfig", JsonObject().apply {
                            add("prebuiltVoiceConfig", JsonObject().apply {
                                addProperty("voiceName", voiceName)
                            })
                        })
                    })
                })
                add("systemInstruction", JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", systemInstruction) })
                    })
                })
                add("inputAudioTranscription", JsonObject())
                add("outputAudioTranscription", JsonObject())
                if (tools.isNotEmpty()) {
                    val declarations = JsonArray()
                    for (tool in tools) {
                        val fn = JsonObject().apply {
                            addProperty("name", tool.name)
                            addProperty("description", tool.description)
                            if (tool.params.isNotEmpty()) {
                                val props = JsonObject()
                                val req = JsonArray()
                                tool.params.forEach { p ->
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
                        }
                        declarations.add(fn)
                    }
                    add("tools", JsonArray().apply {
                        add(JsonObject().apply { add("functionDeclarations", declarations) })
                    })
                }
            }
            root.add("setup", setup)
            return root.toString()
        }

        fun buildAudioChunkJson(pcm16k: ByteArray): String {
            val b64 = Base64.getEncoder().encodeToString(pcm16k)
            val root = JsonObject()
            root.add("realtimeInput", JsonObject().apply {
                add("audio", JsonObject().apply {
                    addProperty("data", b64)
                    addProperty("mimeType", "audio/pcm;rate=$INPUT_SAMPLE_RATE")
                })
            })
            return root.toString()
        }

        fun buildTextTurnJson(text: String): String {
            val root = JsonObject()
            root.add("realtimeInput", JsonObject().apply {
                addProperty("text", text)
            })
            return root.toString()
        }

        fun buildToolResponseJson(callId: String, name: String, outputText: String): String {
            val root = JsonObject()
            root.add("toolResponse", JsonObject().apply {
                add("functionResponses", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("id", callId)
                        addProperty("name", name)
                        add("response", JsonObject().apply {
                            addProperty("output", outputText)
                        })
                    })
                })
            })
            return root.toString()
        }

        fun parseServerMessage(rawJson: String): List<GeminiLiveEvent> {
            val obj = runCatching { com.google.gson.JsonParser.parseString(rawJson).asJsonObject }.getOrNull()
                ?: return emptyList()
            val out = mutableListOf<GeminiLiveEvent>()

            // The server message text is untrusted and is never shown: only a category, the status token and the code.
            obj.get("error")?.let { errElem ->
                val err = errElem.takeIf { it.isJsonObject }?.asJsonObject
                out.add(
                    GeminiLiveEvent.ConnectionFailed(
                        LiveErrorText.serverError(
                            code = err?.get("code")?.let { LiveErrorText.intOrNull(it) },
                            status = err?.get("status")?.let { LiveErrorText.tokenOrNull(it) }
                        )
                    )
                )
            }

            // A malformed optional section (unexpected JSON type) only drops that section; events already
            // collected, such as setupComplete, are kept and the frame is never allowed to crash the socket reader.
            runCatching {
                if (obj.has("setupComplete")) out.add(GeminiLiveEvent.Ready)
                if (obj.has("goAway")) out.add(GeminiLiveEvent.GoingAway)

                obj.getAsJsonObject("serverContent")?.let { sc ->
                    if (sc.get("interrupted")?.asBoolean == true) out.add(GeminiLiveEvent.Interrupted)

                    sc.getAsJsonObject("inputTranscription")?.get("text")?.asString
                        ?.takeIf { it.isNotBlank() }?.let { out.add(GeminiLiveEvent.InputTranscript(it)) }

                    sc.getAsJsonObject("outputTranscription")?.get("text")?.asString
                        ?.takeIf { it.isNotBlank() }?.let { out.add(GeminiLiveEvent.OutputTranscript(it)) }

                    sc.getAsJsonObject("modelTurn")?.getAsJsonArray("parts")?.forEach { elem ->
                        val inline = elem.asJsonObject.getAsJsonObject("inlineData") ?: return@forEach
                        val mime = inline.get("mimeType")?.asString.orEmpty()
                        val b64 = inline.get("data")?.asString.orEmpty()
                        if (mime.startsWith("audio/pcm") && b64.isNotBlank()) {
                            runCatching { Base64.getDecoder().decode(b64) }.getOrNull()?.let {
                                out.add(GeminiLiveEvent.AudioOutput(it))
                            }
                        }
                    }

                    if (sc.get("turnComplete")?.asBoolean == true) out.add(GeminiLiveEvent.TurnComplete)
                }

                obj.getAsJsonObject("toolCall")?.getAsJsonArray("functionCalls")?.forEach { callElem ->
                    val callObj = callElem.asJsonObject
                    val id = callObj.get("id")?.asString ?: return@forEach
                    val name = callObj.get("name")?.asString ?: return@forEach
                    val argsMap = mutableMapOf<String, String>()
                    callObj.getAsJsonObject("args")?.entrySet()?.forEach { (k, v) ->
                        argsMap[k] = if (v.isJsonPrimitive) v.asString else v.toString()
                    }
                    out.add(GeminiLiveEvent.ToolInvocation(id, name, argsMap))
                }

                obj.getAsJsonObject("usageMetadata")?.let { u ->
                    val inTok = u.get("promptTokenCount")?.asInt ?: 0
                    val outTok = u.get("responseTokenCount")?.asInt ?: u.get("candidatesTokenCount")?.asInt ?: 0
                    if (inTok > 0 || outTok > 0) out.add(GeminiLiveEvent.Usage(inTok, outTok))
                }
            }
            return out
        }
    }
}

/**
 * Public, user-facing texts for Gemini Live failures.
 *
 * Policy: server and transport texts are untrusted and are never shown verbatim. The UI receives a fixed
 * category (authentication, quota, timeout, unavailable, network, TLS, ...), the HTTP code or the server
 * status token when they are well formed, and the exception class name. Any configured key value is masked
 * exactly, and the known key formats are masked as before. Exact masking cannot cover every fragment of a key,
 * which is why raw text is not shown at all.
 */
internal object LiveErrorText {
    const val MASK = "[REDACTED]"
    private const val MIN_EXACT_KEY_LENGTH = 8
    private val STATUS_TOKEN = Regex("^[A-Z_]{1,40}$")

    private const val AUTH = "Autenticação recusada pelo Gemini Live. Verifique a chave configurada."
    private const val QUOTA = "Cota ou limite do Gemini Live excedido. Tente novamente mais tarde."
    private const val TIMEOUT = "Tempo esgotado ao falar com o Gemini Live."
    private const val UNAVAILABLE = "Serviço Gemini Live indisponível no momento."
    private const val REQUEST = "Requisição recusada pelo Gemini Live."
    private const val NOT_FOUND = "Modelo ou recurso do Gemini Live não encontrado."
    private const val NETWORK = "Falha de rede ao conectar ao Gemini Live."
    private const val TLS = "Falha de segurança (TLS) ao conectar ao Gemini Live."
    private const val CONNECT = "Falha ao conectar ao Gemini Live."
    private const val GENERIC = "Erro do servidor Gemini Live."

    /** Masks the exact configured key (when long enough to be meaningful) and the known key formats. */
    fun mask(text: String, secret: String?): String {
        var out = text
        if (!secret.isNullOrBlank() && secret.length >= MIN_EXACT_KEY_LENGTH) out = out.replace(secret, MASK)
        return SecureKeyVault.redactSecrets(out)
    }

    fun intOrNull(e: JsonElement): Int? =
        if (e.isJsonPrimitive && e.asJsonPrimitive.isNumber) runCatching { e.asInt }.getOrNull() else null

    /** Accepts only a plain upper-case status token such as RESOURCE_EXHAUSTED; anything else is dropped. */
    fun tokenOrNull(e: JsonElement): String? =
        if (e.isJsonPrimitive && e.asJsonPrimitive.isString) e.asString.takeIf { STATUS_TOKEN.matches(it) } else null

    /** Message for an `error` object received from the server. The server text is not used. */
    fun serverError(code: Int?, status: String?): String {
        val category = categoryFor(status, code) ?: GENERIC
        val diagnostic = listOfNotNull(status?.let { "status $it" }, code?.let { "código $it" })
        return if (diagnostic.isEmpty()) category else "$category (${diagnostic.joinToString(", ")})"
    }

    /** Message for a failed handshake or socket failure, with the HTTP code when there was a response. */
    fun failure(t: Throwable?, httpCode: Int?): String {
        if (httpCode != null) return "HTTP $httpCode · ${categoryFor(null, httpCode) ?: GENERIC}"
        val category = when (t) {
            is InterruptedIOException -> TIMEOUT
            is SSLException -> TLS
            is IOException -> NETWORK
            else -> CONNECT
        }
        val name = t?.javaClass?.simpleName?.takeIf { it.isNotBlank() }
        return if (name == null) category else "$category ($name)"
    }

    /** Message for a close frame from the server. The server's close reason is not shown. */
    fun closedByServer(code: Int): String = "Conexão encerrada pelo serviço (código $code)."

    private fun categoryFor(status: String?, code: Int?): String? {
        when (status) {
            "UNAUTHENTICATED", "PERMISSION_DENIED" -> return AUTH
            "RESOURCE_EXHAUSTED" -> return QUOTA
            "DEADLINE_EXCEEDED" -> return TIMEOUT
            "UNAVAILABLE" -> return UNAVAILABLE
            "NOT_FOUND" -> return NOT_FOUND
            "INVALID_ARGUMENT" -> return REQUEST
        }
        return when (code) {
            null -> null
            400 -> REQUEST
            401, 403 -> AUTH
            404 -> NOT_FOUND
            408, 504 -> TIMEOUT
            429 -> QUOTA
            in 500..599 -> UNAVAILABLE
            else -> null
        }
    }
}
