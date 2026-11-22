package com.novoagentehb.app.gabi

import com.novoagentehb.app.agent.orchestrator.TaskStatus

/**
 * Contrato de estado da personagem HB compartilhado por Home, Chat e Quarto da Gabi.
 * Completamente livre de dependências Android para permitir testes unitários determinísticos em JVM.
 */
sealed interface HBActivity {
    data object Idle : HBActivity
    data object Listening : HBActivity
    data object Thinking : HBActivity
    data object Speaking : HBActivity
    data class Working(val taskId: String, val summary: String) : HBActivity
    data class Success(val taskId: String, val summary: String) : HBActivity
    data class Error(val code: String, val summary: String) : HBActivity
    data object Cancelled : HBActivity
    data object Eating : HBActivity
    data object Reading : HBActivity
    data object AtComputer : HBActivity
    data object Sleeping : HBActivity
    data object MusicReaction : HBActivity
    data object TapReaction : HBActivity
    data class OutfitChange(val outfitLabel: String) : HBActivity
}

/**
 * Estágios etários / fases da personagem HB seguindo a convenção de presets/estágios
 * com seleção manual explícita pelo usuário, sem inventar regras de evolução automática.
 */
enum class HBCharacterStage(
    val id: String,
    val label: String,
    val ageGroupLabel: String,
    val description: String,
    val assetVideoPath: String?,
    val previewPngPath: String,
    val avatarCirclePngPath: String,
    val provenanceNote: String
) {
    YOUNG_CHILD(
        id = "young_child",
        label = "Infância (Padrão)",
        ageGroupLabel = "Base original",
        description = "Personagem HB padrão com guarda-roupa completo e sequências transparentes.",
        assetVideoPath = null,
        previewPngPath = "hb-stages/young_child/preview.png",
        avatarCirclePngPath = "hb-stages/young_child/avatar_circle.png",
        provenanceNote = "voz-latencia (MIT em android/LICENSE)"
    ),
    PRE_ADOLESCENT(
        id = "pre_adolescent",
        label = "Pré-adolescente (idade1)",
        ageGroupLabel = "Pré-adolescente",
        description = "HB em fase pré-adolescente no quarto (preserva esta fase em todos os estados).",
        assetVideoPath = "hb-stages/pre_adolescent/idle.mp4",
        previewPngPath = "hb-stages/pre_adolescent/preview.png",
        avatarCirclePngPath = "hb-stages/pre_adolescent/avatar_circle.png",
        provenanceNote = "Drive Avatar: idade1.mp4 (240q, 720x1280, 24fps)"
    ),
    ADOLESCENT(
        id = "adolescent",
        label = "Adolescente (idade2)",
        ageGroupLabel = "Adolescente",
        description = "HB em fase adolescente no quarto (preserva esta fase em todos os estados).",
        assetVideoPath = "hb-stages/adolescent/idle.mp4",
        previewPngPath = "hb-stages/adolescent/preview.png",
        avatarCirclePngPath = "hb-stages/adolescent/avatar_circle.png",
        provenanceNote = "Drive Avatar: idade2.mp4 (240q, 720x1280, 24fps)"
    );

    companion object {
        fun fromId(id: String): HBCharacterStage =
            entries.firstOrNull { it.id == id } ?: YOUNG_CHILD
    }
}

object HBStageCatalog {
    val all: List<HBCharacterStage> = HBCharacterStage.entries

    fun findById(id: String): HBCharacterStage = HBCharacterStage.fromId(id)
}

enum class RoomBackgroundOption(val id: String, val label: String, val assetPath: String) {
    ROOM_WEBP("room_webp", "Quarto Clássico", "avatar/room.webp"),
    ROOM_PNG("room_png", "Quarto Iluminado", "room-bg.png"),
    ROOM_MP4("room_mp4", "Vídeo Ambiente (fundo)", "res/raw/room_bg_loop.mp4"),
    CUSTOM_URI("custom_uri", "Foto da Galeria", "");

    companion object {
        fun fromId(id: String): RoomBackgroundOption =
            entries.firstOrNull { it.id == id } ?: ROOM_WEBP
    }
}

data class WardrobeOutfit(
    val id: String,
    val label: String,
    val poseFile: String,
    val animFolder: String,
    val unlockIndex: Int,
    val verifiedAppearanceNote: String = "",
    val missingAssetNote: String? = null
)

