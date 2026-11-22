package com.novoagentehb.app.ui.habitat

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.novoagentehb.app.app.navigation.Screen
import com.novoagentehb.app.design.CharacterBibleDialog
import com.novoagentehb.app.design.ConfirmActionBar
import com.novoagentehb.app.design.GabiBottomBar
import com.novoagentehb.app.design.GabiCircleActionButton
import com.novoagentehb.app.design.GabiTokens
import com.novoagentehb.app.gabi.GabiAssetImage
import com.novoagentehb.app.gabi.GabiUriImage
import com.novoagentehb.app.gabi.HBActivity
import com.novoagentehb.app.gabi.HBCharacterStage
import com.novoagentehb.app.gabi.HBMotionTruth
import com.novoagentehb.app.gabi.HBStageCharacter
import com.novoagentehb.app.gabi.HBVisualSource
import com.novoagentehb.app.gabi.RoomBackgroundCompatibility
import com.novoagentehb.app.gabi.RoomBackgroundOption
import com.novoagentehb.app.gabi.RoomIntroVideo
import com.novoagentehb.app.gabi.RoomLoopBackgroundVideo
import com.novoagentehb.app.gabi.RoomUiState
import com.novoagentehb.app.gabi.StageLoopVideo
import com.novoagentehb.app.gabi.WardrobeOutfit
import com.novoagentehb.app.gabi.rememberReduceMotionEnabled

