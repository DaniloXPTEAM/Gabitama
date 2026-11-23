package com.novoagentehb.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.novoagentehb.app.data.providers.CustomProvider
import com.novoagentehb.app.data.providers.CustomProviderFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProviderManagerDialog(viewModel: SettingsViewModel, onClose: () -> Unit) {
    val providers by viewModel.customProviderList.collectAsState()
    val activeId by viewModel.activeCustomId.collectAsState()
    val notice by viewModel.customNotice.collectAsState()
    val models by viewModel.customModels.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf("") }
    var vision by remember { mutableStateOf("") }
    var credential by remember { mutableStateOf("") }
    var confirmActivate by remember { mutableStateOf<CustomProvider?>(null) }
    var confirmRemove by remember { mutableStateOf<CustomProvider?>(null) }
    fun edit(p: CustomProvider?) {
        editing = p?.id; name = p?.name.orEmpty(); url = p?.baseUrl.orEmpty()
        chat = p?.chatModel.orEmpty(); screen = p?.screenModel.orEmpty(); vision = p?.visionModel.orEmpty()
        credential = ""; viewModel.clearCustomModels()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(4096)
                        while (true) {
                            val n = stream.read(buf); if (n < 0) break
                            require(out.size() + n <= 65536) { "Arquivo excede 64 KB." }
                            out.write(buf, 0, n)
                        }
                        out.toByteArray()
                    } ?: error("Arquivo indisponível.")
                    CustomProviderFormat.importJson(bytes.toString(Charsets.UTF_8))
                }
                edit(imported); editing = null
                viewModel.customImportError("Importação validada; revise a URL e informe a credencial antes de salvar.")
            } catch (e: IllegalArgumentException) { viewModel.customImportError(e.message ?: "Arquivo inválido.") }
            catch (_: Exception) { viewModel.customImportError("Não foi possível ler o arquivo.") }
        }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("Gerenciar provedores") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Provedores personalizados: API OpenAI compatível (chat e /models). Listagem não comprova preço, visão, ferramentas ou disponibilidade.", style = MaterialTheme.typography.bodySmall)
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            providers.forEach { p ->
                HorizontalDivider()
                Text("${p.name}${if (p.id == activeId) " · ATIVO" else ""}\n${p.baseUrl}\nChat: ${p.chatModel}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { edit(p) }) { Text("Editar") }
                    TextButton(onClick = { confirmActivate = p }) { Text("Ativar") }
                    TextButton(onClick = { confirmRemove = p }) { Text("Excluir") }
                }
                OutlinedButton(onClick = { edit(p); viewModel.discoverCustomModels(p) }) { Text("Listar modelos") }
            }
            HorizontalDivider()
            Text(if (editing == null) "Adicionar provedor" else "Editar provedor", style = MaterialTheme.typography.titleSmall)
            Text("Formato JSON GabiTama v1: format, name, base_url, chat_model, screen_model, vision_model. Credenciais não são aceitas no arquivo.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain")) }) { Text("Importar JSON de arquivo") }
            OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, label = { Text("URL base HTTPS (ex.: https://api.exemplo.com/v1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(credential, { credential = it }, label = { Text(if (editing == null) "Credencial" else "Nova credencial (vazio mantém)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(chat, { chat = it }, label = { Text("Modelo de conversa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(screen, { screen = it }, label = { Text("Modelo de tela") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(vision, { vision = it }, label = { Text("Modelo de visão") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (models.isNotEmpty()) {
                Text("Toque para preencher as três funções; ajuste conforme as capacidades reais.", style = MaterialTheme.typography.bodySmall)
                models.forEach { model -> Text(model, Modifier.fillMaxWidth().clickable { chat = model; screen = model; vision = model }.padding(vertical = 4.dp)) }
            }
            Row {
                Button(onClick = { viewModel.saveCustomProvider(editing, name, url, chat, screen, vision, credential) { edit(null) } }) { Text("Salvar") }
                TextButton(onClick = { edit(null) }) { Text("Novo") }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Fechar") } })
    confirmActivate?.let { p ->
        AlertDialog(onDismissRequest = { confirmActivate = null }, title = { Text("Ativar ${p.name}?") },
            text = { Text("As próximas conversas serão enviadas a ${p.baseUrl} usando o modelo ${p.chatModel}. O serviço pode cobrar pelo uso. Confira preços, capacidades e política de privacidade antes de autorizar.") },
            confirmButton = { TextButton(onClick = { viewModel.activateCustomProvider(p.id); confirmActivate = null }) { Text("Autorizar e ativar") } },
            dismissButton = { TextButton(onClick = { confirmActivate = null }) { Text("Cancelar") } })
    }
    confirmRemove?.let { p ->
        AlertDialog(onDismissRequest = { confirmRemove = null }, title = { Text("Excluir ${p.name}?") },
            text = { Text("A configuração e a credencial desse provedor serão removidas deste aplicativo.") },
            confirmButton = { TextButton(onClick = { viewModel.removeCustomProvider(p.id); confirmRemove = null }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancelar") } })
    }
}
