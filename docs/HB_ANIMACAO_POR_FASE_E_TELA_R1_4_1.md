# HB_ANIMACAO_POR_FASE_E_TELA_R1_4_1 — Matriz Completa de Animações, Fontes Reais e Verdade de Estado

**Projeto:** `NOVO_AGENTE_HB` (`0.2.5-r1.4.1`, `versionCode = 7`)  
**Princípio R1.4.1:** Separar rigorosamente **`IDADE_PRESERVADA`** (`isStagePreserved`) de **`ANIMACAO_DO_ESTADO_PRESENTE`** (`hasDedicatedStateAnimation` / `HBMotionTruth`). Nenhum vídeo `idle.mp4` é rotulado como animação dedicada de fala, escuta, pensamento ou alimentação.

---

## 1. Inventário Real da Pasta Drive `Avatar` (23 arquivos) e Origem `voz-latencia` (15 `.mp4`)

| Arquivo no Drive `Avatar` / `voz-latencia` | Resolução / Quadros / FPS | Fase HB Real | Destino / Integração em `novo-agente-hb` | Aproveitado no App? |
| --- | --- | --- | --- | --- |
| `melhorzinhoparadaidle.mp4` | `832×1108` · 361q · 24fps | Infância | `assets/avatar/idle/` (`353` WebP `360×480` RGBA) | Sim (repouso animado transparente) |
| `melhorzinhoparaescultando.mp4` | `832×1108` · 361q · 24fps | Infância | `assets/avatar/listening/` (`353` WebP `360×480` RGBA) | Sim (escuta padrão) |
| `melhorzinhoparafalando.mp4` | `1248×1648` · 361q · 24fps | Infância | `assets/avatar/talking/` (`353` WebP `360×480` RGBA) | Sim (fala padrão + reação toque) |
| `melhorzinhoparapensando.mp4` | `1248×1648` · 241q · 24fps | Infância | `assets/avatar/thinking/` (`76` WebP `360×480` RGBA) | Sim (pensando + fallback erro) |
| `Girl_listening_intently_202609081538.mp4` | `720×1280` · 192q · 24fps | Infância | `assets/anim/scene_listening_intent/` (`96` PNG `320×569` RGBA) | Sim (escuta atenta / Gemini Live) |
| `magnific_prompt-animate-the-exact-_WDB0lzjcXe.mp4` | `1080×1920` · 144q · 24fps | Infância | `assets/anim/home_idle/` (`86` PNG `320×569` RGBA) | Sim (idle / sucesso / música / troca) |
| `magnific_prompt-animate-the-exact-_WDB0lzjcXe2.mp4` | `768×1024` · 124q · 24fps | Infância | Arquivado em `GabiTama_R1_3_originais_drive_avatar.zip` + catalogado | Variante de luz quente da cena `home_idle` (não duplicado para poupar tamanho do APK) |
| `magnific_prompt-animate-the-exact-_EbrFq0BuuO.mp4` | `1080×1920` · 144q · 24fps | Infância | `assets/anim/scene_computador/` (`92` PNG `320×569` RGBA) | Sim (computador / trabalhando) |
| `magnific_json-prompt-animate-the-e_3z02plbREY.mp4` | `1080×1920` · 144q · 24fps | Infância | `assets/anim/scene_computador_pensando/` (`96` PNG `320×569` RGBA) | Sim (pensando no notebook) |
| `gabinastelas.mp4` (+ duplicata `gabinastelas (1).mp4`) | `720×1280` · 240q · 24fps | Infância + Quarto | `assets/avatar/room_intro.mp4` | Sim (abertura única do Quarto) |
| `magnific_faca-uam-animacao-normal-_dtfosfxXSL.mp4` | `768×1152` (`1080×1920` em `raw`) · 192q · 24fps | Quarto (fundo) | `res/raw/room_bg_loop.mp4` | Sim (fundo em movimento `ROOM_MP4`) |
| `idade1.mp4` | `720×1280` · 240q · 24fps | **Pré-adolescente** | `assets/hb-stages/pre_adolescent/idle.mp4` + `preview.png` + `avatar_circle.png` | **Sim — único clipe real de `PRE_ADOLESCENT` (idle)** |
| `idade2.mp4` | `720×1280` · 240q · 24fps | **Adolescente** | `assets/hb-stages/adolescent/idle.mp4` + `preview.png` + `avatar_circle.png` | **Sim — único clipe real de `ADOLESCENT` (idle)** |
| `bible.png` | `1448×1086` RGB | Referência oficial | `assets/bible.png` | Sim (`CharacterBibleDialog`) |
| `gabi-spritesheet.png` | `5376×1024` RGBA (7 poses) | Infância | `assets/gabi-spritesheet.png` | Sim (referência visual + diálogo) |
| `gabi-{idle,talking,listening,comer,computador,ler,dormir}.png` | `768×1024` RGBA | Infância | `assets/gabi-*.png` | Sim (poses transparentes / fallbacks) |
| `video-4f7d854d-...mp4`, `video-dd82b275-...mp4` (somente `voz-latencia`) | `832×1108` · 193q · 24fps | Infância | Não adicionados ao APK (regras de tamanho < 128 MB) | Clipes antigos de Infância já cobertos por `avatar/{idle,listening,talking}` |

