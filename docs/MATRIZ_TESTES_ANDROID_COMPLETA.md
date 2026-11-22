# MATRIZ DE TESTES ANDROID COMPLETA — GabiTama (`NOVO_AGENTE_HB`)

**Política de transparência:** Nenhum teste de JVM Gradle, Android Emulator ou dispositivo físico foi simulado como aprovado. Todos os itens que dependem de JDK 17, Android SDK 34 ou hardware/emulador estão marcados explicitamente como **`NOT_RUN_BLOCKED_*`**.

---

## 1. Suítes Executadas com Sucesso neste Sandbox (`PASS`)

| Suíte / Script | Escopo Coberto | Resultado | Observação |
|---|---|---|---|
| `scripts/tests/verify_project_integrity.py` | `8` casos `unittest`: Gradle Wrapper 8.9 (+ bit `+x`, `.gitignore`, workflow), parsing dos `14` XMLs + referências `R.*`, isolamento do `AndroidManifest.xml`, congelamento de voz `gabi_24l` e stack `gemini`, SHA-256 dos `1.324` assets, `64` arquivos Kotlin (pacote/delimitadores/cross-refs/R1.4.1), canais alfa `RGBA` / containers `WebP ALPH`, e `23` entradas Drive `R1.4` + `96×2` frames extraídos | **PASS (`8/8`, 0.35s)** | Executado após todas as correções |
| `scripts/license-audit/audit_ip_and_no_pet.py` | `4` casos `unittest`: zero menções `nanoMuse`/`OpenMinis`/`GPL`, zero camada `Pet`/`Tamagotchi`/`Dragon`, zero segredos ou chaves em `BuildConfig` e geração do `evidence/SBOM.json` | **PASS (`4/4`, 0.13s)** | Escopo direto TOML (`DIRECT_TOML_DECLARED_ONLY`) |
| `scripts/tests/verify_mcp_and_skills_mock.py` | `2` casos `unittest`: contrato estático do cliente `McpJsonRpcClient.kt` + handshake HTTP/JSON-RPC/SSE real contra servidor local efêmero (`initialize`, `notifications/initialized`, `tools/list`, `tools/call` SSE e HTTP `500`) | **PASS (`2/2`, 0.51s)** | Valida protocolo MCP sem substituir `testDebugUnitTest` |
| Varredura AST/Regex Kotlin (`64` arquivos `.kt`) | Verificação `Screen -> ViewModel` (`10/10` telas, `missing=[]`), resolução `NotificationFeedService.refreshActiveMediaSessions`, alinhamento de parâmetros `parseOfflineIntent <-> ToolExecutorAndVerifier`, anotações Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `10` `@HiltViewModel`) | **PASS** | Executado pós-edição |

---

## 2. Testes Unitários Kotlin JVM (`src/test/`) — Pendentes de JDK 17

