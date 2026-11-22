# STATUS_REAL_R1_4_1 — Estado Real e Verificado do Projeto `NOVO_AGENTE_HB` (GabiTama R1.4.1 Pré-APK)

**Projeto:** `NOVO_AGENTE_HB` (`com.novoagentehb.app`)  
**Versão / Código:** `0.2.5-r1.4.1` (`versionCode = 7`, `BUILD_TIME = "2026-11-21-r1.4.1"`)  
**Branch / Diretório isolado:** `arena/cc3c891a-jarvis-projeto` · `novo-agente-hb/`  
**Continuação direta de:** `GabiTama_R1_4_avatares_e_ui_integrados.zip` (`0.2.4-r1.4`, `versionCode = 6`) — R0 a R1.4 preservados integralmente sem sobrescrita.  
**Data (UTC):** 2026-11-21

---

## 1. Resumo Executivo Honesto (O que foi corrigido em R1.4.1 vs o que segue pendente)

### Itens P0 e P1 corrigidos e verificados estaticamente em R1.4.1

1. **P0 — Animação real nas áreas em destaque (Home e Computador):**
   - `HomeScreen.kt` + `GabiAvatarViews.kt` (`HBStageCharacter`): corrigido o comportamento em que `preferCircularStageMedallion = true` desenhava `avatar_circle.png` estático para `PRE_ADOLESCENT` (`idade1.mp4`) e `ADOLESCENT` (`idade2.mp4`). Agora o palco hero (`232.dp`, moldura circular `190.dp`) reproduz **`StageLoopVideo` em movimento (`TextureView` + `ExoPlayer`)**, exibindo o quadro estático (`avatar_circle.png`) **exclusivamente** quando `rememberReduceMotionEnabled() == true` (Acessibilidade) ou em erro de decodificação de mídia.
   - `ComputerScreen.kt`: quando a fase ativa é `PRE_ADOLESCENT` ou `ADOLESCENT`, o cartão hero também passa a renderizar **`StageLoopVideo` em movimento** dentro da moldura circular (`92.dp`), mantendo `GabiFaceAvatar` estático apenas onde tem função explícita de ícone (`GabiBottomBar` e cabeçalho `44.dp` do `ChatScreen`).
   - `HomeScreen.kt` divulga explicitamente abaixo da pílula de status que a moldura circular de `idade1`/`idade2` exibe **vídeo completo em movimento provisório (recorte alfa pendente)** quando aplicável.

2. **P0 — Separação rigorosa entre `IDADE_PRESERVADA` e `ANIMACAO_DO_ESTADO_PRESENTE`:**
   - `HBCharacterModel.kt`: introduzidos os contratos explícitos **`HBMotionTruth`** (`DEDICATED_STATE_ANIMATION`, `REUSED_APPROVED_MOVEMENT`, `STAGE_PRESERVED_IDLE_ONLY`, `STATIC_POSE_BREATHING`), **`isStagePreserved`**, **`hasDedicatedStateAnimation`** e **`catalogStateKey`** diretamente em `HBState` e `RoomUiState`.
   - Auditoria completa dos **23 arquivos** da pasta Drive `Avatar` e dos **15 `.mp4`** da origem `voz-latencia`: confirmou-se que existem `idade1.mp4` (`PRE_ADOLESCENT` `idle`, `240q`) e `idade2.mp4` (`ADOLESCENT` `idle`, `240q`), mas **não existem** vídeos dedicados de fala/escuta/pensamento/trabalho/alimentação para essas duas fases. O app preserva a fase selecionada em todas as telas sem jamais voltar para a criança, porém marca honestamente `STAGE_PRESERVED_IDLE_ONLY` (`hasDedicatedStateAnimation = false`) nesses estados em vez de fingir animação de ação dedicada.
   - `HBCharacterStateUnitTest.kt`: corrigida a asserção `catalogStateKey` (que faltava em `HBState` na R1.4 e impediria compilação do teste unitário em Gradle!) e adicionados testes de verdade para `HBMotionTruth`, `RoomBackgroundCompatibility` e preservação de preferências.

