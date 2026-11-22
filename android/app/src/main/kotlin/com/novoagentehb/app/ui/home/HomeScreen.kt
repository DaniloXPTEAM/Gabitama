package com.novoagentehb.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.novoagentehb.app.agent.models.ProviderFailure
import com.novoagentehb.app.agent.orchestrator.TaskStatus
import com.novoagentehb.app.app.navigation.Screen
import com.novoagentehb.app.design.ActiveExecutionBanner
import com.novoagentehb.app.design.ConfirmActionBar
import com.novoagentehb.app.design.GabiAtmosphericBackdrop
import com.novoagentehb.app.design.GabiBottomBar
import com.novoagentehb.app.design.GabiCircleActionButton
import com.novoagentehb.app.design.GabiFeaturedSurface
import com.novoagentehb.app.design.GabiTokens
import com.novoagentehb.app.design.GlassInfoCard
import com.novoagentehb.app.design.ProviderFailureCard
import com.novoagentehb.app.design.ProviderFailureDetailDialog
import com.novoagentehb.app.gabi.GabiAssetImage
import com.novoagentehb.app.gabi.HBActivity
import com.novoagentehb.app.gabi.HBCharacterStage
import com.novoagentehb.app.gabi.HBMotionTruth
import com.novoagentehb.app.gabi.HBStageCharacter
import com.novoagentehb.app.gabi.HBVisualSource
import com.novoagentehb.app.gabi.LiveSessionState
import com.novoagentehb.app.gabi.rememberReduceMotionEnabled

