# GABITAMA_MATRIZ_PRESERVACAO_FUNCIONAL_R1_2 — Matriz Completa de Preservação Funcional (Gabi + Tama V2 não-GPL)

**Projeto:** `NOVO_AGENTE_HB` (`com.novoagentehb.app`, `versionCode = 4`, `versionName = 0.2.2-r1.2`)  
**Data da auditoria (UTC):** 2026-11-21  
**Fontes comparadas:**
- Gabi original (`DaniloXPTEAM/Jarvis-Projeto@main` e `DaniloXPTEAM/Jarvis-Projeto@voz-latencia`, commit `c6e14c0a`).
- Tama V2 documentação de produto não-GPL (`DaniloXPTEAM/tama-v2@arena/a4704b56-tama-v2` — **zero** código GPL/`nanoMuse`/`OpenMinis`/Pet copiado).

> **Legenda de status real (sem simulação):**
> - `INTEGRATED_UNTESTED`: implementado em Kotlin/XML e verificado por checagem estática e/ou testes unitários locais, aguardando compilação Gradle e execução em aparelho/emulador Android (JDK/Android SDK ausentes no sandbox).
> - `PARTIAL`: funcionalidade preservada com escopo seguro documentado ou recurso complementar pendente.
> - `NOT_IMPLEMENTED`: não implementado — registrado honestamente sem botão falso.
> - `INTENTIONALLY_EXCLUDED`: item Tamagotchi/Pet, `nanoMuse`/GPL ou captura silenciosa incompatível, removido conforme diretriz.

---

## 1. Preservação Integral das Funções da Gabi (`main` + `voz-latencia`)

