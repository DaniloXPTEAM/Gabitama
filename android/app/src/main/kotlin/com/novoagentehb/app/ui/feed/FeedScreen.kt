package com.novoagentehb.app.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.novoagentehb.app.design.GabiAtmosphericBackdrop
import com.novoagentehb.app.design.GabiBottomBar
import com.novoagentehb.app.design.GabiCircleActionButton
import com.novoagentehb.app.design.GabiEmptyStateCard
import com.novoagentehb.app.design.GabiFeaturedSurface
import com.novoagentehb.app.design.GabiTokens
import com.novoagentehb.app.design.GabiTopBar

@Composable
fun FeedScreen(
    navController: NavController,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val notifications by viewModel.notifications.collectAsState()
    val receipts by viewModel.receipts.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }

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
                        title = "Avisos & Recibos",
                        subtitle = "${notifications.size} avisos · ${receipts.size} recibos verificados",
                        onBack = { navController.popBackStack() },
                        actions = {
                            if (tabIndex == 0 && notifications.isNotEmpty()) {
                                GabiCircleActionButton(
                                    icon = Icons.Default.DeleteOutline,
                                    contentDescription = "Limpar feed",
                                    tint = GabiTokens.TextSecondary,
                                    onClick = { viewModel.clearNotifications() }
                                )
                            }
                        }
                    )

                    // Seletor em pílula segmentada ao estilo R1.4
                    Surface(
                        shape = CircleShape,
                        color = GabiTokens.SurfaceDark.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, GabiTokens.Hairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "Notificações (${notifications.size})",
                                "Recibos (${receipts.size})"
                            ).forEachIndexed { idx, label ->
                                val selected = tabIndex == idx
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) GabiTokens.CopperAccent.copy(alpha = 0.25f) else Color.Transparent,
                                    border = if (selected) BorderStroke(1.dp, GabiTokens.GoldAccent.copy(alpha = 0.45f)) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { tabIndex = idx }
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) GabiTokens.GoldAccent else GabiTokens.TextSecondary,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 9.dp)
                                    )
                                }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (tabIndex == 0) {
                            if (notifications.isEmpty()) {
                                item {
                                    GabiEmptyStateCard(
                                        title = "Feed vazio",
                                        subtitle = "Nenhuma notificação capturada ainda. Quando o leitor estiver autorizado, avisos aparecem aqui.",
                                        accent = GabiTokens.TealAccent
                                    )
                                }
                            } else {
                                items(notifications, key = { it.id }) { item ->
                                    FeedRowCard("[${item.sourceLabel}] ${item.title}", item.body, GabiTokens.TealAccent)
                                }
                            }
                        } else {
                            if (receipts.isEmpty()) {
                                item {
                                    GabiEmptyStateCard(
                                        title = "Sem execuções recentes",
                                        subtitle = "Os recibos auditáveis de ferramentas e ações aparecem aqui após cada tarefa.",
                                        accent = GabiTokens.GoldAccent
                                    )
                                }
                            } else {
                                items(receipts, key = { it.taskId }) { rec ->
                                    val color = when (rec.status) {
                                        "COMPLETED" -> if (rec.verifiedOutcome) GabiTokens.SuccessGreen else GabiTokens.AmberAccent
                                        "BLOCKED_POLICY", "FAILED" -> GabiTokens.ErrorRed
                                        else -> GabiTokens.GoldAccent
                                    }
                                    FeedRowCard(
                                        "${rec.capabilityName} · ${rec.status}",
                                        "${rec.summary}\nVerificado: ${rec.verifiedOutcome} · ${rec.durationMs}ms",
                                        color
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRowCard(title: String, body: String, accent: Color) {
    GabiFeaturedSurface(
        borderColor = accent.copy(alpha = 0.28f),
        cornerRadius = 16.dp
    ) {
        Text(title, color = accent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(body, color = GabiTokens.TextPrimary, style = MaterialTheme.typography.bodySmall)
    }
}