@Composable
fun HomeScreen(
    navController: NavController,
    autoStartListening: MutableState<Boolean>? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val hbState by viewModel.hbState.collectAsState()
    val turnState by viewModel.turnState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val recent by viewModel.recentMessages.collectAsState()
    val spotifyPlaying by viewModel.spotifyPlaying.collectAsState()
    val activeGoalsCount by viewModel.activeGoalsCount.collectAsState()
    val unreadFeedCount by viewModel.unreadFeedCount.collectAsState()
    val voiceError by viewModel.voiceError.collectAsState()

    var quickPrompt by remember { mutableStateOf("") }
    var detailFailure by remember { mutableStateOf<ProviderFailure?>(null) }
    var pendingLiveAfterPermission by remember { mutableStateOf(false) }

    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingLiveAfterPermission) {
                pendingLiveAfterPermission = false
                viewModel.onLiveTap(true)
            } else {
                viewModel.onMicTap(true)
            }
        } else {
            pendingLiveAfterPermission = false
        }
    }

    val triggerMic = autoStartListening?.value == true
    LaunchedEffect(triggerMic) {
        if (triggerMic) {
            autoStartListening?.value = false
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.onMicTap(true) else audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val processing = turnState.status == TaskStatus.PLANNING ||
        turnState.status == TaskStatus.EXECUTING ||
        turnState.status == TaskStatus.VERIFYING

    // Sincronizado com HBCharacterController: mantém estritamente a fase selecionada
    // (`FullSceneStageVideo` em medalhão circular p/ idade1/idade2) ou `avatar/idle` transparente em repouso.
    val homeVisualSource = when {
        hbState.visualSource is HBVisualSource.FullSceneStageVideo -> hbState.visualSource
        hbState.activity is HBActivity.Listening ||
            hbState.activity is HBActivity.Speaking ||
            hbState.activity is HBActivity.Thinking ||
            hbState.activity is HBActivity.Working ||
            hbState.activity is HBActivity.Success ||
            hbState.activity is HBActivity.Error ||
            hbState.activity is HBActivity.Cancelled ||
            hbState.activity is HBActivity.Eating ||
            hbState.activity is HBActivity.TapReaction -> hbState.visualSource
        else -> HBVisualSource.AvatarWebp("idle", "gabi-idle.png")
    }

    val liveActive = voiceState.liveSessionState == LiveSessionState.ACTIVE ||
        voiceState.liveSessionState == LiveSessionState.CONNECTING ||
        voiceState.liveSessionState == LiveSessionState.RECONNECTING

    Scaffold(
        containerColor = GabiTokens.NightBg,
        bottomBar = { GabiBottomBar(navController, activeStage = hbState.stage) }
    ) { innerPadding ->
        GabiAtmosphericBackdrop {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(innerPadding)
                    .imePadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cabeçalho editorial com brasão da HB e botões circulares ao estilo Tama V2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GabiAssetImage(
                                fileName = "theme/gabitama_brand_crest.png",
                                contentDescription = "Identidade GabiTama",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(36.dp)
                            )
                            Column {
                                Text(
                                    text = "Gabi · HB",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = GabiTokens.TextPrimary
                                )
                                Text(
                                    text = "${voiceState.activeEngine.label} · ${voiceState.liveSessionState.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (voiceState.liveSessionState == LiveSessionState.ACTIVE) {
                                        GabiTokens.SuccessGreen
                                    } else {
                                        GabiTokens.TealAccent
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GabiCircleActionButton(
                                icon = Icons.Default.MeetingRoom,
                                contentDescription = "Abrir Quarto da Gabi",
                                tint = GabiTokens.GoldAccent,
                                borderColor = GabiTokens.GoldAccent.copy(alpha = 0.35f),
                                onClick = { navController.navigate(Screen.Habitat.route) }
                            )
                            GabiCircleActionButton(
                                icon = Icons.Default.Settings,
                                contentDescription = "Ajustes",
                                onClick = { navController.navigate(Screen.Settings.route) }
                            )
                        }
                    }

                    Text(
                        text = voiceState.fallbackActiveReason ?: voiceState.activeVoiceDisclosure,
                        style = MaterialTheme.typography.labelSmall,
                        color = GabiTokens.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(6.dp))

                    val reduceMotion = rememberReduceMotionEnabled()
                    // Palco Hero R1.4.1: HB animada em destaque (vídeo em movimento na moldura circular para idade1/idade2;
                    // `avatar/idle` ou sequência transparente do estado para Infância; estático apenas se `reduceMotion` ativo)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(232.dp)
                            .clickable { navController.navigate(Screen.Habitat.route) },
                        contentAlignment = Alignment.Center
                    ) {
                        GabiAssetImage(
                            fileName = "theme/gabitama_stage_halo.png",
                            contentScale = ContentScale.Fit,
                            alpha = 0.72f,
                            modifier = Modifier
                                .size(220.dp)
                                .align(Alignment.Center)
                        )

                        HBStageCharacter(
                            visualSource = homeVisualSource,
                            activity = hbState.activity,
                            reduceMotion = reduceMotion,
                            preferCircularStageMedallion = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(204.dp)
                                .align(Alignment.TopCenter)
                        )

                        // Pílula flutuante de status e fase inspirada no conceito Tama V2
                        val pillText = voiceState.partialTranscript.ifBlank { hbState.caption }
                        Surface(
                            shape = CircleShape,
                            color = GabiTokens.SurfaceDark.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, GabiTokens.CopperAccent.copy(alpha = 0.48f)),
                            shadowElevation = 4.dp,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = hbState.stage.ageGroupLabel,
                                    color = GabiTokens.CopperAccent,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "·",
                                    color = GabiTokens.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = pillText,
                                    color = GabiTokens.TextPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 220.dp)
                                )
                            }
                        }
                    }

                    if (homeVisualSource is HBVisualSource.FullSceneStageVideo ||
                        !hbState.hasDedicatedStateAnimation ||
                        reduceMotion
                    ) {
                        Spacer(Modifier.height(4.dp))
                        val honestyHint = when {
                            reduceMotion -> "Movimento reduzido (acessibilidade) · quadro estático ativo"
                            homeVisualSource is HBVisualSource.FullSceneStageVideo &&
                                hbState.motionTruth == HBMotionTruth.STAGE_PRESERVED_IDLE_ONLY ->
                                "Idade preservada (${hbState.stage.ageGroupLabel}) · vídeo idle em moldura circular (clipe do estado e recorte alfa pendentes)"
                            homeVisualSource is HBVisualSource.FullSceneStageVideo ->
                                "Vídeo completo (${hbState.stage.id}) em moldura circular provisória · recorte alfa pendente"
                            else -> hbState.motionTruth.badgeLabel
                        }
                        Text(
                            text = honestyHint,
                            color = GabiTokens.AmberAccent,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Barra tátil de ações rápidas de Voz e Quarto (nunca sobrepõe a personagem)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (voiceState.isListening) GabiTokens.ErrorRed.copy(alpha = 0.20f) else GabiTokens.SurfaceDark.copy(alpha = 0.90f),
                            border = BorderStroke(1.dp, if (voiceState.isListening) GabiTokens.ErrorRed else GabiTokens.GoldAccent.copy(alpha = 0.65f)),
                            modifier = Modifier.clickable {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (granted) viewModel.onMicTap(true) else audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (voiceState.isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                                    contentDescription = "Falar com a Gabi",
                                    tint = if (voiceState.isListening) GabiTokens.ErrorRed else GabiTokens.GoldAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (voiceState.isListening) "Ouvindo…" else "Falar",
                                    color = GabiTokens.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (liveActive) GabiTokens.SuccessGreen.copy(alpha = 0.18f) else GabiTokens.SurfaceDark.copy(alpha = 0.90f),
                            border = BorderStroke(1.dp, if (liveActive) GabiTokens.SuccessGreen else GabiTokens.TealAccent.copy(alpha = 0.65f)),
                            modifier = Modifier.clickable {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    viewModel.onLiveTap(true)
                                } else {
                                    pendingLiveAfterPermission = true
                                    audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = "Alternar Gemini Live",
                                    tint = if (liveActive) GabiTokens.SuccessGreen else GabiTokens.TealAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (liveActive) "Live ON" else "Live",
                                    color = GabiTokens.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = GabiTokens.SurfaceDark.copy(alpha = 0.90f),
                            border = BorderStroke(1.dp, GabiTokens.CopperAccent.copy(alpha = 0.55f)),
                            modifier = Modifier.clickable { navController.navigate(Screen.Habitat.route) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MeetingRoom,
                                    contentDescription = "Quarto da Gabi",
                                    tint = GabiTokens.CopperAccent,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "Quarto",
                                    color = GabiTokens.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    if (voiceState.fallbackActiveReason != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Voz em fallback explícito: ${voiceState.fallbackActiveReason}",
                            color = GabiTokens.AmberAccent,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    val shownError = voiceState.liveConnectionError ?: voiceError
                    if (shownError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = shownError,
                            color = GabiTokens.ErrorRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (processing) {
                        Spacer(Modifier.height(10.dp))
                        ActiveExecutionBanner(
                            statusLabel = turnState.activeTaskDescription,
                            onStop = { viewModel.stopAll() }
                        )
                    }

                    turnState.pendingConfirmation?.let { pending ->
                        Spacer(Modifier.height(10.dp))
                        ConfirmActionBar(
                            prompt = pending.promptToUser,
                            onConfirm = { viewModel.resolveConfirmation(true) },
                            onCancel = { viewModel.resolveConfirmation(false) }
                        )
                    }

                    turnState.lastProviderFailure?.let { failure ->
                        Spacer(Modifier.height(8.dp))
                        ProviderFailureCard(
                            failure = failure,
                            onOpenDetails = { detailFailure = failure },
                            onOpenSettings = { navController.navigate(Screen.Settings.route) },
                            onRetry = { viewModel.retryLastPrompt() }
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassInfoCard(
                            label = "Clima",
                            value = weather?.let { "${it.tempC}°C ${it.cityName}" } ?: "Fortaleza",
                            accent = GabiTokens.TealAccent,
                            fillFraction = 1f,
                            emoji = weather?.emoji ?: "☀️",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.refreshWeather() }
                        )
                        GlassInfoCard(
                            label = "Metas",
                            value = "$activeGoalsCount ativas",
                            accent = GabiTokens.GoldAccent,
                            fillFraction = (activeGoalsCount / 5f).coerceIn(0.2f, 1f),
                            icon = Icons.Default.TrackChanges,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Screen.Goals.route) }
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassInfoCard(
                            label = "Memória",
                            value = "Ver fatos",
                            accent = GabiTokens.VioletAccent,
                            fillFraction = 0.7f,
                            icon = Icons.Default.Psychology,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Screen.Memory.route) }
                        )
                        GlassInfoCard(
                            label = "Arquivos",
                            value = "SAF / Docs",
                            accent = GabiTokens.AmberAccent,
                            fillFraction = 0.8f,
                            icon = Icons.Default.Folder,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Screen.Library.route) }
                        )
                        GlassInfoCard(
                            label = "Computador",
                            value = "$unreadFeedCount avisos",
                            accent = if (spotifyPlaying) GabiTokens.SpotifyGreen else GabiTokens.SuccessGreen,
                            fillFraction = 0.9f,
                            icon = Icons.Default.Computer,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Screen.Computer.route) }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Como está o clima?" to "Clima",
                            "Ler minhas notificações" to "Notificações",
                            "O que tem na agenda?" to "Agenda",
                            "/diagnostico" to "Diagnóstico"
                        ).forEach { (prompt, chipLabel) ->
                            Surface(
                                shape = CircleShape,
                                color = GabiTokens.SurfaceDark.copy(alpha = 0.88f),
                                border = BorderStroke(1.dp, GabiTokens.GoldAccent.copy(alpha = 0.25f)),
                                modifier = Modifier.clickable { viewModel.submitPrompt(prompt) }
                            ) {
                                Text(
                                    text = chipLabel,
                                    color = GabiTokens.GoldAccent,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = quickPrompt,
                        onValueChange = { quickPrompt = it },
                        placeholder = { Text("Peça algo à Gabi…", color = GabiTokens.TextSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GabiTokens.GoldAccent.copy(alpha = 0.7f),
                            unfocusedBorderColor = GabiTokens.Hairline,
                            focusedContainerColor = GabiTokens.SurfaceDark.copy(alpha = 0.85f),
                            unfocusedContainerColor = GabiTokens.SurfaceDark.copy(alpha = 0.75f),
                            focusedTextColor = GabiTokens.TextPrimary,
                            unfocusedTextColor = GabiTokens.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Text(
                                text = "Enviar",
                                color = GabiTokens.GoldAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        if (quickPrompt.isNotBlank()) {
                                            viewModel.submitPrompt(quickPrompt)
                                            quickPrompt = ""
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    )

                    if (recent.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Conversa recente", style = MaterialTheme.typography.titleMedium, color = GabiTokens.TextPrimary)
                            Text(
                                text = "Chat completo →",
                                color = GabiTokens.GoldAccent,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.clickable { navController.navigate(Screen.Chat.route) }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        recent.reversed().forEach { msg ->
                            val isUser = msg.role == "USER"
                            GabiFeaturedSurface(
                                borderColor = if (isUser) GabiTokens.CopperAccent.copy(alpha = 0.32f) else GabiTokens.TealAccent.copy(alpha = 0.24f),
                                cornerRadius = 16.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (isUser) "Você" else "Gabi",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUser) GabiTokens.GoldAccent else GabiTokens.TealAccent
                                )
                                Text(
                                    text = msg.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GabiTokens.TextPrimary,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    detailFailure?.let {
        ProviderFailureDetailDialog(failure = it, onDismiss = { detailFailure = null })
    }
}
