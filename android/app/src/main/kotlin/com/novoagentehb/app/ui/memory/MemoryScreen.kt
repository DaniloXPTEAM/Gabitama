package com.novoagentehb.app.ui.memory

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.novoagentehb.app.data.local.MemoryCategory
import com.novoagentehb.app.design.GabiAtmosphericBackdrop
import com.novoagentehb.app.design.GabiBottomBar
import com.novoagentehb.app.design.GabiCircleActionButton
import com.novoagentehb.app.design.GabiEmptyStateCard
import com.novoagentehb.app.design.GabiFeaturedSurface
import com.novoagentehb.app.design.GabiTokens
import com.novoagentehb.app.design.GabiTopBar

@Composable
fun MemoryScreen(
    navController: NavController,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val memories by viewModel.memories.collectAsState()
    var keyInput by remember { mutableStateOf("") }
    var valueInput by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

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
                        title = "Memória da Gabi",
                        subtitle = "${memories.size} fatos locais com procedência",
                        onBack = { navController.popBackStack() },
                        actions = {
                            if (memories.isNotEmpty()) {
                                GabiCircleActionButton(
                                    icon = Icons.Default.DeleteOutline,
                                    contentDescription = "Limpar memórias",
                                    tint = GabiTokens.ErrorRed,
                                    borderColor = GabiTokens.ErrorRed.copy(alpha = 0.4f),
                                    onClick = { confirmClear = true }
                                )
                            }
                        }
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.VioletAccent.copy(alpha = 0.28f)) {
                                Text("Registrar novo fato", color = GabiTokens.VioletAccent, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = keyInput,
                                    onValueChange = { keyInput = it },
                                    placeholder = { Text("Chave (ex: cidade_natal)", color = GabiTokens.TextSecondary) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GabiTokens.VioletAccent,
                                        unfocusedBorderColor = GabiTokens.Hairline
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = valueInput,
                                    onValueChange = { valueInput = it },
                                    placeholder = { Text("Valor lembrado…", color = GabiTokens.TextSecondary) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GabiTokens.VioletAccent,
                                        unfocusedBorderColor = GabiTokens.Hairline
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.addMemory(keyInput, valueInput, MemoryCategory.FACT)
                                        keyInput = ""
                                        valueInput = ""
                                    },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.GoldAccent)
                                ) {
                                    Text("Salvar fato", color = GabiTokens.NightBg, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (memories.isEmpty()) {
                            item {
                                GabiEmptyStateCard(
                                    title = "Sem memórias gravadas",
                                    subtitle = "Fatos confirmados aparecem aqui com procedência e exclusão explícita.",
                                    accent = GabiTokens.VioletAccent
                                )
                            }
                        } else {
                            items(memories, key = { it.id }) { mem ->
                                GabiFeaturedSurface(
                                    borderColor = GabiTokens.TealAccent.copy(alpha = 0.24f),
                                    cornerRadius = 16.dp
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${mem.memoryKey} (${mem.category})", color = GabiTokens.TealAccent, fontWeight = FontWeight.SemiBold)
                                            Text(mem.value, color = GabiTokens.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                            Text("Origem: ${mem.provenance} · Confiança: ${(mem.confidence * 100).toInt()}%", color = GabiTokens.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remover", tint = GabiTokens.TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = GabiTokens.SurfaceDark,
            title = { Text("Apagar toda a memória?", color = GabiTokens.ErrorRed) },
            text = { Text("Esta ação remove todos os fatos locais da Gabi e não pode ser desfeita.", color = GabiTokens.TextPrimary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        confirmClear = false
                    }
                ) { Text("Apagar tudo", color = GabiTokens.ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancelar", color = GabiTokens.TextSecondary) }
            }
        )
    }
}
