package com.novoagentehb.app.gabi

import android.content.Context
import com.novoagentehb.app.agent.orchestrator.OrchestratorTurnState
import com.novoagentehb.app.agent.orchestrator.TaskStatus
import com.novoagentehb.app.bridges.NotificationFeedService
import com.novoagentehb.app.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controlador único e compartilhado da personagem HB entre Home, Chat e Quarto da Gabi.
 * - Mantém uma única fonte de verdade (`state` e `roomUiState`) para voz, tarefas, rotina,
 *   alimentação, guarda-roupa real, toque na HB e Spotify real.
 */
@Singleton
class HBCharacterController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val assetLookup: AssetLookup by lazy {
        val rootFiles = runCatching { context.assets.list("")?.toSet() ?: emptySet() }.getOrDefault(emptySet())
        val animFolders = runCatching {
            context.assets.list("anim")
                ?.filter { dir ->
                    val inner = context.assets.list("anim/$dir")
                    !inner.isNullOrEmpty() && inner.any { it.endsWith(".png") || it.endsWith(".webp") }
                }
                ?.toSet()
                ?: emptySet()
        }.getOrDefault(emptySet())
        val avatarFolders = runCatching {
            context.assets.list("avatar")
                ?.filter { dir ->
                    val inner = context.assets.list("avatar/$dir")
                    !inner.isNullOrEmpty() && inner.any { it.endsWith(".webp") || it.endsWith(".png") }
                }
                ?.toSet()
                ?: emptySet()
        }.getOrDefault(emptySet())