| Arquivo / Caso de Teste | O que Valida | Status |
|---|---|---|
| `AgentCoreUnitTest.registryExcludesAllPetCapabilitiesAndCoversCoreTools` | 27 capacidades únicas sem comandos Pet | **`NOT_RUN_BLOCKED_NO_JDK`** |
| `AgentCoreUnitTest.policyGateBlocksOtpPasswordsAndPetCommands` | Bloqueio de OTP/PIN/senhas e comandos Pet legados | **`NOT_RUN_BLOCKED_NO_JDK`** |
| `AgentCoreUnitTest.policyGateRequiresConfirmationForExternalEffectsAndPaidCalls` | Confirmação obrigatória para WhatsApp/ligação/calendário/Claude | **`NOT_RUN_BLOCKED_NO_JDK`** |
| `AgentCoreUnitTest.redactSecretsMasksCommonProviderTokens` | Redação de tokens `AIza*`, `AQ.*`, `sk-*`, `Bearer *` | **`NOT_RUN_BLOCKED_NO_JDK`** |
| `AgentCoreUnitTest.diagnosticsMatchesOnlyExactCommand` | Disparo restrito de `/diagnostico` | **`NOT_RUN_BLOCKED_NO_JDK`** |
| `AgentCoreUnitTest.skillSanitizationStripsPromptInjection` | Quarentena contra prompt injection em manifests | **`NOT_RUN_BLOCKED_NO_JDK`** |
| `AgentCoreUnitTest.personaAndOfflineParserStayBrazilianAndPetFree` | Persona Gabi + parser offline (`get_weather`, `open_app`, `set_reminder` c/ `subject`/`trigger_at`, `set_alarm` c/ `hour`/`minute`/`description`) | **`NOT_RUN_BLOCKED_NO_JDK`** |
| `AgentCoreUnitTest.capabilitySemanticsSeparateHandoffNotificationPreviewAndUnimplemented` | Separação `HANDOFF_TO_USER`, `NOTIFICATION_PREVIEW_ONLY`, `NOT_IMPLEMENTED` | **`NOT_RUN_BLOCKED_NO_JDK`** |
| `AgentCoreUnitTest.localSkillSeparatesManifestOnlyFromVerifiedDiskExecution` | `ARTIFACT_PREPARED` vs `TASK_EXECUTED` com releitura SHA-256 e quarentena | **`NOT_RUN_BLOCKED_NO_JDK`** |
| `AgentCoreUnitTest.mcpValidatesEndpointsParsesSseAndCompletesRealLocalHttpHandshake` | SSRF (`169.254.*`, userinfo, `file://`), SSE e servidor `HttpServer` local | **`NOT_RUN_BLOCKED_NO_JDK`** |
| `HBCharacterStateUnitTest` (todos os 8 métodos de teste) | Rotina horária, prioridade estrita de 10 níveis, `ASSET_PENDING`, Spotify, catálogos/guarda-roupa, R1.4/R1.4.1 continuidade de fases (`idade1`/`idade2`) e `HBMotionTruth`, parser offline | **`NOT_RUN_BLOCKED_NO_JDK`** |
| `VoiceAndGeminiLiveUnitTest` (todos os casos de reducer/aggregator/watchdog) | Transições `LiveSessionState`, barge-in `Interrupted`, deduplicação `toolCall.id`, watchdog de escuta | **`NOT_RUN_BLOCKED_NO_JDK`** |

---

## 3. Testes Instrumentados / Emulador / Dispositivo Real — Pendentes de SDK/KVM/Aparelho

| Categoria | Casos a Executar no Emulador ou Celular (`Pacote A` / `BUILD_WINDOWS_GABITAMA.md`) | Status |
|---|---|---|
| **Boot & Navegação Compose** | Abertura fria (`MainActivity`), navegação entre todas as 10 rotas (`Home`, `Chat`, `Habitat/Quarto`, `Feed`, `Goals`, `Library`, `Memory`, `Computer`, `Settings`, `Permissions`), rotação e retenção de estado | **`NOT_TESTED_BLOCKED_NO_EMULATOR`** |
| **Renderização & Vídeo `TextureView`** | `StageLoopVideo` (`idade1.mp4` / `idade2.mp4`) na Home (medalhão circular 190dp), Quarto (tela cheia 9:16), Computador (92dp), `RoomIntroVideo`, `RoomLoopBackgroundVideo`, pausa em `ON_PAUSE`, retorno de `ON_RESUME` e alternância de `Animator duration scale = 0` | **`NOT_TESTED_BLOCKED_NO_EMULATOR`** |
| **Áudio & Gemini Live WebSocket** | Gravação `AudioRecord` 16kHz PCM, playback `AudioTrack` 24kHz (`Channel.UNLIMITED` + `pcmLock`), interrupção por voz (barge-in + flush), fallback explícito TTS Android e liberação `releasePcmTrack()` ao parar sessão | **`NOT_TESTED_BLOCKED_NO_EMULATOR`** |
| **Integrações Android & Serviços** | `NotificationFeedService` (`Spotify` playing + Gmail preview), `AssistantAccessibilityService` (envio WhatsApp c/ janela ativa), `ReminderAlarmReceiver` + `BootReceiver`, `GabiTileService`, `HomeWidgetProvider`, `SafLibraryManager` (picker de pasta + imagem de fundo do Quarto) | **`NOT_TESTED_BLOCKED_NO_EMULATOR`** |
