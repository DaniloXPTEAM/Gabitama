package com.novoagentehb.app.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.novoagentehb.app.app.navigation.Screen
import com.novoagentehb.app.design.GabiAtmosphericBackdrop
import com.novoagentehb.app.design.GabiFeaturedSurface
import com.novoagentehb.app.design.GabiTokens
import com.novoagentehb.app.gabi.GabiAssetImage

/**
 * Tela de Permissões R1.4 com `safeDrawingPadding()` obrigatório para impedir
 * botões escondidos sob barra de gestos ou recorte superior.
 */
@Composable
fun PermissionsScreen(
    navController: NavController,
    viewModel: PermissionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snap by viewModel.snapshot.collectAsState()

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val runtimePerms = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_CALENDAR)
        add(Manifest.permission.WRITE_CALENDAR)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    GabiAtmosphericBackdrop(backdropAlpha = 0.65f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GabiAssetImage(
                        fileName = "theme/gabitama_brand_crest.png",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(44.dp)
                    )
                    Column {
                        Text("Permissões da Gabi · HB", style = MaterialTheme.typography.headlineMedium, color = GabiTokens.TextPrimary)
                        Text(
                            "Autorize apenas o necessário para voz, lembretes, agenda e leitura segura.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GabiTokens.TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        PermRow("Microfone (voz)", snap.recordAudio) { runtimeLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
                    }
                    item {
                        PermRow("Contatos e Ligações", snap.contacts && snap.callPhone) {
                            runtimeLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
                        }
                    }
                    item {
                        PermRow("Agenda (leitura e escrita)", snap.calendarRead && snap.calendarWrite) {
                            runtimeLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        }
                    }
                    item {
                        PermRow("Leitor de Notificações", snap.notificationListener) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    }
                    item {
                        PermRow("Acessibilidade (WhatsApp confirmado)", snap.accessibility) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        item {
                            PermRow("Alarmes exatos", snap.exactAlarm) {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { runtimeLauncher.launch(runtimePerms) },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Solicitar padrão", color = GabiTokens.GoldAccent)
                    }
                    Button(
                        onClick = {
                            viewModel.completeOnboarding()
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Permissions.route) { inclusive = true }
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.GoldAccent),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Continuar", color = GabiTokens.NightBg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermRow(label: String, granted: Boolean, onRequest: () -> Unit) {
    GabiFeaturedSurface(
        borderColor = if (granted) GabiTokens.SuccessGreen.copy(alpha = 0.35f) else GabiTokens.AmberAccent.copy(alpha = 0.32f),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = GabiTokens.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (granted) "Ativa" else "Pendente",
                    color = if (granted) GabiTokens.SuccessGreen else GabiTokens.AmberAccent,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(onClick = onRequest, shape = CircleShape) {
                Text(if (granted) "Rever" else "Ativar", color = GabiTokens.GoldAccent)
            }
        }
    }
}
