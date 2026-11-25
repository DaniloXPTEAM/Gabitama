package com.novoagentehb.app.gabi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.audiofx.AcousticEchoCanceler
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import com.novoagentehb.app.agent.tools.CapabilityRegistry
import com.novoagentehb.app.data.prefs.AppPreferences
import com.novoagentehb.app.data.prefs.VoiceModeId
import com.novoagentehb.app.data.security.SecureKeyVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Watchdog puro de sessão de reconhecimento de fala (testável em JVM sem Android).
 */
class SpeechSessionWatchdog {
    private var serial = 0L
    private var activeId: Long? = null
    private var deadline = 0L
    private var absoluteDeadline = 0L
    private var awaitingResult = false
    private var lastPartial = ""

    val isActive: Boolean get() = activeId != null

    fun start(nowMillis: Long): Long? {
        if (isActive) return null
        val id = ++serial
        activeId = id
        absoluteDeadline = nowMillis + 90_000L
        deadline = nowMillis + 10_000L
        awaitingResult = false
        lastPartial = ""
        return id
    }

    fun accepts(id: Long): Boolean = activeId == id

    fun markReady(id: Long, nowMillis: Long) {
        if (accepts(id) && !awaitingResult) {
            deadline = minOf(nowMillis + 30_000L, absoluteDeadline)
        }
    }

    fun markPartial(id: Long, text: String, nowMillis: Long) {
        if (accepts(id) && !awaitingResult && text.isNotBlank() && text != lastPartial) {
            lastPartial = text
            deadline = minOf(nowMillis + 15_000L, absoluteDeadline)
        }
    }

    fun markSpeechEnded(id: Long, nowMillis: Long) {
        if (accepts(id) && !awaitingResult) {
            awaitingResult = true
            deadline = minOf(nowMillis + 8_000L, absoluteDeadline)
        }
    }

    fun isTimedOut(id: Long, nowMillis: Long): Boolean = accepts(id) && nowMillis >= deadline

    fun remainingMillis(id: Long, nowMillis: Long): Long =
        if (accepts(id)) (deadline - nowMillis).coerceAtLeast(1L) else 0L

    fun finish(id: Long): Boolean {
        if (!accepts(id)) return false
        activeId = null
        return true
    }

    fun cancel() {
        activeId = null
    }
}

enum class LiveSessionState(val label: String) {
    DISCONNECTED("Live desligado"),
    CONNECTING("Conectando Live…"),
    ACTIVE("Gemini Live ativo"),
    RECONNECTING("Reconectando Live…"),
    BLOCKED("Bloqueado / Erro")
}

data class LiveReducerInput(
    val state: LiveSessionState = LiveSessionState.DISCONNECTED,
    val keepAlive: Boolean = false,
    val reconnectAttempts: Int = 0
)