object HBWardrobeCatalog {
    val all: List<WardrobeOutfit> = listOf(
        WardrobeOutfit(
            id = "vestido_vermelho",
            label = "Vestido Vermelho",
            poseFile = "gabi-idle.png",
            animFolder = "home_idle",
            unlockIndex = 0,
            verifiedAppearanceNote = "Vestido vermelho sem mangas e botas pretas (gabi-idle.png / home_idle)"
        ),
        WardrobeOutfit(
            id = "vestido_lilas",
            label = "Vestido Lilás · Leitura",
            poseFile = "gabi-ler.png",
            animFolder = "scene_ler",
            unlockIndex = 0,
            verifiedAppearanceNote = "Vestido lilás segurando livro lilás e botas pretas (gabi-ler.png)"
        ),
        WardrobeOutfit(
            id = "sueter_computador",
            label = "Suéter Mostarda · Estudo",
            poseFile = "gabi-computador.png",
            animFolder = "scene_computador",
            unlockIndex = 0,
            verifiedAppearanceNote = "Suéter mostarda, calça preta e notebook (gabi-computador.png / scene_computador)"
        ),
        WardrobeOutfit(
            id = "pijama_estrelas",
            label = "Pijama Estrelado",
            poseFile = "gabi-dormir.png",
            animFolder = "scene_dormir",
            unlockIndex = 0,
            verifiedAppearanceNote = "Pijama azul estampado com estrelas, pantufas e travesseiro (gabi-dormir.png)"
        ),
        WardrobeOutfit(
            id = "moletom_preto",
            label = "Moletom e Calça Preta",
            poseFile = "gabi-look-moletom.png",
            animFolder = "scene_outfit_moletom_preto",
            unlockIndex = 1,
            missingAssetNote = "Arte pendente (gabi-look-moletom.png)"
        ),
        WardrobeOutfit(
            id = "look_festa",
            label = "Look de Festa",
            poseFile = "gabi-look-festa.png",
            animFolder = "scene_outfit_look_festa",
            unlockIndex = 2,
            missingAssetNote = "Arte pendente (gabi-look-festa.png)"
        ),
        WardrobeOutfit(
            id = "look_pijama_passeio",
            label = "Pijama de Passeio",
            poseFile = "gabi-look-pijama.png",
            animFolder = "scene_outfit_look_pijama_passeio",
            unlockIndex = 3,
            missingAssetNote = "Arte pendente (gabi-look-pijama.png)"
        )
    )

    fun findById(id: String): WardrobeOutfit? =
        if (id.isBlank()) null else all.firstOrNull { it.id == id }

    fun unlockedCount(firstVisitAt: Long, intervalDays: Int, nowMillis: Long): Int {
        if (firstVisitAt <= 0L || intervalDays <= 0) return 4
        val daysPassed = ((nowMillis - firstVisitAt).coerceAtLeast(0L)) / DAY_MS
        val extra = (daysPassed / intervalDays).toInt()
        return (4 + extra).coerceAtMost(all.size)
    }

    fun daysUntilNextUnlock(firstVisitAt: Long, intervalDays: Int, unlockedCount: Int, nowMillis: Long): Int {
        if (firstVisitAt <= 0L || intervalDays <= 0 || unlockedCount >= all.size) return 0
        val daysPassed = ((nowMillis - firstVisitAt).coerceAtLeast(0L)) / DAY_MS
        val intoInterval = daysPassed % intervalDays
        return (intervalDays - intoInterval).toInt()
    }

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}

enum class AvatarState(val assetFolder: String) {
    IDLE("idle"),
    LISTENING("listening"),
    THINKING("thinking"),
    TALKING("talking")
}

fun resolveAvatarState(listening: Boolean, speaking: Boolean, processing: Boolean): AvatarState = when {
    speaking -> AvatarState.TALKING
    listening -> AvatarState.LISTENING
    processing -> AvatarState.THINKING
    else -> AvatarState.IDLE
}

sealed class HBVisualSource {
    abstract val fallbackPng: String

    data class AvatarWebp(val folder: String, override val fallbackPng: String) : HBVisualSource()
    data class FramePngSequence(val folder: String, override val fallbackPng: String, val fps: Int = 18) : HBVisualSource()
    data class StaticPose(val assetFile: String) : HBVisualSource() {
        override val fallbackPng: String get() = assetFile
    }
    data class FullSceneStageVideo(
        val stage: HBCharacterStage,
        val assetVideoPath: String,
        override val fallbackPng: String
    ) : HBVisualSource()
}

data class AssetLookup(
    val hasAnimFolder: (String) -> Boolean,
    val hasAssetFile: (String) -> Boolean,
    val hasAvatarFolder: (String) -> Boolean = { true }
)

data class TransientOutcome(
    val activity: HBActivity,
    val caption: String,
    val expiresAtMs: Long
)

enum class HBMotionTruth(
    val badgeLabel: String,
    val isDedicatedStateAnimation: Boolean
) {
    DEDICATED_STATE_ANIMATION("Animação do estado presente", true),
    REUSED_APPROVED_MOVEMENT("Movimento reaproveitado (sem clipe dedicado)", false),
    STAGE_PRESERVED_IDLE_ONLY("Idade preservada · vídeo idle provisório", false),
    STATIC_POSE_BREATHING("Pose transparente estática com respiração", false)
}