| # | Capacidade Gabi Original | Origem (`voz-latencia` / `main`) | Implementação Atual em `NOVO_AGENTE_HB` | Melhorias / Correções R1.2 | Status Real |
| :-: | :--- | :--- | :--- | :--- | :--- |
| 1 | Perfil vocal e prosódia congelados (`gabi_24l`, B2/C2) | `FrozenVoiceIdentitySpec`, `pocket_tts` (`portuguese_24l`) | `VoiceProfiles.kt`, `assets/config/voice_profiles.json`, `SettingsScreen` | Transparência explícita `activeVoiceDisclosure` / `fallbackActiveReason` sem troca silenciosa para voz robótica do sistema | `INTEGRATED_UNTESTED` |
| 2 | Conversa por voz bidirecional em tempo real | `GeminiLiveVoiceClient`, `VoiceEngineCoordinator` | `GeminiLiveVoiceClient.kt` (WebSocket PCM16 16kHz -> 24kHz `AudioTrack`), `VoiceEngineCoordinator.kt` | Descoberta real `GET /v1beta/models` (`bidiGenerateContent`), agregador de turno `GeminiLiveTurnAggregator`, watchdog e reconexão | `INTEGRATED_UNTESTED` |
| 3 | STT local Android + síntese TTS fallback explícito | `SpeechRecognizer`, `TextToSpeech` | `VoiceEngineCoordinator.kt` (`SpeechRecognizer`, `TextToSpeech`, ElevenLabs opcional BYOK) | Aviso visível na UI sempre que fallback Android TTS estiver ativo | `INTEGRATED_UNTESTED` |
| 4 | Roteamento de áudio Bluetooth SCO (fones/viva-voz) | `BluetoothController.kt` (`voz-latencia`) | `AndroidDeviceBridge.kt` (`connectBluetooth`, `bondedBluetoothDevices`, `routeAudioToBluetoothSco`, `stopBluetoothSco`) | **Restaurado na R1.2:** consulta dispositivos pareados (`BLUETOOTH_CONNECT`) e roteia áudio SCO/BLE (`AudioManager.setCommunicationDevice` API 31+ / `startBluetoothSco` legado) | `INTEGRATED_UNTESTED` |
| 5 | Tile nas Configurações Rápidas (1 toque de qualquer tela) | `JavisTileService.kt` (`main`) | `bridges/GabiTileService.kt` + `AndroidManifest.xml` (`QS_TILE`) | **Restaurado na R1.2:** Tile `"Falar com a Gabi"` abre `MainActivity.ACTION_VOICE_TRIGGER` (compatível com API 26–34) | `INTEGRATED_UNTESTED` |
| 6 | Widget da tela inicial Android | `HomeWidgetProvider` (`main`) | `bridges/HomeWidgetProvider.kt`, `res/layout/widget_novo_agente.xml`, `res/xml/widget_info.xml` | Dispara `ACTION_VOICE_TRIGGER` direto na Home/Chat | `INTEGRATED_UNTESTED` |
| 7 | Deep links do app | `MainActivity` | `AndroidManifest.xml` (`scheme="novoagentehb"`) + `MainActivity.kt` | Navegação direta para `habitat`, `chat`, `feed`, `goals`, `library`, `memory`, `computer`, `settings`, `permissions` | `INTEGRATED_UNTESTED` |
| 8 | Catálogo de 20 ferramentas de dispositivo (`ToolCalling.kt`) | `ToolCalling.kt` (`voz-latencia`) | `CapabilityRegistry.kt` + `ToolExecutorAndVerifier.kt` + `AndroidDeviceBridge.kt` + `WeatherClient.kt` + `SpotifyBridge.kt` + `ReminderScheduler.kt` | Cobre `open_app`, `call_contact`, `call_phone`, `send_whatsapp`, `search_youtube`, `search_spotify`, `play_music`, `pause_music`, `get_weather`, `set_reminder`, `set_alarm`, `search_web`, `read_calendar`, `create_calendar_event`, `read_notifications`, `open_camera`, `open_settings`, `share_content`, `bluetooth_connect`, `read_gmail` | `INTEGRATED_UNTESTED` |
| 9 | Parser offline de comandos em pt-BR (sem LLM) | `CommandParser.kt` (`voz-latencia`) | `AndroidDeviceBridge.parseOfflineIntent` | **Expandido na R1.2:** adicionados atalhos offline `set_reminder`, `set_alarm`, `bluetooth_connect`, `open_settings`, `take_photo`, `share_content`, `read_gmail`, `search_web` | `INTEGRATED_UNTESTED` |
| 10 | Leitura de notificações + detecção ativa de Spotify | `NotificationListenerService` + `MediaSessionManager` | `NotificationFeedService.kt` | Usa `MediaSessionManager.OnActiveSessionsChangedListener` para estado em tempo real + captura notificações em Room (`NotificationEntity`) | `INTEGRATED_UNTESTED` |
| 11 | Lembretes exatos com persistência após reinício | `AlarmManager` + `BootReceiver` | `ReminderScheduler.kt`, `ReminderAlarmReceiver`, `BootReceiver` | Reagendamento automático pós-boot via `ReminderDao` | `INTEGRATED_UNTESTED` |
| 12 | Acessibilidade supervisionada (WhatsApp/tela) | `AccessibilityService` (`voz-latencia`) | `AssistantAccessibilityService.kt` + `res/xml/accessibility_service_config.xml` | Leitura real da árvore de nós + execução supervisionada com botão **Parar** sempre disponível | `INTEGRATED_UNTESTED` |
| 13 | Gerenciador de arquivos Workspace + pasta SAF | `FilesScreen.kt`, `FileManager.kt`, `ShareHelper.kt` (`voz-latencia`) | `SafLibraryManager.kt`, `LibraryViewModel.kt`, `LibraryScreen.kt`, `FileProvider` | **Expandido na R1.2:** navegação multinível SAF (`safBreadcrumb`, `navigateUpSaf`), criação de subpasta (`createSafDirectory`), renomear (`renameSafEntry`), excluir Workspace/SAF (`deleteEntry`), abrir app externo (`openSafEntry`) e compartilhar (`shareWorkspaceFile`, `shareSafEntry`) | `INTEGRATED_UNTESTED` |
| 14 | Quarto da Gabi completo (camadas + vídeo + rotina + guarda-roupa) | `RoomScreen.kt`, `GabiCharacter.kt`, `RoomViewModel.kt` (`voz-latencia`) | `QuartoGabiScreen.kt`, `HabitatViewModel.kt`, `HBCharacterController.kt`, `HBCharacterModel.kt`, `HBAnimationCatalog.kt`, `GabiAvatarViews.kt` | **Aprimorado na R1.2:** `TextureView` em vídeo de fundo/abertura, `reduceMotion` real do Android, reação de toque animada (`avatar/talking`), transição `OUTFIT_CHANGE` preservando look, coexistência `MusicReaction` + look escolhido | `INTEGRATED_UNTESTED` |
| 15 | Serviço persistente silencioso em 2º plano (microfone sempre aberto) | `JavisBackgroundService.kt` (`main`) | Substituído por ativação explícita via **GabiTileService + HomeWidgetProvider + `novoagentehb://`** | Escolha intencional de privacidade e conformidade Android 14 (`FOREGROUND_SERVICE_MICROPHONE` somente em sessão de voz ativa autorizada pela pessoa) | `INTENTIONALLY_EXCLUDED` (substituído por Tile + Widget) |

