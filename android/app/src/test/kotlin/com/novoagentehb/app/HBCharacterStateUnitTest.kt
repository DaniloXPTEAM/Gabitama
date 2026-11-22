package com.novoagentehb.app

import com.novoagentehb.app.agent.orchestrator.TaskStatus
import com.novoagentehb.app.bridges.AndroidDeviceBridge
import com.novoagentehb.app.gabi.AssetLookup
import com.novoagentehb.app.gabi.HBActivity
import com.novoagentehb.app.gabi.HBAnimationCatalog
import com.novoagentehb.app.gabi.HBCatalogStateId
import com.novoagentehb.app.gabi.HBCharacterStage
import com.novoagentehb.app.gabi.HBMediaAvailability
import com.novoagentehb.app.gabi.HBMotionTruth
import com.novoagentehb.app.gabi.HBSignalInputs
import com.novoagentehb.app.gabi.HBStageCatalog
import com.novoagentehb.app.gabi.HBStateResolver
import com.novoagentehb.app.gabi.HBVisualSource
import com.novoagentehb.app.gabi.HBWardrobeCatalog
import com.novoagentehb.app.gabi.RoomBackgroundCompatibility
import com.novoagentehb.app.gabi.RoomBackgroundOption
import com.novoagentehb.app.gabi.TransientOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HBCharacterStateUnitTest {

    private val realPackageAssets = AssetLookup(
        hasAnimFolder = {
            it in setOf(
                "home_idle",
                "scene_computador",
                "scene_computador_pensando",
                "scene_listening_intent"
            )
        },
        hasAssetFile = {
            it in setOf(
                "gabi-idle.png",
                "gabi-listening.png",
                "gabi-talking.png",
                "gabi-comer.png",
                "gabi-computador.png",
                "gabi-ler.png",
                "gabi-dormir.png",
                "hb-stages/pre_adolescent/idle.mp4",
                "hb-stages/adolescent/idle.mp4",
                "hb-stages/young_child/preview.png",
                "hb-stages/young_child/avatar_circle.png",
                "hb-stages/pre_adolescent/preview.png",
                "hb-stages/pre_adolescent/avatar_circle.png",
                "hb-stages/adolescent/preview.png",
                "hb-stages/adolescent/avatar_circle.png",
                "bible.png",
                "gabi-spritesheet.png"
            )
        },
        hasAvatarFolder = { it in setOf("idle", "listening", "talking", "thinking") }
    )

    @Test
    fun routineByHourSelectsExpectedActivityAndRealAsset() {
        val (hbMorning, roomMorning) = HBStateResolver.resolve(
            HBSignalInputs(hourOfDay = 8, nowMs = 10_000L),
            realPackageAssets
        )
        assertTrue(hbMorning.activity is HBActivity.Eating)
        assertEquals("gabi-comer.png", roomMorning.animationSource.fallbackPng)
        assertNotNull(roomMorning.pendingAsset) // scene_comer ainda ausente -> fallback honesto

        val (hbWork, _) = HBStateResolver.resolve(
            HBSignalInputs(hourOfDay = 14, nowMs = 10_000L),
            realPackageAssets
        )
        assertTrue(hbWork.activity is HBActivity.AtComputer)
        assertTrue(hbWork.visualSource is HBVisualSource.FramePngSequence)
        assertEquals("scene_computador", (hbWork.visualSource as HBVisualSource.FramePngSequence).folder)
        assertNull(hbWork.pendingAsset)

        val (hbRead, _) = HBStateResolver.resolve(
            HBSignalInputs(hourOfDay = 19, nowMs = 10_000L),
            realPackageAssets
        )
        assertTrue(hbRead.activity is HBActivity.Reading)
        assertEquals("gabi-ler.png", hbRead.visualSource.fallbackPng)

        val (hbSleep, _) = HBStateResolver.resolve(
            HBSignalInputs(hourOfDay = 23, nowMs = 10_000L),
            realPackageAssets
        )
        assertTrue(hbSleep.activity is HBActivity.Sleeping)
        assertEquals("gabi-dormir.png", hbSleep.visualSource.fallbackPng)
    }

    @Test
    fun strictPriorityRespectsCancelledOverErrorOverVoiceOverAgentOverEatingOverTapOverOutfit() {
        val outfit = HBWardrobeCatalog.findById("vestido_lilas")!!
        val base = HBSignalInputs(
            hourOfDay = 14,
            selectedOutfit = outfit,
            tapUntilMs = 2_000L,
            tapBubbleText = "Oi!",
            eatingUntilMs = 3_000L,
            taskStatus = TaskStatus.EXECUTING,
            activeTaskDescription = "Executando tarefa",
            isListening = true,
            isSpeaking = true,
            transientOutcome = TransientOutcome(HBActivity.Cancelled, "Cancelado", 5_000L),
            nowMs = 1_000L
        )

        // 1. Cancelled > tudo
        assertTrue(HBStateResolver.resolve(base, realPackageAssets).first.activity is HBActivity.Cancelled)

        // 2. Error > Speaking
        val withErr = base.copy(transientOutcome = TransientOutcome(HBActivity.Error("E1", "Falha"), "Ops", 5_000L))
        assertTrue(HBStateResolver.resolve(withErr, realPackageAssets).first.activity is HBActivity.Error)

        // 3. Speaking > Listening
        val noTransient = base.copy(transientOutcome = null)
        assertTrue(HBStateResolver.resolve(noTransient, realPackageAssets).first.activity is HBActivity.Speaking)

        // 4. Listening > Working
        assertTrue(HBStateResolver.resolve(noTransient.copy(isSpeaking = false), realPackageAssets).first.activity is HBActivity.Listening)

        // 5. Thinking (PLANNING) > Working
        val planning = noTransient.copy(isSpeaking = false, isListening = false, taskStatus = TaskStatus.PLANNING)
        assertTrue(HBStateResolver.resolve(planning, realPackageAssets).first.activity is HBActivity.Thinking)

        // 6. Working > Success/Eating
        val working = planning.copy(taskStatus = TaskStatus.EXECUTING)
        assertTrue(HBStateResolver.resolve(working, realPackageAssets).first.activity is HBActivity.Working)

        // 7. Success > Eating
        val success = working.copy(
            taskStatus = TaskStatus.IDLE,
            transientOutcome = TransientOutcome(HBActivity.Success("t1", "OK"), "Pronto!", 5_000L)
        )
        assertTrue(HBStateResolver.resolve(success, realPackageAssets).first.activity is HBActivity.Success)

        // 8. Eating (3s feed override) > TapReaction & Outfit
        val eating = working.copy(taskStatus = TaskStatus.IDLE)
        assertTrue(HBStateResolver.resolve(eating, realPackageAssets).first.activity is HBActivity.Eating)

        // 9. TapReaction > Outfit (após expirar Eating)
        val tapped = eating.copy(nowMs = 3_100L, tapUntilMs = 4_000L)
        assertTrue(HBStateResolver.resolve(tapped, realPackageAssets).first.activity is HBActivity.TapReaction)

        // 10. WearingOutfit > Routine (após expirar TapReaction)
        val dressed = tapped.copy(nowMs = 4_500L)
        val (hbDressed, roomDressed) = HBStateResolver.resolve(dressed, realPackageAssets)
        assertEquals(outfit.id, roomDressed.outfit?.id)
        assertEquals("gabi-ler.png", hbDressed.visualSource.fallbackPng)
    }

    @Test
    fun missingOutfitAssetNeverFakesSelectionAndReportsPendingAsset() {
        val pendingOutfit = HBWardrobeCatalog.findById("moletom_preto")!!
        val (hb, room) = HBStateResolver.resolve(
            HBSignalInputs(hourOfDay = 12, selectedOutfit = pendingOutfit, nowMs = 1_000L),
            realPackageAssets
        )
        assertNull(room.outfit)
        assertTrue(hb.pendingAsset?.contains("ASSET_PENDING") == true)
        assertTrue(hb.pendingAsset?.contains("gabi-look-moletom.png") == true)
    }

    @Test
    fun spotifyPlaybackActivatesMusicReactionWithoutErasingCharacter() {
        val (hb, room) = HBStateResolver.resolve(
            HBSignalInputs(hourOfDay = 15, isSpotifyPlaying = true, spotifyPermissionGranted = true, nowMs = 1_000L),
            realPackageAssets
        )
        assertTrue(hb.isSpotifyPlaying)
        assertTrue(room.isSpotifyPlaying)
        assertTrue(hb.activity is HBActivity.MusicReaction)
        assertTrue(hb.caption.contains("Curtindo música"))
    }

    @Test
    fun wardrobeLabelsAndVerifiedVisualDescriptionsMatchRealAssets() {
        val red = HBWardrobeCatalog.findById("vestido_vermelho")!!
        val lilac = HBWardrobeCatalog.findById("vestido_lilas")!!
        val sweater = HBWardrobeCatalog.findById("sueter_computador")!!
        val pajama = HBWardrobeCatalog.findById("pijama_estrelas")!!

        assertEquals("gabi-idle.png", red.poseFile)
        assertTrue(red.verifiedAppearanceNote.contains("Vestido vermelho"))
        assertEquals("gabi-ler.png", lilac.poseFile)
        assertTrue(lilac.verifiedAppearanceNote.contains("Vestido lilás"))
        assertEquals("gabi-computador.png", sweater.poseFile)
        assertTrue(sweater.verifiedAppearanceNote.contains("Suéter mostarda"))
        assertEquals("gabi-dormir.png", pajama.poseFile)
        assertTrue(pajama.verifiedAppearanceNote.contains("Pijama azul"))
    }

    @Test
    fun catalogCoversAll15MandatoryStatesPlusRoomMp4VideosAndBibleR14() {
        assertTrue(HBAnimationCatalog.mandatoryStatesPresent())
        val counts = HBAnimationCatalog.countByAvailability()
        assertEquals(4, counts[HBMediaAvailability.AVAILABLE_NATIVE_VIDEO])
        assertEquals(7, counts[HBMediaAvailability.AVAILABLE_NATIVE_SEQUENCE])
        assertEquals(2, counts[HBMediaAvailability.AVAILABLE_STATIC_REFERENCE])
        val intro = HBAnimationCatalog.find(HBCatalogStateId.ROOM_INTRO_MP4)
        assertEquals(24, intro.fps)
        assertTrue(intro.sourcePath.endsWith("room_intro.mp4"))
        val bg = HBAnimationCatalog.find(HBCatalogStateId.ROOM_BG_LOOP_MP4)
        assertEquals(24, bg.fps)
        assertTrue(bg.loop)
        val sprite = HBAnimationCatalog.find(HBCatalogStateId.SPRITESHEET_OPTIONAL)
        assertEquals(HBMediaAvailability.AVAILABLE_STATIC_REFERENCE, sprite.availability)
        assertEquals("assets/gabi-spritesheet.png", sprite.sourcePath)
        assertEquals(7, sprite.frameCount)
        val bible = HBAnimationCatalog.find(HBCatalogStateId.BIBLE_CHARACTER_SHEET)
        assertEquals(HBMediaAvailability.AVAILABLE_STATIC_REFERENCE, bible.availability)
        assertEquals("assets/bible.png", bible.sourcePath)
    }

    @Test
    fun r14StagesPreserveActivePhaseContinuityAcrossInteractiveStates() {
        assertEquals(3, HBStageCatalog.all.size)
        assertEquals("Pré-adolescente", HBCharacterStage.PRE_ADOLESCENT.ageGroupLabel)
        assertEquals("Adolescente", HBCharacterStage.ADOLESCENT.ageGroupLabel)
        assertEquals("hb-stages/adolescent/avatar_circle.png", HBCharacterStage.ADOLESCENT.avatarCirclePngPath)

        val (hbPre, roomPre) = HBStateResolver.resolve(
            HBSignalInputs(
                hourOfDay = 12,
                selectedStage = HBCharacterStage.PRE_ADOLESCENT,
                nowMs = 10_000L
            ),
            realPackageAssets
        )
        assertTrue(hbPre.visualSource is HBVisualSource.FullSceneStageVideo)
        assertEquals(
            "hb-stages/pre_adolescent/idle.mp4",
            (hbPre.visualSource as HBVisualSource.FullSceneStageVideo).assetVideoPath
        )
        assertEquals(HBCharacterStage.PRE_ADOLESCENT, roomPre.stage)

        // R1.4 / R1.4.1 Continuity: PRE_ADOLESCENT / ADOLESCENT never revert to YOUNG_CHILD during listening, speaking or working,
        // and honestly separate stage preservation (`isStagePreserved`) from dedicated state clip (`hasDedicatedStateAnimation`)
        val (hbAdolescentListening, roomAdolescentListening) = HBStateResolver.resolve(
            HBSignalInputs(
                hourOfDay = 12,
                selectedStage = HBCharacterStage.ADOLESCENT,
                isListening = true,
                isLiveListening = false,
                nowMs = 10_000L
            ),
            realPackageAssets
        )
        assertEquals(HBCharacterStage.ADOLESCENT, hbAdolescentListening.stage)
        assertEquals(HBCharacterStage.ADOLESCENT, roomAdolescentListening.stage)
        assertTrue(hbAdolescentListening.isStagePreserved)
        assertFalse(hbAdolescentListening.hasDedicatedStateAnimation)
        assertEquals(HBMotionTruth.STAGE_PRESERVED_IDLE_ONLY, hbAdolescentListening.motionTruth)
        assertTrue(hbAdolescentListening.activity is HBActivity.Listening)
        assertTrue(hbAdolescentListening.visualSource is HBVisualSource.FullSceneStageVideo)
        assertEquals(
            HBCharacterStage.ADOLESCENT,
            (hbAdolescentListening.visualSource as HBVisualSource.FullSceneStageVideo).stage
        )
        assertEquals("stage_adolescent_listening", hbAdolescentListening.catalogStateKey)
        assertEquals(
            RoomBackgroundCompatibility.OPAQUE_FULL_SCENE_BLOCKS_CUSTOM_BG,
            roomAdolescentListening.backgroundCompatibility
        )

        // Interactive sequences still drive YOUNG_CHILD directly when that stage is active
        val (hbChildLive, roomChildLive) = HBStateResolver.resolve(
            HBSignalInputs(
                hourOfDay = 12,
                selectedStage = HBCharacterStage.YOUNG_CHILD,
                isListening = true,
                isLiveListening = true,
                backgroundOption = RoomBackgroundOption.CUSTOM_URI,
                customBackgroundUri = "content://media/room_custom_1.jpg",
                nowMs = 10_000L
            ),
            realPackageAssets
        )
        assertTrue(hbChildLive.activity is HBActivity.Listening)
        assertTrue(hbChildLive.hasDedicatedStateAnimation)
        assertEquals(HBMotionTruth.DEDICATED_STATE_ANIMATION, hbChildLive.motionTruth)
        assertEquals(
            RoomBackgroundCompatibility.INDEPENDENT_TRANSPARENT_LAYERS,
            roomChildLive.backgroundCompatibility
        )
        assertEquals("content://media/room_custom_1.jpg", roomChildLive.customBackgroundUri)
        assertTrue(hbChildLive.visualSource is HBVisualSource.FramePngSequence)
        assertEquals("scene_listening_intent", (hbChildLive.visualSource as HBVisualSource.FramePngSequence).folder)

        // Switching to PRE_ADOLESCENT preserves custom background preferences even though full-scene video blocks visual display
        val (_, roomPreCustom) = HBStateResolver.resolve(
            HBSignalInputs(
                hourOfDay = 12,
                selectedStage = HBCharacterStage.PRE_ADOLESCENT,
                backgroundOption = RoomBackgroundOption.CUSTOM_URI,
                customBackgroundUri = "content://media/room_custom_1.jpg",
                nowMs = 10_000L
            ),
            realPackageAssets
        )
        assertEquals(RoomBackgroundOption.CUSTOM_URI, roomPreCustom.background)
        assertEquals("content://media/room_custom_1.jpg", roomPreCustom.customBackgroundUri)
        assertEquals(
            RoomBackgroundCompatibility.OPAQUE_FULL_SCENE_BLOCKS_CUSTOM_BG,
            roomPreCustom.backgroundCompatibility
        )
        assertTrue(roomPreCustom.hasDedicatedStateAnimation) // idle state uses real idade1 idle.mp4

        val (hbCompThink, _) = HBStateResolver.resolve(
            HBSignalInputs(
                hourOfDay = 14,
                taskStatus = TaskStatus.PLANNING,
                activeTaskDescription = "Planejando tarefa",
                nowMs = 10_000L
            ),
            realPackageAssets
        )
        assertTrue(hbCompThink.activity is HBActivity.Thinking)
        assertTrue(hbCompThink.visualSource is HBVisualSource.FramePngSequence)
        assertEquals("scene_computador_pensando", (hbCompThink.visualSource as HBVisualSource.FramePngSequence).folder)
    }

    @Test
    fun outfitChangeAnimatesAndPreservesSelectedOutfitWithMusicCoexistence() {
        val outfit = HBWardrobeCatalog.findById("sueter_computador")!!
        val duringChange = HBSignalInputs(
            hourOfDay = 9,
            selectedOutfit = outfit,
            outfitChangeUntilMs = 2_000L,
            outfitChangeLabel = outfit.label,
            nowMs = 1_500L
        )
        val (hbDuring, _) = HBStateResolver.resolve(duringChange, realPackageAssets)
        assertTrue(hbDuring.activity is HBActivity.OutfitChange)

        val afterChangeWithSpotify = duringChange.copy(nowMs = 2_500L, isSpotifyPlaying = true)
        val (hbAfter, roomAfter) = HBStateResolver.resolve(afterChangeWithSpotify, realPackageAssets)
        assertTrue(hbAfter.activity is HBActivity.MusicReaction)
        assertEquals(outfit.id, roomAfter.outfit?.id)
        assertEquals("gabi-computador.png", hbAfter.visualSource.fallbackPng)
        assertTrue(hbAfter.caption.contains(outfit.label))
    }

    @Test
    fun tapReactionUsesRealTalkingWebpSequenceWhenDedicatedSceneMissing() {
        val (hb, _) = HBStateResolver.resolve(
            HBSignalInputs(hourOfDay = 12, tapUntilMs = 2_000L, tapBubbleText = "Oi!", nowMs = 1_000L),
            realPackageAssets
        )
        assertTrue(hb.activity is HBActivity.TapReaction)
        assertTrue(hb.visualSource is HBVisualSource.AvatarWebp)
        assertEquals("talking", (hb.visualSource as HBVisualSource.AvatarWebp).folder)
        assertTrue(hb.pendingAsset?.contains("scene_tap_reaction") == true)
    }

    @Test
    fun offlineCommandParserPreservesGabiQuickIntents() {
        val rem = AndroidDeviceBridge.parseOfflineIntent("criar lembrete da reunião")
        assertEquals("set_reminder", rem?.first)
        assertTrue(rem?.second?.containsKey("subject") == true)
        assertTrue(rem?.second?.containsKey("trigger_at") == true)

        val alarm = AndroidDeviceBridge.parseOfflineIntent("colocar alarme 06:45")
        assertEquals("set_alarm", alarm?.first)
        assertEquals("6", alarm?.second?.get("hour"))
        assertEquals("45", alarm?.second?.get("minute"))
        assertEquals("Alarme da Gabi", alarm?.second?.get("description"))

        assertEquals("bluetooth_connect", AndroidDeviceBridge.parseOfflineIntent("conectar bluetooth")?.first)
        assertEquals("take_photo", AndroidDeviceBridge.parseOfflineIntent("abrir camera")?.first)
        assertEquals("read_gmail", AndroidDeviceBridge.parseOfflineIntent("ler meu gmail")?.first)
        assertEquals("search_web", AndroidDeviceBridge.parseOfflineIntent("pesquisar receita de bolo")?.first)
    }
}
