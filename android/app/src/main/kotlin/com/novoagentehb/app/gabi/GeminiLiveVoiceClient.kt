package com.novoagentehb.app.gabi

import com.google.gson.Gson
import com.google.gson.JsonArray
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
import java.util.Base64
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
    data class ConnectionFailed(val reason: String) : GeminiLiveEvent()
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
    private var activeSocket: WebSocket? = null
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _events = MutableSharedFlow<GeminiLiveEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<GeminiLiveEvent> = _events.asSharedFlow()

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
        activeSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connected.value = true
                webSocket.send(setupJson)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseServerMessage(text).forEach { _events.tryEmit(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
                val codePart = response?.code?.let { "HTTP $it · " }.orEmpty()
                val msg = SecureKeyVault.redactSecrets("$codePart${t.message ?: "Falha WebSocket Gemini Live"}")
                _events.tryEmit(GeminiLiveEvent.ConnectionFailed(msg))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connected.value = false
                val sanitized = SecureKeyVault.redactSecrets(reason)
                if (code != 1000) {
                    _events.tryEmit(GeminiLiveEvent.ConnectionFailed("Conexão encerrada ($code): $sanitized"))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connected.value = false
                _events.tryEmit(GeminiLiveEvent.Closed(code, SecureKeyVault.redactSecrets(reason)))
            }
        })
        return true
    }

    fun sendPcm16AudioChunk(pcm16k: ByteArray): Boolean {
        val ws = activeSocket ?: return false
        return ws.send(buildAudioChunkJson(pcm16k))
    }

    fun sendTextTurn(text: String): Boolean {
        val ws = activeSocket ?: return false
        return ws.send(buildTextTurnJson(text))
    }

    fun sendToolResponse(callId: String, name: String, outputText: String): Boolean {
        val ws = activeSocket ?: return false
        return ws.send(buildToolResponseJson(callId, name, outputText))
    }

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

            obj.getAsJsonObject("error")?.let { err ->
                val msg = err.get("message")?.asString ?: "Erro do servidor Gemini Live"
                out.add(GeminiLiveEvent.ConnectionFailed(SecureKeyVault.redactSecrets(msg)))
            }

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

            return out
        }
    }
}
