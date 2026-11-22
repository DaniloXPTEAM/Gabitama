package com.novoagentehb.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novoagentehb.app.agent.orchestrator.OrchestratorTurnState
import com.novoagentehb.app.agent.orchestrator.TaskOrchestrator
import com.novoagentehb.app.bridges.NotificationFeedService
import com.novoagentehb.app.bridges.WeatherClient
import com.novoagentehb.app.bridges.WeatherSummary
import com.novoagentehb.app.data.local.ChatMessageDao
import com.novoagentehb.app.data.local.ChatMessageEntity
import com.novoagentehb.app.data.local.FeedEventDao
import com.novoagentehb.app.data.local.GoalDao
import com.novoagentehb.app.data.prefs.AppPreferences
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val orchestrator: TaskOrchestrator,
    private val voiceCoordinator: VoiceEngineCoordinator,
    private val hbController: HBCharacterController,
    private val weatherClient: WeatherClient,
    private val chatDao: ChatMessageDao,
    private val feedDao: FeedEventDao,
    private val goalDao: GoalDao,
    private val prefs: AppPreferences
) : ViewModel() {

    val hbState: StateFlow<HBState> = hbController.state
    val turnState: StateFlow<OrchestratorTurnState> = orchestrator.turnState
    val voiceState: StateFlow<VoiceStatusSnapshot> = voiceCoordinator.status
    val spotifyPlaying: StateFlow<Boolean> = NotificationFeedService.spotifyPlaying

    val recentMessages: StateFlow<List<ChatMessageEntity>> = chatDao.observeRecent(4)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeGoalsCount: StateFlow<Int> = goalDao.observeAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val unreadFeedCount: StateFlow<Int> = feedDao.observeRecent(50)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _weather = MutableStateFlow<WeatherSummary?>(null)
    val weather: StateFlow<WeatherSummary?> = _weather.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private var lastSubmittedPrompt: String? = null

    init {
        refreshWeather()
        viewModelScope.launch {
            voiceCoordinator.events.collect { event ->
                when (event) {
                    is VoiceTranscriptEvent.VoiceError -> _voiceError.value = event.message
                    is VoiceTranscriptEvent.FinalResult -> _voiceError.value = null
                    else -> Unit
                }
            }
        }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            val city = prefs.defaultCity.first()
            _weather.value = weatherClient.getCurrentWeather(city)
        }
    }

    fun onMicTap(hasAudioPermission: Boolean) {
        if (!hasAudioPermission) return
        if (voiceState.value.isListening) {
            voiceCoordinator.stopListening()
        } else {
            _voiceError.value = null
            voiceCoordinator.startListening()
        }
    }

    fun onLiveTap(hasAudioPermission: Boolean) {
        if (!hasAudioPermission) return
        _voiceError.value = null
        voiceCoordinator.toggleGeminiLiveSession()
    }

    fun submitPrompt(text: String) {
        lastSubmittedPrompt = text
        orchestrator.submitUserMessage(text) { reply ->
            voiceCoordinator.speak(reply)
        }
    }

    fun retryLastPrompt() {
        lastSubmittedPrompt?.let { submitPrompt(it) }
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
}
