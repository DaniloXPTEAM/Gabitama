# RELATÓRIO DE TESTES E VERIFICAÇÕES EXECUTADAS (R1.1 · 2026-11-21)

## 1. Testes Executados com Sucesso no Sandbox

| Script / Ferramenta | Escopo real | Resultado |
| --- | --- | --- |
| `python3 novo-agente-hb/scripts/license-audit/audit_ip_and_no_pet.py -v` | IP alheia / GPL, zero Pet/Tamagotchi, zero segredos ou `BuildConfig` API keys, geração de `evidence/SBOM.json` (`DIRECT_TOML_DECLARED_ONLY`) | **PASS (4/4 testes em 0.104s)** |
| `python3 novo-agente-hb/scripts/tests/verify_project_integrity.py -v` | Gradle Wrapper 8.9, XML/R.*, Manifest, JSON `gabi_24l` + provedores, SHA-256 dos 1.324 assets Gabi, 62 `.kt` (pacotes, delimitadores, zero `org.json`, zero `accompanist`, contratos R1.1), PNG RGBA `768x1024` & WebP `ALPH` | **PASS (7/7 testes em 0.300s)** |
| `python3 novo-agente-hb/scripts/tests/verify_mcp_and_skills_mock.py -v` | Servidor HTTP/SSE efêmero em `127.0.0.1` validando `initialize` -> `notifications/initialized` -> `tools/list` -> `tools/call` (JSON + SSE + HTTP 500) + checagem de controles no `McpJsonRpcClient.kt` | **PASS (2/2 testes em 0.509s)** |
| Checagem estática Kotlin cross-reference (Python) | 62 arquivos `.kt`, 0 tipos top-level duplicados, 0 imports internos quebrados, 27 tools únicas, todos os aliases `libs.*` batendo com `libs.versions.toml` | **STATIC_PASS** |

## 2. Testes Escritos em JUnit 4 (Aguardando JDK 17 Local)

Os arquivos abaixo estão prontos em `novo-agente-hb/android/app/src/test/kotlin/com/novoagentehb/app/`, porém **não puderam rodar no sandbox** por ausência de JDK 17 / Gradle offline cache:

1. `AgentCoreUnitTest.kt`: PolicyGate, redação de segredos, sanitização de skills, 27 tools e semânticas R1.1, `LocalSkillExecutor.executeWithStore` (`ARTIFACT_PREPARED` vs `TASK_EXECUTED` com SHA-256 real vs `BLOCKED_QUARANTINE`), e teste HTTP real do `McpJsonRpcClient` com `com.sun.net.httpserver.HttpServer` + `OkHttpClient`.
2. `ModelRouterAndVoiceUnitTest.kt`: catálogo BYOK, Gemini Live reducer, `GeminiLiveTurnAggregator` (turno único usuário+agente, barge-in `[interrompido]`, deduplicação de `callId`).
3. `HBCharacterStateUnitTest.kt`: prioridade de estados, rotina por hora, fallback seguro `ASSET_PENDING`, persistência de guarda-roupa e descrições visuais reais de `HBWardrobeCatalog`.

## 3. Verificações Bloqueadas no Sandbox (`BLOCKED`)

- `./gradlew --version --offline` -> falhou por falta de `JAVA_HOME` / `java` (`APK_NOT_BUILT / BLOCKED`).
- `./gradlew testDebugUnitTest` e `./gradlew assembleDebug` -> `BLOCKED` (aguardam máquina local seguindo `docs/BUILD_LOCAL.md`).
- Testes em aparelho Android, captura de áudio real, Gemini Live API, WhatsApp/Gmail/Spotify em dispositivo -> `NOT_TESTED / BLOCKED`.
