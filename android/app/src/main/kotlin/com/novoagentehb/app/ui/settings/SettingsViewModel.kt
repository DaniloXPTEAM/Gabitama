package com.novoagentehb.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novoagentehb.app.agent.diagnostics.DiagnosticsEngine
import com.novoagentehb.app.agent.diagnostics.DiagnosticsInput
import com.novoagentehb.app.agent.models.ModelRouter
import com.novoagentehb.app.agent.orchestrator.TaskOrchestrator
import com.novoagentehb.app.data.migration.ExplicitDataExporter
import com.novoagentehb.app.data.providers.CustomProvider
import com.novoagentehb.app.data.providers.CustomProviderFormat
import com.novoagentehb.app.data.providers.CustomProviderRepository
import com.novoagentehb.app.data.providers.ProviderNetwork
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
    private val modelRouter: ModelRouter,
    private val customProviders: CustomProviderRepository,
    private val providerNetwork: ProviderNetwork
) : ViewModel() {

    val geminiImageModel = prefs.geminiImageModel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun saveGeminiImageModel(value: String) {
        val model = value.trim()
        if (model.isNotEmpty() && !Regex("[A-Za-z0-9._-]{1,100}").matches(model)) {
            _customNotice.value = "Identificador de imagem Gemini inválido."
            return
        }
        viewModelScope.launch {
            prefs.setGeminiImageModel(model)
            _customNotice.value = if (model.isEmpty()) "Rota de imagem Gemini desativada." else "Rota Gemini salva; confirme compatibilidade e custo. Cada imagem exige autorização."
        }
    }
    val customProviderList = customProviders.providers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeCustomId = customProviders.activeId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    private val _customModels = MutableStateFlow<List<String>>(emptyList())
    val customModels = _customModels.asStateFlow()
    private val _customNotice = MutableStateFlow<String?>(null)
    val customNotice = _customNotice.asStateFlow()

    fun saveCustomProvider(existingId: String?, name: String, url: String, chat: String, screen: String, vision: String, key: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val profile = if (existingId == null) CustomProviderFormat.validate(name, url, chat, screen, vision)
                    else CustomProviderFormat.validate(name, url, chat, screen, vision, existingId)
                if (existingId == null) customProviders.add(profile, key) else customProviders.update(profile, key)
                _customNotice.value = "Provedor salvo no cofre. Após editar, reative para autorizar o uso."
                onSuccess()
            } catch (e: IllegalArgumentException) { _customNotice.value = e.message ?: "Configuração inválida." }
            catch (_: Exception) { _customNotice.value = "Não foi possível salvar o provedor." }
        }
    }
    fun activateCustomProvider(id: String) {
        viewModelScope.launch {
            try { customProviders.activate(id); _customNotice.value = "Provedor selecionado para futuras conversas. Uso pode gerar custos." }
            catch (_: Exception) { _customNotice.value = "Não foi possível selecionar o provedor." }
        }
    }
    fun removeCustomProvider(id: String) {
        viewModelScope.launch {
            try { customProviders.remove(id); _customNotice.value = "Provedor removido." }
            catch (_: Exception) { _customNotice.value = "Não foi possível remover o provedor." }
        }
    }
    fun discoverCustomModels(provider: CustomProvider) {
        viewModelScope.launch {
            _customModels.value = emptyList()
            _customNotice.value = "Consultando catálogo; capacidade e preço não são verificados…"
            try {
                _customModels.value = providerNetwork.discover(provider, customProviders.key(provider.id))
                _customNotice.value = "${_customModels.value.size} modelo(s) listado(s). Verifique função, acesso e custo antes de usar."
            } catch (e: IllegalStateException) { _customNotice.value = e.message ?: "Falha na descoberta." }
            catch (_: Exception) { _customNotice.value = "Falha na descoberta." }
        }
    }
    fun clearCustomModels() { _customModels.value = emptyList() }
    fun customImportError(message: String) { _customNotice.value = message }

    val primaryProvider: StateFlow<ProviderId> = prefs.primaryProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderId.GEMINI)

    val chatModel: StateFlow<String> = prefs.chatModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences.DEFAULT_GEMINI_CHAT_MODEL)

    val screenOpModel: StateFlow<String> = prefs.screenOpModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "gemini-2.5-flash")

    val visionModel: StateFlow<String> = prefs.visionModel
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
            val selectedModel = prefs.chatModel.first()
            _statusBanner.value = "Provedor ativo: ${provider.displayName} ($selectedModel)."
        }
    }

    fun updateModels(chat: String, screen: String, vision: String, live: String) {
        viewModelScope.launch {
            prefs.setModels(chat.trim(), screen.trim(), vision.trim(), live.trim())
            _statusBanner.value = "Modelos atualizados."
        }
    }

    fun selectDiscoveredLiveModel(model: String) {
        if (model !in _discoveredLiveModels.value) return
        viewModelScope.launch {
            prefs.setModels(prefs.chatModel.first(), prefs.screenOpModel.first(), prefs.visionModel.first(), model)
            _statusBanner.value = "Modelo Live selecionado: $model. Confirme disponibilidade de áudio e custos antes de usar."
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