enum class RoomBackgroundCompatibility(val disclosureLabel: String) {
    INDEPENDENT_TRANSPARENT_LAYERS("Fundo independente ativo atrás da HB transparente"),
    OPAQUE_FULL_SCENE_BLOCKS_CUSTOM_BG("Cena completa opaca · fundo escolhido preservado para Infância")
}

data class HBSignalInputs(
    val hourOfDay: Int = 12,
    val isListening: Boolean = false,
    val isLiveListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val taskStatus: TaskStatus = TaskStatus.IDLE,
    val activeTaskId: String = "",
    val activeTaskDescription: String = "",
    val eatingUntilMs: Long = 0L,
    val tapUntilMs: Long = 0L,
    val tapBubbleText: String = "",
    val outfitChangeUntilMs: Long = 0L,
    val outfitChangeLabel: String = "",
    val transientOutcome: TransientOutcome? = null,
    val selectedOutfit: WardrobeOutfit? = null,
    val selectedStage: HBCharacterStage = HBCharacterStage.YOUNG_CHILD,
    val backgroundOption: RoomBackgroundOption = RoomBackgroundOption.ROOM_WEBP,
    val customBackgroundUri: String = "",
    val careLastAt: Long = 0L,
    val firstVisitAt: Long = 0L,
    val unlockIntervalDays: Int = 7,
    val isSpotifyPlaying: Boolean = false,
    val spotifyPermissionGranted: Boolean = false,
    val introSeen: Boolean = false,
    val nowMs: Long = 0L
)

data class HBState(
    val activity: HBActivity = HBActivity.Idle,
    val stage: HBCharacterStage = HBCharacterStage.YOUNG_CHILD,
    val visualSource: HBVisualSource = HBVisualSource.FramePngSequence("home_idle", "gabi-idle.png"),
    val avatarState: AvatarState = AvatarState.IDLE,
    val caption: String = "Por aqui com você",
    val pendingAsset: String? = null,
    val catalogStateKey: String = "idle",
    val isStagePreserved: Boolean = true,
    val hasDedicatedStateAnimation: Boolean = true,
    val motionTruth: HBMotionTruth = HBMotionTruth.DEDICATED_STATE_ANIMATION,
    val isBusy: Boolean = false,
    val canStopTask: Boolean = false,
    val activeTaskLabel: String = "",
    val bubbleText: String? = null,
    val isSpotifyPlaying: Boolean = false,
    val spotifyPermissionGranted: Boolean = false
)

data class RoomUiState(
    val background: RoomBackgroundOption = RoomBackgroundOption.ROOM_WEBP,
    val customBackgroundUri: String = "",
    val backgroundCompatibility: RoomBackgroundCompatibility = RoomBackgroundCompatibility.INDEPENDENT_TRANSPARENT_LAYERS,
    val stage: HBCharacterStage = HBCharacterStage.YOUNG_CHILD,
    val outfit: WardrobeOutfit? = null,
    val activity: HBActivity = HBActivity.Idle,
    val caption: String = "",
    val careLastAt: Long = 0L,
    val isSpotifyPlaying: Boolean = false,
    val spotifyPermissionGranted: Boolean = false,
    val introSeen: Boolean = false,
    val animationSource: HBVisualSource = HBVisualSource.StaticPose("gabi-idle.png"),
    val pendingAsset: String? = null,
    val catalogStateKey: String = "idle",
    val isStagePreserved: Boolean = true,
    val hasDedicatedStateAnimation: Boolean = true,
    val motionTruth: HBMotionTruth = HBMotionTruth.DEDICATED_STATE_ANIMATION,
    val unlockedCount: Int = 4,
    val nextUnlockInDays: Int = 0,
    val availableOutfitIds: Set<String> = emptySet(),
    val tapBubbleText: String? = null,
    val activeTaskLabel: String = "",
    val canStopTask: Boolean = false
)

/**
 * Resolvedor determinístico com prioridade estrita:
 * Cancelled > Error > Speaking > Listening > Thinking > Working > Success > Eating > TapReaction > OutfitChange > StagePresence(idade1/idade2) > WearingOutfit(+Music) > Routine(+Music).
 * Toda reação prioriza animação aprovada real em `HBAnimationCatalog` antes de recorrer a fallback.
 */
object HBStateResolver {

    const val EATING_DURATION_MS = 3_000L
    const val TAP_REACTION_DURATION_MS = 1_800L
    const val SUCCESS_REACTION_DURATION_MS = 2_600L
    const val ERROR_REACTION_DURATION_MS = 3_400L
    const val CANCELLED_REACTION_DURATION_MS = 1_800L
    const val OUTFIT_CHANGE_DURATION_MS = 900L