        AssetLookup(
            hasAnimFolder = { folder -> folder in animFolders },
            hasAssetFile = { file ->
                if (file.contains('/')) {
                    runCatching { context.assets.open(file).use { true } }.getOrDefault(false)
                } else {
                    file in rootFiles
                }
            },
            hasAvatarFolder = { folder -> folder in avatarFolders }
        )
    }

    private var clockHourOverride: Int? = null
    private var transientResetJob: Job? = null
    private var tapCounter = 0

    private val tapLines = listOf(
        "Oi! Tô aqui no meu quarto 😊",
        "Quer conversar ou organizar alguma meta?",
        "Sempre pronta pra te ajudar!",
        "Toca no botão de roupa se quiser mudar meu visual!"
    )

    private val _inputs = MutableStateFlow(
        HBSignalInputs(
            hourOfDay = currentHour(),
            spotifyPermissionGranted = NotificationFeedService.isEnabled(context),
            nowMs = System.currentTimeMillis()
        )
    )

    private val _state = MutableStateFlow(HBStateResolver.resolve(_inputs.value, assetLookup).first)
    val state: StateFlow<HBState> = _state.asStateFlow()

    private val _roomUiState = MutableStateFlow(HBStateResolver.resolve(_inputs.value, assetLookup).second)
    val roomUiState: StateFlow<RoomUiState> = _roomUiState.asStateFlow()

    init {
        observePreferences()
        observeSpotify()
        startClockTicker()
    }

    private fun observePreferences() {
        scope.launch {
            if (prefs.quartoFirstVisitAt.first() == 0L) {
                prefs.setQuartoFirstVisitAt(System.currentTimeMillis())
            }
            if (prefs.quartoUnlockIntervalDays.first() == 0) {
                prefs.setQuartoUnlockIntervalDays((4..12).random())
            }
        }
        scope.launch {
            prefs.quartoSelectedOutfitId.collectLatest { id ->
                updateInputs { it.copy(selectedOutfit = HBWardrobeCatalog.findById(id)) }
            }
        }
        scope.launch {
            prefs.quartoBackgroundId.collectLatest { bgId ->
                updateInputs { it.copy(backgroundOption = RoomBackgroundOption.fromId(bgId)) }
            }
        }
        scope.launch {
            prefs.quartoCustomBackgroundUri.collectLatest { uri ->
                updateInputs { it.copy(customBackgroundUri = uri) }
            }
        }
        scope.launch {
            prefs.quartoLastCareAt.collectLatest { ts ->
                updateInputs { it.copy(careLastAt = ts) }
            }
        }
        scope.launch {
            prefs.quartoFirstVisitAt.collectLatest { ts ->
                updateInputs { it.copy(firstVisitAt = ts) }
            }
        }
        scope.launch {
            prefs.quartoUnlockIntervalDays.collectLatest { days ->
                updateInputs { it.copy(unlockIntervalDays = days) }
            }
        }
        scope.launch {
            prefs.quartoIntroSeen.collectLatest { seen ->
                updateInputs { it.copy(introSeen = seen) }
            }
        }
        scope.launch {
            prefs.quartoSelectedStageId.collectLatest { stageId ->
                updateInputs { it.copy(selectedStage = HBStageCatalog.findById(stageId)) }
            }
        }
    }

    private fun observeSpotify() {
        scope.launch {
            NotificationFeedService.spotifyPlaying.collectLatest { playing ->
                updateInputs {
                    it.copy(
                        isSpotifyPlaying = playing,
                        spotifyPermissionGranted = NotificationFeedService.isEnabled(context)
                    )
                }
            }
        }
    }

    private fun startClockTicker() {
        scope.launch {
            while (isActive) {
                delay(30_000L)
                refreshEnvironmentState()
            }
        }
    }

    fun refreshEnvironmentState() {
        NotificationFeedService.refreshActiveMediaSessions(context)
        updateInputs {
            it.copy(
                hourOfDay = currentHour(),
                spotifyPermissionGranted = NotificationFeedService.isEnabled(context),
                isSpotifyPlaying = NotificationFeedService.spotifyPlaying.value,
                nowMs = System.currentTimeMillis()
            )
        }
    }

    fun updateVoiceStatus(snapshot: VoiceStatusSnapshot) {
        updateInputs {
            it.copy(
                isListening = snapshot.isListening,
                isLiveListening = snapshot.isListening && snapshot.liveSessionState == LiveSessionState.ACTIVE,
                isSpeaking = snapshot.isSpeaking,
                nowMs = System.currentTimeMillis()
            )
        }
    }

    fun updateTurnState(turn: OrchestratorTurnState) {
        updateInputs {
            it.copy(
                taskStatus = turn.status,
                activeTaskDescription = turn.activeTaskDescription,
                nowMs = System.currentTimeMillis()
            )
        }
    }

    fun notifyTaskOutcome(
        success: Boolean,
        cancelled: Boolean,
        taskId: String,
        summary: String,
        errorCode: String? = null
    ) {
        val now = System.currentTimeMillis()
        val (act, cap, ttl) = when {
            cancelled -> Triple(HBActivity.Cancelled, "Ação interrompida", HBStateResolver.CANCELLED_REACTION_DURATION_MS)
            success -> Triple(HBActivity.Success(taskId, summary), "Pronto! Concluído", HBStateResolver.SUCCESS_REACTION_DURATION_MS)
            else -> Triple(
                HBActivity.Error(errorCode ?: "ERR", summary),
                "Ops: ${summary.take(48)}",
                HBStateResolver.ERROR_REACTION_DURATION_MS
            )
        }
        updateInputs {
            it.copy(
                taskStatus = TaskStatus.IDLE,
                activeTaskDescription = "",
                transientOutcome = TransientOutcome(act, cap, now + ttl),
                nowMs = now
            )
        }
    }

    fun feed() {
        val now = System.currentTimeMillis()
        scope.launch {
            prefs.setQuartoLastCareAt(now)
        }
        updateInputs {
            it.copy(
                careLastAt = now,
                eatingUntilMs = now + HBStateResolver.EATING_DURATION_MS,
                nowMs = now
            )
        }
    }

    fun onCharacterTapped() {
        val now = System.currentTimeMillis()
        val line = tapLines[tapCounter % tapLines.size]
        tapCounter++
        updateInputs {
            it.copy(
                tapUntilMs = now + HBStateResolver.TAP_REACTION_DURATION_MS,
                tapBubbleText = line,
                nowMs = now
            )
        }
    }

    /**
     * Seleciona um look no guarda-roupa ou retorna para "Rotina do dia" (`null`).
     * Bloqueia looks cuja arte não existe no pacote (`ASSET_PENDING`).
     */
    fun selectOutfit(outfit: WardrobeOutfit?): Boolean {
        if (outfit != null && !assetLookup.hasAssetFile(outfit.poseFile)) {
            return false
        }
        val now = System.currentTimeMillis()
        val resetStageToBase = outfit != null && _inputs.value.selectedStage != HBCharacterStage.YOUNG_CHILD
        scope.launch {
            prefs.setQuartoSelectedOutfitId(outfit?.id ?: "")
            if (resetStageToBase) {
                prefs.setQuartoSelectedStageId(HBCharacterStage.YOUNG_CHILD.id)
            }
        }
        updateInputs {
            it.copy(
                selectedOutfit = outfit,
                selectedStage = if (resetStageToBase) HBCharacterStage.YOUNG_CHILD else it.selectedStage,
                outfitChangeUntilMs = now + HBStateResolver.OUTFIT_CHANGE_DURATION_MS,
                outfitChangeLabel = outfit?.label ?: "Rotina do dia",
                nowMs = now
            )
        }
        return true
    }

    fun selectBackground(option: RoomBackgroundOption) {
        scope.launch {
            prefs.setQuartoBackgroundId(option.id)
        }
        updateInputs { it.copy(backgroundOption = option, nowMs = System.currentTimeMillis()) }
    }

    fun setCustomBackgroundUri(uri: String) {
        scope.launch {
            prefs.setQuartoCustomBackgroundUri(uri)
            if (uri.isNotBlank()) prefs.setQuartoBackgroundId(RoomBackgroundOption.CUSTOM_URI.id)
        }
        updateInputs {
            it.copy(
                customBackgroundUri = uri,
                backgroundOption = if (uri.isNotBlank()) RoomBackgroundOption.CUSTOM_URI else it.backgroundOption,
                nowMs = System.currentTimeMillis()
            )
        }
    }

    fun markIntroSeen() {
        scope.launch { prefs.setQuartoIntroSeen(true) }
        updateInputs { it.copy(introSeen = true, nowMs = System.currentTimeMillis()) }
    }

    fun replayIntro() {
        scope.launch { prefs.setQuartoIntroSeen(false) }
        updateInputs { it.copy(introSeen = false, nowMs = System.currentTimeMillis()) }
    }

    fun isOutfitAssetAvailable(outfit: WardrobeOutfit): Boolean =
        assetLookup.hasAssetFile(outfit.poseFile)

    fun isStageAvailable(stage: HBCharacterStage): Boolean =
        stage.assetVideoPath == null || assetLookup.hasAssetFile(stage.assetVideoPath)

    fun selectStage(stage: HBCharacterStage): Boolean {
        if (!isStageAvailable(stage)) {
            return false
        }
        val clearOutfit = stage != HBCharacterStage.YOUNG_CHILD && _inputs.value.selectedOutfit != null
        scope.launch {
            prefs.setQuartoSelectedStageId(stage.id)
            if (clearOutfit) {
                prefs.setQuartoSelectedOutfitId("")
            }
        }
        updateInputs {
            it.copy(
                selectedStage = stage,
                selectedOutfit = if (clearOutfit) null else it.selectedOutfit,
                nowMs = System.currentTimeMillis()
            )
        }
        return true
    }

    internal fun setClockHourOverrideForTest(hour: Int?) {
        clockHourOverride = hour
        refreshEnvironmentState()
    }

    private fun currentHour(): Int =
        clockHourOverride ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    private fun scheduleNextExpiry(inputs: HBSignalInputs) {
        val now = System.currentTimeMillis()
        val futureDeadlines = listOfNotNull(
            inputs.eatingUntilMs.takeIf { it > now },
            inputs.tapUntilMs.takeIf { it > now },
            inputs.outfitChangeUntilMs.takeIf { it > now },
            inputs.transientOutcome?.expiresAtMs?.takeIf { it > now }
        )
        transientResetJob?.cancel()
        val nextDeadline = futureDeadlines.minOrNull() ?: return
        val delayMs = (nextDeadline - now + 45L).coerceAtLeast(30L)
        transientResetJob = scope.launch {
            delay(delayMs)
            updateInputs { it.copy(nowMs = System.currentTimeMillis()) }
        }
    }

    private fun updateInputs(transform: (HBSignalInputs) -> HBSignalInputs) {
        var latestInputs: HBSignalInputs? = null
        _inputs.update { prev ->
            val next = transform(prev)
            latestInputs = next
            val (hb, room) = HBStateResolver.resolve(next, assetLookup)
            _state.value = hb
            _roomUiState.value = room
            next
        }
        latestInputs?.let { scheduleNextExpiry(it) }
    }
}
