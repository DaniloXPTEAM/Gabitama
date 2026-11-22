# RELATORIO_TESTES_R1_4 — Verificações Executadas e Escopo Real

**Projeto:** `NOVO_AGENTE_HB` / GabiTama (`0.2.4-r1.4`, `versionCode = 6`)  
**Data (UTC):** 2026-11-21

---

## 1. Resumo das Execuções Locais (`LOCALLY_TESTED`)

| Suíte / Ferramenta | Comando | Resultado | Escopo Verificado |
| :--- | :--- | :--- | :--- |
| **Integridade do Projeto & Assets** | `python3 novo-agente-hb/scripts/tests/verify_project_integrity.py -v` | **8 / 8 PASS** | Gradle Wrapper 8.9 (Apache-2.0); parse de todos os XMLs + referências `R.string`/`R.raw`/`R.layout`; `AndroidManifest.xml`; JSONs congelados (`gabi_24l`, Gemini primário); SHA-256 de todos os **1.324** assets `voz-latencia`; pacotes Kotlin, delimitadores balanceados, rotas `NavRoutes`, tokens R1.4 (`LocalHBStage`, `GabiAtmosphericBackdrop`, `CharacterBibleDialog`, `preferCircularStageMedallion`); containers RGBA `768×1024` + WebP `ALPH`; **23** originais do Drive (`9` PNGs + `14` MP4s referenciados), `5` gráficos `theme/*.png` e `3` `avatar_circle.png`. |
| **Auditoria de IP, GPL & Zero Pet + SBOM** | `python3 novo-agente-hb/scripts/license-audit/audit_ip_and_no_pet.py -v` | **4 / 4 PASS** | Zero menções a `nanoMuse`, `OpenMinis`, `tama://`, `ai.tama.assistant` ou GPL/AGPL; zero código/asset Pet/Tamagotchi; zero chaves/segredos em código ou `BuildConfig`; geração de `evidence/SBOM.json` com escopo explícito (`DIRECT_TOML_DECLARED_ONLY`, 0 GPL). |
| **Contrato MCP Streamable HTTP & SSRF** | `python3 novo-agente-hb/scripts/tests/verify_mcp_and_skills_mock.py -v` | **2 / 2 PASS** | Fluxo `initialize` -> `notifications/initialized` -> `tools/list`, SSE, `Mcp-Session-Id`, `followRedirects(false)` e controles SSRF. |
| **Tamanho Total do Workspace** | `du -sh novo-agente-hb` | **~105 MB PASS** | Abaixo do limite de persistência de 128 MB da Arena (originais MP4 redundantes arquivados externamente no Drive em `GabiTama_R1_3_originais_drive_avatar.zip`). |

---

## 2. Verificações Bloqueadas no Sandbox (`BLOCKED_BY_ENV`)

- **Compilação Gradle / JVM Unit Tests (`./gradlew testDebugUnitTest assembleDebug`):** Não executados neste sandbox por ausência de `java`/`JDK`, `Android SDK` e acesso de rede aos repositórios `dl.google.com`, `repo.maven.apache.org` e `services.gradle.org`. Como consequência, os novos testes Kotlin em `HBCharacterStateUnitTest.kt` (`r14StagesPreserveActivePhaseContinuityAcrossInteractiveStates`, `catalogCoversAll15MandatoryStatesPlusRoomMp4VideosAndBibleR14`) e a compilação Compose R1.4 devem ser validados executando o build em máquina local conforme `docs/BUILD_LOCAL.md`.
- **Instrumentação em dispositivo Android / Renderização em tela:** Não há emulador ou aparelho Android conectado no sandbox; toda inspeção visual de R1.4 foi feita diretamente sobre os PNGs renderizados/pós-processados e revisão estática do layout Compose.
- **Processamento de vídeo neural / Google Flow (`ffmpeg`):** `ffmpeg` não está instalado neste container; as 10 animações dedicadas pendentes seguem documentadas com os 17 parâmetros em `docs/GABITAMA_PROMPTS_FLOW_ANIMACOES_PENDENTES.md`.
