package com.novoagentehb.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novoagentehb.app.agent.orchestrator.OrchestratorTurnState
import com.novoagentehb.app.agent.orchestrator.TaskOrchestrator
import com.novoagentehb.app.data.local.ChatMessageDao
import com.novoagentehb.app.data.local.ChatMessageEntity
import com.novoagentehb.app.gabi.HBCharacterController
import com.novoagentehb.app.gabi.HBState
import com.novoagentehb.app.gabi.VoiceEngineCoordinator
import com.novoagentehb.app.gabi.VoiceStatusSnapshot
import com.novoagentehb.app.gabi.VoiceTranscriptEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.novoagentehb.app.bridges.SpotifyHandoffMemory
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val orchestrator: TaskOrchestrator,
    private val voiceCoordinator: VoiceEngineCoordinator,
    private val hbController: HBCharacterController,
    private val chatDao: ChatMessageDao
) : ViewModel() {

    val hbState: StateFlow<HBState> = hbController.state

    val messages: StateFlow<List<ChatMessageEntity>> = chatDao.observeRecent(80)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val turnState: StateFlow<OrchestratorTurnState> = orchestrator.turnState
    val voiceState: StateFlow<VoiceStatusSnapshot> = voiceCoordinator.status

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private var lastSubmitted: String? = null

    init {
        viewModelScope.launch {
            voiceCoordinator.events.collect { evt ->
                when (evt) {
                    is VoiceTranscriptEvent.VoiceError -> _voiceError.value = evt.message
                    is VoiceTranscriptEvent.FinalResult -> _voiceError.value = null
                    else -> Unit
                }
            }
        }
    }

    /**
     * Chamado quando o chat volta ao primeiro plano. Se houve handoff (ex.: Spotify), grava um aviso
     * explícito do que foi e do que NÃO foi confirmado. Consome o handoff uma única vez.
     */
    fun onAppResumed() {
        val pending = SpotifyHandoffMemory.takeForResume(System.currentTimeMillis()) ?: return
        viewModelScope.launch {
            orchestrator.recordResumeNotice(SpotifyHandoffMemory.resumeText(pending))
        }
    }

    fun send(text: String) {
        lastSubmitted = text
        orchestrator.submitUserMessage(text) { reply ->
            voiceCoordinator.speak(reply)
        }
    }

    fun retryLast() {
        lastSubmitted?.let { send(it) }
    }

    fun toggleListening(hasAudioPermission: Boolean) {
        if (!hasAudioPermission) return
        if (voiceState.value.isListening) {
            voiceCoordinator.stopListening()
        } else {
            _voiceError.value = null
            voiceCoordinator.startListening()
        }
    }

    fun toggleLiveSession(hasAudioPermission: Boolean) {
        if (!hasAudioPermission) return
        _voiceError.value = null
        voiceCoordinator.toggleGeminiLiveSession()
    }

    /** Usuário negou o microfone ao tocar em Gemini Live: sem isso a tela não mostrava motivo algum. */
    fun reportLiveMicrophoneDenied() {
        _voiceError.value = LIVE_MIC_DENIED_MESSAGE
    }

    fun resolveConfirmation(approved: Boolean) {
        orchestrator.resolveConfirmation(approved) { reply ->
            voiceCoordinator.speak(reply)
        }
    }

    fun stopAll() {
        voiceCoordinator.stopAllVoice()
        orchestrator.stopCurrentExecution()
    }

    fun clearHistory() {
        viewModelScope.launch { chatDao.clearAll() }
    }
}

internal const val LIVE_MIC_DENIED_MESSAGE =
    "Microfone negado: o Gemini Live precisa do microfone. Conceda a permissão do microfone ao app nas configurações do aparelho e toque em Gemini Live de novo."