@Composable
fun QuartoGabiScreen(
    navController: NavHostController,
    viewModel: HabitatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.roomUiState.collectAsStateWithLifecycle()
    val turnState by viewModel.turnState.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotionEnabled()

    var showWardrobe by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    var showStageDialog by remember { mutableStateOf(false) }
    var showBibleDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.saveCustomBackground(uri.toString())
        }
    }

    Scaffold(
        containerColor = GabiTokens.NightBg,
        bottomBar = { GabiBottomBar(navController, activeStage = uiState.stage) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val fullStageVideo = uiState.animationSource as? HBVisualSource.FullSceneStageVideo

            if (fullStageVideo != null) {
                // Estágio completo sem retângulo de fundo (idade1 / idade2 preenchendo quarto + HB em 9:16)
                StageLoopVideo(
                    assetPath = fullStageVideo.assetVideoPath,
                    fallbackPng = fullStageVideo.fallbackPng,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // CAMADA 0: Fundo independente do Quarto (imagem/vídeo ambiente ou foto da galeria)
                RoomBackgroundLayer(
                    background = uiState.background,
                    customUri = uiState.customBackgroundUri,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Gradiente sutil de contraste para HUD topo e rodapé sem esconder o ambiente
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to GabiTokens.NightBg.copy(alpha = 0.50f),
                            0.28f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1.0f to GabiTokens.NightBg.copy(alpha = 0.65f)
                        )
                    )
            )

            // CAMADA 1: HB em primeiro plano com fundo transparente (ou área clicável no estágio completo)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.84f)
                    .fillMaxHeight(0.75f)
                    .padding(bottom = 58.dp)
                    .semantics {
                        contentDescription = "Personagem HB no Quarto da Gabi: ${uiState.caption}"
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { viewModel.onCharacterTapped() }
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (fullStageVideo == null) {
                    HBStageCharacter(
                        visualSource = uiState.animationSource,
                        activity = uiState.activity,
                        reduceMotion = reduceMotion,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                val bubble = uiState.tapBubbleText
                AnimatedVisibility(
                    visible = bubble != null &&
                        (uiState.activity is HBActivity.TapReaction ||
                            uiState.activity is HBActivity.Eating ||
                            uiState.activity is HBActivity.Success ||
                            uiState.activity is HBActivity.Error ||
                            uiState.activity is HBActivity.Cancelled ||
                            uiState.activity is HBActivity.Speaking),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = GabiTokens.SurfaceDark.copy(alpha = 0.92f),
                        border = BorderStroke(
                            1.dp,
                            when (uiState.activity) {
                                is HBActivity.Error -> GabiTokens.ErrorRed
                                is HBActivity.Success -> GabiTokens.SuccessGreen
                                is HBActivity.Cancelled -> GabiTokens.AmberAccent
                                else -> GabiTokens.GoldAccent
                            }
                        )
                    ) {
                        Text(
                            text = bubble.orEmpty(),
                            color = GabiTokens.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // CAMADA 2: HUD superior em discos circulares e cartão de status R1.4
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GabiTokens.SurfaceDark.copy(alpha = 0.86f),
                    border = BorderStroke(1.dp, GabiTokens.GoldAccent.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Quarto da Gabi",
                                color = GabiTokens.GoldAccent,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Surface(
                                shape = CircleShape,
                                color = GabiTokens.CopperAccent.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = uiState.stage.ageGroupLabel,
                                    color = GabiTokens.GoldAccent,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = uiState.caption,
                            color = GabiTokens.TextPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatCareRelative(uiState.careLastAt),
                            color = GabiTokens.TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (uiState.backgroundCompatibility == RoomBackgroundCompatibility.OPAQUE_FULL_SCENE_BLOCKS_CUSTOM_BG) {
                            Text(
                                text = "Cena completa (${uiState.stage.id}) · fundo '${uiState.background.label}' preservado p/ Infância",
                                color = GabiTokens.AmberAccent,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (uiState.motionTruth == HBMotionTruth.STAGE_PRESERVED_IDLE_ONLY) {
                                Text(
                                    text = uiState.motionTruth.badgeLabel,
                                    color = GabiTokens.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else if (!uiState.hasDedicatedStateAnimation || uiState.pendingAsset != null) {
                            Text(
                                text = "${uiState.motionTruth.badgeLabel} · fundo '${uiState.background.label}' ativo",
                                color = GabiTokens.AmberAccent,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GabiCircleActionButton(
                            icon = Icons.Default.Face,
                            contentDescription = "Fase da HB",
                            tint = GabiTokens.CopperAccent,
                            onClick = { showStageDialog = true }
                        )
                        GabiCircleActionButton(
                            icon = Icons.Default.Checkroom,
                            contentDescription = "Guarda-roupa",
                            tint = GabiTokens.GoldAccent,
                            onClick = { showWardrobe = true }
                        )
                        GabiCircleActionButton(
                            icon = Icons.Default.Wallpaper,
                            contentDescription = "Ambiente do Quarto",
                            onClick = { showBackgroundDialog = true }
                        )
                        GabiCircleActionButton(
                            icon = Icons.Default.Computer,
                            contentDescription = "Computador e Skills",
                            onClick = { navController.navigate(Screen.Computer.route) }
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = GabiTokens.SurfaceDark.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, GabiTokens.GoldAccent.copy(alpha = 0.68f)),
                        shadowElevation = 4.dp,
                        modifier = Modifier.clickable { navController.navigate(Screen.Chat.route) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "Conversar no Chat",
                                tint = GabiTokens.GoldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Conversar",
                                color = GabiTokens.TextPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // CAMADA 3: Controles inferiores (Caixinha Spotify + Alimentar + Parar tarefa + Aviso/Confirmação)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (notice != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = GabiTokens.SurfaceDark.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, GabiTokens.TealAccent.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable { viewModel.clearNotice() }
                    ) {
                        Text(
                            text = notice.orEmpty(),
                            color = GabiTokens.TextPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                turnState.pendingConfirmation?.let { pending ->
                    ConfirmActionBar(
                        prompt = pending.promptToUser,
                        onConfirm = { viewModel.resolveConfirmation(true) },
                        onCancel = { viewModel.resolveConfirmation(false) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    SpotifySpeakerBadge(
                        playing = uiState.isSpotifyPlaying,
                        permissionGranted = uiState.spotifyPermissionGranted,
                        reduceMotion = reduceMotion,
                        onOpenPermissions = { navController.navigate(Screen.Permissions.route) }
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.canStopTask) {
                            Button(
                                onClick = { viewModel.stopAll() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.ErrorRed)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Parar")
                            }
                        }

                        Button(
                            onClick = { viewModel.feed() },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GabiTokens.CopperAccent,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Alimentar", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // CAMADA 4: Abertura única persistente com controles Pular / Não mostrar novamente
            if (!uiState.introSeen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    RoomIntroVideo(
                        onFinish = { _ -> viewModel.markIntroSeen() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (showWardrobe) {
        WardrobeDialog(
            uiState = uiState,
            outfits = viewModel.allOutfits,
            isAssetAvailable = { viewModel.isOutfitAvailable(it) },
            onSelectOutfit = { outfit ->
                viewModel.selectOutfit(outfit)
                showWardrobe = false
            },
            onDismiss = { showWardrobe = false }
        )
    }

    if (showStageDialog) {
        StageSelectorDialog(
            currentStage = uiState.stage,
            stages = viewModel.allStages,
            isStageAvailable = { viewModel.isStageAvailable(it) },
            onSelectStage = { stage ->
                viewModel.selectStage(stage)
                showStageDialog = false
            },
            onOpenBible = {
                showStageDialog = false
                showBibleDialog = true
            },
            onDismiss = { showStageDialog = false }
        )
    }

    if (showBackgroundDialog) {
        RoomEnvironmentDialog(
            current = uiState.background,
            activeStage = uiState.stage,
            onSelect = { opt ->
                if (opt == RoomBackgroundOption.CUSTOM_URI) {
                    showBackgroundDialog = false
                    photoPicker.launch(arrayOf("image/*"))
                } else {
                    viewModel.selectBackground(opt)
                    showBackgroundDialog = false
                }
            },
            onSwitchToYoungChild = {
                viewModel.selectStage(HBCharacterStage.YOUNG_CHILD)
            },
            onReplayIntro = {
                showBackgroundDialog = false
                viewModel.replayIntro()
            },
            onDismiss = { showBackgroundDialog = false }
        )
    }

    if (showBibleDialog) {
        CharacterBibleDialog(onDismiss = { showBibleDialog = false })
    }
}

@Composable
private fun RoomBackgroundLayer(
    background: RoomBackgroundOption,
    customUri: String,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    when (background) {
        RoomBackgroundOption.ROOM_WEBP -> GabiAssetImage(
            fileName = "avatar/room.webp",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        RoomBackgroundOption.ROOM_PNG -> GabiAssetImage(
            fileName = "room-bg.png",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        RoomBackgroundOption.ROOM_MP4 -> RoomLoopBackgroundVideo(
            reduceMotion = reduceMotion,
            modifier = modifier
        )
        RoomBackgroundOption.CUSTOM_URI -> GabiUriImage(
            uriString = customUri,
            fallbackAsset = "avatar/room.webp",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

@Composable
private fun SpotifySpeakerBadge(
    playing: Boolean,
    permissionGranted: Boolean,
    reduceMotion: Boolean,
    onOpenPermissions: () -> Unit
) {
    val noteFloat = if (playing && !reduceMotion) {
        val infiniteTransition = rememberInfiniteTransition(label = "spotify_notes")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "note_float"
        )
        animated
    } else {
        0f
    }

    Column(horizontalAlignment = Alignment.Start) {
        if (playing) {
            Row(
                modifier = Modifier
                    .offset(y = (-6 - noteFloat * 14f).dp)
                    .alpha(1f - noteFloat * 0.7f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Tocando música no Spotify",
                    tint = GabiTokens.SpotifyGreen,
                    modifier = Modifier.size(18.dp)
                )
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = GabiTokens.GoldAccent,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = GabiTokens.SurfaceDark.copy(alpha = 0.88f),
            border = BorderStroke(
                1.dp,
                when {
                    playing -> GabiTokens.SpotifyGreen
                    !permissionGranted -> GabiTokens.AmberAccent.copy(alpha = 0.6f)
                    else -> GabiTokens.Hairline
                }
            ),
            modifier = Modifier.clickable(enabled = !permissionGranted) { onOpenPermissions() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Speaker,
                    contentDescription = "Caixinha de som Spotify",
                    tint = if (playing) GabiTokens.SpotifyGreen else GabiTokens.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        playing -> "Spotify tocando"
                        !permissionGranted -> "Ativar avisos p/ Spotify"
                        else -> "Som em repouso"
                    },
                    color = if (playing) GabiTokens.SpotifyGreen else GabiTokens.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun WardrobeDialog(
    uiState: RoomUiState,
    outfits: List<WardrobeOutfit>,
    isAssetAvailable: (WardrobeOutfit) -> Boolean,
    onSelectOutfit: (WardrobeOutfit?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GabiTokens.SurfaceDark,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Guarda-roupa da Gabi", color = GabiTokens.TextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        text = when {
                            uiState.stage != HBCharacterStage.YOUNG_CHILD ->
                                "Looks exclusivos da fase Infância · escolher um look aplica Infância"
                            uiState.nextUnlockInDays > 0 ->
                                "Próximo look de rotina em ${uiState.nextUnlockInDays} dia(s)"
                            else ->
                                "Looks reais disponíveis agora no pacote"
                        },
                        color = if (uiState.stage != HBCharacterStage.YOUNG_CHILD) GabiTokens.AmberAccent else GabiTokens.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = GabiTokens.TextSecondary)
                }
            }
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(380.dp)
            ) {
                item {
                    val isRoutine = uiState.outfit == null
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isRoutine) GabiTokens.GoldAccent.copy(alpha = 0.18f) else GabiTokens.NightBg,
                        border = BorderStroke(
                            1.dp,
                            if (isRoutine) GabiTokens.GoldAccent else GabiTokens.Hairline
                        ),
                        modifier = Modifier
                            .height(140.dp)
                            .clickable { onSelectOutfit(null) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Rotina do dia", color = GabiTokens.GoldAccent, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Muda sozinha pelo horário (café, notebook, livro, dormir)",
                                color = GabiTokens.TextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                itemsIndexed(outfits) { index, outfit ->
                    val assetExists = isAssetAvailable(outfit)
                    val unlocked = index < uiState.unlockedCount
                    val selectable = assetExists && unlocked
                    val selected = uiState.outfit?.id == outfit.id

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            selected -> GabiTokens.GoldAccent.copy(alpha = 0.2f)
                            !selectable -> GabiTokens.NightBg.copy(alpha = 0.55f)
                            else -> GabiTokens.NightBg
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) GabiTokens.GoldAccent else GabiTokens.Hairline
                        ),
                        modifier = Modifier
                            .height(140.dp)
                            .clickable(enabled = selectable) { onSelectOutfit(outfit) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (assetExists) {
                                GabiAssetImage(
                                    fileName = outfit.poseFile,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .alpha(if (selectable) 1f else 0.45f)
                                )
                            } else {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Arte pendente",
                                        tint = GabiTokens.AmberAccent
                                    )
                                }
                            }
                            Text(
                                text = outfit.label,
                                color = if (selectable) GabiTokens.TextPrimary else GabiTokens.TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!assetExists) {
                                Text(
                                    text = "Arte pendente",
                                    color = GabiTokens.AmberAccent,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else if (!unlocked) {
                                Text(
                                    text = "Bloqueado",
                                    color = GabiTokens.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else if (outfit.verifiedAppearanceNote.isNotBlank()) {
                                Text(
                                    text = outfit.poseFile,
                                    color = GabiTokens.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

@Composable
private fun StageSelectorDialog(
    currentStage: HBCharacterStage,
    stages: List<HBCharacterStage>,
    isStageAvailable: (HBCharacterStage) -> Boolean,
    onSelectStage: (HBCharacterStage) -> Unit,
    onOpenBible: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GabiTokens.SurfaceDark,
        title = {
            Column {
                Text("Fase da HB", color = GabiTokens.TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    text = "Seleção manual explícita — preserva a fase escolhida em todas as telas. Pré-adolescente e Adolescente usam vídeo idle opaco até o recorte alfa e clipes dedicados de ação.",
                    color = GabiTokens.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stages.forEach { stage ->
                    val available = isStageAvailable(stage)
                    val selected = currentStage == stage
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) GabiTokens.CopperAccent.copy(alpha = 0.20f) else GabiTokens.NightBg,
                        border = BorderStroke(
                            1.dp,
                            if (selected) GabiTokens.GoldAccent else GabiTokens.Hairline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = available) { onSelectStage(stage) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GabiAssetImage(
                                fileName = stage.avatarCirclePngPath,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, if (selected) GabiTokens.GoldAccent else GabiTokens.Hairline, CircleShape)
                                    .alpha(if (available) 1f else 0.45f)
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stage.label,
                                    color = if (available) GabiTokens.TextPrimary else GabiTokens.TextSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stage.ageGroupLabel,
                                    color = GabiTokens.GoldAccent,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = stage.description,
                                    color = GabiTokens.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = onOpenBible,
                    border = BorderStroke(1.dp, GabiTokens.GoldAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GabiTokens.GoldAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ver Bíblia & Spritesheet da HB", color = GabiTokens.GoldAccent)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

@Composable
private fun RoomEnvironmentDialog(
    current: RoomBackgroundOption,
    activeStage: HBCharacterStage,
    onSelect: (RoomBackgroundOption) -> Unit,
    onSwitchToYoungChild: () -> Unit,
    onReplayIntro: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GabiTokens.SurfaceDark,
        title = {
            Text("Ambiente do Quarto", color = GabiTokens.TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (activeStage != HBCharacterStage.YOUNG_CHILD) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GabiTokens.SurfaceWarm,
                        border = BorderStroke(1.dp, GabiTokens.AmberAccent.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "A fase ${activeStage.label} usa vídeo de cena completa opaca (recorte alfa pendente) para evitar retângulo artificial. Seu fundo escolhido ('${current.label}') continua salvo e será aplicado automaticamente ao voltar para Infância.",
                                color = GabiTokens.AmberAccent,
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = onSwitchToYoungChild) {
                                Text("Voltar p/ Infância e aplicar fundo", color = GabiTokens.GoldAccent)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Escolha apenas o cenário atrás da HB. A personagem continua viva e transparente na frente.",
                        color = GabiTokens.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                RoomBackgroundOption.entries.forEach { option ->
                    val selected = current == option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) GabiTokens.TealAccent.copy(alpha = 0.2f) else GabiTokens.NightBg,
                        border = BorderStroke(
                            1.dp,
                            if (selected) GabiTokens.TealAccent else GabiTokens.Hairline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                    ) {
                        Text(
                            text = option.label,
                            color = GabiTokens.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
                OutlinedButton(
                    onClick = onReplayIntro,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rever abertura do Quarto")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

private fun formatCareRelative(ts: Long): String {
    if (ts <= 0L) return "Ainda não alimentada hoje"
    val rel = DateUtils.getRelativeTimeSpanString(ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    return "Alimentada $rel"
}
