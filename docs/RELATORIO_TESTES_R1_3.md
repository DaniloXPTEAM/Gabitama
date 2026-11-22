# RELATORIO_TESTES_R1_3 — Relatório Honesto de Testes e Verificações (R1.3)

**Projeto:** `NOVO_AGENTE_HB` (`com.novoagentehb.app`, `versionCode = 5`, `versionName = 0.2.3-r1.3`)  
**Data (UTC):** 2026-11-21  

---

## 1. Testes Executados Neste Sandbox (PASS)

| Verificação | Comando | Resultado | Escopo real validado |
|---|---|---|---|
| **Auditoria IP, Zero-Pet, Segredos e SBOM** | `python3 novo-agente-hb/scripts/license-audit/audit_ip_and_no_pet.py -v` | **PASS (4/4)** | Zero strings GPL/nanoMuse/Tamagotchi/Pet, zero chaves embutidas, SBOM direto atualizado em `evidence/SBOM.json` |
| **Integridade do Projeto, Assets & Drive R1.3** | `python3 novo-agente-hb/scripts/tests/verify_project_integrity.py -v` | **PASS (8/8)** | Gradle 8.9 intacto, XMLs/referências `R.*`, `voice_profiles.json` + `providers_catalog.json`, 1.324 hashes SHA-256 de `voz-latencia`, 13 MP4s originais do Drive em `assets_originals/drive_avatar/` (`DRIVE_AVATAR_ORIGINALS_SHA256.txt`), 96 PNGs RGBA `320×569` em `scene_listening_intent` e `scene_computador_pensando`, `hb-stages/{young_child,pre_adolescent,adolescent}` com `source.json` + `preview.png` + `idle.mp4`, pacotes/delimitadores equilibrados em todos os `.kt` |
| **Protocolo Streamable HTTP MCP (Mock)** | `python3 novo-agente-hb/scripts/tests/verify_mcp_and_skills_mock.py -v` | **PASS (2/2)** | `initialize` -> `notifications/initialized` -> `tools/list` -> `tools/call` JSON/SSE + validação estática SSRF/LAN opt-in |
| **Checagem Estática de Imports Kotlin** | Script Python sobre `src/main/kotlin` + `src/test/kotlin` | **PASS (0 erros)** | Todos os imports `com.novoagentehb.app.*` resolvidos (únicos símbolos gerados por AGP: `BuildConfig` e `R`) |
| **Inspeção Visual de Quadros Extraídos** | PyAV + Pillow + inspeção direta (`read_file`) sobre `room.webp` | **PASS** | Conferência visual de `f001, f032, f064, f096` de `scene_listening_intent` e `scene_computador_pensando` sobre o Quarto (`avatar/room.webp`), confirmando ausência de retângulo de fundo, solidez de olhos/calças/botas e miniaturas de `idade1`/`idade2` |

---

## 2. Verificações Não Executadas no Sandbox (Bloqueio Estrutural Documentado)

- `./gradlew testDebugUnitTest` e `./gradlew assembleDebug`: **NÃO EXECUTADOS NESTE SANDBOX** porque o ambiente não possui `JDK 17` (`java` ausente) nem `Android SDK 34` nem cache de dependências Maven/Gradle.
- Validação visual/áudio ao vivo no dispositivo físico Android (`ExoPlayer` tocando `idade1.mp4` / `idade2.mp4`, Gemini Live alternando `scene_listening_intent`, fluxo completo de conversa e acessibilidade `reduceMotion`): **pendente de execução em aparelho real** (`INTEGRATED_UNTESTED`).
