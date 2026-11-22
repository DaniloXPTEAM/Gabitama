# FONTES E EVIDÊNCIAS — GATE 0

Data da coleta: 2026-11-21
Branch local observada: `arena/cc3c891a-jarvis-projeto` (derivada de `main` `dcae193c02737a34e6f7f0fff8d4051d490e50d4`)

## 1. Fontes Inspecionadas (Local e GitHub Privado/Autorizado)

| Fonte | Origem | Identificador / Commit | Papel nesta criação | O que NÃO foi copiado |
| --- | --- | --- | --- | --- |
| Ordem Mestra do Usuário | Arquivo local anexado (`/tmp/arena-coding-uploads/ARENA_ORDEM_MESTRA_NOVO_PRODUTO_TAMA_V2_ESTRUTURA_GABI_HB_2026-11-21.md`) | Versão 1.0, 21/11/2026 (427 linhas) | Especificação normativa de Gates G0–G6, A01–A16, Quarto da Gabi, voz congelada e relatório Seção 12 | — |
| `DaniloXPTEAM/Jarvis-Projeto` (`voz-latencia`) | GitHub (`api.github.com` / `codeload.github.com`) | Commit `c6e14c0` (branch `voz-latencia`, 1.802 arquivos ~183 MB; MIT em `android/LICENSE`) | Inventário de UX/telas, paleta "Céu de Outono", 20 ferramentas Gabi, parâmetros `Pocket TTS` (`gabi_24l`, `B2`/`C2`), e extração verificada dos **1.324 ativos visuais/vídeo aprovados** (`evidence/APPROVED_ASSETS_SHA256.txt`) | Arquivo privado `Gabi Voz isolada.mp3` (`684f80e...`), WAVs brutos de treino e banco/Keystore legado |
| `DaniloXPTEAM/tama-v2` | GitHub (`api.github.com`) | Branch `arena/a4704b56-tama-v2` (GPL-3.0 em `LICENSE`) | Apenas referência arquitetural documental (`TAMA_V2_PLANO_COMPLETO_PARA_ARENA.md`, `TAMA_V2_STATUS.md`, `TAMA_PET_AUDITORIA_2026-11-10.md`) para extrair lista de capacidades de agente e confirmar tudo o que deve ser excluído da camada Pet | Zero código-fonte, zero XML, zero assets, zero URIs (`tama://`, `minis://`, `nanomuse://`), zero `ai.tama.assistant` / `io.github.nanomuse.app` |
| `DaniloXPTEAM/Opportunity-CRM` | GitHub (`api.github.com`) | `workers/voice-agent/geminiLive.ts` | Referência de protocolo da pilha ativa Google Gemini Multimodal Live API (`BidiGenerateContent`, `x-goog-api-key`, `gemini-3.8-live`, PCM 16 kHz / 24 kHz, `toolCall`/`toolResponse`, barge-in) | Código TypeScript/Cloudflare Worker não copiado |
| Google Drive do proprietário | Conector `google_drive` | `list_files`, `list_drives`, `search_files` | Confirmação de inexistência de APK Gabi recente ou `Ordem Mestra v1.1` no Drive e verificação do histórico Pippit/Tama | Nenhum APK antigo herdado ou instalado por cima |

## 2. Inventário Técnico do Ambiente Sandbox Arena (Pré-Build Gate 0)

| Item verificado | Resultado observado | Impacto |
| --- | --- | --- |
| Sistema e disco livre | Linux sandbox com ~20 GB livres em `/` | Suficiente para código, 1.324 assets (~58,5 MB) e empacotamento ZIP em `/tmp` |
| Ferramentas disponíveis | `python3`, `node`, `npm`, `zip`, `unzip`, `sha256sum`, `git`, `gh` | Permitem auditoria IP/SBOM/SHA-256/XML/JSON/Kotlin e geração do pacote |
| Toolchain Android/Java | `java`, `javac`, `kotlinc`, `gradle`, `sdkmanager`, `adb`, `emulator` **ausentes** | Compilação real APK (`./gradlew assembleDebug`) e `./gradlew testDebugUnitTest` estão `BLOCKED` no sandbox |
| Rede outbound para build | Somente `github.com`, `codeload.github.com`, `api.github.com`, `registry.npmjs.org`, `pypi.org`, `files.pythonhosted.org` liberados (`dl.google.com`, `repo.maven.apache.org` e `services.gradle.org` bloqueados) | Impossível baixar Gradle Wrapper/AGP/Kotlin/Android SDK dentro do sandbox; compilar em máquina local conforme `docs/BUILD_LOCAL.md` |

## 3. Resumo dos 1.324 Ativos Aprovados Copiados de `voz-latencia`

- `assets/*.png` (raiz): 8 arquivos (7.213.982 bytes)
- `assets/anim/home_idle/*.png`: 86 quadros (12.671.846 bytes)
- `assets/anim/scene_computador/*.png`: 92 quadros (11.470.711 bytes)
- `assets/avatar/room.webp` e `room_intro.mp4`: 2 arquivos (2.584.011 bytes)
- `assets/avatar/idle/*.webp`: 353 quadros (6.622.396 bytes)
- `assets/avatar/listening/*.webp`: 353 quadros (5.975.008 bytes)
- `assets/avatar/talking/*.webp`: 353 quadros (7.317.088 bytes)
- `assets/avatar/thinking/*.webp`: 76 quadros (1.534.524 bytes)
- `res/raw/room_bg_loop.mp4`: 1 arquivo (2.554.580 bytes)
- **Total verificado:** **1.324 arquivos** com hashes individuais conferidos em `evidence/APPROVED_ASSETS_SHA256.txt` e revalidados localmente por `scripts/tests/verify_project_integrity.py`.
