package com.novoagentehb.app.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.novoagentehb.app.app.navigation.Screen
import com.novoagentehb.app.data.prefs.ProviderId
import com.novoagentehb.app.data.prefs.VoiceModeId
import com.novoagentehb.app.data.security.SecureKeyVault
import com.novoagentehb.app.design.CharacterBibleDialog
import com.novoagentehb.app.design.GabiAtmosphericBackdrop
import com.novoagentehb.app.design.GabiBottomBar
import com.novoagentehb.app.design.GabiFeaturedSurface
import com.novoagentehb.app.design.GabiTokens
import com.novoagentehb.app.design.GabiTopBar
import com.novoagentehb.app.gabi.GabiAssetImage

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val primaryProvider by viewModel.primaryProvider.collectAsState()
    val chatModel by viewModel.chatModel.collectAsState()
    val liveModel by viewModel.liveVoiceModel.collectAsState()
    val discoveredLiveModels by viewModel.discoveredLiveModels.collectAsState()
    val voiceMode by viewModel.voiceMode.collectAsState()
    val prosodyProfile by viewModel.prosodyProfile.collectAsState()
    val defaultCity by viewModel.defaultCity.collectAsState()
    val allowPaidAuto by viewModel.allowPaidAuto.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()
    val exportJson by viewModel.exportJson.collectAsState()
    val diagnosticsText by viewModel.diagnosticsText.collectAsState()

    var geminiKeyInput by remember { mutableStateOf("") }
    var groqKeyInput by remember { mutableStateOf("") }
    var openRouterKeyInput by remember { mutableStateOf("") }
    var deepSeekKeyInput by remember { mutableStateOf("") }
    var anthropicKeyInput by remember { mutableStateOf("") }
    var elevenLabsKeyInput by remember { mutableStateOf("") }
    var spotifyIdInput by remember { mutableStateOf("") }
    var spotifySecretInput by remember { mutableStateOf("") }

    var cityInput by remember(defaultCity) { mutableStateOf(defaultCity) }
    var chatModelInput by remember(chatModel) { mutableStateOf(chatModel) }
    var liveModelInput by remember(liveModel) { mutableStateOf(liveModel) }
    var showBibleDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = GabiTokens.NightBg,
        bottomBar = { GabiBottomBar(navController) }
    ) { innerPadding ->
        GabiAtmosphericBackdrop(backdropAlpha = 0.55f) {
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
                        title = "Ajustes · Gemini & Voz",
                        subtitle = "Chaves BYOK, modelos e identidade da HB",
                        onBack = { navController.popBackStack() },
                        actions = {
                            TextButton(onClick = { navController.navigate(Screen.Permissions.route) }) {
                                Text("Permissões", color = GabiTokens.GoldAccent)
                            }
                        }
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        statusBanner?.let { banner ->
                            item {
                                GabiFeaturedSurface(borderColor = GabiTokens.SuccessGreen.copy(alpha = 0.45f), cornerRadius = 14.dp) {
                                    Text(banner, color = GabiTokens.SuccessGreen)
                                }
                            }
                        }

                        // Cartão oficial R1.4 da Bíblia Visual & Identidade HB
                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.CopperAccent.copy(alpha = 0.45f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    GabiAssetImage(
                                        fileName = "theme/gabitama_brand_crest.png",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Identidade Visual GabiTama", color = GabiTokens.GoldAccent, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Bíblia de Personagem (bible.png) & Spritesheet oficial integrados ao pacote.",
                                            color = GabiTokens.TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { showBibleDialog = true },
                                        shape = CircleShape,
                                        border = BorderStroke(1.dp, GabiTokens.GoldAccent.copy(alpha = 0.5f))
                                    ) {
                                        Text("Abrir Bíblia", color = GabiTokens.GoldAccent, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.GoldAccent.copy(alpha = 0.25f)) {
                                Text("Provedor principal (Gemini padrão)", style = MaterialTheme.typography.titleMedium, color = GabiTokens.GoldAccent)
                                Spacer(Modifier.height(6.dp))
                                ProviderId.entries.forEach { p ->
                                    val selected = p == primaryProvider
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) GabiTokens.GoldAccent.copy(alpha = 0.16f) else GabiTokens.NightBg,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) GabiTokens.GoldAccent else GabiTokens.Hairline
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable { viewModel.selectProvider(p) }
                                    ) {
                                        Text(
                                            text = "${if (selected) "✓ " else ""}${p.displayName}",
                                            color = if (selected) GabiTokens.GoldAccent else GabiTokens.TextPrimary,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.Hairline) {
                                Text("Chaves isoladas (Keystore AES256-GCM · BYOK)", style = MaterialTheme.typography.titleMedium, color = GabiTokens.GoldAccent)
                                Text(
                                    text = "Nenhuma chave é embutida no APK; todas são salvas localmente no cofre seguro.",
                                    color = GabiTokens.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(6.dp))
                                ApiKeyRow("Gemini (${if (viewModel.hasKey(SecureKeyVault.KEY_GEMINI)) "configurada" else "ausente"})", geminiKeyInput, { geminiKeyInput = it }) {
                                    viewModel.saveApiKey(SecureKeyVault.KEY_GEMINI, geminiKeyInput)
                                    geminiKeyInput = ""
                                }
                                ApiKeyRow("Groq (${if (viewModel.hasKey(SecureKeyVault.KEY_GROQ)) "configurada" else "ausente"})", groqKeyInput, { groqKeyInput = it }) {
                                    viewModel.saveApiKey(SecureKeyVault.KEY_GROQ, groqKeyInput)
                                    groqKeyInput = ""
                                }
                                ApiKeyRow("OpenRouter (${if (viewModel.hasKey(SecureKeyVault.KEY_OPENROUTER)) "configurada" else "ausente"})", openRouterKeyInput, { openRouterKeyInput = it }) {
                                    viewModel.saveApiKey(SecureKeyVault.KEY_OPENROUTER, openRouterKeyInput)
                                    openRouterKeyInput = ""
                                }
                                ApiKeyRow("DeepSeek (${if (viewModel.hasKey(SecureKeyVault.KEY_DEEPSEEK)) "configurada" else "ausente"})", deepSeekKeyInput, { deepSeekKeyInput = it }) {
                                    viewModel.saveApiKey(SecureKeyVault.KEY_DEEPSEEK, deepSeekKeyInput)
                                    deepSeekKeyInput = ""
                                }
                                ApiKeyRow("Anthropic Claude (${if (viewModel.hasKey(SecureKeyVault.KEY_ANTHROPIC)) "configurada" else "ausente"})", anthropicKeyInput, { anthropicKeyInput = it }) {
                                    viewModel.saveApiKey(SecureKeyVault.KEY_ANTHROPIC, anthropicKeyInput)
                                    anthropicKeyInput = ""
                                }
                                ApiKeyRow("ElevenLabs (${if (viewModel.hasKey(SecureKeyVault.KEY_ELEVENLABS)) "configurada" else "ausente"})", elevenLabsKeyInput, { elevenLabsKeyInput = it }) {
                                    viewModel.saveApiKey(SecureKeyVault.KEY_ELEVENLABS, elevenLabsKeyInput)
                                    elevenLabsKeyInput = ""
                                }
                                ApiKeyRow("Spotify Client ID (${if (viewModel.hasKey(SecureKeyVault.KEY_SPOTIFY_CLIENT_ID)) "configurada" else "ausente"})", spotifyIdInput, { spotifyIdInput = it }) {
                                    viewModel.saveApiKey(SecureKeyVault.KEY_SPOTIFY_CLIENT_ID, spotifyIdInput)
                                    spotifyIdInput = ""
                                }
                                ApiKeyRow("Spotify Client Secret (${if (viewModel.hasKey(SecureKeyVault.KEY_SPOTIFY_CLIENT_SECRET)) "configurada" else "ausente"})", spotifySecretInput, { spotifySecretInput = it }) {
                                    viewModel.saveApiKey(SecureKeyVault.KEY_SPOTIFY_CLIENT_SECRET, spotifySecretInput)
                                    spotifySecretInput = ""
                                }
                            }
                        }

                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.Hairline) {
                                Text("Modelos ativos", style = MaterialTheme.typography.titleMedium, color = GabiTokens.GoldAccent)
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = chatModelInput,
                                    onValueChange = { chatModelInput = it },
                                    label = { Text("Modelo de chat") },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GabiTokens.GoldAccent, unfocusedBorderColor = GabiTokens.Hairline),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = liveModelInput,
                                    onValueChange = { liveModelInput = it },
                                    label = { Text("Modelo Gemini Live (bidiGenerateContent)") },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GabiTokens.TealAccent, unfocusedBorderColor = GabiTokens.Hairline),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.updateModels(chatModelInput, liveModelInput) },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.GoldAccent)
                                    ) {
                                        Text("Salvar modelos", color = GabiTokens.NightBg, fontWeight = FontWeight.SemiBold)
                                    }
                                    OutlinedButton(onClick = { viewModel.discoverGeminiLiveModels() }, shape = CircleShape) {
                                        Text("Verificar modelos Live", color = GabiTokens.TealAccent)
                                    }
                                }
                                if (discoveredLiveModels.isNotEmpty()) {
                                    Text("Candidatos bidirecionais: confirme compatibilidade de áudio, acesso e preço antes de selecionar. A listagem não garante gratuidade.", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(6.dp))
                                    discoveredLiveModels.forEach { m ->
                                        Text(
                                            text = "• $m",
                                            color = GabiTokens.TealAccent,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    liveModelInput = m
                                                    viewModel.selectDiscoveredLiveModel(m)
                                                }
                                                .padding(vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.TealAccent.copy(alpha = 0.28f)) {
                                Text("Motor de Voz e Prosódia (gabi_24l)", style = MaterialTheme.typography.titleMedium, color = GabiTokens.GoldAccent)
                                Text(
                                    text = "Nota de transparência: Gemini Live usa voz pré-construída Kore do modelo selecionado; o runtime local Pocket TTS (gabi_24l) não está embarcado no APK (fallback explícito para TTS Android).",
                                    color = GabiTokens.AmberAccent,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(6.dp))
                                VoiceModeId.entries.forEach { vm ->
                                    val selected = vm == voiceMode
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) GabiTokens.TealAccent.copy(alpha = 0.16f) else GabiTokens.NightBg,
                                        border = BorderStroke(1.dp, if (selected) GabiTokens.TealAccent else GabiTokens.Hairline),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable { viewModel.selectVoiceMode(vm) }
                                    ) {
                                        Text(
                                            text = "${if (selected) "✓ " else ""}${vm.label}",
                                            color = if (selected) GabiTokens.TealAccent else GabiTokens.TextPrimary,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("Perfil de prosódia congelado:", color = GabiTokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                viewModel.voiceProfiles.forEach { prof ->
                                    val selected = prof.id == prosodyProfile
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) GabiTokens.GoldAccent.copy(alpha = 0.14f) else GabiTokens.NightBg,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clickable { viewModel.selectProsody(prof.id) }
                                    ) {
                                        Text(
                                            text = "${if (selected) "✓ " else ""}${prof.id} (max_tokens=${prof.maxTokens}, ${prof.measuredWpm} wpm)",
                                            color = GabiTokens.TextPrimary,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.Hairline) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Liberar chamadas pagas sem confirmação", color = GabiTokens.TextPrimary)
                                        Text("Padrão desligado para evitar gasto acidental de cota.", color = GabiTokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Switch(checked = allowPaidAuto, onCheckedChange = { viewModel.setAllowPaidAuto(it) })
                                }
                            }
                        }

                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.Hairline) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = cityInput,
                                        onValueChange = { cityInput = it },
                                        label = { Text("Cidade padrão (clima)") },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GabiTokens.GoldAccent, unfocusedBorderColor = GabiTokens.Hairline),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Button(
                                        onClick = { viewModel.setCity(cityInput) },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.GoldAccent)
                                    ) { Text("Salvar", color = GabiTokens.NightBg, fontWeight = FontWeight.SemiBold) }
                                }
                            }
                        }

                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.generateExport() }, shape = CircleShape, modifier = Modifier.weight(1f)) {
                                    Text("Exportar JSON", color = GabiTokens.GoldAccent)
                                }
                                OutlinedButton(onClick = { viewModel.runLocalDiagnostics() }, shape = CircleShape, modifier = Modifier.weight(1f)) {
                                    Text("Diagnóstico", color = GabiTokens.TealAccent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBibleDialog) {
        CharacterBibleDialog(onDismiss = { showBibleDialog = false })
    }

    exportJson?.let { json ->
        AlertDialog(
            onDismissRequest = { viewModel.clearExportPreview() },
            containerColor = GabiTokens.SurfaceDark,
            title = { Text("Exportação explícita", color = GabiTokens.GoldAccent) },
            text = { Text(json.take(1600), color = GabiTokens.TextPrimary, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearExportPreview() }) { Text("Fechar", color = GabiTokens.GoldAccent) }
            }
        )
    }

    diagnosticsText?.let { report ->
        AlertDialog(
            onDismissRequest = { viewModel.clearDiagnostics() },
            containerColor = GabiTokens.SurfaceDark,
            title = { Text("Diagnóstico Local", color = GabiTokens.TealAccent) },
            text = { Text(report, color = GabiTokens.TextPrimary, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearDiagnostics() }) { Text("Fechar", color = GabiTokens.GoldAccent) }
            }
        )
    }
}

@Composable
private fun ApiKeyRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GabiTokens.GoldAccent, unfocusedBorderColor = GabiTokens.Hairline),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onSave,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.GoldAccent)
        ) { Text("Salvar", color = GabiTokens.NightBg, fontWeight = FontWeight.SemiBold) }
    }
}
