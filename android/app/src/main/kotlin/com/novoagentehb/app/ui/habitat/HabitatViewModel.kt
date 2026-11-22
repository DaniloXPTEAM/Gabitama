package com.novoagentehb.app.ui.habitat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novoagentehb.app.agent.orchestrator.OrchestratorTurnState
import com.novoagentehb.app.agent.orchestrator.TaskOrchestrator
import com.novoagentehb.app.gabi.HBCharacterController
import com.novoagentehb.app.gabi.HBCharacterStage
import com.novoagentehb.app.gabi.HBStageCatalog
import com.novoagentehb.app.gabi.HBWardrobeCatalog
import com.novoagentehb.app.gabi.RoomBackgroundOption
import com.novoagentehb.app.gabi.RoomUiState
import com.novoagentehb.app.gabi.VoiceEngineCoordinator
import com.novoagentehb.app.gabi.VoiceStatusSnapshot
import com.novoagentehb.app.gabi.WardrobeOutfit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HabitatViewModel @Inject constructor(
    private val hbController: HBCharacterController,
    private val orchestrator: TaskOrchestrator,
    private val voiceCoordinator: VoiceEngineCoordinator
) : ViewModel() {

    val roomUiState: StateFlow<RoomUiState> = hbController.roomUiState
    val turnState: StateFlow<OrchestratorTurnState> = orchestrator.turnState
    val voiceState: StateFlow<VoiceStatusSnapshot> = voiceCoordinator.status

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val allOutfits: List<WardrobeOutfit> = HBWardrobeCatalog.all
    val allStages: List<HBCharacterStage> = HBStageCatalog.all

    fun refreshOnResume() {
        hbController.refreshEnvironmentState()
    }

    fun feed() {
        hbController.feed()
        _notice.value = "Gabi alimentada agora!"
    }

    fun onCharacterTapped() {
        hbController.onCharacterTapped()
    }

    fun selectOutfit(outfit: WardrobeOutfit?) {
        val ok = hbController.selectOutfit(outfit)
        _notice.value = when {
            outfit == null -> "Modo Rotina do dia ativado."
            ok -> "Look alterado para ${outfit.label}."
            else -> "Arte pendente: ${outfit.poseFile} ainda não existe no pacote."
        }
    }

    fun isOutfitAvailable(outfit: WardrobeOutfit): Boolean =
        hbController.isOutfitAssetAvailable(outfit)

    fun isStageAvailable(stage: HBCharacterStage): Boolean =
        hbController.isStageAvailable(stage)

    fun selectStage(stage: HBCharacterStage) {
        val ok = hbController.selectStage(stage)
        _notice.value = if (ok) {
            "Fase da HB: ${stage.label}."
        } else {
            "Mídia pendente para a fase ${stage.label}."
        }
    }

    fun selectBackground(option: RoomBackgroundOption) {
        hbController.selectBackground(option)
        _notice.value = "Ambiente: ${option.label}."
    }

    fun saveCustomBackground(uri: String) {
        hbController.setCustomBackgroundUri(uri)
        _notice.value = if (uri.isBlank()) "Fundo personalizado limpo." else "Foto da galeria aplicada ao Quarto."
    }

    fun markIntroSeen() {
        hbController.markIntroSeen()
    }

    fun replayIntro() {
        hbController.replayIntro()
    }

    fun resolveConfirmation(approved: Boolean) {
        viewModelScope.launch { orchestrator.resolveConfirmation(approved) }
    }

    fun stopAll() {
        orchestrator.stopCurrentExecution()
        voiceCoordinator.stopAllVoice()
    }

    fun clearNotice() {
        _notice.value = null
    }
}
