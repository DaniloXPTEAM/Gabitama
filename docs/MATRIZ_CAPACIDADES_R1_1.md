# MATRIZ HONESTO DE CAPACIDADES — `MATRIZ_CAPACIDADES_R1_1.md`

**Data:** 2026-11-21  
**Legenda obrigatória de verificação:**
- `CODE_PRESENT`: código Kotlin/Compose/manifesto escrito no repositório.
- `STATIC_PASS`: validado por inspeção de código, `verify_project_integrity.py`, `audit_ip_and_no_pet.py` e checagem de símbolos/imports/delimitadores.
- `UNIT_PASS`: executado em JVM/Python no ambiente atual (ver coluna de escopo exato).
- `APK_BUILT` / `EMULATOR_PASS` / `DEVICE_PASS` / `LIVE_API_PASS`: **não alcançados neste sandbox** por falta de JDK 17 / Android SDK / rede com Google & Maven (`BLOCKED`).
- `NOT_IMPLEMENTED`: recurso explicitamente não implementado e assim declarado ao usuário.

---

## 1. Matriz das 27 Ferramentas do `CapabilityRegistry`

Todas as 27 ferramentas estão classificadas em `CapabilityRegistry.kt` com `expectedSemantics: ToolExecutionSemantics` e `verificationState: CapabilityVerificationState = INTEGRATED_UNTESTED` (aguardando validação real em dispositivo Android).