**Conclusão objetiva da auditoria:** Não existe nenhum vídeo oculto de ação (`talking`, `listening`, `thinking`, `working`, `eating`, etc.) para `PRE_ADOLESCENT` nem para `ADOLESCENT` em `Avatar` nem em `voz-latencia`. Somente `idade1.mp4` (`idle`) e `idade2.mp4` (`idle`) existem nessas fases.

---

## 2. Matriz Completa: Fase × Estado × Tela → Arquivo Real → Tipo de Movimento → Fallback → Evidência

### 2.1 Fase `YOUNG_CHILD` — Infância (Padrão)

| Estado (`HBActivity`) | Tela(s) | Arquivo Real Usado | Tipo de Movimento | `HBMotionTruth` (`hasDedicatedStateAnimation`) | Fallback quando indisponível ou `reduceMotion` | Evidência no Código |
| --- | --- | --- | --- | --- | --- | --- |
| `Idle` (Home) | Home | `assets/avatar/idle/*.webp` (`353q` RGBA 24fps) | Animação transparente WebP | `DEDICATED_STATE_ANIMATION` (`true`) | `gabi-idle.png` estático | `HomeScreen.kt`, `GabiAvatarViews.kt` |
| `Listening` (Speech) | Home, Quarto | `assets/avatar/listening/*.webp` (`353q` RGBA) | Animação transparente WebP | `DEDICATED_STATE_ANIMATION` (`true`) | `gabi-listening.png` | `HBCharacterModel.kt` |
| `Listening` (Gemini Live) | Home, Quarto | `assets/anim/scene_listening_intent/*.png` (`96q` RGBA) | Sequência PNG RGBA 12fps | `DEDICATED_STATE_ANIMATION` (`true`) | `gabi-listening.png` | `HBCharacterModel.kt` |
| `Speaking` | Home, Quarto | `assets/avatar/talking/*.webp` (`353q` RGBA) | Animação transparente WebP | `DEDICATED_STATE_ANIMATION` (`true`) | `gabi-talking.png` | `HBCharacterModel.kt` |
| `Thinking` (geral) | Home, Quarto | `assets/avatar/thinking/*.webp` (`76q` RGBA) | Animação transparente WebP | `DEDICATED_STATE_ANIMATION` (`true`) | `gabi-idle.png` | `HBCharacterModel.kt` |
| `Thinking` (contexto notebook) | Home, Quarto | `assets/anim/scene_computador_pensando/*.png` (`96q` RGBA) | Sequência PNG RGBA 16fps | `DEDICATED_STATE_ANIMATION` (`true`) | `gabi-computador.png` | `HBCharacterModel.kt` |
| `Working` / `AtComputer` | Home, Quarto, Computador | `assets/anim/scene_computador/*.png` (`92q` RGBA) | Sequência PNG RGBA 18fps | `DEDICATED_STATE_ANIMATION` (`true`) | `gabi-computador.png` | `HBCharacterModel.kt`, `ComputerScreen.kt` |
| `Eating` (rotina 06h–10h / Alimentar) | Quarto, Home (`3s`) | `assets/gabi-comer.png` (sem `scene_comer`/`scene_comendo_reacao`) | Pose RGBA estática + respiração `1.0..1.022` | `STATIC_POSE_BREATHING` (`false`) | `gabi-comer.png` estático (`1.0` se `reduceMotion`) | `HBCharacterModel.kt` (`pendingAsset` explícito) |
| `Reading` (rotina 17h–20h / `vestido_lilas`) | Quarto | `assets/gabi-ler.png` (sem `scene_ler`) | Pose RGBA estática + respiração | `STATIC_POSE_BREATHING` (`false`) | `gabi-ler.png` estático | `HBCharacterModel.kt` |
| `Sleeping` (rotina 21h–05h / `pijama_estrelas`) | Quarto | `assets/gabi-dormir.png` (sem `scene_dormir`) | Pose RGBA estática + respiração | `STATIC_POSE_BREATHING` (`false`) | `gabi-dormir.png` estático | `HBCharacterModel.kt` |
| `MusicReaction` (Spotify) | Quarto | `anim/home_idle/` ou rotina ativa + `SpotifySpeakerBadge` animado | Movimento base reaproveitado | `REUSED_APPROVED_MOVEMENT` (`false`) | Pose do look ativo | `HBCharacterModel.kt`, `QuartoGabiScreen.kt` |
| `TapReaction` | Quarto, Home | `assets/avatar/talking/*.webp` (`353q` RGBA) | Movimento reaproveitado (`avatar/talking`) | `REUSED_APPROVED_MOVEMENT` (`false`) | `gabi-talking.png` + bolha | `HBCharacterModel.kt` |
| `Success` / `OutfitChange` | Quarto, Home | `assets/anim/home_idle/*.png` (`86q` RGBA) | Movimento reaproveitado (`home_idle`) | `REUSED_APPROVED_MOVEMENT` (`false`) | `gabi-idle.png` / pose do look | `HBCharacterModel.kt` |
| `Error` / `Cancelled` | Quarto, Home | `avatar/thinking/` (`Error`) · `avatar/idle/` (`Cancelled`) | Movimento reaproveitado | `REUSED_APPROVED_MOVEMENT` (`false`) | `gabi-idle.png` | `HBCharacterModel.kt` |
| Ícone Barra / Cabeçalho Chat | `GabiBottomBar`, `ChatScreen` | `hb-stages/young_child/avatar_circle.png` | Medalhão estático (ícone permitido) | `N/A (ícone)` | `gabi-face.png` / `GabiParticleOrb` | `GabiBottomBar.kt`, `ChatScreen.kt` |

