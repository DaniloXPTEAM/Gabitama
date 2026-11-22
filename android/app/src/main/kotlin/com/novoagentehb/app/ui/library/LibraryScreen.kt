package com.novoagentehb.app.ui.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.novoagentehb.app.bridges.LibraryEntry
import com.novoagentehb.app.design.GabiAtmosphericBackdrop
import com.novoagentehb.app.design.GabiBottomBar
import com.novoagentehb.app.design.GabiEmptyStateCard
import com.novoagentehb.app.design.GabiFeaturedSurface
import com.novoagentehb.app.design.GabiTokens
import com.novoagentehb.app.design.GabiTopBar

@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val safTreeUri by viewModel.safTreeUri.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val preview by viewModel.previewText.collectAsState()
    val statusNotice by viewModel.statusNotice.collectAsState()

    var newFileName by remember { mutableStateOf("") }
    var newFileBody by remember { mutableStateOf("") }
    var showNewSafFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var renameValue by remember { mutableStateOf("") }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            viewModel.onTreeAuthorized(uri)
        }
    }

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
                        title = "Arquivos & SAF",
                        subtitle = if (safTreeUri.isBlank()) "Workspace privado" else "SAF: ${viewModel.safBreadcrumb()}",
                        onBack = {
                            if (!viewModel.navigateUpSaf()) navController.popBackStack()
                        },
                        actions = {
                            if (safTreeUri.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { showNewSafFolder = true },
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, GabiTokens.TealAccent.copy(alpha = 0.5f))
                                ) {
                                    Text("+ Pasta", color = GabiTokens.TealAccent, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            OutlinedButton(
                                onClick = { safLauncher.launch(null) },
                                shape = CircleShape,
                                border = BorderStroke(1.dp, GabiTokens.GoldAccent.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = GabiTokens.GoldAccent, modifier = Modifier.size(16.dp))
                                Text(" SAF", color = GabiTokens.GoldAccent, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        statusNotice?.let { msg ->
                            item {
                                GabiFeaturedSurface(
                                    borderColor = GabiTokens.TealAccent.copy(alpha = 0.45f),
                                    cornerRadius = 14.dp,
                                    modifier = Modifier.clickable { viewModel.clearNotice() }
                                ) {
                                    Text(msg, color = GabiTokens.TealAccent, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.TealAccent.copy(alpha = 0.28f)) {
                                Text("Escopo autorizado", color = GabiTokens.TealAccent, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (safTreeUri.isBlank()) {
                                        "Nenhuma pasta externa selecionada. Apenas o Workspace privado do agente está ativo."
                                    } else {
                                        "Pasta SAF (${viewModel.safBreadcrumb()}): $safTreeUri"
                                    },
                                    color = GabiTokens.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        item {
                            GabiFeaturedSurface(borderColor = GabiTokens.Hairline) {
                                Text("Criar nota no Workspace", color = GabiTokens.GoldAccent, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = newFileName,
                                    onValueChange = { newFileName = it },
                                    placeholder = { Text("Novo arquivo (ex: notas.txt)", color = GabiTokens.TextSecondary) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GabiTokens.GoldAccent,
                                        unfocusedBorderColor = GabiTokens.Hairline
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = newFileBody,
                                    onValueChange = { newFileBody = it },
                                    placeholder = { Text("Conteúdo…", color = GabiTokens.TextSecondary) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GabiTokens.GoldAccent,
                                        unfocusedBorderColor = GabiTokens.Hairline
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 4
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.createNote(newFileName, newFileBody)
                                        newFileName = ""
                                        newFileBody = ""
                                    },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = GabiTokens.GoldAccent)
                                ) {
                                    Icon(Icons.Default.NoteAdd, contentDescription = null, tint = GabiTokens.NightBg, modifier = Modifier.size(18.dp))
                                    Text(" Salvar no Workspace", color = GabiTokens.NightBg, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (entries.isEmpty()) {
                            item {
                                GabiEmptyStateCard(
                                    title = "Nenhum arquivo no diretório",
                                    subtitle = "Crie uma nota rápida acima ou autorize uma pasta do aparelho via SAF.",
                                    accent = GabiTokens.AmberAccent
                                )
                            }
                        } else {
                            items(entries) { entry ->
                                GabiFeaturedSurface(
                                    borderColor = GabiTokens.Hairline,
                                    cornerRadius = 16.dp,
                                    modifier = Modifier.clickable { viewModel.openEntry(entry) }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${if (entry.isDirectory) "📁" else "📄"} ${entry.name}",
                                                color = GabiTokens.TextPrimary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${entry.scopeLabel} · ${entry.sizeBytes} bytes",
                                                color = GabiTokens.TextSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (!entry.isDirectory) {
                                                IconButton(onClick = { viewModel.shareEntry(entry) }) {
                                                    Icon(
                                                        Icons.Default.Share,
                                                        contentDescription = "Compartilhar",
                                                        tint = GabiTokens.TealAccent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            if (!entry.scopeLabel.contains("Workspace")) {
                                                IconButton(onClick = {
                                                    renameTarget = entry
                                                    renameValue = entry.name
                                                }) {
                                                    Icon(
                                                        Icons.Default.DriveFileRenameOutline,
                                                        contentDescription = "Renomear",
                                                        tint = GabiTokens.GoldAccent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { viewModel.deleteEntry(entry) }) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Remover",
                                                    tint = GabiTokens.ErrorRed,
                                                    modifier = Modifier.size(18.dp)
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
        }
    }

    preview?.let { txt ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPreview() },
            containerColor = GabiTokens.SurfaceDark,
            title = { Text("Prévia do arquivo", color = GabiTokens.GoldAccent) },
            text = { Text(txt.take(1600), color = GabiTokens.TextPrimary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPreview() }) { Text("Fechar", color = GabiTokens.GoldAccent) }
            }
        )
    }

    if (showNewSafFolder) {
        AlertDialog(
            onDismissRequest = { showNewSafFolder = false },
            containerColor = GabiTokens.SurfaceDark,
            title = { Text("Nova pasta SAF", color = GabiTokens.GoldAccent) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Nome da pasta") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createSafFolder(newFolderName)
                    newFolderName = ""
                    showNewSafFolder = false
                }) { Text("Criar", color = GabiTokens.GoldAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showNewSafFolder = false }) { Text("Cancelar") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = GabiTokens.SurfaceDark,
            title = { Text("Renomear item SAF", color = GabiTokens.GoldAccent) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameSafEntry(target, renameValue)
                    renameTarget = null
                }) { Text("Salvar", color = GabiTokens.GoldAccent) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancelar") }
            }
        )
    }
}
