package com.novoagentehb.app.ui.computer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.novoagentehb.app.agent.orchestrator.TaskStatus
import com.novoagentehb.app.agent.orchestrator.ToolExecutionSemantics
import com.novoagentehb.app.agent.skills.LocalSkillExecutor
import com.novoagentehb.app.design.ActiveExecutionBanner
import com.novoagentehb.app.design.ConfirmActionBar
import com.novoagentehb.app.design.GabiAtmosphericBackdrop
import com.novoagentehb.app.design.GabiBottomBar
import com.novoagentehb.app.design.GabiFeaturedSurface
import com.novoagentehb.app.design.GabiTokens
import com.novoagentehb.app.design.GabiTopBar
import com.novoagentehb.app.gabi.FrameSequenceAnimation
import com.novoagentehb.app.gabi.HBCharacterStage
import com.novoagentehb.app.gabi.StageLoopVideo
import com.novoagentehb.app.gabi.rememberAnimFrameCount
import com.novoagentehb.app.gabi.rememberReduceMotionEnabled

@Composable
fun ComputerScreen(
    navController: NavController,
    viewModel: ComputerViewModel = hiltViewModel()
) {
    val skills by viewModel.skills.collectAsState()
    val turnState by viewModel.turnState.collectAsState()
    val hbState by viewModel.hbState.collectAsState()
    val mcpToolsByServer by viewModel.mcpToolsByServer.collectAsState()
    val mcpAllowLanByServer by viewModel.mcpAllowLanByServer.collectAsState()
    val mcpLastLog by viewModel.mcpLastLog.collectAsState()
    val statusNotice by viewModel.statusNotice.collectAsState()
    val runtimeNotice by viewModel.runtimeNotice.collectAsState()
    val sceneFrames = rememberAnimFrameCount("scene_computador") ?: 0

    var expression by remember { mutableStateOf("") }
    var numbers by remember { mutableStateOf("") }
    var skillName by remember { mutableStateOf("") }
    var skillPrompt by remember { mutableStateOf("") }
    var skillWriteVerifyMode by remember { mutableStateOf(false) }
    var mcpName by remember { mutableStateOf("") }
    var mcpEndpoint by remember { mutableStateOf("") }
    var mcpAllowLanNew by remember { mutableStateOf(false) }

    val processing = turnState.status == TaskStatus.PLANNING ||
        turnState.status == TaskStatus.EXECUTING ||
        turnState.status == TaskStatus.VERIFYING

    Scaffold(
        containerColor = GabiTokens.NightBg,
        bottomBar = { GabiBottomBar(navController, activeStage = hbState.stage) }
    ) { innerPadding ->
        GabiAtmosphericBackdrop(backdropAlpha = 0.50f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize()
                ) {
                    GabiTopBar(
                        title = "Computador & Skills",
                        subtitle = "${hbState.stage.ageGroupLabel} · ${viewModel.capabilityCount} capabilities",
                        onBack = { navController.popBackStack() },
                        actions = {
                            Button(
                                onClick = { viewModel.runDiagnosticsCommand() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.GoldAccent)
                            ) {
                                Text("Diagnóstico", color = GabiTokens.NightBg, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )

                    if (processing) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            ActiveExecutionBanner(
                                statusLabel = turnState.activeTaskDescription,
                                onStop = { viewModel.stopAll() }
                            )
                        }
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

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            // Cena hero sem recorte quadrado artificial e coerente com a fase selecionada
                            GabiFeaturedSurface(borderColor = GabiTokens.CopperAccent.copy(alpha = 0.35f)) {
                                if (hbState.stage == HBCharacterStage.YOUNG_CHILD && sceneFrames > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(178.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FrameSequenceAnimation(
                                            folder = "scene_computador",
                                            frameCount = sceneFrames,
                                            fps = 18,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        val stageVideo = hbState.stage.assetVideoPath
                                        Box(
                                            modifier = Modifier
                                                .size(92.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, GabiTokens.GoldAccent, CircleShape)
                                        ) {
                                            if (stageVideo != null) {
                                                StageLoopVideo(
                                                    assetPath = stageVideo,
                                                    fallbackPng = hbState.stage.avatarCirclePngPath,
                                                    reduceMotion = rememberReduceMotionEnabled(),
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        Column {
                                            Text("HB no Computador", color = GabiTokens.GoldAccent, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Fase ativa: ${hbState.stage.label}",
                                                color = GabiTokens.TextPrimary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(hbState.caption, color = GabiTokens.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                            Text(
                                                hbState.motionTruth.badgeLabel,
                                                color = GabiTokens.AmberAccent,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (statusNotice != null) {
                            item {
                                GabiFeaturedSurface(borderColor = GabiTokens.TealAccent.copy(alpha = 0.45f), cornerRadius = 14.dp) {
                                    Text(statusNotice.orEmpty(), color = GabiTokens.TextPrimary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        if (runtimeNotice != null) {
                            item {
                                GabiFeaturedSurface(borderColor = GabiTokens.TealAccent.copy(alpha = 0.3f), cornerRadius = 14.dp) {
                                    Text(runtimeNotice.orEmpty(), color = GabiTokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        if (mcpLastLog.isNotEmpty()) {
                            item {
                                GabiFeaturedSurface(borderColor = GabiTokens.GoldAccent.copy(alpha = 0.3f), cornerRadius = 14.dp) {
                                    Text("Log de negociação MCP", color = GabiTokens.GoldAccent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                                    mcpLastLog.forEach { line ->
                                        Text(line, color = GabiTokens.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        item {
                            val semSummary = viewModel.capabilitiesBySemantics
                            GabiFeaturedSurface(borderColor = GabiTokens.TealAccent.copy(alpha = 0.28f)) {
                                Text(
                                    "Catálogo declarado (${viewModel.capabilityCount} tools · INTEGRATED_UNTESTED)",
                                    color = GabiTokens.TealAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("Estado HB: ${hbState.caption}", color = GabiTokens.GoldAccent, style = MaterialTheme.typography.labelSmall)
                                ToolExecutionSemantics.entries.forEach { sem ->
                                    val count = semSummary[sem] ?: 0
                                    if (count > 0) {
                                        Text(
                                            text = "• ${sem.label}: $count",
                                            color = GabiTokens.TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.AmberAccent.copy(alpha = 0.42f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = GabiTokens.AmberAccent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Recursos não implementados nesta versão", color = GabiTokens.AmberAccent, fontWeight = FontWeight.SemiBold)
                                        viewModel.unimplementedItems.forEach { (id, desc) ->
                                            Text(
                                                text = "• $id: $desc",
                                                color = GabiTokens.TextSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.Hairline) {
                                Text("Cálculo local limitado", style = MaterialTheme.typography.titleMedium, color = GabiTokens.TextPrimary)
                                Text("Expressões aritméticas com x, +, -, *, / e parênteses; produz um CSV privado após autorização. Não é terminal Linux nem executa Python.", color = GabiTokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = expression, onValueChange = { expression = it },
                                    label = { Text("Expressão (ex.: x*2+1)") },
                                    modifier = Modifier.fillMaxWidth().testTag("workspace_expression"), singleLine = true
                                )
                                OutlinedTextField(
                                    value = numbers, onValueChange = { numbers = it },
                                    label = { Text("Números separados por vírgula") },
                                    modifier = Modifier.fillMaxWidth().testTag("workspace_numbers"), maxLines = 3
                                )
                                Button(onClick = { viewModel.requestWorkspaceProgram(expression, numbers) },
                                    enabled = expression.isNotBlank() && numbers.isNotBlank() && !processing && turnState.pendingConfirmation == null) {
                                    Text("Preparar cálculo")
                                }
                            }
                        }

                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.Hairline) {
                                Text("Adicionar Skill local (workspace privado)", style = MaterialTheme.typography.titleMedium, color = GabiTokens.TextPrimary)
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = skillName,
                                    onValueChange = { skillName = it },
                                    placeholder = { Text("Nome da skill") },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GabiTokens.GoldAccent, unfocusedBorderColor = GabiTokens.Hairline),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = skillPrompt,
                                    onValueChange = { skillPrompt = it },
                                    placeholder = { Text("Instrução ou conteúdo do documento") },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GabiTokens.GoldAccent, unfocusedBorderColor = GabiTokens.Hairline),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (skillWriteVerifyMode) {
                                            "Modo: Adaptador workspace_write_verify (grava + relê SHA-256)"
                                        } else {
                                            "Modo: Manifesto informativo (não executa instruções livres)"
                                        },
                                        color = GabiTokens.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(checked = skillWriteVerifyMode, onCheckedChange = { skillWriteVerifyMode = it })
                                }
                                Button(
                                    onClick = {
                                        viewModel.registerSkill(skillName, skillPrompt, skillWriteVerifyMode)
                                        skillName = ""
                                        skillPrompt = ""
                                    },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.GoldAccent)
                                ) { Text("Registrar Skill", color = GabiTokens.NightBg, fontWeight = FontWeight.SemiBold) }
                            }
                        }

                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.Hairline) {
                                Text("Registrar servidor MCP (Streamable HTTP POST · opt-in)", style = MaterialTheme.typography.titleMedium, color = GabiTokens.TextPrimary)
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = mcpName,
                                    onValueChange = { mcpName = it },
                                    placeholder = { Text("Nome do servidor MCP") },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GabiTokens.TealAccent, unfocusedBorderColor = GabiTokens.Hairline),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = mcpEndpoint,
                                    onValueChange = { mcpEndpoint = it },
                                    placeholder = { Text("https://…") },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GabiTokens.TealAccent, unfocusedBorderColor = GabiTokens.Hairline),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Permitir LAN/localhost (laboratório local opt-in)",
                                        color = GabiTokens.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(checked = mcpAllowLanNew, onCheckedChange = { mcpAllowLanNew = it })
                                }
                                Button(
                                    onClick = {
                                        viewModel.registerMcp(mcpName, mcpEndpoint, mcpAllowLanNew)
                                        mcpName = ""
                                        mcpEndpoint = ""
                                    },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.TealAccent)
                                ) { Text("Adicionar MCP (opt-in)", color = GabiTokens.NightBg, fontWeight = FontWeight.SemiBold) }
                            }
                        }

                        items(skills, key = { it.skillId }) { skill ->
                            val remoteTools = mcpToolsByServer[skill.skillId].orEmpty()
                            val allowLan = mcpAllowLanByServer[skill.skillId] ?: (skill.scope == "opt_in_lan_or_external")
                            GabiFeaturedSurface(borderColor = GabiTokens.Hairline, cornerRadius = 16.dp) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${skill.name} [${skill.sourceType} v${skill.version}]", color = GabiTokens.TextPrimary, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "untrusted=${skill.untrustedContentFlag} · ${skill.scope} · ${skill.summary}",
                                            color = GabiTokens.TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = skill.enabledByUser,
                                            onCheckedChange = { viewModel.toggleSkill(skill.skillId, it) }
                                        )
                                        IconButton(onClick = { viewModel.deleteSkill(skill.skillId) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remover", tint = GabiTokens.TextSecondary)
                                        }
                                    }
                                }

                                if (skill.sourceType == "LOCAL_SKILL") {
                                    val isAction = skill.scope == LocalSkillExecutor.SCOPE_WORKSPACE_WRITE_VERIFY
                                    OutlinedButton(
                                        enabled = skill.enabledByUser,
                                        onClick = { viewModel.runLocalSkill(skill.skillId) }
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            when {
                                                !skill.enabledByUser -> "Ative o opt-in"
                                                isAction -> "Executar Ação Workspace (SHA-256)"
                                                else -> "Preparar Manifesto (não executa ação)"
                                            }
                                        )
                                    }
                                } else if (skill.sourceType == "MCP_SERVER") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Permitir LAN local (opt-in)", color = GabiTokens.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                        Switch(checked = allowLan, onCheckedChange = { viewModel.setMcpAllowLan(skill.skillId, it) })
                                    }
                                    OutlinedButton(
                                        enabled = skill.enabledByUser,
                                        onClick = { viewModel.verifyMcpServer(skill.skillId) }
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (skill.enabledByUser) "Testar Handshake (initialize + notify + tools/list)" else "Ative o opt-in para testar")
                                    }
                                    remoteTools.forEach { rTool ->
                                        OutlinedButton(
                                            onClick = { viewModel.requestMcpTool(skill.skillId, rTool.name) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Executar MCP: ${rTool.name} (pede confirmação)")
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}
