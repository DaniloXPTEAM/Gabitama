# CORREÇÕES E VERIFICAÇÃO R1.1 — `GABITAMA_CORRECOES_R1_1.md`

**Data:** 2026-11-21  
**Escopo:** Resposta objetiva ao documento de continuação `GABITAMA_R1_1_VERIFICACAO_INDEPENDENTE_E_CORRECOES_ARENA_2026-11-21.md`, preservando o nome técnico interno `NOVO_AGENTE_HB` / pacote `com.novoagentehb.app` e mantendo os arquivos R0 e R1.0 intactos na pasta `GabiTama` do Google Drive.

---

## 1. Gates R1.1 (0 a 4) — Resultado Real

| Gate | Descrição | Status real R1.1 | Evidência objetiva |
| --- | --- | --- | --- |
| **Gate R1.1-0** | Diagnóstico reproduzível de build + compatibilidade + correções preventivas P0 | **`STATIC_PASS` (preventivo) / `APK_BUILT: BLOCKED`** | `./gradlew --version --offline` falhou (`JAVA_HOME is not set`); P0 aplicados (`accompanist-permissions` removida, `org.json` zerado via Gson, `kapt.correctErrorTypes = true`, `unitTests.isReturnDefaultValues = true`, `BUILD_TIME` determinístico, `versionCode = 3`). |
| **Gate R1.1-1** | Honestidade de Skills, MCP e 27 ferramentas | **`STATIC_PASS` + `UNIT_PASS` (mock HTTP Python)** | `LocalSkillExecutor` separa `ARTIFACT_PREPARED` (`workspace_only`) de `TASK_EXECUTED` (`workspace_action_write_verify` c/ SHA-256); `McpJsonRpcClient` implementa `notifications/initialized`, `Mcp-Session-Id`, SSE e opt-in LAN; 27 tools classificadas com `ToolExecutionSemantics`; PRoot e Screen Operator marcados `NOT_IMPLEMENTED`. |
| **Gate R1.1-2** | Gemini Live, transcrição por turno e voz `gabi_24l` | **`STATIC_PASS` / `LIVE_API_PASS: BLOCKED`** | `GeminiLiveTurnAggregator` garante 1 turno usuário + 1 turno agente, barge-in (`[interrompido]`) e dedup `callId`; `TaskOrchestrator` usa idempotência `live:$callId:$cap` e `liveCallId` em `PendingConfirmation`; Home/Chat/Ajustes exibem transparência explícita `Kore` vs `gabi_24l` pendente. |
| **Gate R1.1-3** | Quarto da Gabi, transparência visual e Spotify | **`STATIC_PASS` / `DEVICE_PASS: BLOCKED`** | Todos os PNG `gabi-*.png` (`768x1024`, RGBA) e `avatar/*/f001.webp` (`VP8X+ALPH`) verificados; `WardrobeDialog` distingue pose reaproveitada (`gabi-ler.png`, `gabi-computador.png`, `gabi-dormir.png`) vs `ASSET_PENDING`; `NotificationFeedService` registra `OnActiveSessionsChangedListener` + `MediaController.Callback`. |
| **Gate R1.1-4** | Segurança, Zero Pet, Zero IP alheia, Drive íntegro | **`STATIC_PASS` + `UNIT_PASS` (auditorias Python)** | `audit_ip_and_no_pet.py` (4/4 OK), `verify_project_integrity.py` (7/7 OK), `verify_mcp_and_skills_mock.py` (2/2 OK), 0 segredos em código/`BuildConfig`, R0 + R1.0 preservados no Drive, R1.1 entregue em pasta/arquivos próprios sem sobrescrever. |

---

## 2. Checklist A01–A16 (Estado por Caso)

| Caso | Descrição | Status honesto R1.1 |
| --- | --- | --- |
| **A01** | Build/instalação independente (`com.novoagentehb.app.debug`) | `APK_NOT_BUILT / BLOCKED` no sandbox; correções preventivas P0 aplicadas (`CODE_PRESENT` / `STATIC_PASS`). |
| **A02** | Onboarding / Ajustes / BYOK (`EncryptedSharedPreferences`) | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED`. |
| **A03** | Chat texto + fila única + Parar + recibo | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED`. |
| **A04** | Gemini Live áudio + transcrição consolidada por turno | `CODE_PRESENT` / `STATIC_PASS` (`GeminiLiveTurnAggregator`) / `LIVE_API_PASS: BLOCKED`. |
| **A05** | Barge-in / interrupção no Gemini Live | `CODE_PRESENT` / `STATIC_PASS` (lógica testada em `ModelRouterAndVoiceUnitTest.kt`, pendente JDK). |
| **A06** | Tool call no Gemini Live + `PolicyGate` + confirmação | `CODE_PRESENT` / `STATIC_PASS` (`liveCallId` + `CONFIRMACAO_PENDENTE` + `live:$callId`). |
| **A07** | Transparência de voz (`Kore` vs `gabi_24l`) | `CODE_PRESENT` / `STATIC_PASS` (exibida em Home, Chat e Ajustes). |
| **A08** | Quarto da Gabi: camadas + transparência PNG/WebP + guarda-roupa honesto | `CODE_PRESENT` / `STATIC_PASS` (RGBA/ALPH validados via inspeção binária e visual) / `DEVICE_PASS: BLOCKED`. |
| **A09** | Quarto da Gabi: persistência + alimentar + toque + abertura pulável | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED`. |
| **A10** | Spotify badge + `MediaSessionManager.OnActiveSessionsChangedListener` | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED`. |
| **A11** | Skill `workspace_only` (`ARTIFACT_PREPARED`, sem execução falsa) | `CODE_PRESENT` / `STATIC_PASS` (teste JVM escrito em `AgentCoreUnitTest.kt`). |
| **A12** | Skill `workspace_action_write_verify` (`TASK_EXECUTED` + SHA-256) | `CODE_PRESENT` / `STATIC_PASS` (teste JVM escrito em `AgentCoreUnitTest.kt`). |
| **A13** | MCP handshake (`initialize` -> `notifications/initialized` -> `tools/list` -> `tools/call` + SSE) | `CODE_PRESENT` / `STATIC_PASS` / `UNIT_PASS (Python mock HTTP/SSE)` / `DEVICE_PASS: BLOCKED`. |
| **A14** | MCP falha controlada + anti-SSRF / bloqueio de LAN sem opt-in | `CODE_PRESENT` / `STATIC_PASS` / `UNIT_PASS (Python mock HTTP 500)`. |
| **A15** | `/diagnostico` exato com redação de segredos e `BUILD_TIME` determinístico | `CODE_PRESENT` / `STATIC_PASS` / `DEVICE_PASS: BLOCKED`. |
| **A16** | PRoot & Operador de Tela Genérico declarados `NOT_IMPLEMENTED` | `CODE_PRESENT` / `STATIC_PASS` (`NOT_IMPLEMENTED` explícito na matriz, código e UI). |
