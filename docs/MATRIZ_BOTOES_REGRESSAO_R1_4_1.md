# MATRIZ_BOTOES_REGRESSAO_R1_4_1 — Cobertura Completa de Telas, Botões, Diálogos, Widgets, Tile e Serviços

**Projeto:** `NOVO_AGENTE_HB` (`0.2.5-r1.4.1`, `versionCode = 7`)  
**Regra de honestidade R1.4.1:** Nenhuma função é marcada como `100% funcional` apenas por leitura estática. Toda linha indica a evidência real e a classificação exata:
- `STATIC_PASS`: código, rotas, recursos `R.*`, XML e integridade verificados por `verify_project_integrity.py` (`8/8 OK`), `audit_ip_and_no_pet.py` (`4/4 OK`) e inspeção direta.
- `UNIT_PASS`: contrato/lógica coberto por suítes unitárias Python (`verify_mcp_and_skills_mock.py` `2/2 OK`) ou preparado em JUnit JVM (`HBCharacterStateUnitTest`, `AgentCoreUnitTest`, `ModelRouterAndVoiceUnitTest` — execução JVM bloqueada até rodar `./gradlew testDebugUnitTest` no Windows).
- `CODE_PRESENT`: callback e integração Android implementados e revisados no código, aguardando compilação/execução no dispositivo.
- `BLOCKED`: execução real de Gradle/emulador/dispositivo impedida neste sandbox Linux sem JDK 17 / Android SDK 34 / ADB.
- `NOT_IMPLEMENTED`: recurso intencionalmente ausente nesta versão e divulgado ao usuário.

---

## 1. Telas Principais & Barra Inferior (10 Telas + `GabiBottomBar`)

