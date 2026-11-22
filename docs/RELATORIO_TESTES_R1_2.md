# RELATORIO_TESTES_R1_2 — Evidências Reais de Verificação e Testes (R1.2)

**Projeto:** `NOVO_AGENTE_HB` (`com.novoagentehb.app`, `versionCode = 4`, `0.2.2-r1.2`)  
**Data da execução (UTC):** 2026-11-21  
**Ambiente do sandbox:** Linux (`python3` disponível; **sem** `java`/JDK, **sem** Android SDK, **sem** `ffmpeg`/`ffprobe`, **sem** acesso de rede a Maven/Gradle).

---

## 1. Resumo Executivo dos Testes Executados Nesta Revisão (R1.2)

| Suíte / Comando | Escopo Verificado | Resultado Real | Detalhes |
| :--- | :--- | :---: | :--- |
| `python3 novo-agente-hb/scripts/license-audit/audit_ip_and_no_pet.py -v` | IP limpo, zero GPL/nanoMuse/OpenMinis, zero Pet/Tamagotchi/dragão, zero segredos embutidos, geração de `evidence/SBOM.json` | **PASSOU (4/4)** | `0` correspondências proibidas; `0` componentes GPL declarados no `libs.versions.toml` |
| `python3 novo-agente-hb/scripts/tests/verify_project_integrity.py -v` | Gradle Wrapper 8.9 (SHA + Apache-2.0), parsing de todos os XMLs + referências `R.*` (incluindo `R.layout.view_texture_player`, `R.string.quick_tile_label`), `AndroidManifest.xml`, JSONs de voz/provedores, SHA-256 de todos os 1.324 assets Gabi, pacotes/delimitadores/contratos nos 64 arquivos `.kt`, `HBAnimationCatalog` (15 estados + 2 MP4s), `TextureView` + lifecycle + `reduceMotion`, `AndroidDeviceBridge` Bluetooth SCO + offline parser, `SafLibraryManager` CRUD/SAF, e cabeçalhos PNG RGBA + WebP `VP8X+ALPH` | **PASSOU (7/7)** | 1.324/1.324 hashes de mídia idênticos à origem; zero regressão estrutural |
| `python3 novo-agente-hb/scripts/tests/verify_mcp_and_skills_mock.py -v` | Servidor HTTP/SSE loopback real validando `initialize`, `notifications/initialized`, `Mcp-Session-Id`, `tools/list`, resposta JSON-RPC com erro e bloqueio SSRF no `McpJsonRpcClient.kt` | **PASSOU (2/2)** | Handshake completo + parsing SSE verificados em runtime local |
| Checagem estática Kotlin inter-arquivos (`python3` inline sobre `android/app/src`) | Varredura de todos os 64 `.kt` (177 símbolos declarados), verificando resolução de todos os `import com.novoagentehb.app.*` | **PASSOU** | `0` imports de projeto quebrados (apenas classes geradas `R` e `BuildConfig`, esperadas antes da compilação AGP) |
| Inspeção binária ISO-BMFF + PNG + WebP sobre `assets/` e `res/raw/` | Medição factual de duração, `FPS`, codec `avc1`, contagem de quadros, bit depth e canal alfa diretamente dos contêineres | **PASSOU** | `room_intro.mp4` (`24 fps`, `10.0s`, `240` q), `room_bg_loop.mp4` (`24 fps`, `8.0s`, `192` q), `anim/home_idle` (`86` RGBA), `anim/scene_computador` (`92` RGBA), `avatar/{idle,listening,talking,thinking}` (`VP8X+ALPH`) |
| Testes unitários JVM JUnit4 (`AgentCoreUnitTest.kt`, `ModelRouterAndVoiceUnitTest.kt`, `HBCharacterStateUnitTest.kt`) | **Atualizados na R1.2** com cobertura de `HBAnimationCatalog.mandatoryStatesPresent()`, transição `OutfitChange` + retorno ao look + coexistência `MusicReaction`, `TapReaction` animada via `avatar/talking` e novos comandos offline do `AndroidDeviceBridge.parseOfflineIntent` | **NÃO EXECUTADO (`BLOCKED`)** | `JAVA_HOME is not set and no 'java' command could be found in your PATH` (JDK ausente no sandbox; código revisado manualmente e por script estático) |
| Build Gradle / APK / Android Instrumented / Emulador / Dispositivo físico (`./gradlew testDebugUnitTest assembleDebug`) | Compilação Android real, execução em emulador API 34 ou dispositivo com ExoPlayer/AudioRecord/Accessibility/Spotify reais | **NÃO EXECUTADO (`BLOCKED`)** | Sandbox sem JDK 17, sem Android SDK 34 e sem acesso de rede ao Maven/Gradle; **Gate V5 permanece `BLOCKED`** |

---

## 2. Reprodução Local Após Entrega (Para o Proprietário com JDK 17 + Android SDK 34)

```bash
cd novo-agente-hb/android
./gradlew testDebugUnitTest --info
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Checklist manual obrigatório no primeiro teste em aparelho físico (Gate V5):
1. Abrir **Quarto da Gabi**: confirmar `RoomIntroVideo` reproduzindo com som/controles **Pular** e **Não mostrar novamente**, sem travar em tela preta.
2. Alternar **Ambiente do Quarto -> Vídeo Ambiente (fundo)** (`ROOM_MP4`): confirmar que `room_bg_loop.mp4` (`TextureView`) fica **atrás** da personagem HB transparente (Camada 1) e dos botões HUD (Camada 2/3).
3. Ativar **Remover animações** nas configurações de acessibilidade do Android e validar que `RoomLoopBackgroundVideo`, `FrameSequenceAnimation`, `AvatarAnimation`, `BreathingCharacter` e `SpotifySpeakerBadge` param movimento contínuo mantendo quadros nítidos.
4. Trocar look no guarda-roupa (`Vestido Lilás`, `Suéter Mostarda`, `Pijama Estrelado`), tocar música no Spotify e tocar em **Alimentar** / **tocar na Gabi**: confirmar reação animada + preservação do look após o término da reação.
5. Testar **Quick Settings Tile** ("Falar com a Gabi"), **Bluetooth SCO**, criação/renomeação/compartilhamento/exclusão em **Arquivos & SAF** e botão **Parar** durante tarefa ativa.
