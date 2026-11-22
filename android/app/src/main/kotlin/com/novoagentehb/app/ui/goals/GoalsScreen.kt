package com.novoagentehb.app.ui.goals

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
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.novoagentehb.app.design.GabiAtmosphericBackdrop
import com.novoagentehb.app.design.GabiBottomBar
import com.novoagentehb.app.design.GabiEmptyStateCard
import com.novoagentehb.app.design.GabiFeaturedSurface
import com.novoagentehb.app.design.GabiTokens
import com.novoagentehb.app.design.GabiTopBar

@Composable
fun GoalsScreen(
    navController: NavController,
    viewModel: GoalsViewModel = hiltViewModel()
) {
    val goals by viewModel.goals.collectAsState()
    val reminders by viewModel.reminders.collectAsState()

    var goalTitle by remember { mutableStateOf("") }
    var reminderTitle by remember { mutableStateOf("") }

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
                        title = "Metas & Lembretes",
                        subtitle = "${goals.size} metas · ${reminders.size} lembretes",
                        onBack = { navController.popBackStack() }
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.GoldAccent.copy(alpha = 0.25f)) {
                                Text("Nova meta", color = GabiTokens.GoldAccent, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = goalTitle,
                                        onValueChange = { goalTitle = it },
                                        placeholder = { Text("Nova meta pessoal…", color = GabiTokens.TextSecondary) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GabiTokens.GoldAccent,
                                            unfocusedBorderColor = GabiTokens.Hairline
                                        ),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.addGoal(goalTitle, "")
                                            goalTitle = ""
                                        },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.GoldAccent)
                                    ) {
                                        Icon(Icons.Default.AddTask, contentDescription = "Adicionar meta", tint = GabiTokens.NightBg)
                                    }
                                }
                            }
                        }

                        if (goals.isEmpty() && reminders.isEmpty()) {
                            item {
                                GabiEmptyStateCard(
                                    title = "Nenhuma meta registrada ainda",
                                    subtitle = "Crie uma meta pessoal ou um lembrete rápido (+15 min) para acompanhar seu dia com a Gabi.",
                                    accent = GabiTokens.GoldAccent
                                )
                            }
                        }

                        items(goals, key = { "goal:${it.id}" }) { goal ->
                            GabiFeaturedSurface(
                                borderColor = GabiTokens.GoldAccent.copy(alpha = 0.25f),
                                cornerRadius = 16.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${goal.title} (${goal.progressPercent}%)", color = GabiTokens.TextPrimary, style = MaterialTheme.typography.titleMedium)
                                        Text("Status: ${goal.status}", color = GabiTokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row {
                                        IconButton(onClick = { viewModel.bumpProgress(goal) }) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Avançar", tint = GabiTokens.SuccessGreen)
                                        }
                                        IconButton(onClick = { viewModel.deleteGoal(goal.id) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir", tint = GabiTokens.TextSecondary)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { goal.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = GabiTokens.GoldAccent,
                                    trackColor = GabiTokens.SurfaceMid
                                )
                            }
                        }

                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.CopperAccent.copy(alpha = 0.28f)) {
                                Text("Lembretes agendados", style = MaterialTheme.typography.titleMedium, color = GabiTokens.TextPrimary)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = reminderTitle,
                                        onValueChange = { reminderTitle = it },
                                        placeholder = { Text("Lembrete rápido (+15 min)…", color = GabiTokens.TextSecondary) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GabiTokens.CopperAccent,
                                            unfocusedBorderColor = GabiTokens.Hairline
                                        ),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.addReminderInMinutes(reminderTitle, 15)
                                            reminderTitle = ""
                                        },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.CopperAccent)
                                    ) { Text("+15m", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }

                        items(reminders, key = { "reminder:${it.id}" }) { rem ->
                            GabiFeaturedSurface(
                                borderColor = if (rem.triggered) GabiTokens.Hairline else GabiTokens.AmberAccent.copy(alpha = 0.35f),
                                cornerRadius = 16.dp
                            ) {
                                Text(rem.title, color = GabiTokens.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = if (rem.triggered) "Disparado" else "Alarme armado",
                                    color = if (rem.triggered) GabiTokens.TextSecondary else GabiTokens.AmberAccent,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