---

## 2. Preservação das Capacidades Tama V2 (Arquitetura Não-GPL)

| # | Capacidade Tama V2 (Referência Documental) | Módulo em `NOVO_AGENTE_HB` | Política & Comportamento Real | Status Real |
| :-: | :--- | :--- | :--- | :--- |
| 16 | Roteamento multi-provedor com fallback explícito e proteção de custo | `ModelRouter.kt` (`ModelRole`, `ProviderId`: Gemini padrão + Groq, OpenRouter, DeepSeek, Anthropic BYOK) | Nunca troca silenciosamente de provedor gratuito para pago após recusa; exige consentimento explícito em `SettingsScreen` | `INTEGRATED_UNTESTED` |
| 17 | Classificação legível de erros HTTP (401/403/413/429/cota/timeout) | `ModelRouter.kt` (`FailureCategory`, `ProviderFailure`), `ProviderFailureCard`, `ProviderFailureDetailDialog` | Cartão retrátil em pt-BR com sanitização de segredos (`SecureKeyVault.redactSecrets`) antes de exibir/logar | `INTEGRATED_UNTESTED` |
| 18 | Orquestração por turno + máquina de estados + botão **Parar** + confirmação | `TaskOrchestrator.kt` (`IDLE`, `PLANNING`, `EXECUTING`, `VERIFYING`, `AWAITING_CONFIRMATION`, `COMPLETED`, `FAILED`, `CANCELLED`), `PolicyGate.kt` | Confirmação explícita (`ConfirmActionBar`) antes de ações sensíveis; cancelamento real propaga estado `HBActivity.Cancelled` | `INTEGRATED_UNTESTED` |
| 19 | Execução verificada de ferramentas + idempotência por turno + anti-alucinação | `ToolExecutorAndVerifier.kt` (`ToolExecutionSemantics`: `VERIFIED_EFFECT`, `INTENT_HANDOFF`, `ARTIFACT_PREPARED`, `NOTIFICATION_PREVIEW_ONLY`, `NOT_IMPLEMENTED`) | `workspace_write_file` valida existência e hash SHA-256 pós-escrita; `linux_proot_shell` e `generic_screen_operator` retornam `NOT_IMPLEMENTED` honesto | `INTEGRATED_UNTESTED` |
| 20 | Memória de longo prazo e metas persistentes | `MemoryStore.kt`, `GoalsScreen.kt`, `GoalsViewModel.kt`, `AppDatabase.kt` (`MemoryDao`, `GoalDao`) | CRUD em SQLite/Room + tela dedicada de memória e metas (sem mecânica de XP/nível de pet) | `INTEGRATED_UNTESTED` |
| 21 | Skills locais reutilizáveis + registro e execução segura | `SkillAndMcpRegistry.kt`, `LocalSkillExecutor.kt`, `ComputerScreen.kt` | Separa preparação de rascunho/artefato (`ARTIFACT_PREPARED`) de gravação verificada (`TASK_EXECUTED`); quarentena restritiva | `INTEGRATED_UNTESTED` |
| 22 | Cliente MCP remoto (`Streamable HTTP` + SSE) com proteção SSRF | `McpJsonRpcClient.kt` + `verify_mcp_and_skills_mock.py` | Envia `notifications/initialized`, preserva `Mcp-Session-Id`, consome `text/event-stream`, bloqueia IPs loopback/privados/RFC1918 por padrão (`allowLocalLan` opcional) | `INTEGRATED_UNTESTED` (protocolo validado em loopback Python) |
| 23 | Feed proativo / central de avisos | `FeedScreen.kt`, `FeedViewModel.kt`, `NotificationFeedService.kt` | Exibe avisos reais capturados + estado de permissão de ouvinte de notificações | `INTEGRATED_UNTESTED` |
| 24 | Diagnóstico técnico & exportação explícita de dados | `DiagnosticsEngine.kt`, `ExplicitDataExporter.kt` | Relatório JSON/texto sem segredos + exportação explícita iniciada pelo usuário (`allowBackup="false"`) | `INTEGRATED_UNTESTED` |
| 25 | Cofre de chaves seguro BYOK | `SecureKeyVault.kt` (`EncryptedSharedPreferences` AES256-GCM) | Zero chaves em `BuildConfig`, código ou `assets/`; chaves somente no dispositivo | `INTEGRATED_UNTESTED` |
| 26 | Shell Linux PRoot local (`linux_proot_shell`) | Declarado em `CapabilityRegistry.kt` / `ToolExecutorAndVerifier.kt` | Mantido explicitamente como `NOT_IMPLEMENTED` (binário/proot ausente nesta fase, sem stub falso) | `NOT_IMPLEMENTED` |
| 27 | Operador genérico de qualquer tela externa (`generic_screen_operator`) | Declarado em `CapabilityRegistry.kt` / `ToolExecutorAndVerifier.kt` | Mantido explicitamente como `NOT_IMPLEMENTED` até integração supervisionada completa por visão/acessibilidade | `NOT_IMPLEMENTED` |