| # | Ferramenta | Risco | Semântica real R1.1 (`ToolExecutionSemantics`) | Tier | Estado atual no sandbox |
| --- | --- | --- | --- | --- | --- |
| 1 | `open_app` | `EXTERNAL_SIDE_EFFECT` | `HANDOFF_TO_USER` (lança Intent de app Android; usuário interage no app) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 2 | `call_contact` | `EXTERNAL_SIDE_EFFECT` | `HANDOFF_TO_USER` (abre discador `ACTION_DIAL` com confirmação; não liga sozinho) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 3 | `set_alarm` | `LOCAL_MUTATION` | `HANDOFF_TO_USER` (dispara Intent `AlarmClock.ACTION_SET_ALARM`) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 4 | `search_web` | `READ_ONLY` | `HANDOFF_TO_USER` (abre navegador Android com busca) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 5 | `search_youtube` | `READ_ONLY` | `HANDOFF_TO_USER` (abre YouTube/navegador com consulta) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 6 | `search_spotify` | `EXTERNAL_SIDE_EFFECT` | `HANDOFF_TO_USER` (abre Spotify via Intent / Spotify Web API search; **não controla fila diretamente via OAuth player**) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 7 | `pause_music` | `LOCAL_MUTATION` | `EXECUTED_AND_VERIFIED` (`AudioManager` / `MediaController` `KeyEvent.KEYCODE_MEDIA_PAUSE`) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 8 | `resume_music` | `LOCAL_MUTATION` | `EXECUTED_AND_VERIFIED` (`KeyEvent.KEYCODE_MEDIA_PLAY`) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 9 | `next_track` | `LOCAL_MUTATION` | `EXECUTED_AND_VERIFIED` (`KeyEvent.KEYCODE_MEDIA_NEXT`) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 10 | `send_whatsapp_message` | `EXTERNAL_SIDE_EFFECT` | `HANDOFF_TO_USER` (abre chat WhatsApp pré-preenchido via Intent após confirmação `PolicyGate`; envio final depende do toque ou acessibilidade assistida) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 11 | `open_whatsapp_chat` | `READ_ONLY` | `HANDOFF_TO_USER` (abre conversa WhatsApp via Intent) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 12 | `read_notifications` | `READ_ONLY` | `NOTIFICATION_PREVIEW_ONLY` (lê eventos armazenados em `FeedEventDao` via `NotificationListenerService`) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 13 | `get_weather` | `READ_ONLY` | `READ_VERIFIED` (consulta Open-Meteo HTTPS sem chave) | `EXTERNAL_NETWORK` | `CODE_PRESENT` / `STATIC_PASS` / `LIVE_API_PASS: BLOCKED` |
| 14 | `set_reminder` | `LOCAL_MUTATION` | `EXECUTED_AND_VERIFIED` (persiste em `ReminderDao` + agenda `AlarmManager`) | `NATIVE_LOCAL_VERIFIED` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 15 | `read_calendar` | `READ_ONLY` | `READ_VERIFIED` (`CalendarContract` leitura com checagem de permissão) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 16 | `create_calendar_event` | `LOCAL_MUTATION` | `HANDOFF_TO_USER` (abre Intent `CalendarContract.Events` para revisão e gravação) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 17 | `read_gmail` | `READ_ONLY` | `NOTIFICATION_PREVIEW_ONLY` (**não usa Gmail REST/IMAP OAuth** — lê somente prévias de notificações `com.google.android.gm` capturadas localmente) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 18 | `ask_claude` | `PAID_API_CALL` | `READ_VERIFIED` (chama Anthropic Messages API com BYOK + confirmação `PolicyGate` se `allowPaid=false`) | `EXTERNAL_NETWORK` | `CODE_PRESENT` / `STATIC_PASS` / `LIVE_API_PASS: BLOCKED` |
| 19 | `save_memory` | `LOCAL_MUTATION` | `EXECUTED_AND_VERIFIED` (`MemoryStore` + `MemoryDao` com leitura de confirmação) | `NATIVE_LOCAL_VERIFIED` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 20 | `search_memory` | `READ_ONLY` | `READ_VERIFIED` (`MemoryDao` consulta local) | `NATIVE_LOCAL_VERIFIED` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 21 | `upsert_goal` | `LOCAL_MUTATION` | `EXECUTED_AND_VERIFIED` (`GoalDao` criação/atualização local) | `NATIVE_LOCAL_VERIFIED` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 22 | `workspace_write_file` | `LOCAL_MUTATION` | `EXECUTED_AND_VERIFIED` (grava em `agent_workspace/` + **releitura do disco + verificação SHA-256** adicionadas na R1.1) | `NATIVE_LOCAL_VERIFIED` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 23 | `workspace_read_file` | `READ_ONLY` | `READ_VERIFIED` (lê arquivo dentro de `agent_workspace/`) | `NATIVE_LOCAL_VERIFIED` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 24 | `take_screenshot_hint` | `READ_ONLY` | `HANDOFF_TO_USER` (`AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT` se ativo) | `ANDROID_BRIDGE_INTENT` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 25 | `open_quarto_gabi` | `READ_ONLY` | `HANDOFF_TO_USER` (navegação interna para `QuartoGabiScreen` / `novoagentehb://habitat`) | `NATIVE_LOCAL_VERIFIED` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 26 | `feed_gabi` | `LOCAL_MUTATION` | `EXECUTED_AND_VERIFIED` (`HBCharacterController.feed()` com persistência em `AppPreferences`) | `NATIVE_LOCAL_VERIFIED` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED` |
| 27 | `diagnostics_snapshot` | `READ_ONLY` | `READ_VERIFIED` (`DiagnosticsEngine.buildReport` + redação de segredos) | `NATIVE_LOCAL_VERIFIED` | `CODE_PRESENT` / `STATIC_PASS` / `UNIT_PASS (JVM test written; Python static verified)` |

---

## 2. Subsistemas, Skills, MCP, Voz e Recursos Explicitamente Não Implementados

| Subsistema / Recurso | Estado real R1.1 | Observação técnica honesta |
| --- | --- | --- |
| **`LocalSkillExecutor` (modo manifesto `workspace_only`)** | `CODE_PRESENT` (`ARTIFACT_PREPARED`, `taskExecuted = false`) | Salva manifesto Markdown em `agent_workspace/skills/` e informa `SKILL_INTERPRETER_NOT_IMPLEMENTED`. Nunca finge ter executado instruções livres. |
| **`LocalSkillExecutor` (modo `workspace_action_write_verify`)** | `CODE_PRESENT` (`TASK_EXECUTED`, `taskExecuted = true`, `verifiedPostCondition = true`) | Adaptador determinístico: grava documento, relê do disco e valida SHA-256. Coberto em `AgentCoreUnitTest.kt` (`CODE_PRESENT`; execução real do Gradle JUnit bloqueada por falta de JDK 17). |
| **`McpJsonRpcClient` (Streamable HTTP POST + SSE)** | `CODE_PRESENT` + `UNIT_PASS (Python mock HTTP/SSE)` | Implementa `initialize` -> `Mcp-Session-Id` -> `notifications/initialized` -> `tools/list` -> `tools/call`, SSE `text/event-stream`, anti-redirect, bloqueio de metadados/SSRF e opt-in `allowLocalLan`. Testado contra servidor HTTP local em Python (`verify_mcp_and_skills_mock.py`) e também codificado com `HttpServer` em `AgentCoreUnitTest.kt`. |
| **Gemini Live (`BidiGenerateContent`)** | `CODE_PRESENT` / `STATIC_PASS` / `LIVE_API_PASS: BLOCKED` | Consolidação de 1 mensagem de usuário + 1 do agente por turno (`GeminiLiveTurnAggregator`), dedup por `callId`, confirmação obrigatória no `PolicyGate`, transparência explícita da voz `Kore` na UI. |
| **Pocket TTS local (`gabi_24l`)** | `PARTIAL_RUNTIME_PENDING` | Especificação e perfis congelados (`B2`/`C2`), mas sem runtime ONNX/Pocket TTS embarcado no APK; app faz fallback explícito para `Android TTS` ou usa voz pré-construída `Kore` no Live. |
| **Linux PRoot / Shell Linux embarcado** | **`NOT_IMPLEMENTED`** | Declarado explicitamente em `CapabilityRegistry.unimplementedCapabilities` e exibido na tela `Computador & Skills`. |
| **Operador Genérico de Tela por Visão** | **`NOT_IMPLEMENTED`** | Declarado explicitamente em `CapabilityRegistry.unimplementedCapabilities` e exibido na tela `Computador & Skills`. |