3. **P0 — Fundos independentes, transparência real e toque sem bloqueio no Quarto (`QuartoGabiScreen.kt`):**
   - Inspeção de transparência pixel a pixel sobre fundo escuro (`#120F11`) e fundo claro (`#F3EFEA`): `gabi-*.png`, `avatar/{idle,listening,talking,thinking}/*.webp`, `scene_listening_intent/` (`60% alpha=0`) e `scene_computador_pensando/` (`70% alpha=0`) possuem recorte limpo sem caixa opaca; `scene_computador/` (legado `voz-latencia`) apresenta leve perda parcial de alfa nas botas pretas sobre fundo claro (documentada em `HB_FUNDOS_E_TRANSPARENCIA_R1_4_1.md`).
   - `idade1.mp4` e `idade2.mp4` têm quartos opacos complexos (cortinas, cama e pelúcia com tons próximos ao cabelo cobre e vestido vermelho). Sem rede neural de *alpha matting* neste sandbox, não foi gerado falso recorte nem `RGBA` com alfa opaco: `QuartoGabiScreen.kt` isola formalmente o modo cena completa (`RoomBackgroundCompatibility.OPAQUE_FULL_SCENE_BLOCKS_CUSTOM_BG`), **preserva integralmente** `quartoBackgroundId` e `quartoCustomBackgroundUri` nas preferências DataStore e exibe aviso honesto no HUD topo e em `RoomEnvironmentDialog` em vez de fingir que o fundo escolhido está visível atrás do MP4 opaco.
   - `view_texture_player.xml`: adicionados `android:clickable="false"`, `android:focusable="false"` e `android:focusableInTouchMode="false"` para impedir que `TextureView`/`PlayerView` intercepte toques sobre a personagem ou controles.

4. **P1 — Refinamento de Tema, Equilíbrio Cobre/Carmesim/Dourado e Distinção dos Medalhões:**
   - `Theme.kt`, `colors.xml` e `assets/theme/gabitama_home_backdrop.png`: reduzido o peso azul-frio (`#0B1219` / `#131D28` / `#1A2734`) para obsidiana quente cobre/carmesim (`NightBg #120F11`, `SurfaceDark #1B1618`, `SurfaceMid #251E20`, `Hairline #3E302E`, `TextSecondary #B5A9A5`), mantendo o destaque em cobre (`#D96830`), carmesim (`#C62828`) e dourado (`#FFCF8C`).
   - `hb-stages/{young_child,pre_adolescent,adolescent}/avatar_circle.png`: regenerados os 3 medalhões `256×256` RGBA a partir de quadros reais (`gabi-idle.png`, `idade1.mp4` q76, `idade2.mp4` q56) com enquadramento de três quartos e aros distintos (`#FFCF8C`, `#E5A952`, `#D96830`) para que as três fases sejam visualmente distinguíveis mesmo em `32.dp`–`56.dp`.

5. **P1 — Regressão de Telas, Botões e Teclado (`imePadding`) e Build Local Reproduzível:**
   - `ChatScreen.kt`: adicionado `.imePadding()` junto de `adjustResize` no Manifest para proteger a barra de digitação contra o teclado em modo `edgeToEdge`.
   - `MATRIZ_BOTOES_REGRESSAO_R1_4_1.md`: cobertura de todas as **10 telas** (`95` handlers interativos + `15` diálogos/pickers + Widget + Quick Tile + Acessibilidade + Notificações + Boot/Alarmes) classificados com tags honestas.

---

## 2. Estado de Build e Testes (`APK_NOT_BUILT`)

| Verificação | Status Honesto | Evidência / Motivo |
| --- | --- | --- |
| `verify_project_integrity.py` | **STATIC_PASS (8/8 OK)** | Executado nesta sessão em `novo-agente-hb/` (`64` `.kt`, `15` `.xml`, `1.324` hashes `voz-latencia`, `23` entradas Drive `Avatar`, Gradle Wrapper `8.9`, XML, `R.*`, pacotes e delimitadores). |
| `audit_ip_and_no_pet.py` | **STATIC_PASS (4/4 OK)** | Zero referências GPL/Pet em `novo-agente-hb/`. |
| `verify_mcp_and_skills_mock.py` | **UNIT_PASS (2/2 OK)** | Handshake MCP JSON-RPC `initialize` -> `notifications/initialized` -> `tools/list` em servidor HTTP mock + contratos Kotlin. |
| `./gradlew --version` / `:app:compileDebugKotlin` / `:app:testDebugUnitTest` / `:app:assembleDebug` | **BLOCKED (`APK_NOT_BUILT`)** | Sandbox Linux sem `java`, `javac`, `kotlinc`, `gradle`, Android SDK 34, `adb` ou emulador (verificado via `which`). Guia PowerShell completo em `RELATORIO_BUILD_R1_4_1.md`. |
| Execução em emulador / aparelho real (Play Store / ADB / microfone / Gemini Live / SAF / APK instalado) | **BLOCKED (`APK_NOT_BUILT`)** | Depende de compilação local em Windows com JDK 17 + Android SDK 34. |
| `linux_proot_shell` / operador genérico de tela / clipes Flow pendentes (`scene_comer`, `scene_ler`, `scene_dormir`, clipes dedicados `idade1`/`idade2`) | **NOT_IMPLEMENTED** | Mantidos explicitamente declarados em `ComputerScreen`, `HBAnimationCatalog` e `HB_ANIMACAO_POR_FASE_E_TELA_R1_4_1.md`. |
