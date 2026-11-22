package com.novoagentehb.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novoagentehb.app.agent.diagnostics.DiagnosticsEngine
import com.novoagentehb.app.agent.diagnostics.DiagnosticsInput
import com.novoagentehb.app.agent.models.ModelRouter
import com.novoagentehb.app.agent.orchestrator.TaskOrchestrator
import com.novoagentehb.app.data.migration.ExplicitDataExporter
import com.novoagentehb.app.data.prefs.AppPreferences
import com.novoagentehb.app.data.prefs.ProviderId
import com.novoagentehb.app.data.prefs.VoiceModeId
import com.novoagentehb.app.data.security.SecureKeyVault
import com.novoagentehb.app.gabi.FrozenVoiceIdentitySpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val keyVault: SecureKeyVault,
    private val exporter: ExplicitDataExporter,
    private val diagnosticsEngine: DiagnosticsEngine,
    private val orchestrator: TaskOrchestrator,
    private val modelRouter: ModelRouter
) : ViewModel() {

    val primaryProvider: StateFlow<ProviderId> = prefs.primaryProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderId.GEMINI)

    val chatModel: StateFlow<String> = prefs.chatModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "gemini-2.5-flash")

    val liveVoiceModel: StateFlow<String> = prefs.liveVoiceModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences.DEFAULT_GEMINI_LIVE_MODEL)

    val voiceMode: StateFlow<VoiceModeId> = prefs.voiceMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceModeId.GEMINI_LIVE)

    val prosodyProfile: StateFlow<String> = prefs.prosodyProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "B2_base")

    val defaultCity: StateFlow<String> = prefs.defaultCity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Fortaleza")

    val allowPaidAuto: StateFlow<Boolean> = prefs.allowPaidCallsWithoutConfirm
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val voiceProfiles = FrozenVoiceIdentitySpec.defaultProfiles

    private val _discoveredLiveModels = MutableStateFlow<List<String>>(emptyList())
    val discoveredLiveModels: StateFlow<List<String>> = _discoveredLiveModels.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _exportJson = MutableStateFlow<String?>(null)
    val exportJson: StateFlow<String?> = _exportJson.asStateFlow()

    private val _diagnosticsText = MutableStateFlow<String?>(null)
    val diagnosticsText: StateFlow<String?> = _diagnosticsText.asStateFlow()

    fun hasKey(keyName: String): Boolean = keyVault.get(keyName).isNotBlank()

    fun saveApiKey(keyName: String, value: String) {
        keyVault.set(keyName, value)
        _statusBanner.value = "Chave salva no cofre AES256-GCM ($keyName)."
    }

    fun selectProvider(provider: ProviderId) {
        viewModelScope.launch {
            prefs.setPrimaryProvider(provider)
            val defaultModel = when (provider) {
                ProviderId.GEMINI -> "gemini-2.5-flash"
                ProviderId.GROQ -> "llama-3.3-70b-versatile"
                ProviderId.OPENROUTER -> "openrouter/free"
                ProviderId.DEEPSEEK -> "deepseek-chat"
                ProviderId.ANTHROPIC -> "claude-sonnet-4-20250514"
            }
            prefs.setModels(defaultModel, defaultModel, defaultModel, prefs.liveVoiceModel.first())
            _statusBanner.value = "Provedor ativo: ${provider.displayName} ($defaultModel)."
        }
    }

    fun updateModels(chat: String, live: String) {
        viewModelScope.launch {
            prefs.setModels(chat.trim(), chat.trim(), chat.trim(), live.trim())
            _statusBanner.value = "Modelos atualizados."
        }
    }

    fun discoverGeminiLiveModels() {
        viewModelScope.launch {
            _statusBanner.value = "Consultando modelos bidiGenerateContent na sua conta Gemini…"
            modelRouter.discoverGeminiLiveModels()
                .onSuccess { list ->
                    _discoveredLiveModels.value = list
                    val selected = prefs.liveVoiceModel.first().removePrefix("models/")
                    _statusBanner.value = when {
                        list.isEmpty() -> "Nenhum modelo bidirecional retornado. A seleção atual foi mantida."
                        selected !in list -> "${list.size} candidato(s); modelo atual não listado. Escolha manualmente após verificar áudio, acesso e custos."
                        else -> "${list.size} candidato(s). Modelo atual: $selected. Áudio, acesso e custos exigem verificação."
                    }
                }
                .onFailure { err ->
                    _discoveredLiveModels.value = emptyList()
                    _statusBanner.value = "Falha na descoberta Live: ${err.message}"
                }
        }
    }

    fun selectVoiceMode(mode: VoiceModeId) {
        viewModelScope.launch {
            prefs.setVoiceConfig(mode, prefs.prosodyProfile.first(), prefs.speechRate.first(), prefs.speechPitch.first())
        }
    }

    fun selectProsody(profileId: String) {
        viewModelScope.launch {
            prefs.setVoiceConfig(prefs.voiceMode.first(), profileId, prefs.speechRate.first(), prefs.speechPitch.first())
        }
    }

    fun setCity(city: String) {
        viewModelScope.launch {
            prefs.setDefaultCity(city)
            _statusBanner.value = "Cidade padrão atualizada para ${city.ifBlank { "Fortaleza" }}."
        }
    }

    fun setAllowPaidAuto(allow: Boolean) {
        viewModelScope.launch { prefs.setAllowPaidCallsWithoutConfirm(allow) }
    }

    fun generateExport() {
        viewModelScope.launch {
            _exportJson.value = exporter.exportToJson()
        }
    }

    fun importSnapshot(rawJson: String) {
        viewModelScope.launch {
            val out = exporter.importFromJson(rawJson, replaceExisting = false)
            _statusBanner.value = out.message
        }
    }

    fun clearExportPreview() {
        _exportJson.value = null
    }

    fun runLocalDiagnostics() {
        viewModelScope.launch {
            _diagnosticsText.value = diagnosticsEngine.buildReport(
                DiagnosticsInput(
                    primaryProvider = prefs.primaryProvider.first(),
                    chatModel = prefs.chatModel.first(),
                    voiceMode = prefs.voiceMode.first(),
                    prosodyProfile = prefs.prosodyProfile.first(),
                    lastFailure = orchestrator.turnState.value.lastProviderFailure,
                    petComponentsPresent = false
                )
            )
        }
    }

    fun clearDiagnostics() {
        _diagnosticsText.value = null
    }
}