| Tela / Componente | Controles, Botões e Diálogos Verificados no Código | Arquivo | Status Honesto |
| --- | --- | --- | --- |
| **`GabiBottomBar`** | 5 abas (`Início`, `Avisos`, `Gabi` com `GabiFaceAvatar` sincronizado com `LocalHBStage`, `Quarto`, `Ajustes`), navegação `popUpTo(Home)+restoreState` e `navigationBarsPadding()` | `design/GabiBottomBar.kt` | `STATIC_PASS` / `CODE_PRESENT` (UI touch `BLOCKED`) |
| **1. `HomeScreen`** | Botão disco `Quarto`, botão disco `Ajustes`, palco hero clicável (`232.dp`, animação viva `StageLoopVideo`/`AvatarAnimation` respeitando `reduceMotion`), pílula de status + aviso honesto `HBMotionTruth`, botões `Falar` (`RECORD_AUDIO` + STT), `Live` (Gemini Live), `Quarto`, `Parar` (`ActiveExecutionBanner`), `Sim, autorizar`/`Cancelar` (`ConfirmActionBar`), `ProviderFailureCard` (`Detalhes`, `Ajustes`, `Tentar novamente`), 5 `GlassInfoCard` (`Clima`, `Metas`, `Memória`, `Arquivos`, `Computador`), 4 chips rápidos (`Clima`, `Notificações`, `Agenda`, `/diagnostico`), campo texto + `Enviar`, atalho `Chat completo →` | `ui/home/HomeScreen.kt` | `STATIC_PASS` / `CODE_PRESENT` (execução `BLOCKED`) |
| **2. `QuartoGabiScreen`** | `RoomBackgroundLayer` (`ROOM_WEBP`, `ROOM_PNG`, `ROOM_MP4`, `CUSTOM_URI`), `StageLoopVideo` (`idade1`/`idade2` sem retângulo + `view_texture_player.xml` não bloqueante), toque direto na HB (`TapReaction` + bolha), disco `Face` (`StageSelectorDialog` + `Ver Bíblia & Spritesheet`), disco `Checkroom` (`WardrobeDialog`: `Rotina do dia` + 4 looks reais + 3 bloqueados com `Arte pendente`), disco `Wallpaper` (`RoomEnvironmentDialog` + preservação de fundo escolhido + `OpenDocument` galeria + `Rever abertura`), disco `Computer`, pílula `Conversar`, `SpotifySpeakerBadge`, `Parar`, `Alimentar`, `RoomIntroVideo` (`Pular`, `Não mostrar novamente`), `CharacterBibleDialog` | `ui/habitat/QuartoGabiScreen.kt` | `STATIC_PASS` / `CODE_PRESENT` (vídeo/toque real `BLOCKED`) |
| **3. `ChatScreen`** | `GabiTopBar` (voltar + `GabiFaceAvatar` clicável -> Quarto), botão disco `Gemini Live`, pílula `/diag`, botão `Limpar conversa`, `ActiveExecutionBanner` (`Parar`), `ConfirmActionBar`, `ProviderFailureCard`, lista reversa + `GabiEmptyStateCard`, barra inferior com `imePadding()`, botão `Falar` (`Mic`), campo de mensagem e botão `Enviar` | `ui/chat/ChatScreen.kt` | `STATIC_PASS` / `CODE_PRESENT` |
| **4. `FeedScreen`** | Botão voltar, disco `Limpar feed`, pílula segmentada `Notificações` / `Recibos`, cards com status colorido (`COMPLETED`, `BLOCKED_POLICY`, `FAILED`) e `GabiEmptyStateCard` | `ui/feed/FeedScreen.kt` | `STATIC_PASS` / `CODE_PRESENT` |
| **5. `GoalsScreen`** | Botão voltar, campo + botão `+` `Nova meta`, botão `Avançar` (`CheckCircle`) e `Excluir` por meta, campo + botão `+15m` `Lembretes agendados` (`ReminderScheduler`), `GabiEmptyStateCard` | `ui/goals/GoalsScreen.kt` | `STATIC_PASS` / `CODE_PRESENT` |
| **6. `LibraryScreen`** | Voltar hierárquico (`navigateUpSaf()`), botão `+ Pasta` (`AlertDialog` `Nova pasta SAF`), botão `SAF` (`OpenDocumentTree` persistente), criar nota no Workspace (`Salvar no Workspace`), abrir entrada, `Compartilhar` (`FileProvider`), `Renomear` (`AlertDialog`), `Remover` e `Prévia do arquivo` (`AlertDialog`) | `ui/library/LibraryScreen.kt` | `STATIC_PASS` / `CODE_PRESENT` |
| **7. `MemoryScreen`** | Voltar, disco `Limpar memórias` (`AlertDialog` confirmação), campos chave/valor + `Salvar fato`, botão `Remover` por item, `GabiEmptyStateCard` | `ui/memory/MemoryScreen.kt` | `STATIC_PASS` / `CODE_PRESENT` |
| **8. `ComputerScreen`** | Voltar, botão `Diagnóstico`, card hero dinâmico (`scene_computador` em `YOUNG_CHILD`; `StageLoopVideo` `92.dp` circular em `PRE_ADOLESCENT`/`ADOLESCENT`), banner `Parar` / `ConfirmActionBar`, resumo de `ToolExecutionSemantics` + lista honesta de não implementados, form `Adicionar Skill local` + switch `workspace_write_verify`, form `Registrar servidor MCP` + switch `Permitir LAN/localhost`, por item: switch opt-in, deletar, executar Skill / testar Handshake MCP (`initialize + notifications/initialized + tools/list`) + botões remotos com confirmação | `ui/computer/ComputerScreen.kt` | `STATIC_PASS` + `UNIT_PASS` (handshake mock Python `2/2 OK`) / `CODE_PRESENT` |
| **9. `SettingsScreen`** | Voltar, atalho `Permissões`, card `Identidade Visual GabiTama` -> `Abrir Bíblia` (`CharacterBibleDialog`), seletor de provedor (`Gemini`, `Groq`, `OpenRouter`, `DeepSeek`, `Anthropic`), 8 linhas BYOK (`ApiKeyRow` em `SecureKeyVault` AES256-GCM: Gemini, Groq, OpenRouter, DeepSeek, Anthropic, ElevenLabs, Spotify ID, Spotify Secret), campos `Modelo de chat` / `Modelo Gemini Live` + `Salvar modelos` + `Verificar modelos Live`, seletor `Motor de Voz` + nota honesta `gabi_24l` / Kore + perfis `B2`/`C2`, switch `Chamadas pagas sem confirmação`, `Cidade padrão`, botões `Exportar JSON` e `Diagnóstico` (`AlertDialog`) | `ui/settings/SettingsScreen.kt` | `STATIC_PASS` / `CODE_PRESENT` |
| **10. `PermissionsScreen`** | `safeDrawingPadding()`, 6 linhas de permissão (`Microfone`, `Contatos e Ligações`, `Agenda`, `Leitor de Notificações`, `Acessibilidade`, `Alarmes exatos` API 31+), botões `Solicitar padrão` e `Continuar` | `ui/permissions/PermissionsScreen.kt` | `STATIC_PASS` / `CODE_PRESENT` |

