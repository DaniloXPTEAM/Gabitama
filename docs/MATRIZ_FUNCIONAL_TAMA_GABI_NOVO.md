# MATRIZ FUNCIONAL TAMA (ARQUITETURA) + GABI/HB (UX E CAPACIDADES) — R1.0

Legenda honesta de estado:
- `IMPLEMENTED_STATIC_VERIFIED`: código Kotlin/XML/JSON escrito, com testes JUnit4 JVM escritos e validação estática/Python verde, aguardando compilação/execução em JDK 17 + Android SDK 34.
- `PARTIAL_RUNTIME_PENDING`: contrato e fallback explícito prontos, porém binário externo (ex.: Pocket TTS ONNX `portuguese_24l`) não embarcado.
- `NOT_IMPLEMENTED`: recurso ausente nesta versão (sem simulação nem código fictício).
- `NOT_TESTED_ON_DEVICE`: sem execução em emulador/aparelho Android neste ambiente.

## 1. Núcleo de Agência e Personagem HB

| Capacidade | Contrato Entrada / Saída | Arquivo real | Risco | Status real |
| --- | --- | --- | --- | --- |
| `HBCharacterController` + `HBStateResolver` | Sinais (voz, tarefa, alimentação 3s, toque, rotina por hora, guarda-roupa real vs `ASSET_PENDING`, Spotify) -> `HBState` + `RoomUiState` sincronizados em Home, Chat e Quarto | `gabi/HBCharacterModel.kt`, `gabi/HBCharacterController.kt` | Médio | `IMPLEMENTED_STATIC_VERIFIED` + JUnit4 (`HBCharacterStateUnitTest`) |
| `TaskOrchestrator` | Texto/voz + `goalId?` + tool calls Live -> `OrchestratorTurnState` + `ExecutionReceipt` (fila `Mutex`, botão Parar, sincroniza `HBCharacterController`) | `agent/orchestrator/TaskOrchestrator.kt` | Baixo | `IMPLEMENTED_STATIC_VERIFIED` (`NOT_TESTED_ON_DEVICE`) |
| `PolicyGate` | `ToolDescriptor` + `ToolExecutionRequest` -> `Allowed` / `RequireConfirmation` / `Denied` | `agent/policy/PolicyGate.kt` | Crítico | `IMPLEMENTED_STATIC_VERIFIED` + JUnit4 (`AgentCoreUnitTest`) |
| `ModelRouter` | `ModelRole` + histórico + tools -> `TextReply` / `ToolCallReply` / `Failure` + descoberta real `/v1beta/models` (`bidiGenerateContent`) sem ler chaves de `BuildConfig` | `agent/models/ModelRouter.kt` | Alto | `IMPLEMENTED_STATIC_VERIFIED` + JUnit4 (`ModelRouterAndVoiceUnitTest`) |
| `CapabilityRegistry` (27 ferramentas) | 27 `ToolDescriptor`s com `RuntimeSupportTier` explícito (`NATIVE_LOCAL_VERIFIED`, `ANDROID_BRIDGE_INTENT`, `ACCESSIBILITY_OR_LISTENER`, `HTTP_BYOK_OR_PUBLIC`) | `agent/tools/CapabilityRegistry.kt` | Baixo | `IMPLEMENTED_STATIC_VERIFIED` + JUnit4 |
| `LocalSkillExecutor` | Executa skills locais `enabledByUser=true` gravando artefato verificado por SHA-256 em `agent_workspace/skills/` com `TaskReceipt` | `agent/skills/LocalSkillExecutor.kt` | Médio | `IMPLEMENTED_STATIC_VERIFIED` + JUnit4 |
| `McpJsonRpcClient` | Cliente HTTP/HTTPS JSON-RPC 2.0 opt-in (`initialize`, `tools/list`, `tools/call`) com confirmação `PolicyGate` obrigatória | `agent/skills/McpJsonRpcClient.kt` | Alto | `IMPLEMENTED_STATIC_VERIFIED` + JUnit4 |
| `Shell Linux / PRoot` | Terminal Linux embarcado | Não incluído (evita Termux/PRoot GPL) | Crítico IP | **`NOT_IMPLEMENTED`** (explícito na tela Computador) |
| `Operador Genérico de Tela` | Navegação autônoma arbitrária em apps de terceiros | Apenas bridge explícito de acessibilidade WhatsApp presente | Crítico | **`NOT_IMPLEMENTED`** (explícito na tela Computador) |
| `DiagnosticsEngine` (`/diagnostico`) | Comando exato local -> relatório sanitizado sem chaves nem histórico | `agent/diagnostics/DiagnosticsEngine.kt` | Crítico | `IMPLEMENTED_STATIC_VERIFIED` + JUnit4 |
| `SecureKeyVault` | `EncryptedSharedPreferences` AES256-GCM isolado (`novo_agente_hb_secure_prefs`), zero chaves em `BuildConfig` | `data/security/SecureKeyVault.kt` | Crítico | `IMPLEMENTED_STATIC_VERIFIED` + JUnit4 |

## 2. Subsistema de Voz e Gemini Live

| Componente | Implementação | Estado honesto |
| --- | --- | --- |
| `GeminiLiveVoiceClient.kt` + `VoiceEngineCoordinator.kt` + `GeminiLiveSessionReducer` | WebSocket `BidiGenerateContent` com `x-goog-api-key`, `AudioRecord` PCM16 16 kHz, fila sequencial `Channel` -> `AudioTrack` PCM 24 kHz, barge-in (`Interrupted` drena fila), dispatch de `ToolInvocation` para `TaskOrchestrator`/`PolicyGate`, watchdog 15s e 1x reconexão controlada | `IMPLEMENTED_STATIC_VERIFIED` + JUnit4 (`ModelRouterAndVoiceUnitTest`) / Rede e áudio ao vivo `BLOCKED` no sandbox |
| `VoiceProfiles.kt` + `voice_profiles.json` | Identidade congelada `gabi_24l`, perfis `B2_base`, `B2_pausas`, `C2_base`, `C2_pausas`, `allow_silent_swap_to_system_tts = false` | `IMPLEMENTED_STATIC_VERIFIED` |
| Runtime local Pocket TTS (`portuguese_24l`) | Contrato + fallback explícito com aviso visível | `PARTIAL_RUNTIME_PENDING` |