data class LiveReducerDecision(
    val nextState: LiveSessionState,
    val nextReconnectAttempts: Int = 0,
    val isListening: Boolean? = null,
    val isSpeaking: Boolean? = null,
    val startMicStream: Boolean = false,
    val stopMicStream: Boolean = false,
    val clearAudioQueue: Boolean = false,
    val enqueuePcm24k: ByteArray? = null,
    val partialTranscript: String? = null,
    val assistantTranscript: String? = null,
    val dispatchToolCall: GeminiLiveEvent.ToolInvocation? = null,
    val attemptReconnect: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Máquina de estados pura para eventos da sessão Gemini Live (testável em JVM sem hardware).
 */
object GeminiLiveSessionReducer {
    const val MAX_RECONNECT_ATTEMPTS = 1

    fun reduce(current: LiveReducerInput, event: GeminiLiveEvent): LiveReducerDecision {
        return when (event) {
            is GeminiLiveEvent.Ready -> LiveReducerDecision(
                nextState = LiveSessionState.ACTIVE,
                nextReconnectAttempts = 0,
                isListening = false,
                startMicStream = true
            )
            is GeminiLiveEvent.AudioOutput -> LiveReducerDecision(
                nextState = LiveSessionState.ACTIVE,
                nextReconnectAttempts = current.reconnectAttempts,
                isSpeaking = true,
                enqueuePcm24k = event.pcm24k
            )
            is GeminiLiveEvent.InputTranscript -> LiveReducerDecision(
                nextState = current.state,
                nextReconnectAttempts = current.reconnectAttempts,
                partialTranscript = event.text
            )
            is GeminiLiveEvent.OutputTranscript -> LiveReducerDecision(
                nextState = current.state,
                nextReconnectAttempts = current.reconnectAttempts,
                assistantTranscript = event.text
            )
            is GeminiLiveEvent.Interrupted -> LiveReducerDecision(
                nextState = LiveSessionState.ACTIVE,
                nextReconnectAttempts = current.reconnectAttempts,
                isSpeaking = false,
                clearAudioQueue = true
            )
            is GeminiLiveEvent.TurnComplete -> LiveReducerDecision(
                nextState = current.state,
                nextReconnectAttempts = current.reconnectAttempts,
                isSpeaking = false
            )
            is GeminiLiveEvent.ToolInvocation -> LiveReducerDecision(
                nextState = current.state,
                nextReconnectAttempts = current.reconnectAttempts,
                dispatchToolCall = event
            )
            is GeminiLiveEvent.GoingAway -> {
                if (current.keepAlive && current.reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    LiveReducerDecision(
                        nextState = LiveSessionState.RECONNECTING,
                        nextReconnectAttempts = current.reconnectAttempts + 1,
                        isListening = false,
                        isSpeaking = false,
                        stopMicStream = true,
                        clearAudioQueue = true,
                        attemptReconnect = true
                    )
                } else {
                    LiveReducerDecision(
                        nextState = LiveSessionState.DISCONNECTED,
                        nextReconnectAttempts = 0,
                        isListening = false,
                        isSpeaking = false,
                        stopMicStream = true,
                        clearAudioQueue = true
                    )
                }
            }
            is GeminiLiveEvent.ConnectionFailed -> {
                val retry = event.retryable && current.keepAlive && current.state == LiveSessionState.ACTIVE &&
                    current.reconnectAttempts < MAX_RECONNECT_ATTEMPTS
                LiveReducerDecision(
                    nextState = if (retry) LiveSessionState.RECONNECTING else LiveSessionState.BLOCKED,
                    nextReconnectAttempts = current.reconnectAttempts + if (retry) 1 else 0,
                    isListening = false,
                    isSpeaking = false,
                    stopMicStream = true,
                    clearAudioQueue = true,
                    attemptReconnect = retry,
                    errorMessage = event.reason
                )
            }
            is GeminiLiveEvent.Closed -> LiveReducerDecision(
                nextState = when (current.state) {
                    LiveSessionState.RECONNECTING -> LiveSessionState.RECONNECTING
                    LiveSessionState.BLOCKED -> LiveSessionState.BLOCKED
                    else -> LiveSessionState.DISCONNECTED
                },
                nextReconnectAttempts = current.reconnectAttempts,
                isListening = false,
                isSpeaking = false,
                stopMicStream = true,
                clearAudioQueue = true
            )
            is GeminiLiveEvent.Usage -> LiveReducerDecision(
                nextState = current.state,
                nextReconnectAttempts = current.reconnectAttempts
            )
        }
    }
}

/**
 * Agregador puro por turno de transcrições do Gemini Live (testável em JVM):
 * - Consolida fragmentos incrementais ou parciais cumulativos de `inputTranscription`
 *   e `outputTranscription` sem gravar uma linha no banco a cada token/chunk.
 * - Garante no máximo 1 mensagem final do usuário + 1 resposta final do agente por turno.
 * - Deduplica `toolCall.id` contra reenvio após reconexão.
 */
data class TurnAggregatorFlush(
    val userTurnId: Long? = null,
    val committedUserText: String? = null,
    val assistantTurnId: Long? = null,
    val committedAssistantText: String? = null,
    val liveUserDraft: String = "",
    val liveAssistantDraft: String = "",
    val deduplicatedToolCall: GeminiLiveEvent.ToolInvocation? = null
)

class GeminiLiveTurnAggregator {
    private var turnSerial: Long = 1L
    private val userBuffer = StringBuilder()
    private val assistantBuffer = StringBuilder()
    private var userCommittedForTurn = false
    private val handledToolCallIds = LinkedHashSet<String>()

    val currentTurnId: Long get() = turnSerial
    val currentUserDraft: String get() = userBuffer.toString().trim()
    val currentAssistantDraft: String get() = assistantBuffer.toString().trim()

    fun onEvent(event: GeminiLiveEvent): TurnAggregatorFlush {
        return when (event) {
            is GeminiLiveEvent.InputTranscript -> {
                mergeTranscript(userBuffer, event.text)
                TurnAggregatorFlush(
                    liveUserDraft = userBuffer.toString().trim(),
                    liveAssistantDraft = assistantBuffer.toString().trim()
                )
            }
            is GeminiLiveEvent.OutputTranscript -> {
                val flushedUser = flushPendingUserIfNeeded()
                mergeTranscript(assistantBuffer, event.text)
                TurnAggregatorFlush(
                    userTurnId = flushedUser?.first,
                    committedUserText = flushedUser?.second,
                    liveUserDraft = "",
                    liveAssistantDraft = assistantBuffer.toString().trim()
                )
            }
            is GeminiLiveEvent.AudioOutput -> {
                val flushedUser = flushPendingUserIfNeeded()
                TurnAggregatorFlush(
                    userTurnId = flushedUser?.first,
                    committedUserText = flushedUser?.second,
                    liveUserDraft = userBuffer.toString().trim(),
                    liveAssistantDraft = assistantBuffer.toString().trim()
                )
            }
            is GeminiLiveEvent.ToolInvocation -> {
                val flushedUser = flushPendingUserIfNeeded()
                val shouldRun = event.callId.isNotBlank() && handledToolCallIds.add(event.callId)
                if (handledToolCallIds.size > 128) {
                    val oldest = handledToolCallIds.firstOrNull()
                    if (oldest != null) handledToolCallIds.remove(oldest)
                }
                TurnAggregatorFlush(
                    userTurnId = flushedUser?.first,
                    committedUserText = flushedUser?.second,
                    deduplicatedToolCall = if (shouldRun) event else null
                )
            }
            is GeminiLiveEvent.TurnComplete -> flushWholeTurn(interrupted = false)
            is GeminiLiveEvent.Interrupted -> flushWholeTurn(interrupted = true)
            is GeminiLiveEvent.Closed,
            is GeminiLiveEvent.ConnectionFailed -> flushWholeTurn(interrupted = false)
            else -> TurnAggregatorFlush(
                liveUserDraft = userBuffer.toString().trim(),
                liveAssistantDraft = assistantBuffer.toString().trim()
            )
        }
    }

    fun flushOnStop(): TurnAggregatorFlush = flushWholeTurn(interrupted = false)

    fun reset() {
        userBuffer.clear()
        assistantBuffer.clear()
        userCommittedForTurn = false
        turnSerial = 1L
        handledToolCallIds.clear()
    }

    private fun flushPendingUserIfNeeded(): Pair<Long, String>? {
        if (userCommittedForTurn) return null
        val text = userBuffer.toString().trim()
        if (text.isBlank()) return null
        userBuffer.clear()
        userCommittedForTurn = true
        return turnSerial to text
    }

    private fun flushWholeTurn(interrupted: Boolean): TurnAggregatorFlush {
        val flushedUser = flushPendingUserIfNeeded()
        val rawAssistant = assistantBuffer.toString().trim()
        assistantBuffer.clear()
        val finalAssistant = if (rawAssistant.isNotBlank()) {
            if (interrupted) "$rawAssistant [interrompido]" else rawAssistant
        } else {
            null
        }
        val completedTurnId = turnSerial
        if (flushedUser != null || finalAssistant != null) {
            turnSerial++
        }
        userCommittedForTurn = false
        return TurnAggregatorFlush(
            userTurnId = flushedUser?.first ?: completedTurnId.takeIf { finalAssistant != null },
            committedUserText = flushedUser?.second,
            assistantTurnId = completedTurnId.takeIf { finalAssistant != null },
            committedAssistantText = finalAssistant,
            liveUserDraft = "",
            liveAssistantDraft = ""
        )
    }

    companion object {
        fun mergeTranscript(buffer: StringBuilder, incomingRaw: String) {
            val incoming = incomingRaw.trim()
            if (incoming.isBlank()) return
            val existing = buffer.toString().trim()
            if (existing.isEmpty()) {
                buffer.append(incoming)
                return
            }
            // Se o servidor enviar transcrição acumulada começando pelo texto já recebido, substitui
            if (incoming.startsWith(existing, ignoreCase = false)) {
                buffer.clear()
                buffer.append(incoming)
                return
            }
            // Ignora repetição exata de trecho final
            if (existing.endsWith(incoming, ignoreCase = false)) {
                return
            }
            val needsSpace = !existing.last().isWhitespace() &&
                incoming.first() !in listOf('.', ',', '!', '?', ';', ':', ')', ']')
            if (needsSpace) buffer.append(' ')
            buffer.append(incoming)
        }
    }
}

sealed class VoiceTranscriptEvent {
    object ListeningStarted : VoiceTranscriptEvent()
    data class Partial(val text: String) : VoiceTranscriptEvent()
    data class FinalResult(val text: String) : VoiceTranscriptEvent()
    data class LiveUserTurnCommitted(val turnId: Long, val text: String) : VoiceTranscriptEvent()
    data class LiveAssistantTurnCommitted(val turnId: Long, val text: String, val modelUsed: String) : VoiceTranscriptEvent()
    data class VoiceError(val message: String) : VoiceTranscriptEvent()
}

data class VoiceStatusSnapshot(
    val activeEngine: VoiceModeId = VoiceModeId.GEMINI_LIVE,
    val frozenProfileId: String = "gabi_24l",
    val activeProsodyProfile: String = "B2_base",
    val activeVoiceDisclosure: String = "Gemini Live usa voz pré-construída Kore (gabi_24l requer Pocket TTS não embarcado)",
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val liveSessionState: LiveSessionState = LiveSessionState.DISCONNECTED,
    val liveConnectionError: String? = null,
    val fallbackActiveReason: String? = null,
    val partialTranscript: String = ""
)

/**
 * Coordenador único de voz da Gabi/HB:
 * - Liga Gemini Live WebSocket bidirecional (captura PCM16 16kHz -> envio -> fila PCM24kHz -> barge-in -> tool calls).
 * - Consolida transcrições por turno via [GeminiLiveTurnAggregator] sem duplicar linhas nem repetir tools.
 * - Mantém identidade vocal `gabi_24l` congelada nos metadados e expõe com transparência qual motor realmente toca.
 */
@Singleton
class VoiceEngineCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val keyVault: SecureKeyVault,
    private val okHttpClient: OkHttpClient,
    private val geminiLive: GeminiLiveVoiceClient,
    private val capabilityRegistry: CapabilityRegistry,
    private val hbController: HBCharacterController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchdog = SpeechSessionWatchdog()
    private val turnAggregator = GeminiLiveTurnAggregator()

    private var recognizer: SpeechRecognizer? = null
    private var activeCall: Call? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioRecord: PcmCapture? = null
    private var systemTts: TextToSpeech? = null
    private var systemTtsReady = false
    private var speechJob: Job? = null
    private var micStreamJob: Job? = null
    private var liveWatchdogJob: Job? = null
    private var liveReconnectJob: Job? = null
    private var liveConnectJob: Job? = null
    private var livePlaybackJob: Job? = null
    private val micLock = Any()
    private val micGeneration = java.util.concurrent.atomic.AtomicLong()
    private data class PcmChunk(val generation: Long, val bytes: ByteArray)
    private val pcmEngine = PcmPlaybackEngine(GeminiLiveVoiceClient.OUTPUT_SAMPLE_RATE) { pcmAudioFactory }
    private val pcmOutChannel = Channel<PcmChunk>(capacity = Channel.UNLIMITED)

    private var liveReducerState = LiveReducerInput()
    private var activeLiveModel: String = AppPreferences.DEFAULT_GEMINI_LIVE_MODEL
    private var liveToolCallHandler: (suspend (callId: String, name: String, args: Map<String, String>) -> String)? = null
    /** Test seam: when false the Live session never opens AudioRecord (CI emulators run without audio). Production keeps true. */
    internal var microphoneEnabled: Boolean = true
    /** Test seam for AudioRecord/AudioTrack; production always uses AndroidPcmAudioFactory. */
    internal var pcmAudioFactory: PcmAudioFactory = AndroidPcmAudioFactory

    private val _status = MutableStateFlow(VoiceStatusSnapshot())
    val status: StateFlow<VoiceStatusSnapshot> = _status.asStateFlow()

    private val _events = MutableSharedFlow<VoiceTranscriptEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<VoiceTranscriptEvent> = _events.asSharedFlow()

    val voiceSpec = FrozenVoiceIdentitySpec()

    init {
        systemTts = TextToSpeech(context) { result ->
            systemTtsReady = (result == TextToSpeech.SUCCESS)
            if (systemTtsReady) {
                systemTts?.language = Locale("pt", "BR")
            }
        }
        observeGeminiLiveEvents()
        ensurePcmPlayerConsumer()
    }

    fun registerLiveToolCallHandler(
        handler: suspend (callId: String, name: String, args: Map<String, String>) -> String
    ) {
        liveToolCallHandler = handler
    }

    private fun updateStatus(transform: (VoiceStatusSnapshot) -> VoiceStatusSnapshot) {
        val next = transform(_status.value)
        _status.value = next
        hbController.updateVoiceStatus(next)
    }

    private fun ensurePcmPlayerConsumer() {
        if (livePlaybackJob?.isActive == true) return
        livePlaybackJob = scope.launch(Dispatchers.IO) {
            for (chunk in pcmOutChannel) {
                writePcm24kBlocking(chunk)
            }
        }
    }

    private fun observeGeminiLiveEvents() {
        scope.launch {
            geminiLive.events.collect { evt ->
                // A queued callback must not revive a session stopped by the user or after failure.
                if (!liveReducerState.keepAlive) return@collect
                val decision = GeminiLiveSessionReducer.reduce(liveReducerState, evt)
                liveReducerState = liveReducerState.copy(
                    state = decision.nextState,
                    keepAlive = liveReducerState.keepAlive && decision.nextState != LiveSessionState.BLOCKED && decision.nextState != LiveSessionState.DISCONNECTED,
                    reconnectAttempts = decision.nextReconnectAttempts
                )

                val turnFlush = turnAggregator.onEvent(evt)

                if (evt is GeminiLiveEvent.Ready || evt is GeminiLiveEvent.ConnectionFailed || evt is GeminiLiveEvent.Closed) {
                    liveWatchdogJob?.cancel()
                }

                if (decision.clearAudioQueue) {
                    drainPcmOutQueueAndFlushTrack()
                }
                if (decision.stopMicStream) {
                    stopPcmMicCapture()
                }
                if (decision.startMicStream) {
                    startPcmMicCapture()
                }

                decision.enqueuePcm24k?.let { pcm ->
                    pcmOutChannel.trySend(PcmChunk(pcmEngine.generation, pcm))
                }

                emitTurnCommits(turnFlush)

                decision.errorMessage?.let { msg ->
                    _events.tryEmit(VoiceTranscriptEvent.VoiceError(msg))
                }

                updateStatus { cur ->
                    val nextPartial = when {
                        turnFlush.liveUserDraft.isNotBlank() -> turnFlush.liveUserDraft
                        evt is GeminiLiveEvent.TurnComplete || evt is GeminiLiveEvent.Interrupted || evt is GeminiLiveEvent.Closed -> ""
                        else -> decision.partialTranscript ?: cur.partialTranscript
                    }
                    cur.copy(
                        liveSessionState = decision.nextState,
                        liveConnectionError = decision.errorMessage ?: if (decision.nextState == LiveSessionState.ACTIVE) null else cur.liveConnectionError,
                        isListening = decision.isListening ?: cur.isListening,
                        isSpeaking = decision.isSpeaking ?: cur.isSpeaking,
                        partialTranscript = nextPartial
                    )
                }

                turnFlush.deduplicatedToolCall?.let { call ->
                    scope.launch {
                        val handler = liveToolCallHandler
                        val reply = if (handler != null) {
                            runCatching { handler(call.callId, call.name, call.argsJson) }
                                .getOrElse { "Erro ao executar ${call.name}: ${it.message ?: "falha"}" }
                        } else {
                            "Executor de ferramentas indisponível no momento."
                        }
                        geminiLive.sendToolResponse(call.callId, call.name, reply)
                    }
                }

                if (decision.attemptReconnect) {
                    liveReconnectJob?.cancel()
                    liveReconnectJob = scope.launch {
                        delay(600L)
                        if (liveReducerState.keepAlive) {
                            connectGeminiLiveInternal(isReconnect = true)
                        }
                    }
                }
            }
        }
    }

    private fun emitTurnCommits(flush: TurnAggregatorFlush) {
        val userText = flush.committedUserText
        val userTurn = flush.userTurnId
        if (!userText.isNullOrBlank() && userTurn != null) {
            _events.tryEmit(VoiceTranscriptEvent.LiveUserTurnCommitted(userTurn, userText))
        }
        val assistantText = flush.committedAssistantText
        val assistantTurn = flush.assistantTurnId
        if (!assistantText.isNullOrBlank() && assistantTurn != null) {
            _events.tryEmit(VoiceTranscriptEvent.LiveAssistantTurnCommitted(assistantTurn, assistantText, activeLiveModel))
        }
    }

    fun toggleGeminiLiveSession() {
        if (liveReducerState.state == LiveSessionState.ACTIVE ||
            liveReducerState.state == LiveSessionState.CONNECTING ||
            liveReducerState.state == LiveSessionState.RECONNECTING
        ) {
            stopGeminiLiveSession()
        } else {
            startGeminiLiveSession()
        }
    }

    fun startGeminiLiveSession() {
        if (liveReducerState.keepAlive) return
        // The microphone gate is part of the microphone path; the test seam (microphoneEnabled = false) skips both.
        if (microphoneEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val msg = "Permissão de microfone necessária para Gemini Live."
            updateStatus { it.copy(liveSessionState = LiveSessionState.BLOCKED, liveConnectionError = msg) }
            _events.tryEmit(VoiceTranscriptEvent.VoiceError(msg))
            return
        }
        turnAggregator.reset()
        liveReducerState = LiveReducerInput(state = LiveSessionState.CONNECTING, keepAlive = true, reconnectAttempts = 0)
        liveConnectJob = scope.launch { connectGeminiLiveInternal(isReconnect = false) }
    }

    private suspend fun connectGeminiLiveInternal(isReconnect: Boolean) {
        val apiKey = keyVault.get(SecureKeyVault.KEY_GEMINI).trim()
        if (apiKey.isBlank()) {
            val msg = "Configure sua chave Gemini em Ajustes antes de iniciar o Gemini Live."
            liveReducerState = LiveReducerInput(state = LiveSessionState.BLOCKED, keepAlive = false)
            updateStatus { it.copy(liveSessionState = LiveSessionState.BLOCKED, liveConnectionError = msg) }
            _events.tryEmit(VoiceTranscriptEvent.VoiceError(msg))
            return
        }

        val model = prefs.liveVoiceModel.first().ifBlank { AppPreferences.DEFAULT_GEMINI_LIVE_MODEL }
        activeLiveModel = model
        val city = prefs.defaultCity.first()
        val systemPrompt = GabiPersona.buildSystemPrompt(defaultCity = city)

        updateStatus {
            it.copy(
                activeEngine = VoiceModeId.GEMINI_LIVE,
                activeVoiceDisclosure = "Gemini Live ($model · voz pré-construída ${GeminiLiveVoiceClient.DEFAULT_VOICE_NAME}; gabi_24l requer Pocket TTS não embarcado)",
                liveSessionState = if (isReconnect) LiveSessionState.RECONNECTING else LiveSessionState.CONNECTING,
                liveConnectionError = null,
                fallbackActiveReason = null
            )
        }

        stopSpeaking()
        val initiated = geminiLive.connect(
            apiKey = apiKey,
            model = model,
            systemInstruction = systemPrompt,
            voiceName = GeminiLiveVoiceClient.DEFAULT_VOICE_NAME,
            tools = capabilityRegistry.capabilities
        )
        if (!initiated) {
            liveReducerState = LiveReducerInput(state = LiveSessionState.BLOCKED, keepAlive = false)
            updateStatus {
                it.copy(
                    liveSessionState = LiveSessionState.BLOCKED,
                    liveConnectionError = "Não foi possível abrir o WebSocket do Gemini Live."
                )
            }
            return
        }

        liveWatchdogJob?.cancel()
        liveWatchdogJob = scope.launch {
            delay(15_000L)
            if (liveReducerState.state == LiveSessionState.CONNECTING ||
                liveReducerState.state == LiveSessionState.RECONNECTING
            ) {
                val msg = "Tempo limite ao aguardar handshake Gemini Live (modelo: $model)."
                liveReducerState = LiveReducerInput(state = LiveSessionState.BLOCKED, keepAlive = false)
                geminiLive.disconnect()
                updateStatus { it.copy(liveSessionState = LiveSessionState.BLOCKED, liveConnectionError = msg) }
                _events.tryEmit(VoiceTranscriptEvent.VoiceError(msg))
            }
        }
    }

    fun stopGeminiLiveSession() {
        liveConnectJob?.cancel()
        liveConnectJob = null
        liveWatchdogJob?.cancel()
        liveReconnectJob?.cancel()
        emitTurnCommits(turnAggregator.flushOnStop())
        liveReducerState = LiveReducerInput(state = LiveSessionState.DISCONNECTED, keepAlive = false)
        stopPcmMicCapture()
        drainPcmOutQueueAndFlushTrack()
        releasePcmTrack()
        geminiLive.disconnect()
        updateStatus {
            it.copy(
                liveSessionState = LiveSessionState.DISCONNECTED,
                isListening = false,
                isSpeaking = false,
                partialTranscript = ""
            )
        }
    }

    /** Fail closed, without exposing audio, keys or raw exception messages. */
    private fun failLiveAudio(message: String) {
        if (!liveReducerState.keepAlive || liveReducerState.state != LiveSessionState.ACTIVE) return
        liveWatchdogJob?.cancel()
        liveReconnectJob?.cancel()
        liveReducerState = LiveReducerInput(state = LiveSessionState.BLOCKED, keepAlive = false)
        stopPcmMicCapture()
        drainPcmOutQueueAndFlushTrack()
        releasePcmTrack()
        geminiLive.disconnect()
        updateStatus { it.copy(liveSessionState = LiveSessionState.BLOCKED, liveConnectionError = message, isListening = false, isSpeaking = false) }
        _events.tryEmit(VoiceTranscriptEvent.VoiceError(message))
    }

    @SuppressLint("MissingPermission")
    private fun startPcmMicCapture() {
        if (!microphoneEnabled) return
        stopPcmMicCapture()
        val generation = micGeneration.get()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            scope.launch { if (generation == micGeneration.get()) failLiveAudio("Permissão de microfone necessária para Gemini Live.") }
            return
        }
        micStreamJob = scope.launch(Dispatchers.IO) {
            var recorder: PcmCapture? = null
            var echo: AcousticEchoCanceler? = null
            try {
                val minBuf = PcmIoContracts.requireBufferSize(pcmAudioFactory.minCaptureBufferSize(
                    GeminiLiveVoiceClient.INPUT_SAMPLE_RATE
                )).coerceAtLeast(3200)
                val created = pcmAudioFactory.createCapture(GeminiLiveVoiceClient.INPUT_SAMPLE_RATE, minBuf * 2)
                recorder = created
                check(created.isInitialized) { "Microfone não inicializado." }
                synchronized(micLock) {
                    if (generation != micGeneration.get()) return@launch
                    audioRecord = created
                    created.startRecording()
                    check(created.isRecording) { "Microfone não iniciou gravação." }
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    echo = runCatching { AcousticEchoCanceler.create(created.audioSessionId)?.apply { enabled = true } }.getOrNull()
                }
                scope.launch {
                    if (generation == micGeneration.get() && liveReducerState.state == LiveSessionState.ACTIVE) {
                        updateStatus { it.copy(isListening = true) }
                    }
                }
                val buffer = ByteArray(1280)
                while (isActive && generation == micGeneration.get() && geminiLive.connected.value) {
                    val count = PcmIoContracts.requireReadResult(created.read(buffer, 0, buffer.size))
                    if (count > 0 && generation == micGeneration.get() && !geminiLive.sendPcm16AudioChunk(buffer.copyOf(count))) {
                        error("Falha ao enviar áudio do microfone.")
                    }
                }
            } catch (_: Exception) {
                if (generation == micGeneration.get() && isActive) scope.launch {
                    if (generation == micGeneration.get()) failLiveAudio("Falha no microfone ou no envio de áudio Live. Verifique a permissão e tente novamente.")
                }
            } finally {
                runCatching { echo?.release() }
                synchronized(micLock) { if (audioRecord === recorder) audioRecord = null }
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
            }
        }
    }

    private fun stopPcmMicCapture() {
        micGeneration.incrementAndGet()
        micStreamJob?.cancel()
        micStreamJob = null
        // stop() unblocks read(); the capture job owns release(), including cancellation races.
        synchronized(micLock) { runCatching { audioRecord?.stop() } }
    }

    private fun writePcm24kBlocking(chunk: PcmChunk) {
        try {
            pcmEngine.write(chunk.generation, chunk.bytes)
        } catch (_: Exception) {
            scope.launch {
                if (chunk.generation == pcmEngine.generation) failLiveAudio("Falha na reprodução de áudio Live. Verifique a saída de áudio e tente novamente.")
            }
        }
    }

    private fun drainPcmOutQueueAndFlushTrack() {
        pcmEngine.invalidate()
        while (pcmOutChannel.tryReceive().isSuccess) {
            // descarta áudio pendente imediatamente em barge-in/encerramento
        }
        pcmEngine.silence()
    }

    private fun releasePcmTrack() {
        pcmEngine.release()
    }

    fun startListening() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _events.tryEmit(VoiceTranscriptEvent.VoiceError("Reconhecimento de fala indisponível neste aparelho."))
                return@post
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                _events.tryEmit(VoiceTranscriptEvent.VoiceError("Permissão de microfone necessária."))
                return@post
            }

            stopSpeaking()

            val sessionId = watchdog.start(SystemClock.elapsedRealtime()) ?: return@post
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            }
            recognizer?.setRecognitionListener(buildListener(sessionId))
            scheduleWatchdog(sessionId)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L)
            }
            runCatching {
                recognizer?.startListening(intent)
                updateStatus { it.copy(isListening = true, partialTranscript = "") }
                _events.tryEmit(VoiceTranscriptEvent.ListeningStarted)
            }.onFailure { err ->
                watchdog.finish(sessionId)
                updateStatus { it.copy(isListening = false) }
                _events.tryEmit(VoiceTranscriptEvent.VoiceError("Erro ao iniciar escuta: ${err.message}"))
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            watchdog.cancel()
            runCatching { recognizer?.stopListening() }
            updateStatus { it.copy(isListening = false, partialTranscript = "") }
        }
    }

    fun speak(text: String, allowPaidTtsConsent: Boolean = false) {
        val clean = SpeechText.fromMarkdown(text)
        if (clean.isBlank()) return

        stopSpeaking()
        speechJob = scope.launch {
            val mode = prefs.voiceMode.first()
            val prosody = prefs.prosodyProfile.first()
            val rate = prefs.speechRate.first()
            val pitch = prefs.speechPitch.first()

            updateStatus {
                it.copy(
                    activeEngine = mode,
                    activeProsodyProfile = prosody,
                    isSpeaking = true,
                    fallbackActiveReason = null
                )
            }

            when (mode) {
                VoiceModeId.GEMINI_LIVE -> {
                    val key = keyVault.get(SecureKeyVault.KEY_GEMINI)
                    if (geminiLive.connected.value && key.isNotBlank() && geminiLive.sendTextTurn(clean)) {
                        // A resposta em áudio e o evento TurnComplete chegam pelo fluxo GeminiLiveEvent
                    } else {
                        val reason = when {
                            key.isBlank() -> "Chave Gemini ausente — usando fallback explícito TTS Android (não é gabi_24l)."
                            !geminiLive.connected.value -> "Sessão Gemini Live desligada — usando fallback explícito TTS Android (não é gabi_24l)."
                            else -> "Falha ao enviar turno ao Gemini Live — usando fallback explícito TTS Android."
                        }
                        speakSystemFallback(clean, rate, pitch, reason)
                    }
                }
                VoiceModeId.GABI_24L_POCKET -> {
                    val profile = voiceSpec.profileById(prosody)
                    speakSystemFallback(
                        text = clean,
                        rate = rate,
                        pitch = pitch,
                        reason = "Runtime local Pocket TTS (gabi_24l / ${profile.id}) não embarcado; fallback TTS Android ativo."
                    )
                }
                VoiceModeId.ELEVENLABS -> {
                    val elKey = keyVault.get(SecureKeyVault.KEY_ELEVENLABS)
                    if (elKey.isBlank() || !allowPaidTtsConsent) {
                        val reason = if (elKey.isBlank()) {
                            "Chave ElevenLabs ausente — fallback TTS Android ativo."
                        } else {
                            "Uso de TTS pago (ElevenLabs) requer autorização explícita — fallback ativo."
                        }
                        speakSystemFallback(clean, rate, pitch, reason)
                    } else {
                        val ok = runCatching { speakElevenLabs(elKey, clean) }.isSuccess
                        if (!ok) {
                            speakSystemFallback(clean, rate, pitch, "Falha na chamada ElevenLabs — fallback ativo.")
                        } else {
                            updateStatus { it.copy(isSpeaking = false) }
                        }
                    }
                }
                VoiceModeId.ANDROID_TTS -> {
                    speakSystemFallback(clean, rate, pitch, "Motor TTS padrão do Android ativo.")
                }
            }
        }
    }

    fun sendLiveSystemNote(text: String) {
        if (geminiLive.connected.value) {
            geminiLive.sendTextTurn(text)
        }
    }

    fun playPcm24kAudio(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return
        ensurePcmPlayerConsumer()
        pcmOutChannel.trySend(PcmChunk(pcmEngine.generation, pcmBytes))
    }

    fun stopSpeaking() {
        speechJob?.cancel()
        speechJob = null
        runCatching { activeCall?.cancel() }
        activeCall = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        drainPcmOutQueueAndFlushTrack()
        runCatching { systemTts?.stop() }
        updateStatus { it.copy(isSpeaking = false) }
    }

    fun stopAllVoice() {
        stopSpeaking()
        stopListening()
        stopGeminiLiveSession()
    }

    private suspend fun speakSystemFallback(text: String, rate: Float, pitch: Float, reason: String?) {
        updateStatus { it.copy(fallbackActiveReason = reason) }
        if (!systemTtsReady) {
            updateStatus { it.copy(isSpeaking = false) }
            return
        }
        suspendCancellableCoroutine { cont ->
            val utteranceId = UUID.randomUUID().toString()
            systemTts?.setSpeechRate(rate.coerceIn(0.6f, 1.8f))
            systemTts?.setPitch(pitch.coerceIn(0.7f, 1.5f))
            systemTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(Unit)
                }
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(Unit)
                }
            })
            cont.invokeOnCancellation { runCatching { systemTts?.stop() } }
            val params = Bundle()
            systemTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
        updateStatus { it.copy(isSpeaking = false) }
    }

    private suspend fun speakElevenLabs(apiKey: String, text: String) {
        val bodyJson = JsonObject().apply {
            addProperty("text", text)
            addProperty("model_id", "eleven_turbo_v2_5")
        }.toString()

        val req = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/EXAVITQu4vr4xnSDxMaL/stream")
            .addHeader("xi-api-key", apiKey)
            .addHeader("Accept", "audio/mpeg")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        val call = okHttpClient.newCall(req)
        activeCall = call
        val audioBytes = suspendCancellableCoroutine<ByteArray> { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    val data = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw IOException("ElevenLabs HTTP ${it.code}")
                            it.body?.bytes() ?: ByteArray(0)
                        }
                    }
                    data.onSuccess { if (cont.isActive) cont.resume(it) }
                        .onFailure { if (cont.isActive) cont.resumeWithException(it) }
                }
            })
        }

        val tmpFile = File.createTempFile("gabi_el_", ".mp3", context.cacheDir)
        try {
            tmpFile.writeBytes(audioBytes)
            suspendCancellableCoroutine { cont ->
                val mp = MediaPlayer()
                mediaPlayer = mp
                cont.invokeOnCancellation {
                    runCatching { mp.stop(); mp.release() }
                }
                mp.setDataSource(tmpFile.absolutePath)
                mp.setOnCompletionListener {
                    runCatching { it.release() }
                    if (cont.isActive) cont.resume(Unit)
                }
                mp.setOnErrorListener { player, _, _ ->
                    runCatching { player.release() }
                    if (cont.isActive) cont.resume(Unit)
                    true
                }
                mp.prepare()
                mp.start()
            }
        } finally {
            tmpFile.delete()
        }
    }

    private fun scheduleWatchdog(sessionId: Long) {
        val delayMs = watchdog.remainingMillis(sessionId, SystemClock.elapsedRealtime())
        if (delayMs <= 0L) return
        mainHandler.postDelayed({
            if (watchdog.isTimedOut(sessionId, SystemClock.elapsedRealtime())) {
                watchdog.finish(sessionId)
                runCatching { recognizer?.cancel() }
                updateStatus { it.copy(isListening = false, partialTranscript = "") }
                _events.tryEmit(VoiceTranscriptEvent.VoiceError("Tempo limite de escuta atingido."))
            }
        }, delayMs)
    }

    private fun buildListener(sessionId: Long) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (!watchdog.accepts(sessionId)) return
            watchdog.markReady(sessionId, SystemClock.elapsedRealtime())
            scheduleWatchdog(sessionId)
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            if (!watchdog.accepts(sessionId)) return
            watchdog.markSpeechEnded(sessionId, SystemClock.elapsedRealtime())
            scheduleWatchdog(sessionId)
            updateStatus { it.copy(isListening = false) }
        }
        override fun onError(error: Int) {
            if (!watchdog.finish(sessionId)) return
            updateStatus { it.copy(isListening = false, partialTranscript = "") }
            _events.tryEmit(VoiceTranscriptEvent.VoiceError("Erro de captura de voz ($error)."))
        }
        override fun onResults(results: Bundle?) {
            if (!watchdog.finish(sessionId)) return
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
            updateStatus { it.copy(isListening = false, partialTranscript = "") }
            if (text.isNotBlank()) {
                _events.tryEmit(VoiceTranscriptEvent.FinalResult(text))
            } else {
                _events.tryEmit(VoiceTranscriptEvent.VoiceError("Não consegui ouvir fala válida."))
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            if (!watchdog.accepts(sessionId)) return
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (partial.isNotBlank()) {
                watchdog.markPartial(sessionId, partial, SystemClock.elapsedRealtime())
                updateStatus { it.copy(partialTranscript = partial) }
                _events.tryEmit(VoiceTranscriptEvent.Partial(partial))
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