### 2.2 Fases `PRE_ADOLESCENT` (`idade1.mp4`) e `ADOLESCENT` (`idade2.mp4`)

| Estado (`HBActivity`) | Tela(s) | Arquivo Real Usado | Tipo de Movimento | `isStagePreserved` / `hasDedicatedStateAnimation` (`HBMotionTruth`) | Fallback (`reduceMotion` ou erro de vídeo) | Evidência / Divulgação na UI |
| --- | --- | --- | --- | --- | --- | --- |
| `Idle` | **Home** (moldura circular `190.dp`), **Quarto** (tela cheia), **Computador** (`92.dp` circular) | `hb-stages/{pre_adolescent,adolescent}/idle.mp4` (`240q`, `720×1280`, `24fps`) | **Vídeo MP4 real em movimento contínuo (`StageLoopVideo`)** | `isStagePreserved = true` · `hasDedicatedStateAnimation = true` (`DEDICATED_STATE_ANIMATION`) | `avatar_circle.png` (Home/Computador) ou `preview.png` (Quarto) | `HomeScreen.kt`, `GabiAvatarViews.kt`, `QuartoGabiScreen.kt`, `ComputerScreen.kt` |
| `Listening`, `Speaking`, `Thinking`, `Working`, `Eating`, `Success`, `Error`, `Cancelled`, `TapReaction`, `MusicReaction`, `OutfitChange` | **Home**, **Quarto**, **Computador** | `hb-stages/{pre_adolescent,adolescent}/idle.mp4` (enquanto `anim/stage_${id}_${state}/` não existir) | **Vídeo `idle.mp4` em movimento preservando a idade escolhida** | `isStagePreserved = true` · `hasDedicatedStateAnimation = false` (**`STAGE_PRESERVED_IDLE_ONLY`**) | `avatar_circle.png` / `preview.png` | `HBCharacterModel.kt`, aviso explícito em `HomeScreen.kt` e `QuartoGabiScreen.kt` |
| Ícone Barra / Cabeçalho Chat / Diálogo Fases | `GabiBottomBar`, `ChatScreen`, `StageSelectorDialog` | `hb-stages/{pre_adolescent,adolescent}/avatar_circle.png` (`256×256` RGBA redesenhado em R1.4.1 com enquadramento 3/4 distinto) | Medalhão estático (ícone) | `isStagePreserved = true` | `gabi-idle.png` | `GabiAvatarViews.kt`, `GabiBottomBar.kt`, `ChatScreen.kt` |

---

## 3. Lista Honesta de Animações Realmente Ausentes para o Google Flow (Não recriar o que já existe)

**Já existem no pacote/Drive (NÃO pedir para recriar):**
- `home_idle` (`WDB0lzjcXe` + variante `WDB0lzjcXe2`), `avatar/idle`, `avatar/listening`, `avatar/talking`, `avatar/thinking`, `scene_listening_intent` (`Girl_listening_intently`), `scene_computador` (`EbrFq0BuuO`), `scene_computador_pensando` (`3z02plbREY`), `room_intro` (`gabinastelas`), `room_bg_loop` (`dtfosfxXSL`), `bible.png`, `gabi-spritesheet.png`, `idade1.mp4`, `idade2.mp4`.

**Realmente pendentes para geração futura no Google Flow (detalhados em `GABITAMA_PROMPTS_FLOW_ANIMACOES_PENDENTES.md`):**
1. **Infância (`YOUNG_CHILD`) — 10 cenas dedicadas:** `scene_comer`, `scene_comendo_reacao`, `scene_ler`, `scene_dormir`, `scene_music_reaction`, `scene_tap_reaction`, `scene_success`, `scene_error`, `scene_cancelled`, `scene_outfit_change` (+ 3 looks extras: `moletom_preto`, `look_festa`, `look_pijama_passeio`).
2. **Pré-adolescente (`PRE_ADOLESCENT`, base `idade1.mp4`) e Adolescente (`ADOLESCENT`, base `idade2.mp4`):**
   - Clipes interativos com fundo uniforme (ou recorte RGBA limpo) para `listening`, `talking`, `thinking`, `working`, `eating`, `tap_reaction`, `success`, `error`, `cancelled` e `idle` transparente sobre fundo independente, preservando exatamente a aparência facial, cabelo, idade e vestido vermelho de `idade1.mp4` e `idade2.mp4`.
