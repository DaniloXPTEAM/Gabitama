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
