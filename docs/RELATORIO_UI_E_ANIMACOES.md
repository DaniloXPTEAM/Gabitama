# RELATÓRIO DE UI, TEMA E ANIMAÇÕES — GabiTama (`NOVO_AGENTE_HB`)

**Base verificada:** `0.2.5-r1.4.1` (`versionCode = 7`) + correções pós-auditoria de runtime/ciclo de vida.  
**Escopo honesto:** Toda a verificação abaixo resulta de auditoria direta do código Compose, XML, recursos e metadados reais dos assets (`RGBA`, `WebP ALPH`, `MP4`); screenshots e FPS em dispositivo/emulador permanecem **`NOT_TESTED_NO_EMULATOR`**.

---

## 1. Melhorias de UI / Acessibilidade Aplicadas nesta Rodada

1. **`rememberReduceMotionEnabled()` reativo em `GabiAvatarViews.kt`:**
   - **Antes:** lia `Settings.Global.ANIMATOR_DURATION_SCALE` apenas uma vez na primeira composição (`remember(context)`), ignorando mudanças feitas em Ajustes do Android enquanto o app estava aberto em segundo plano.
   - **Depois:** observa `LocalLifecycleOwner.current` (`Lifecycle.Event.ON_RESUME`) via `DisposableEffect`, atualizando imediatamente o estado de movimento reduzido ao retornar ao aplicativo.
2. **Compensação de tempo de decodificação em `AvatarAnimation` e `FrameSequenceAnimation` (`GabiAvatarViews.kt`):**
   - **Antes:** executava `delay(frameDelayMs)` após decodificar cada quadro PNG/WebP, somando o custo de I/O/decode ao intervalo e reduzindo o FPS efetivo em aparelhos intermediários.
   - **Depois:** mede `SystemClock.uptimeMillis()` antes da decodificação e aguarda `(frameDelayMs - elapsed).coerceAtLeast(16L)`.
3. **Teclado virtual na Home (`HomeScreen.kt`):**
   - Adicionado `.imePadding()` no container principal de `HomeScreen.kt` (alinhando com `ChatScreen.kt`) para evitar que o teclado cubra o campo rápido `"Peça algo à Gabi…"` ao focar na parte inferior da Home.

---

## 2. Comportamento Auditado por Tela e por Fase da HB

| Tela / Componente | Comportamento Verificado no Código | Status |
|---|---|---|
| **Tema "Céu de Outono" (`Theme.kt`, `colors.xml`, `gabitama_home_backdrop.png`)** | Fundo obsidiana (`#120F11`), superfícies `#1B1618` / `#282022`, cobre `#D96830`, carmesim `#C62828`, dourado `#FFCF8C`, `LocalHBStage` provido no `GabiTheme` | **`CODE_VERIFIED`** |
| **Barra Inferior (`GabiBottomBar.kt`)** | 4 atalhos + medalhão central sincronizado com `activeStage.avatarCirclePngPath` (`256×256`) via `GabiFaceAvatar` | **`CODE_VERIFIED`** |
| **Home (`HomeScreen.kt`)** | `YOUNG_CHILD`: `AvatarAnimation` transparente `idle` (353 frames WebP) / estados interativos (`listening`, `scene_listening_intent`, `thinking`, `scene_computador_pensando`, `talking`, `scene_computador`); `PRE_ADOLESCENT` / `ADOLESCENT`: `StageLoopVideo` em medalhão circular (`190.dp`) com chip explícito de `STAGE_PRESERVED_IDLE_ONLY` quando em estado sem clipe próprio | **`CODE_VERIFIED`** |
| **Quarto da Gabi (`QuartoGabiScreen.kt`)** | Intro (`room_intro.mp4` c/ `SKIP`), 4 opções de fundo (`ROOM_WEBP`, `ROOM_PNG`, `ROOM_MP4`, `CUSTOM_URI` galeria), rotina horária (Comer/Computador/Ler/Dormir), guarda-roupa (`vestido_vermelho`, `vestido_lilas`, `sueter_computador`, `pijama_estrelas` ativos; 3 looks futuros desabilitados com aviso `"Arte pendente"`), seletor de 3 fases e `CharacterBibleDialog` (`bible.png`) | **`CODE_VERIFIED`** |
| **Toque sobre `TextureView` (`view_texture_player.xml`)** | `app:surface_type="texture_view"`, `app:resize_mode="zoom"`, `android:clickable="false"`, `android:focusable="false"` garantindo que cliques sobre a personagem/fundo nunca sejam engolidos pelo `PlayerView` | **`CODE_VERIFIED`** |
| **Restantes (`Chat`, `Feed`, `Goals`, `Library`, `Memory`, `Computer`, `Settings`, `Permissions`)** | Todas as 10 rotas conectadas aos respectivos `ViewModel`s (`missing=[]`), `PermissionsScreen` com `safeDrawingPadding()`, `ChatScreen` com `.imePadding()`, `LibraryScreen` com escrita verificada + SAF | **`CODE_VERIFIED`** |

---

## 3. Limitações Visuais Conhecidas e Explicitadas ao Usuário

1. **`PRE_ADOLESCENT` (`idade1.mp4`) e `ADOLESCENT` (`idade2.mp4`) são MP4s opacos de cena cheia (`720×1280`):** não possuem canal alfa nem clipes dedicados de escuta/fala/pensamento; por isso `RoomBackgroundCompatibility.OPAQUE_FULL_SCENE_BLOCKS_CUSTOM_BG` desativa visualmente o fundo personalizado apenas enquanto essas fases estão ativas (preservando a escolha nas preferências para quando o usuário retornar a `Infância`).
2. **Looks pendentes no guarda-roupa:** `moletom_preto` (`gabi-look-moletom.png`), `look_festa` (`gabi-look-festa.png`) e `look_pijama_passeio` (`gabi-look-passeio.png`) permanecem visivelmente bloqueados como `"Arte pendente"`.
3. **Teste visual em tela real (FPS, layout em notch/360dp, contraste real com IME aberto):** **`NOT_TESTED_NO_EMULATOR`** — executar roteiro da Seção 3 do `MATRIZ_TESTES_ANDROID_COMPLETA.md` após gerar o APK no PC.
