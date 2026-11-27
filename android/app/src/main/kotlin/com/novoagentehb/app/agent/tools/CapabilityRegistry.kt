package com.novoagentehb.app.agent.tools

import com.novoagentehb.app.agent.orchestrator.CapabilityVerificationState
import com.novoagentehb.app.agent.orchestrator.RiskLevel
import com.novoagentehb.app.agent.orchestrator.RuntimeSupportTier
import com.novoagentehb.app.agent.orchestrator.ToolDescriptor
import com.novoagentehb.app.agent.orchestrator.ToolExecutionSemantics
import com.novoagentehb.app.agent.orchestrator.ToolParamSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registro central de capacidades do NOVO_AGENTE_HB:
 * - Classifica honestamente cada ferramenta por `RuntimeSupportTier`, `expectedSemantics`,
 *   permissão necessária, pós-condição e estado real (`INTEGRATED_UNTESTED` até prova em aparelho).
 * - Recursos ausentes nesta versão (Linux PRoot, Operador Genérico de Tela) não são fingidos.
 */
@Singleton
class CapabilityRegistry @Inject constructor() {

    val capabilities: List<ToolDescriptor> = listOf(
        ToolDescriptor(
            name = "open_app",
            description = "Abre um aplicativo instalado pelo nome (handoff para usuário).",
            riskLevel = RiskLevel.DEVICE_BRIDGE,
            params = listOf(ToolParamSpec("app_name", "Nome do app em português ou pacote", required = true)),
            idempotent = true,
            runtimeTier = RuntimeSupportTier.ANDROID_BRIDGE_INTENT,
            expectedSemantics = ToolExecutionSemantics.HANDOFF_TO_USER,
            requiredPermissionNote = "queries explícitos no AndroidManifest",
            postConditionNote = "startActivity sem exceção; conteúdo interno não operado"
        ),
        ToolDescriptor(
            name = "call_contact",
            description = "Prepara/inicia ligação telefônica para contato salvo.",
            riskLevel = RiskLevel.EXTERNAL_SIDE_EFFECT,
            params = listOf(ToolParamSpec("contact_name", "Nome do contato", required = true)),
            runtimeTier = RuntimeSupportTier.ANDROID_BRIDGE_INTENT,
            expectedSemantics = ToolExecutionSemantics.HANDOFF_TO_USER,
            requiredPermissionNote = "READ_CONTACTS (+ CALL_PHONE opcional para ligação direta)",
            postConditionNote = "Discador ou chamada iniciada após confirmação"
        ),
        ToolDescriptor(
            name = "call_number",
            description = "Disca ou abre discador para um número específico.",
            riskLevel = RiskLevel.EXTERNAL_SIDE_EFFECT,
            params = listOf(ToolParamSpec("phone_number", "Número com DDD", required = true)),
            runtimeTier = RuntimeSupportTier.ANDROID_BRIDGE_INTENT,
            expectedSemantics = ToolExecutionSemantics.HANDOFF_TO_USER,
            requiredPermissionNote = "CALL_PHONE opcional (sem ela usa ACTION_DIAL)",
            postConditionNote = "Discador/chamada aberto após confirmação"
        ),
        ToolDescriptor(
            name = "set_alarm",
            description = "Abre o Relógio do Android com alarme pré-preenchido (handoff).",
            riskLevel = RiskLevel.DEVICE_BRIDGE,
            params = listOf(
                ToolParamSpec("hour", "Hora (0-23)", required = false),
                ToolParamSpec("minute", "Minuto (0-59)", required = false),
                ToolParamSpec("description", "Descrição do alarme", required = false)
            ),
            runtimeTier = RuntimeSupportTier.ANDROID_BRIDGE_INTENT,
            expectedSemantics = ToolExecutionSemantics.HANDOFF_TO_USER,
            requiredPermissionNote = "com.android.alarm.permission.SET_ALARM",
            postConditionNote = "Tela de alarme aberta (EXTRA_SKIP_UI = false)"
        ),
        ToolDescriptor(
            name = "search_web",
            description = "Abre pesquisa no navegador padrão (handoff).",
            riskLevel = RiskLevel.DEVICE_BRIDGE,
            params = listOf(ToolParamSpec("query", "Termo de busca", required = true)),
            idempotent = true,
            runtimeTier = RuntimeSupportTier.ANDROID_BRIDGE_INTENT,
            expectedSemantics = ToolExecutionSemantics.HANDOFF_TO_USER,
            postConditionNote = "Navegador aberto com query; resultados lidos pela pessoa"
        ),
        ToolDescriptor(
            name = "search_youtube",
            description = "Abre busca no YouTube (handoff).",
            riskLevel = RiskLevel.DEVICE_BRIDGE,
            params = listOf(ToolParamSpec("query", "Termo ou vídeo", required = true)),
            idempotent = true,
            runtimeTier = RuntimeSupportTier.ANDROID_BRIDGE_INTENT,
            expectedSemantics = ToolExecutionSemantics.HANDOFF_TO_USER,
            postConditionNote = "App YouTube/navegador aberto na busca (não reproduz automaticamente)"
        ),
        ToolDescriptor(
            name = "search_spotify",
            description = "Abre busca ou URI de faixa no Spotify (handoff; não confirma reprodução).",
            riskLevel = RiskLevel.DEVICE_BRIDGE,
            params = listOf(ToolParamSpec("query", "Música, artista ou playlist", required = true)),
            idempotent = true,
            runtimeTier = RuntimeSupportTier.ANDROID_BRIDGE_INTENT,
            expectedSemantics = ToolExecutionSemantics.HANDOFF_TO_USER,
            requiredPermissionNote = "Opcional: Client ID/Secret Spotify para resolver URI de faixa",
            postConditionNote = "Spotify aberto na busca/URI; playback real depende do app Spotify"
        ),
        ToolDescriptor(
            name = "pause_music",
            description = "Envia KeyEvent KEYCODE_MEDIA_PAUSE ao AudioManager do aparelho.",
            riskLevel = RiskLevel.DEVICE_BRIDGE,
            idempotent = true,
            runtimeTier = RuntimeSupportTier.ANDROID_BRIDGE_INTENT,
            expectedSemantics = ToolExecutionSemantics.HANDOFF_TO_USER,
            postConditionNote = "Evento de mídia despachado (depende do player ativo aceitar)"
        ),
        ToolDescriptor(
            name = "send_whatsapp_message",
            description = "Prepara/tenta enviar mensagem no WhatsApp via Acessibilidade (sempre exige confirmação).",
            riskLevel = RiskLevel.EXTERNAL_SIDE_EFFECT,
            params = listOf(
                ToolParamSpec("contact_name", "Destinatário", required = true),
                ToolParamSpec("message", "Texto da mensagem", required = true)
            ),
            runtimeTier = RuntimeSupportTier.ACCESSIBILITY_OR_LISTENER,
            expectedSemantics = ToolExecutionSemantics.ACCESSIBILITY_AUTOMATED,
            requiredPermissionNote = "Serviço de Acessibilidade ativo + READ_CONTACTS",
            postConditionNote = "Confirma campo e botão enviar na janela ativa do WhatsApp"
        ),
        ToolDescriptor(
            name = "open_whatsapp_chat",
            description = "Abre a conversa de um contato no WhatsApp sem enviar mensagem (handoff).",
            riskLevel = RiskLevel.DEVICE_BRIDGE,
            params = listOf(ToolParamSpec("contact_name", "Nome do contato", required = true)),
            idempotent = true,
            runtimeTier = RuntimeSupportTier.ANDROID_BRIDGE_INTENT,
            expectedSemantics = ToolExecutionSemantics.HANDOFF_TO_USER,
            requiredPermissionNote = "READ_CONTACTS opcional",
            postConditionNote = "Janela do WhatsApp aberta sem disparo automático"
        ),
        ToolDescriptor(
            name = "read_notifications",
            description = "Lê notificações capturadas pelo NotificationFeedService no banco Room.",
            riskLevel = RiskLevel.READ_ONLY,
            idempotent = true,
            runtimeTier = RuntimeSupportTier.ACCESSIBILITY_OR_LISTENER,
            expectedSemantics = ToolExecutionSemantics.NOTIFICATION_PREVIEW_ONLY,
            requiredPermissionNote = "NotificationListenerService habilitado pelo usuário",
            postConditionNote = "Consulta tabela feed_events"
        ),
        ToolDescriptor(
            name = "open_settings",
            description = "Abre configurações do sistema Android (handoff).",
            riskLevel = RiskLevel.DEVICE_BRIDGE,
            idempotent = true,
            runtimeTier = RuntimeSupportTier.ANDROID_BRIDGE_INTENT,
            expectedSemantics = ToolExecutionSemantics.HANDOFF_TO_USER,
            postConditionNote = "Tela de Configurações aberta"
        ),
        ToolDescriptor(
            name = "take_photo",
            description = "Abre a câmera do aparelho (handoff).",
            riskLevel = RiskLevel.DEVICE_BRIDGE,
            idempotent = true,
            runtimeTier = RuntimeSupportTier.ANDROID_BRIDGE_INTENT,
            expectedSemantics = ToolExecutionSemantics.HANDOFF_TO_USER,
            postConditionNote = "App de câmera aberto; captura é feita pela pessoa"
        ),
        ToolDescriptor(
            name = "get_weather",
            description = "Consulta clima atual via Open-Meteo (padrão Fortaleza).",
            riskLevel = RiskLevel.READ_ONLY,
            params = listOf(ToolParamSpec("city", "Nome da cidade (vazio = padrão)")),
            idempotent = true,
            runtimeTier = RuntimeSupportTier.HTTP_BYOK_OR_PUBLIC,
            expectedSemantics = ToolExecutionSemantics.READ_VERIFIED,
            requiredPermissionNote = "INTERNET",
            postConditionNote = "Resposta JSON HTTP Open-Meteo validada"
        ),
        ToolDescriptor(
            name = "set_reminder",
            description = "Grava lembrete no Room e arma alarme local no AlarmManager.",
            riskLevel = RiskLevel.LOCAL_MUTATION,
            params = listOf(
                ToolParamSpec("subject", "Assunto do lembrete", required = true),
                ToolParamSpec("trigger_at", "Data/hora ISO-8601 ou minutos", required = true)
            ),
            runtimeTier = RuntimeSupportTier.NATIVE_LOCAL_VERIFIED,
            expectedSemantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED,
            requiredPermissionNote = "SCHEDULE_EXACT_ALARM / alarme local",
            postConditionNote = "ID gravado em reminders + PendingIntent agendado"
        ),
        ToolDescriptor(
            name = "share_content",
            description = "Abre a folha de compartilhamento do Android com um texto (handoff).",
            riskLevel = RiskLevel.DEVICE_BRIDGE,
            params = listOf(ToolParamSpec("text", "Conteúdo a compartilhar", required = true)),
            runtimeTier = RuntimeSupportTier.ANDROID_BRIDGE_INTENT,
            expectedSemantics = ToolExecutionSemantics.HANDOFF_TO_USER,
            postConditionNote = "Intent Chooser aberto; destino selecionado pela pessoa"
        ),
        ToolDescriptor(
            name = "bluetooth_connect",
            description = "Abre ajustes Bluetooth do sistema (handoff).",
            riskLevel = RiskLevel.DEVICE_BRIDGE,
            runtimeTier = RuntimeSupportTier.ANDROID_BRIDGE_INTENT,
            expectedSemantics = ToolExecutionSemantics.HANDOFF_TO_USER,
            requiredPermissionNote = "BLUETOOTH_CONNECT opcional apenas para status",
            postConditionNote = "Tela Settings.ACTION_BLUETOOTH_SETTINGS aberta"
        ),
        ToolDescriptor(
            name = "read_calendar",
            description = "Lê próximos compromissos no ContentResolver do CalendarContract.",
            riskLevel = RiskLevel.READ_ONLY,
            idempotent = true,
            runtimeTier = RuntimeSupportTier.ACCESSIBILITY_OR_LISTENER,
            expectedSemantics = ToolExecutionSemantics.READ_VERIFIED,
            requiredPermissionNote = "READ_CALENDAR",
            postConditionNote = "Query CalendarContract.Events.CONTENT_URI"
        ),
        ToolDescriptor(
            name = "create_calendar_event",
            description = "Insere evento no CalendarContract local (exige confirmação).",
            riskLevel = RiskLevel.EXTERNAL_SIDE_EFFECT,
            params = listOf(
                ToolParamSpec("title", "Título do evento", required = true),
                ToolParamSpec("start_at", "ISO-8601 início", required = true)
            ),
            runtimeTier = RuntimeSupportTier.ACCESSIBILITY_OR_LISTENER,
            expectedSemantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED,
            requiredPermissionNote = "WRITE_CALENDAR",
            postConditionNote = "URI retornada por ContentResolver.insert != null"
        ),
        ToolDescriptor(
            name = "read_gmail",
            description = "Lê SOMENTE prévias de notificações do Gmail capturadas localmente (não usa Gmail API).",
            riskLevel = RiskLevel.READ_ONLY,
            idempotent = true,
            runtimeTier = RuntimeSupportTier.ACCESSIBILITY_OR_LISTENER,
            expectedSemantics = ToolExecutionSemantics.NOTIFICATION_PREVIEW_ONLY,
            requiredPermissionNote = "NotificationListenerService habilitado",
            postConditionNote = "Filtra feed_events onde sourcePackage = com.google.android.gm"
        ),
        ToolDescriptor(
            name = "ask_claude",
            description = "Encaminha consulta explícita ao provedor Anthropic Claude (BYOK pago).",
            riskLevel = RiskLevel.PAID_API_CALL,
            params = listOf(ToolParamSpec("query", "Pergunta para enviar ao Claude", required = true)),
            runtimeTier = RuntimeSupportTier.HTTP_BYOK_OR_PUBLIC,
            expectedSemantics = ToolExecutionSemantics.READ_VERIFIED,
            requiredPermissionNote = "Chave Anthropic em SecureKeyVault + consentimento pago",
            postConditionNote = "Resposta HTTP 200 da API Messages"
        ),
        ToolDescriptor(
            name = "save_memory",
            description = "Salva preferência/fato autorizado na memória única Room.",
            riskLevel = RiskLevel.LOCAL_MUTATION,
            params = listOf(
                ToolParamSpec("key", "Chave única snake_case", required = true),
                ToolParamSpec("value", "Conteúdo da lembrança", required = true),
                ToolParamSpec("category", "Categoria (PREFERENCE, FACT, HABIT, ROUTINE, USER_NAME, FAVORITE_APP)")
            ),
            idempotent = true,
            runtimeTier = RuntimeSupportTier.NATIVE_LOCAL_VERIFIED,
            expectedSemantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED,
            postConditionNote = "Linha persistida em memory_items com ID > 0"
        ),
        ToolDescriptor(
            name = "search_memory",
            description = "Pesquisa registros na tabela memory_items.",
            riskLevel = RiskLevel.READ_ONLY,
            params = listOf(ToolParamSpec("query", "Termo de busca", required = true)),
            idempotent = true,
            runtimeTier = RuntimeSupportTier.NATIVE_LOCAL_VERIFIED,
            expectedSemantics = ToolExecutionSemantics.READ_VERIFIED,
            postConditionNote = "Query LIKE em memory_items"
        ),
        ToolDescriptor(
            name = "upsert_goal",
            description = "Cria ou atualiza meta/ideia na tabela goals.",
            riskLevel = RiskLevel.LOCAL_MUTATION,
            params = listOf(
                ToolParamSpec("title", "Título da meta", required = true),
                ToolParamSpec("description", "Descrição ou próximo passo"),
                ToolParamSpec("progress", "Percentual 0..100")
            ),
            runtimeTier = RuntimeSupportTier.NATIVE_LOCAL_VERIFIED,
            expectedSemantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED,
            postConditionNote = "ID gravado em goals"
        ),
        ToolDescriptor(
            name = "workspace_write_file",
            description = "Grava artefato no workspace privado e valida existência + SHA-256 em disco.",
            riskLevel = RiskLevel.LOCAL_MUTATION,
            params = listOf(
                ToolParamSpec("filename", "Nome do arquivo relativo", required = true),
                ToolParamSpec("content", "Conteúdo textual", required = true)
            ),
            runtimeTier = RuntimeSupportTier.NATIVE_LOCAL_VERIFIED,
            expectedSemantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED,
            postConditionNote = "Arquivo existe + SHA-256 conferido"
        ),
        ToolDescriptor(
            name = "generate_image",
            description = "Gera uma imagem por texto com FLUX.1-schnell (NVIDIA NIM), salva no armazenamento privado e mostra no chat. Requer confirmação por pedido. Não suporta referência nem edição.",
            riskLevel = RiskLevel.PAID_API_CALL,
            params = listOf(
                ToolParamSpec("prompt", "Descrição da imagem", required = true)
            ),
            runtimeTier = RuntimeSupportTier.HTTP_BYOK_OR_PUBLIC,
            expectedSemantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED,
            requiredPermissionNote = "INTERNET, chave NVIDIA no cofre e confirmação por geração",
            postConditionNote = "Imagem válida salva e relida no workspace; visualização na Biblioteca"
        ),
        ToolDescriptor(
            name = "execute_workspace_program",
            description = "Executa uma expressão aritmética limitada para cada número e salva CSV. Não executa shell, Python ou comandos arbitrários.",
            riskLevel = RiskLevel.LOCAL_MUTATION,
            params = listOf(
                ToolParamSpec("expression", "Expressão com x, números, + - * / e parênteses; exemplo x*2+1", required = true),
                ToolParamSpec("inputs", "Números separados por vírgula ou linha", required = true)
            ),
            runtimeTier = RuntimeSupportTier.NATIVE_LOCAL_VERIFIED,
            expectedSemantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED,
            postConditionNote = "Cálculo executado; CSV novo relido no workspace; recibo no histórico"
        ),
        ToolDescriptor(
            name = "workspace_read_file",
            description = "Lê um arquivo do workspace privado do agente.",
            riskLevel = RiskLevel.READ_ONLY,
            params = listOf(ToolParamSpec("filename", "Nome do arquivo relativo", required = true)),
            idempotent = true,
            runtimeTier = RuntimeSupportTier.NATIVE_LOCAL_VERIFIED,
            expectedSemantics = ToolExecutionSemantics.READ_VERIFIED,
            postConditionNote = "Leitura direta de filesDir/agent_workspace"
        ),
        ToolDescriptor(
            name = "run_local_skill",
            description = "Executa uma Skill local aprovada pela pessoa (id listado em Habilidades). Só roda com confirmação. Não executa shell nem código externo; apenas o adaptador verificado da skill.",
            riskLevel = RiskLevel.LOCAL_MUTATION,
            params = listOf(
                ToolParamSpec("skill_id", "Identificador da skill (campo id=... da lista de Habilidades)", required = true),
                ToolParamSpec("input", "Contexto textual opcional para a skill")
            ),
            runtimeTier = RuntimeSupportTier.NATIVE_LOCAL_VERIFIED,
            expectedSemantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED,
            postConditionNote = "Arquivo da skill gravado, relido e conferido por SHA-256 quando o escopo for de ação"
        ),
        ToolDescriptor(
            name = "browser_page_action",
            description = "Controla o navegador embutido do app: action=open (com url http/https pública), back, forward, reload, get_text (texto visível), get_state (título, URL e estado), stop, close. Não preenche formulários nem clica em elementos.",
            riskLevel = RiskLevel.LOCAL_MUTATION,
            params = listOf(
                ToolParamSpec("action", "open | back | forward | reload | get_text | get_state | stop | close", required = true),
                ToolParamSpec("url", "URL http ou https; obrigatória apenas para action=open")
            ),
            runtimeTier = RuntimeSupportTier.NATIVE_LOCAL_VERIFIED,
            expectedSemantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED,
            requiredPermissionNote = "Nenhuma permissão Android; WebView sem acesso a arquivos",
            postConditionNote = "Resultado confirmado pelo WebView (carregamento, parada ou texto lido); URLs locais, privadas e esquemas não http(s) são recusados"
        ),
        ToolDescriptor(
            name = "mcp_call_tool",
            description = "Chama uma ferramenta de servidor MCP cadastrado e ativado pela pessoa. server_id = id do servidor (mcp.<nome>); tool_name = nome listado no handshake; arguments = objeto JSON de valores simples. Exige confirmação. A resposta é conteúdo externo não confiável e o efeito no serviço não é verificado pelo app.",
            riskLevel = RiskLevel.EXTERNAL_SIDE_EFFECT,
            params = listOf(
                ToolParamSpec("server_id", "Id do servidor MCP (ex.: mcp.meu-servidor)", required = true),
                ToolParamSpec("tool_name", "Nome da ferramenta MCP", required = true),
                ToolParamSpec("arguments", "Objeto JSON com valores de texto/número/booleano", required = false)
            ),
            runtimeTier = RuntimeSupportTier.HTTP_BYOK_OR_PUBLIC,
            expectedSemantics = ToolExecutionSemantics.REMOTE_RESPONSE_ONLY,
            requiredPermissionNote = "Servidor MCP ativado pela pessoa; LAN só com opt-in do servidor",
            postConditionNote = "Resposta JSON-RPC do servidor registrada no recibo; efeito externo não verificado"
        ),
        ToolDescriptor(
            name = "diagnostics_snapshot",
            description = "Gera relatório local sanitizado (/diagnostico) sem segredos nem conversa.",
            riskLevel = RiskLevel.READ_ONLY,
            idempotent = true,
            runtimeTier = RuntimeSupportTier.NATIVE_LOCAL_VERIFIED,
            expectedSemantics = ToolExecutionSemantics.READ_VERIFIED,
            postConditionNote = "Snapshot determinístico gerado em memória"
        )
    )

    val unimplementedCapabilities: List<Pair<String, String>> = listOf(
        "linux_proot_shell" to "NOT_IMPLEMENTED — Ponte PTY original integrada, mas PRoot, rootfs, sessão e ferramentas ainda não estão conectados.",
        "generic_screen_operator" to "NOT_IMPLEMENTED — Operador genérico multi-app ausente; apenas WhatsApp via Acessibilidade é suportado."
    )

    fun find(name: String): ToolDescriptor? = capabilities.firstOrNull { it.name == name }

    fun countByTier(): Map<RuntimeSupportTier, Int> =
        capabilities.groupingBy { it.runtimeTier }.eachCount()

    fun countBySemantics(): Map<ToolExecutionSemantics, Int> =
        capabilities.groupingBy { it.expectedSemantics }.eachCount()

    fun countByVerificationState(): Map<CapabilityVerificationState, Int> =
        capabilities.groupingBy { it.verificationState }.eachCount()
}
