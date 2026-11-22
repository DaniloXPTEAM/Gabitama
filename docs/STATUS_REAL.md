# STATUS REAL — NOVO_AGENTE_HB (R1.1 · 2026-11-21)

Documento de consolidação objetiva pós-auditoria e correções **R1.1** (Gates R1.1-0..4).

## 1. Resumo Executivo

- **Produto / Pacote:** `com.novoagentehb.app` (identificador provisório `NOVO_AGENTE_HB`, branch `arena/cc3c891a-jarvis-projeto`, `versionCode = 3`, `versionName = "0.2.1-r1.1"`).
- **Estado do Build APK no sandbox:** **`APK_NOT_BUILT / BLOCKED`** — `./gradlew --version --offline` falha no sandbox com `JAVA_HOME is not set and no 'java' command could be found in your PATH` (ausência de JDK 17, Android SDK 34 e rede Maven/Google).
- **Correções preventivas de build aplicadas (P0):** compatibilidade Gradle 8.9 / AGP 8.7.0 / Kotlin 2.0.0 / KSP 2.0.0-1.0.24 / Hilt 2.52 conferida manualmente; dependência não usada `accompanist-permissions` removida; única referência `org.json.JSONObject` eliminada via Gson; adicionados `kapt.correctErrorTypes = true`, `unitTests.isReturnDefaultValues = true` e `BUILD_TIME = "2026-11-21-r1.1"` determinístico.
- **Estado dos Testes Executados no Sandbox:**
  - `scripts/license-audit/audit_ip_and_no_pet.py`: **`UNIT_PASS` (4/4)**
  - `scripts/tests/verify_project_integrity.py`: **`UNIT_PASS` (7/7)** — inclui validação de PNG RGBA (`768x1024`), WebP `ALPH`, ausência de `org.json.*`, `accompanist` removida e contratos R1.1.
  - `scripts/tests/verify_mcp_and_skills_mock.py`: **`UNIT_PASS` (2/2)** — servidor HTTP/SSE local em Python validando `initialize` -> `notifications/initialized` -> `tools/list` -> `tools/call` (JSON + SSE + erro HTTP 500).
  - Análise estática Kotlin (62 arquivos `.kt`): **`STATIC_PASS`** — 0 tipos duplicados, 0 imports internos não resolvidos, 27 ferramentas únicas, todos os aliases `libs.*` conferidos.
  - Suítes JUnit4 Android/JVM (`AgentCoreUnitTest`, `ModelRouterAndVoiceUnitTest`, `HBCharacterStateUnitTest`): **`CODE_PRESENT` (não executadas por ausência de JDK 17/Gradle no sandbox -> `BLOCKED`)**.
  - Execução em emulador, aparelho físico e APIs ao vivo (Gemini Live, Spotify, WhatsApp, Gmail): **`DEVICE_PASS / LIVE_API_PASS: BLOCKED`**.

## 2. Principais Mudanças da R1.1 (sobre a R1.0)

1. **Honestidade total em Skills locais (`LocalSkillExecutor`)**: instrução livre em `workspace_only` retorna `ARTIFACT_PREPARED` (`taskExecuted = false`, `SKILL_INTERPRETER_NOT_IMPLEMENTED`), enquanto somente o adaptador determinístico `workspace_action_write_verify` executa gravação + releitura do disco + SHA-256 retornando `TASK_EXECUTED`.
2. **Cliente MCP JSON-RPC endurecido (`McpJsonRpcClient`)**: adiciona `notifications/initialized`, propagação de `Mcp-Session-Id` e `MCP-Protocol-Version`, suporte a `text/event-stream` (SSE), `followRedirects(false)`, bloqueio de LAN/metadados por padrão com opt-in `allowLocalLan`, log detalhado `negotiationLog` na UI e teste contra servidor HTTP real em loopback.
3. **27 ferramentas com semântica explícita (`ToolExecutionSemantics`)**: `read_gmail` claramente rotulada como `NOTIFICATION_PREVIEW_ONLY`, `search_spotify` e apps Android como `HANDOFF_TO_USER`, `workspace_write_file` com verificação por releitura SHA-256, e `linux_proot_shell` + `generic_screen_operator` mantidos explicitamente como `NOT_IMPLEMENTED`.
4. **Gemini Live por turno + idempotência + transparência de voz**: `GeminiLiveTurnAggregator` grava no máximo 1 mensagem de usuário e 1 do agente por turno, deduplica tool calls por `callId` e marca interrupção (`[interrompido]`); `TaskOrchestrator` usa chave `live:$callId:$capabilityName` e `liveCallId` em `PendingConfirmation`; Home/Chat/Ajustes exibem explicitamente que o Live usa voz `Kore` e que o runtime local `gabi_24l` está pendente.
5. **Quarto da Gabi honesto e Spotify em tempo real**: `HBWardrobeCatalog` e `WardrobeDialog` explicam que vestido lilás, suéter mostarda e pijama estrelado reaproveitam as poses existentes (`gabi-ler.png`, `gabi-computador.png`, `gabi-dormir.png`), mantendo looks sem arte bloqueados (`ASSET_PENDING`); `NotificationFeedService` registra `MediaSessionManager.OnActiveSessionsChangedListener` + `MediaController.Callback`.