    fun routineForHour(hour: Int, assets: AssetLookup): Triple<HBActivity, String, HBVisualSource> {
        val (act, label, pose, anim) = when (hour) {
            in 6..10 -> Quad(HBActivity.Eating, "Tomando café da manhã", "gabi-comer.png", "scene_comer")
            in 11..16 -> Quad(HBActivity.AtComputer, "Mexendo no computador", "gabi-computador.png", "scene_computador")
            in 17..20 -> Quad(HBActivity.Reading, "Lendo um livro", "gabi-ler.png", "scene_ler")
            else -> Quad(HBActivity.Sleeping, "Quase dormindo", "gabi-dormir.png", "scene_dormir")
        }
        val source = pickBestRoomVisual(animFolder = anim, fallbackPng = pose, assets = assets)
        return Triple(act, label, source)
    }

    private fun pickBestRoomVisual(
        animFolder: String,
        fallbackPng: String,
        assets: AssetLookup
    ): HBVisualSource {
        return when {
            assets.hasAnimFolder(animFolder) -> HBVisualSource.FramePngSequence(animFolder, fallbackPng)
            assets.hasAssetFile(fallbackPng) -> HBVisualSource.StaticPose(fallbackPng)
            else -> HBVisualSource.StaticPose("gabi-idle.png")
        }
    }

    private fun pickBestReactionVisual(
        dedicatedFolder: String,
        reusedAvatarFolder: String?,
        reusedAnimFolder: String?,
        fallbackPng: String,
        assets: AssetLookup
    ): Pair<HBVisualSource, String?> {
        if (assets.hasAnimFolder(dedicatedFolder)) {
            return HBVisualSource.FramePngSequence(dedicatedFolder, fallbackPng) to null
        }
        val pending = "ASSET_PENDING: anim/$dedicatedFolder/"
        val reused = when {
            reusedAvatarFolder != null && assets.hasAvatarFolder(reusedAvatarFolder) ->
                HBVisualSource.AvatarWebp(reusedAvatarFolder, fallbackPng)
            reusedAnimFolder != null && assets.hasAnimFolder(reusedAnimFolder) ->
                HBVisualSource.FramePngSequence(reusedAnimFolder, fallbackPng)
            assets.hasAssetFile(fallbackPng) -> HBVisualSource.StaticPose(fallbackPng)
            else -> HBVisualSource.StaticPose("gabi-idle.png")
        }
        return reused to pending
    }

    private fun computePendingNote(
        animFolder: String?,
        poseFile: String?,
        assets: AssetLookup
    ): String? {
        if (poseFile != null && !assets.hasAssetFile(poseFile)) {
            return "ASSET_PENDING: $poseFile"
        }
        if (animFolder != null && !assets.hasAnimFolder(animFolder)) {
            return "ASSET_PENDING: anim/$animFolder/ (usando pose estática com respiração)"
        }
        return null
    }