---

## 2. Integrações de Sistema Android (Widget, Tile, Serviços, Receivers, Deep Links)

| Componente | Responsabilidade | Evidência no Manifesto / Código | Status Honesto |
| --- | --- | --- | --- |
| `MainActivity` (`novoagentehb://...` + `ACTION_VOICE_TRIGGER`) | Roteia deep links (`habitat`, `chat`, `feed`, `goals`, `library`, `memory`, `computer`, `settings`, `permissions`) e gatilho de voz; propaga `LocalHBStage` | `MainActivity.kt`, `AndroidManifest.xml` | `STATIC_PASS` / `CODE_PRESENT` |
| `HomeWidgetProvider` | Widget `widget_novo_agente` abre `MainActivity` com `ACTION_VOICE_TRIGGER` | `bridges/HomeWidgetProvider.kt`, `res/xml/widget_info.xml` | `STATIC_PASS` / `CODE_PRESENT` (execução real `BLOCKED`) |
| `GabiTileService` | Quick Settings Tile `Gabi Voz` (`startActivityAndCollapse` compatível API 34+) | `bridges/GabiTileService.kt`, `AndroidManifest.xml` | `STATIC_PASS` / `CODE_PRESENT` (execução real `BLOCKED`) |
| `NotificationFeedService` | `NotificationListenerService` + `OnActiveSessionsChangedListener` (detecção Spotify sem poluir feed + captura notificações) | `bridges/NotificationFeedService.kt` | `STATIC_PASS` / `CODE_PRESENT` |
| `AssistantAccessibilityService` | Envio assistido no WhatsApp somente após confirmação explícita do usuário | `bridges/AssistantAccessibilityService.kt` | `STATIC_PASS` / `CODE_PRESENT` |
| `ReminderAlarmReceiver` & `BootReceiver` | Disparo de lembretes (`SCHEDULE_EXACT_ALARM`) e rearmamento após boot | `bridges/ReminderScheduler.kt` | `STATIC_PASS` / `CODE_PRESENT` |

---

## 3. Recursos Mantidos Honestamente como `NOT_IMPLEMENTED`

| Recurso | Motivo Honesto | Divulgado no App? |
| --- | --- | --- |
| `linux_proot_shell` | Shell Linux/PRoot não está embarcado no APK Android nesta versão. | Sim (`CapabilityRegistry` + `ComputerScreen` seção *Recursos não implementados*). |
| Operador genérico de tela em outros apps | Apenas WhatsApp assistido via `AssistantAccessibilityService` após confirmação explícita; controle arbitrário de apps terceiros não implementado. | Sim (`CapabilityRegistry` + `ComputerScreen`). |
| Cenas Flow ainda ausentes (`scene_comer`, `scene_ler`, `scene_dormir`, clipes interativos e recorte alfa de `idade1`/`idade2`) | Aguardam geração externa no Google Flow + extração alfa. App usa poses transparentes / vídeo `idle.mp4` com aviso claro (`HBMotionTruth`). | Sim (`HomeScreen`, `QuartoGabiScreen`, `HBAnimationCatalog`, `HB_ANIMACAO_POR_FASE_E_TELA_R1_4_1.md`). |
| Pocket TTS local `gabi_24l` embutido offline no APK | Runtime nativo C++/ONNX do Pocket TTS não embarcado no APK; voz cai explicitamente para TTS Android ou Gemini Live (Kore) com aviso visível na barra. | Sim (`VoiceEngineCoordinator`, `SettingsScreen`). |
