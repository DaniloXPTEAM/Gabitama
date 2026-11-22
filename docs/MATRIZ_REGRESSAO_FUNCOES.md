# MATRIZ DE REGRESSÃO FUNCIONAL — GabiTama (`NOVO_AGENTE_HB`)

**Base:** `0.2.5-r1.4.1` (`versionCode = 7`) + correções pós-auditoria de runtime, bridge offline, acessibilidade e áudio.

---

## 1. Correções Funcionais Aplicadas nesta Rodada

1. **Alinhamento do Parser Offline com o Executor (`AndroidDeviceBridge.kt` <-> `ToolExecutorAndVerifier.kt`):**
   - **Defeito anterior:** `parseOfflineIntent` emitia `"title"`/`"time_iso"` para `set_reminder` e `"label"` para `set_alarm`, enquanto `ToolExecutorAndVerifier` lê `"subject"`/`"trigger_at"` e `"description"` — causando lembretes genéricos ("Lembrete") ignorando o texto e horário falados offline.
   - **Correção:** `parseOfflineIntent` agora extrai o assunto limpo em `"subject"`, duração explícita (`"20 minutos"`, `"1 hora"`, etc.) em `"trigger_at"`, e horário `HH:MM`/`HHh` + `"description"` para `set_alarm`. Coberto em `AgentCoreUnitTest` e `HBCharacterStateUnitTest`.
2. **Estabilidade de Áudio e Barge-in Gemini Live (`VoiceEngineCoordinator.kt`):**
   - **`Channel.UNLIMITED` no `pcmOutChannel`:** substituiu `Channel(96)` + `trySend`, que podia descartar chunks PCM 24kHz durante rajadas rápidas do Gemini Live antes do playback em tempo real.
   - **Sincronização + liberação (`pcmLock` + `releasePcmTrack()`):** protege acesso concorrente entre `Dispatchers.IO` (`writePcm24kBlocking`) e `Dispatchers.Main` (`drainPcmOutQueueAndFlushTrack`), e libera `AudioTrack` no `stopGeminiLiveSession()`.
   - **Verificação do envio em `speak()`:** checa o booleano de retorno de `geminiLive.sendTextTurn(clean)` e ativa `speakSystemFallback(...)` em caso de falha, evitando travar `isSpeaking = true`.
3. **Janela WhatsApp via Acessibilidade (`AndroidDeviceBridge.kt` + `AssistantAccessibilityService.kt`):**
   - **Defeito anterior:** `sendWhatsAppMessage` abria o Intent do WhatsApp e imediatamente chamava `attemptTypeAndSendWhatsApp()` com 0ms de espera, falhando antes da transição de janela no Android.
   - **Correção:** convertido em `suspend fun` com espera incremental (até ~2,4s em fatias de 150ms respeitando `stopRequested` e cancelamento de coroutine) e leitura direta de `rootInActiveWindow.packageName` além de `_activePackage`.
4. **Atualização de Mídia em Segundo Plano (`NotificationFeedService.kt`):**
   - Implementado `refreshActiveMediaSessions(context)` referenciado por `HBCharacterController.kt:151`.

---

## 2. Matriz Completa por Domínio Funcional

| Domínio / Função | Mecanismo Implementado | Evidência Atual | Pendência em Dispositivo / Emulador |
|---|---|---|---|
| **Roteamento LLM (`gemini` primário + BYOK)** | `ModelRouter`: Gemini 2.5 Flash + Groq, OpenRouter, DeepSeek, Anthropic; redator de segredos | **`CODE_VERIFIED`** | Testar chaves reais (`AIza...` / `AQ...`) |
| **Voz Live (`gabi_24l` / `GEMINI_LIVE`)** | WebSocket Bidi `v1beta`, `AudioRecord` 16kHz, `AudioTrack` 24kHz (`Channel.UNLIMITED` + `pcmLock`), barge-in flush | **`CODE_VERIFIED`** | Teste real de microfone/eco/latência no aparelho |
| **Voz `GABI_24L_POCKET` / `ELEVENLABS` / `ANDROID_TTS`** | Disclosure explícito quando Pocket TTS não está embarcado; ElevenLabs exige consentimento pago | **`CODE_VERIFIED`** | Validar síntese real no hardware |
| **Comandos Offline (`parseOfflineIntent`)** | Lembretes, alarmes, apps, Bluetooth, câmera, Gmail preview, clima, busca Web/YouTube/Spotify | **`CODE_VERIFIED`** | `./gradlew testDebugUnitTest` + uso no aparelho |
| **Ferramentas (`27` capacidades + `PolicyGate`)** | Bloqueio OTP/senhas/Pet; confirmação para WhatsApp/ligação/calendário/Claude; `NOT_IMPLEMENTED` explícito p/ `linux_proot_shell` e `generic_screen_operator` | **`CODE_VERIFIED`** | Teste ponta a ponta c/ usuário aprovando modal |
| **WhatsApp (`open_whatsapp_chat` / `send_whatsapp_message`)** | Intent `wa.me` + espera de janela ativa + preenchimento `entry`/`send` | **`CODE_VERIFIED`** | Validar em aparelho com WhatsApp + Acessibilidade ligados |
| **Notificações / Gmail / Spotify (`NotificationFeedService`)** | Somente prévias de notificação (`NOTIFICATION_PREVIEW_ONLY`) e `MediaSessionManager` | **`CODE_VERIFIED`** | Validar permissão de acesso a notificações e Spotify tocando |
| **Lembretes (`ReminderAlarmReceiver` + `BootReceiver`)** | `Room` (`reminders`) + `AlarmManager` + restauração pós-reboot | **`CODE_VERIFIED`** | Validar disparo real de alarme no emulador/aparelho |
| **Arquivos & SAF (`SafLibraryManager` + `LibraryScreen`)** | Workspace interno com releitura SHA-256 + picker SAF (`create/rename/delete/share`) | **`CODE_VERIFIED`** | Validar permissões persistidas de pasta SAF no Android 14 |
| **Skills Locais & MCP Streamable HTTP** | `ARTIFACT_PREPARED` vs `TASK_EXECUTED`; handshake JSON-RPC + `notifications/initialized` + SSE + anti-SSRF | **`MOCK_TESTED`** (`2/2 PASS` Python + `verify_project_integrity.py`) | `./gradlew testDebugUnitTest` (`AgentCoreUnitTest`) |
| **Quarto da Gabi, Fases HB e Barra Inferior** | 3 fases (`YOUNG_CHILD`, `PRE_ADOLESCENT`, `ADOLESCENT`), rotina horária, 4 looks + 3 pendentes, 4 fundos | **`CODE_VERIFIED`** | Teste visual/touch completo no emulador/aparelho |
| **Diagnóstico (`/diagnostico`) & Memória/Metas** | Snapshot seguro sem segredos + `MemoryStore` + `GoalDao` em Room (`allowBackup="false"`) | **`CODE_VERIFIED`** | Testar fluxo completo em sessão real |