    fun resolve(inputs: HBSignalInputs, assets: AssetLookup): Pair<HBState, RoomUiState> {
        val now = inputs.nowMs
        val activeTransient = inputs.transientOutcome?.takeIf { now < it.expiresAtMs }
        val eatingActive = now < inputs.eatingUntilMs
        val tapActive = now < inputs.tapUntilMs
        val outfitChangeActive = now < inputs.outfitChangeUntilMs

        val isThinking = inputs.taskStatus == TaskStatus.PLANNING ||
            inputs.taskStatus == TaskStatus.AWAITING_CONFIRMATION
        val isWorking = inputs.taskStatus == TaskStatus.EXECUTING ||
            inputs.taskStatus == TaskStatus.VERIFYING

        val availableOutfitIds = HBWardrobeCatalog.all
            .filter { assets.hasAssetFile(it.poseFile) }
            .map { it.id }
            .toSet()

        val validSelectedOutfit = inputs.selectedOutfit?.takeIf { it.id in availableOutfitIds }
        val stageVideoAvailable = inputs.selectedStage.assetVideoPath != null &&
            assets.hasAssetFile(inputs.selectedStage.assetVideoPath)
        val effectiveStage = if (inputs.selectedStage == HBCharacterStage.YOUNG_CHILD || stageVideoAvailable) {
            inputs.selectedStage
        } else {
            HBCharacterStage.YOUNG_CHILD
        }

        val unlocked = HBWardrobeCatalog.unlockedCount(inputs.firstVisitAt, inputs.unlockIntervalDays, now)
        val nextIn = HBWardrobeCatalog.daysUntilNextUnlock(inputs.firstVisitAt, inputs.unlockIntervalDays, unlocked, now)

        val resolvedActivity: HBActivity
        val caption: String
        val visualSource: HBVisualSource
        var pendingAsset: String? = null
        var bubble: String? = null
        val catalogStateKey: String
        val hasDedicatedStateAnimation: Boolean
        val motionTruth: HBMotionTruth
        val isStagePreserved = effectiveStage == inputs.selectedStage
        val avatarState = resolveAvatarState(
            listening = inputs.isListening,
            speaking = inputs.isSpeaking,
            processing = isThinking || isWorking
        )

        val isComputerContext = validSelectedOutfit?.id == "sueter_computador" ||
            (validSelectedOutfit == null && inputs.hourOfDay in 11..16)

        if (effectiveStage != HBCharacterStage.YOUNG_CHILD && effectiveStage.assetVideoPath != null) {
            val stateSlug: String
            when {
                activeTransient?.activity is HBActivity.Cancelled -> {
                    resolvedActivity = activeTransient.activity
                    caption = activeTransient.caption
                    bubble = activeTransient.caption
                    stateSlug = "cancelled"
                }
                activeTransient?.activity is HBActivity.Error -> {
                    resolvedActivity = activeTransient.activity
                    caption = activeTransient.caption
                    bubble = activeTransient.caption
                    stateSlug = "error"
                }
                inputs.isSpeaking -> {
                    resolvedActivity = HBActivity.Speaking
                    caption = "${effectiveStage.ageGroupLabel} · Falando com você…"
                    stateSlug = "talking"
                }
                inputs.isListening -> {
                    resolvedActivity = HBActivity.Listening
                    caption = if (inputs.isLiveListening) {
                        "${effectiveStage.ageGroupLabel} · Ouvindo você (Live)…"
                    } else {
                        "${effectiveStage.ageGroupLabel} · Ouvindo você…"
                    }
                    stateSlug = if (inputs.isLiveListening) "listening_intent" else "listening"
                }
                isThinking -> {
                    resolvedActivity = HBActivity.Thinking
                    caption = inputs.activeTaskDescription.ifBlank { "${effectiveStage.ageGroupLabel} · Pensando…" }
                    stateSlug = "thinking"
                }
                isWorking -> {
                    resolvedActivity = HBActivity.Working(inputs.activeTaskId, inputs.activeTaskDescription)
                    caption = inputs.activeTaskDescription.ifBlank { "${effectiveStage.ageGroupLabel} · Executando tarefa…" }
                    stateSlug = "working"
                }
                activeTransient?.activity is HBActivity.Success -> {
                    resolvedActivity = activeTransient.activity
                    caption = activeTransient.caption
                    bubble = activeTransient.caption
                    stateSlug = "success"
                }
                eatingActive -> {
                    resolvedActivity = HBActivity.Eating
                    caption = "${effectiveStage.ageGroupLabel} · Nham nham! 😋"
                    bubble = "Nham nham! 😋"
                    stateSlug = "eating"
                }
                tapActive -> {
                    resolvedActivity = HBActivity.TapReaction
                    caption = inputs.tapBubbleText.ifBlank { "Oi! Tô aqui com você!" }
                    bubble = caption
                    stateSlug = "tap_reaction"
                }
                outfitChangeActive -> {
                    val targetLabel = inputs.outfitChangeLabel.ifBlank { validSelectedOutfit?.label ?: effectiveStage.label }
                    resolvedActivity = HBActivity.OutfitChange(targetLabel)
                    caption = "Fase ativa: $targetLabel"
                    bubble = caption
                    stateSlug = "outfit_change"
                }
                inputs.isSpotifyPlaying -> {
                    resolvedActivity = HBActivity.MusicReaction
                    caption = "${effectiveStage.label} · Curtindo música 🎵"
                    stateSlug = "music_reaction"
                }
                else -> {
                    resolvedActivity = HBActivity.Idle
                    caption = "${effectiveStage.label} · Quarto da Gabi"
                    stateSlug = "idle"
                }
            }

            val dedicatedStageAnim = "stage_${effectiveStage.id}_$stateSlug"
            catalogStateKey = dedicatedStageAnim
            if (assets.hasAnimFolder(dedicatedStageAnim)) {
                visualSource = HBVisualSource.FramePngSequence(dedicatedStageAnim, effectiveStage.previewPngPath)
                pendingAsset = null
                hasDedicatedStateAnimation = true
                motionTruth = HBMotionTruth.DEDICATED_STATE_ANIMATION
            } else {
                visualSource = HBVisualSource.FullSceneStageVideo(
                    stage = effectiveStage,
                    assetVideoPath = effectiveStage.assetVideoPath,
                    fallbackPng = effectiveStage.previewPngPath
                )
                hasDedicatedStateAnimation = stateSlug == "idle"
                motionTruth = if (hasDedicatedStateAnimation) {
                    HBMotionTruth.DEDICATED_STATE_ANIMATION
                } else {
                    HBMotionTruth.STAGE_PRESERVED_IDLE_ONLY
                }
                pendingAsset = if (stateSlug == "idle") {
                    "ASSET_PENDING: recorte alfa transparente de ${effectiveStage.id} (exibindo idle.mp4 opaco sem retângulo)"
                } else {
                    "ASSET_PENDING: anim/stage_${effectiveStage.id}_$stateSlug/ (idade ${effectiveStage.ageGroupLabel} preservada via idle.mp4)"
                }
            }
        } else {
            when {
                activeTransient?.activity is HBActivity.Cancelled -> {
                    resolvedActivity = activeTransient.activity
                    caption = activeTransient.caption
                    val (v, p) = pickBestReactionVisual("scene_cancelled", "idle", "home_idle", "gabi-idle.png", assets)
                    visualSource = v
                    pendingAsset = p
                    bubble = activeTransient.caption
                    catalogStateKey = "scene_cancelled"
                    hasDedicatedStateAnimation = p == null
                    motionTruth = if (hasDedicatedStateAnimation) HBMotionTruth.DEDICATED_STATE_ANIMATION else HBMotionTruth.REUSED_APPROVED_MOVEMENT
                }
                activeTransient?.activity is HBActivity.Error -> {
                    resolvedActivity = activeTransient.activity
                    caption = activeTransient.caption
                    val (v, p) = pickBestReactionVisual("scene_error", "thinking", null, "gabi-idle.png", assets)
                    visualSource = v
                    pendingAsset = p
                    bubble = activeTransient.caption
                    catalogStateKey = "scene_error"
                    hasDedicatedStateAnimation = p == null
                    motionTruth = if (hasDedicatedStateAnimation) HBMotionTruth.DEDICATED_STATE_ANIMATION else HBMotionTruth.REUSED_APPROVED_MOVEMENT
                }
                inputs.isSpeaking -> {
                    resolvedActivity = HBActivity.Speaking
                    caption = "Falando com você…"
                    val hasTalking = assets.hasAvatarFolder("talking")
                    visualSource = if (hasTalking) {
                        HBVisualSource.AvatarWebp("talking", "gabi-talking.png")
                    } else {
                        HBVisualSource.StaticPose("gabi-talking.png")
                    }
                    catalogStateKey = "talking"
                    hasDedicatedStateAnimation = hasTalking
                    motionTruth = if (hasTalking) HBMotionTruth.DEDICATED_STATE_ANIMATION else HBMotionTruth.STATIC_POSE_BREATHING
                }
                inputs.isListening -> {
                    resolvedActivity = HBActivity.Listening
                    caption = if (inputs.isLiveListening) "Ouvindo você (Live)…" else "Ouvindo você…"
                    visualSource = when {
                        inputs.isLiveListening && assets.hasAnimFolder("scene_listening_intent") ->
                            HBVisualSource.FramePngSequence("scene_listening_intent", "gabi-listening.png", fps = 12)
                        assets.hasAvatarFolder("listening") ->
                            HBVisualSource.AvatarWebp("listening", "gabi-listening.png")
                        assets.hasAnimFolder("scene_listening_intent") ->
                            HBVisualSource.FramePngSequence("scene_listening_intent", "gabi-listening.png", fps = 12)
                        else -> HBVisualSource.StaticPose("gabi-listening.png")
                    }
                    catalogStateKey = if (visualSource is HBVisualSource.FramePngSequence) "scene_listening_intent" else "listening"
                    hasDedicatedStateAnimation = visualSource !is HBVisualSource.StaticPose
                    motionTruth = if (hasDedicatedStateAnimation) HBMotionTruth.DEDICATED_STATE_ANIMATION else HBMotionTruth.STATIC_POSE_BREATHING
                }
                isThinking -> {
                    resolvedActivity = HBActivity.Thinking
                    caption = inputs.activeTaskDescription.ifBlank { "Pensando…" }
                    visualSource = when {
                        isComputerContext && assets.hasAnimFolder("scene_computador_pensando") ->
                            HBVisualSource.FramePngSequence("scene_computador_pensando", "gabi-computador.png", fps = 16)
                        assets.hasAvatarFolder("thinking") ->
                            HBVisualSource.AvatarWebp("thinking", "gabi-idle.png")
                        assets.hasAnimFolder("scene_computador_pensando") ->
                            HBVisualSource.FramePngSequence("scene_computador_pensando", "gabi-computador.png", fps = 16)
                        else -> HBVisualSource.StaticPose("gabi-idle.png")
                    }
                    catalogStateKey = if (visualSource is HBVisualSource.FramePngSequence) "scene_computador_pensando" else "thinking"
                    hasDedicatedStateAnimation = visualSource !is HBVisualSource.StaticPose
                    motionTruth = if (hasDedicatedStateAnimation) HBMotionTruth.DEDICATED_STATE_ANIMATION else HBMotionTruth.STATIC_POSE_BREATHING
                }
                isWorking -> {
                    resolvedActivity = HBActivity.Working(inputs.activeTaskId, inputs.activeTaskDescription)
                    caption = inputs.activeTaskDescription.ifBlank { "Executando tarefa…" }
                    visualSource = pickBestRoomVisual("scene_computador", "gabi-computador.png", assets)
                    catalogStateKey = "scene_computador"
                    hasDedicatedStateAnimation = visualSource !is HBVisualSource.StaticPose
                    motionTruth = if (hasDedicatedStateAnimation) HBMotionTruth.DEDICATED_STATE_ANIMATION else HBMotionTruth.STATIC_POSE_BREATHING
                }
                activeTransient?.activity is HBActivity.Success -> {
                    resolvedActivity = activeTransient.activity
                    caption = activeTransient.caption
                    val (v, p) = pickBestReactionVisual("scene_success", null, "home_idle", "gabi-idle.png", assets)
                    visualSource = v
                    pendingAsset = p
                    bubble = activeTransient.caption
                    catalogStateKey = "scene_success"
                    hasDedicatedStateAnimation = p == null
                    motionTruth = if (hasDedicatedStateAnimation) HBMotionTruth.DEDICATED_STATE_ANIMATION else HBMotionTruth.REUSED_APPROVED_MOVEMENT
                }
                eatingActive -> {
                    resolvedActivity = HBActivity.Eating
                    caption = "Nham nham! 😋"
                    visualSource = pickBestRoomVisual("scene_comendo_reacao", "gabi-comer.png", assets)
                    pendingAsset = computePendingNote("scene_comendo_reacao", "gabi-comer.png", assets)
                    bubble = "Nham nham! 😋"
                    catalogStateKey = "scene_comendo_reacao"
                    hasDedicatedStateAnimation = visualSource !is HBVisualSource.StaticPose
                    motionTruth = if (hasDedicatedStateAnimation) HBMotionTruth.DEDICATED_STATE_ANIMATION else HBMotionTruth.STATIC_POSE_BREATHING
                }
                tapActive -> {
                    resolvedActivity = HBActivity.TapReaction
                    caption = inputs.tapBubbleText.ifBlank { "Oi! Tô aqui com você!" }
                    val (v, p) = pickBestReactionVisual("scene_tap_reaction", "talking", null, "gabi-talking.png", assets)
                    visualSource = v
                    pendingAsset = p
                    bubble = caption
                    catalogStateKey = "scene_tap_reaction"
                    hasDedicatedStateAnimation = p == null
                    motionTruth = if (hasDedicatedStateAnimation) HBMotionTruth.DEDICATED_STATE_ANIMATION else HBMotionTruth.REUSED_APPROVED_MOVEMENT
                }
                outfitChangeActive -> {
                    val targetLabel = inputs.outfitChangeLabel.ifBlank { validSelectedOutfit?.label ?: "Rotina do dia" }
                    resolvedActivity = HBActivity.OutfitChange(targetLabel)
                    caption = "Trocando para: $targetLabel"
                    val poseFallback = validSelectedOutfit?.poseFile ?: "gabi-idle.png"
                    val (v, p) = pickBestReactionVisual("scene_outfit_change", null, "home_idle", poseFallback, assets)
                    visualSource = v
                    pendingAsset = p
                    bubble = "Look alterado: $targetLabel"
                    catalogStateKey = "scene_outfit_change"
                    hasDedicatedStateAnimation = p == null
                    motionTruth = if (hasDedicatedStateAnimation) HBMotionTruth.DEDICATED_STATE_ANIMATION else HBMotionTruth.REUSED_APPROVED_MOVEMENT
                }
                inputs.selectedStage != HBCharacterStage.YOUNG_CHILD && !stageVideoAvailable -> {
                    val (rAct, rLabel, rSource) = routineForHour(inputs.hourOfDay, assets)
                    resolvedActivity = rAct
                    caption = "$rLabel (fase pendente)"
                    visualSource = rSource
                    pendingAsset = "ASSET_PENDING: ${inputs.selectedStage.assetVideoPath}"
                    catalogStateKey = "stage_pending_${inputs.selectedStage.id}"
                    hasDedicatedStateAnimation = false
                    motionTruth = HBMotionTruth.REUSED_APPROVED_MOVEMENT
                }
                validSelectedOutfit != null -> {
                    resolvedActivity = if (inputs.isSpotifyPlaying) HBActivity.MusicReaction else HBActivity.Idle
                    caption = if (inputs.isSpotifyPlaying) {
                        "Usando: ${validSelectedOutfit.label} · Curtindo música 🎵"
                    } else {
                        "Usando: ${validSelectedOutfit.label}"
                    }
                    visualSource = pickBestRoomVisual(validSelectedOutfit.animFolder, validSelectedOutfit.poseFile, assets)
                    pendingAsset = computePendingNote(validSelectedOutfit.animFolder, validSelectedOutfit.poseFile, assets)
                    catalogStateKey = validSelectedOutfit.animFolder
                    hasDedicatedStateAnimation = visualSource !is HBVisualSource.StaticPose && !inputs.isSpotifyPlaying
                    motionTruth = when {
                        visualSource is HBVisualSource.StaticPose -> HBMotionTruth.STATIC_POSE_BREATHING
                        inputs.isSpotifyPlaying -> HBMotionTruth.REUSED_APPROVED_MOVEMENT
                        else -> HBMotionTruth.DEDICATED_STATE_ANIMATION
                    }
                }
                inputs.selectedOutfit != null -> {
                    val (rAct, rLabel, rSource) = routineForHour(inputs.hourOfDay, assets)
                    resolvedActivity = rAct
                    caption = "$rLabel (look pendente)"
                    visualSource = rSource
                    pendingAsset = "ASSET_PENDING: ${inputs.selectedOutfit.poseFile}"
                    catalogStateKey = "outfit_pending_${inputs.selectedOutfit.id}"
                    hasDedicatedStateAnimation = false
                    motionTruth = if (rSource is HBVisualSource.StaticPose) HBMotionTruth.STATIC_POSE_BREATHING else HBMotionTruth.REUSED_APPROVED_MOVEMENT
                }
                else -> {
                    val (rAct, rLabel, rSource) = routineForHour(inputs.hourOfDay, assets)
                    resolvedActivity = if (inputs.isSpotifyPlaying && rAct != HBActivity.Sleeping) {
                        HBActivity.MusicReaction
                    } else {
                        rAct
                    }
                    caption = if (inputs.isSpotifyPlaying && rAct != HBActivity.Sleeping) {
                        "$rLabel · Curtindo música 🎵"
                    } else {
                        rLabel
                    }
                    visualSource = rSource
                    val expectedFolder = when (inputs.hourOfDay) {
                        in 6..10 -> "scene_comer"
                        in 11..16 -> "scene_computador"
                        in 17..20 -> "scene_ler"
                        else -> "scene_dormir"
                    }
                    pendingAsset = computePendingNote(expectedFolder, rSource.fallbackPng, assets)
                    catalogStateKey = expectedFolder
                    hasDedicatedStateAnimation = visualSource !is HBVisualSource.StaticPose && !inputs.isSpotifyPlaying
                    motionTruth = when {
                        visualSource is HBVisualSource.StaticPose -> HBMotionTruth.STATIC_POSE_BREATHING
                        inputs.isSpotifyPlaying -> HBMotionTruth.REUSED_APPROVED_MOVEMENT
                        else -> HBMotionTruth.DEDICATED_STATE_ANIMATION
                    }
                }
            }
        }

        val backgroundCompatibility = if (visualSource is HBVisualSource.FullSceneStageVideo) {
            RoomBackgroundCompatibility.OPAQUE_FULL_SCENE_BLOCKS_CUSTOM_BG
        } else {
            RoomBackgroundCompatibility.INDEPENDENT_TRANSPARENT_LAYERS
        }

        val hbState = HBState(
            activity = resolvedActivity,
            stage = effectiveStage,
            visualSource = visualSource,
            avatarState = avatarState,
            caption = caption,
            pendingAsset = pendingAsset,
            catalogStateKey = catalogStateKey,
            isStagePreserved = isStagePreserved,
            hasDedicatedStateAnimation = hasDedicatedStateAnimation,
            motionTruth = motionTruth,
            isBusy = isThinking || isWorking,
            canStopTask = isThinking || isWorking,
            activeTaskLabel = inputs.activeTaskDescription,
            bubbleText = bubble,
            isSpotifyPlaying = inputs.isSpotifyPlaying,
            spotifyPermissionGranted = inputs.spotifyPermissionGranted
        )

        val roomUiState = RoomUiState(
            background = inputs.backgroundOption,
            customBackgroundUri = inputs.customBackgroundUri,
            backgroundCompatibility = backgroundCompatibility,
            stage = effectiveStage,
            outfit = validSelectedOutfit,
            activity = resolvedActivity,
            caption = caption,
            careLastAt = inputs.careLastAt,
            isSpotifyPlaying = inputs.isSpotifyPlaying,
            spotifyPermissionGranted = inputs.spotifyPermissionGranted,
            introSeen = inputs.introSeen,
            animationSource = visualSource,
            pendingAsset = pendingAsset,
            catalogStateKey = catalogStateKey,
            isStagePreserved = isStagePreserved,
            hasDedicatedStateAnimation = hasDedicatedStateAnimation,
            motionTruth = motionTruth,
            unlockedCount = unlocked,
            nextUnlockInDays = nextIn,
            availableOutfitIds = availableOutfitIds,
            tapBubbleText = bubble,
            activeTaskLabel = inputs.activeTaskDescription,
            canStopTask = isThinking || isWorking
        )

        return hbState to roomUiState
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
