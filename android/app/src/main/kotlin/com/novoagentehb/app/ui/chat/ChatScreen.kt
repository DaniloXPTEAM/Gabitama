package com.novoagentehb.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.novoagentehb.app.design.GabiEmptyStateCard
import com.novoagentehb.app.design.GabiFeaturedSurface
import com.novoagentehb.app.design.GabiTokens
import com.novoagentehb.app.design.GabiTopBar
import com.novoagentehb.app.design.ProviderFailureCard
import com.novoagentehb.app.design.ProviderFailureDetailDialog
import com.novoagentehb.app.gabi.GabiFaceAvatar
import com.novoagentehb.app.gabi.GabiParticleOrb
import com.novoagentehb.app.gabi.HBActivity
import com.novoagentehb.app.gabi.LiveSessionState

@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val hbState by viewModel.hbState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    // Retomada: ao voltar ao GabiTama (ex.: depois do Spotify), registra o aviso do handoff pendente.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val turnState by viewModel.turnState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val voiceError by viewModel.voiceError.collectAsState()

    var input by remember { mutableStateOf("") }
    var detailFailure by remember { mutableStateOf<ProviderFailure?>(null) }
    var pendingLiveAfterPermission by remember { mutableStateOf(false) }

    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingLiveAfterPermission) {
                pendingLiveAfterPermission = false
                viewModel.toggleLiveSession(true)
            } else {
                viewModel.toggleListening(true)
            }
        } else {
            if (pendingLiveAfterPermission) viewModel.reportLiveMicrophoneDenied()
            pendingLiveAfterPermission = false
        }
    }

    val processing = turnState.status == TaskStatus.PLANNING ||
        turnState.status == TaskStatus.EXECUTING ||
        turnState.status == TaskStatus.VERIFYING

    val avatarRingColor = when (hbState.activity) {
        is HBActivity.Speaking -> GabiTokens.SuccessGreen
        is HBActivity.Listening -> GabiTokens.ErrorRed
        is HBActivity.Thinking, is HBActivity.Working -> GabiTokens.GoldAccent
        is HBActivity.Error -> GabiTokens.ErrorRed
        else -> GabiTokens.CopperAccent
    }

    val liveActive = voiceState.liveSessionState == LiveSessionState.ACTIVE ||
        voiceState.liveSessionState == LiveSessionState.CONNECTING ||
        voiceState.liveSessionState == LiveSessionState.RECONNECTING

    Scaffold(
        containerColor = GabiTokens.NightBg,
        bottomBar = { GabiBottomBar(navController, activeStage = hbState.stage) }
    ) { innerPadding ->
        GabiAtmosphericBackdrop(backdropAlpha = 0.42f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize()
                ) {
                    GabiTopBar(
                        title = "Chat com a Gabi",
                        subtitle = "${hbState.stage.ageGroupLabel} · ${hbState.caption} · ${voiceState.liveSessionState.label}",
                        onBack = { navController.popBackStack() },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(GabiTokens.SurfaceMid)
                                    .border(2.dp, avatarRingColor, CircleShape)
                                    .clickable { navController.navigate(Screen.Habitat.route) }
                            ) {
                                GabiFaceAvatar(
                                    stage = hbState.stage,
                                    modifier = Modifier.fillMaxSize(),
                                    fallback = { GabiParticleOrb(sizeDp = 44, state = hbState.avatarState) }
                                )
                            }
                        },
                        actions = {
                            GabiCircleActionButton(
                                icon = Icons.Default.Sensors,
                                contentDescription = "Gemini Live",
                                tint = if (liveActive) GabiTokens.SuccessGreen else GabiTokens.TealAccent,
                                borderColor = if (liveActive) GabiTokens.SuccessGreen else GabiTokens.Hairline,
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                        PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.toggleLiveSession(true)
                                    } else {
                                        pendingLiveAfterPermission = true
                                        audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            )
                            Surface(
                                shape = CircleShape,
                                color = GabiTokens.SurfaceDark.copy(alpha = 0.88f),
                                border = BorderStroke(1.dp, GabiTokens.GoldAccent.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { viewModel.send("/diagnostico") }
                            ) {
                                Text(
                                    text = "/diag",
                                    color = GabiTokens.GoldAccent,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                                )
                            }
                            GabiCircleActionButton(
                                icon = Icons.Default.DeleteOutline,
                                contentDescription = "Limpar conversa",
                                tint = GabiTokens.TextSecondary,
                                onClick = { viewModel.clearHistory() }
                            )
                        }
                    )

                    Text(
                        text = voiceState.fallbackActiveReason ?: voiceState.activeVoiceDisclosure,
                        style = MaterialTheme.typography.labelSmall,
                        color = GabiTokens.TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )

                    if (processing) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            ActiveExecutionBanner(
                                statusLabel = turnState.activeTaskDescription,
                                onStop = { viewModel.stopAll() }
                            )
                        }
                    }

                    val errMsg = voiceState.liveConnectionError ?: voiceError
                    if (errMsg != null) {
                        Text(
                            text = errMsg,
                            color = GabiTokens.ErrorRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    turnState.pendingConfirmation?.let { pending ->
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            ConfirmActionBar(
                                prompt = pending.promptToUser,
                                onConfirm = { viewModel.resolveConfirmation(true) },
                                onCancel = { viewModel.resolveConfirmation(false) }
                            )
                        }
                    }

                    turnState.lastProviderFailure?.let { failure ->
                        Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                            ProviderFailureCard(
                                failure = failure,
                                onOpenDetails = { detailFailure = failure },
                                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                                onRetry = { viewModel.retryLast() }
                            )
                        }
                    }

                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            GabiEmptyStateCard(
                                title = "Pronta para conversar",
                                subtitle = "Fale pelo microfone, ative o Gemini Live ou escreva abaixo. Você também pode rodar /diagnostico a qualquer momento.",
                                accent = GabiTokens.GoldAccent
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            reverseLayout = true,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                val isUser = msg.role == "USER"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                ) {
                                    GabiFeaturedSurface(
                                        borderColor = if (isUser) {
                                            GabiTokens.CopperAccent.copy(alpha = 0.42f)
                                        } else {
                                            GabiTokens.TealAccent.copy(alpha = 0.30f)
                                        },
                                        cornerRadius = 18.dp,
                                        modifier = Modifier.fillMaxWidth(0.86f)
                                    ) {
                                        Text(
                                            text = if (isUser) "Você" else "Gabi",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isUser) GabiTokens.GoldAccent else GabiTokens.TealAccent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = msg.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = GabiTokens.TextPrimary
                                        )
                                        if (!isUser) WorkspaceImagePreview(msg.content)
                                        if (!isUser && !msg.providerUsed.isNullOrBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "${msg.providerUsed} · ${msg.modelUsed.orEmpty()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GabiTokens.TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Barra de digitação em cápsula flutuante harmonizada com a pílula inferior
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = GabiTokens.SurfaceDark.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, GabiTokens.Hairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                        PackageManager.PERMISSION_GRANTED
                                    if (granted) viewModel.toggleListening(true) else audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Falar",
                                    tint = if (voiceState.isListening) GabiTokens.ErrorRed else GabiTokens.GoldAccent
                                )
                            }
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                placeholder = { Text("Escreva para a Gabi…", color = GabiTokens.TextSecondary) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedTextColor = GabiTokens.TextPrimary,
                                    unfocusedTextColor = GabiTokens.TextPrimary
                                ),
                                modifier = Modifier.weight(1f),
                                maxLines = 4
                            )
                            IconButton(
                                onClick = {
                                    if (input.isNotBlank()) {
                                        viewModel.send(input)
                                        input = ""
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = GabiTokens.GoldAccent)
                            }
                        }
                    }
                }
            }
        }
    }

    detailFailure?.let {
        ProviderFailureDetailDialog(failure = it, onDismiss = { detailFailure = null })
    }
}