---

## 3. Mapeamento da Progressão e Cuidado para o Quarto da Gabi (Sem Camada Pet)

| Conceito | Regra Aplicada no `NOVO_AGENTE_HB` | Local no Código | Status |
| :--- | :--- | :--- | :---: |
| Alimentar / Cuidado diário | Botão **Alimentar** no Quarto e tool `feed_gabi` registram `careLastAt`, exibem carimbo relativo ("Alimentada há X min") e acionam reação de 3s da HB (`EATING_REACTION`) retornando ao look/rotina anterior. **Não há fome punitiva, HP, doença ou morte.** | `HBCharacterController.feed()`, `QuartoGabiScreen.kt` | `INTEGRATED_UNTESTED` |
| Rotina diária por horário | `06h–10h`: café (`Eating`); `11h–16h`: computador (`AtComputer`); `17h–20h`: leitura (`Reading`); `21h–05h`: pijama (`Sleeping`). | `HBStateResolver.routineForHour()` | `INTEGRATED_UNTESTED` |
| Guarda-roupa e progressão temporal | 4 looks verificados já liberados + 3 looks extras marcados como `ASSET_PENDING` com cálculo de desbloqueio (`unlockedCount`, `daysUntilNextUnlock`). Seleção bloqueada se o PNG ainda não existir. | `HBWardrobeCatalog`, `HBCharacterController.selectOutfit()` | `INTEGRATED_UNTESTED` |
| Metas produtivas | Substituem "missões de XP de dragão" por metas reais da pessoa assistida (`GoalsScreen` + `GoalDao`). | `GoalsScreen.kt`, `Entities.kt` | `INTEGRATED_UNTESTED` |

---

## 4. Recursos Explicitamente Excluídos (Tamagotchi / `nanoMuse` / GPL)

- **Excluídos permanentemente (confirmado por `audit_ip_and_no_pet.py`):** qualquer personagem dragão/mascota, estágios de ovo/crescimento/XP de pet, perda de HP por fome, morte ou tela memorial, pacote `ai.tama.assistant` / `io.github.nanomuse` / `minis://` / `tama://`, e qualquer código ou cabeçalho GPL-3.0 / AGPL.
