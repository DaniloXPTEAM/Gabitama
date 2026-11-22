# HB_VIDEO_ANIMATION_INVENTORY_R1_2 — Inventário Técnico de Vídeos, Animações e Players da HB

**Projeto:** `NOVO_AGENTE_HB` (`com.novoagentehb.app`, `versionCode = 4`, `versionName = 0.2.2-r1.2`)  
**Data da auditoria (UTC):** 2026-11-21  
**Escopo:** Gate V0 (inventário integral de mídias existentes), Gate V1 (players ExoPlayer/Media3 e Compose) e Gate V3 (catálogo central `HBAnimationCatalog` + estados da HB).

---

## 1. Catálogo central implementado no código (`HBAnimationCatalog.kt`)

Arquivo: `novo-agente-hb/android/app/src/main/kotlin/com/novoagentehb/app/gabi/HBAnimationCatalog.kt`  
Cobertura verificada por código e testes unitários (`HBCharacterStateUnitTest.kt`): **15 estados obrigatórios da HB + 2 vídeos MP4 de ambiente do Quarto** (`17` entradas totais).

| ID no Catálogo | Caminho Real / Alvo | Formato | Quadros | FPS | Duração | Dimensão | Razão | Alfa | Loop | Disponibilidade Real | Política de Interrupção & Retorno |
| :--- | :--- | :--- | ---: | ---: | ---: | ---: | :---: | :---: | :---: | :--- | :--- |
| `ROOM_INTRO_MP4` | `assets/avatar/room_intro.mp4` | `MP4_VIDEO` (`avc1`) | 240 | 24 | 10.0 s | 720×1280* | 9:16 | Não | Não | `AVAILABLE_NATIVE_VIDEO` | `IMMEDIATE_PREEMPT` · pulável / não mostrar de novo; retorna ao Quarto |
| `ROOM_BG_LOOP_MP4` | `res/raw/room_bg_loop.mp4` | `MP4_VIDEO` (`avc1`) | 192 | 24 | 8.0 s | 1080×1920* | 9:16 | Não | Sim | `AVAILABLE_NATIVE_VIDEO` | Camada 0 atrás da HB; pausa se `reduceMotion=true` |
| `IDLE` | `assets/anim/home_idle/` | `PNG_SEQUENCE` (RGBA8) | 86 | 18 | 4.78 s | 320×569 | 9:16 | Sim | Sim | `AVAILABLE_NATIVE_SEQUENCE` | Base (`priority = 10`) |
| `LISTENING` | `assets/avatar/listening/` | `WEBP_SEQUENCE` (`VP8X+ALPH`) | 353 | 24 | 14.71 s | 360×480 | 3:4 | Sim | Sim | `AVAILABLE_NATIVE_SEQUENCE` | `IMMEDIATE_PREEMPT` (`priority = 90`), retorna ao estado/look anterior |
| `THINKING` | `assets/avatar/thinking/` | `WEBP_SEQUENCE` (`VP8X+ALPH`) | 76 | 24 | 3.17 s | 360×480 | 3:4 | Sim | Sim | `AVAILABLE_NATIVE_SEQUENCE` | `IMMEDIATE_PREEMPT` (`priority = 80`), retorna ao estado/look anterior |
| `TALKING` | `assets/avatar/talking/` | `WEBP_SEQUENCE` (`VP8X+ALPH`) | 353 | 24 | 14.71 s | 360×480 | 3:4 | Sim | Sim | `AVAILABLE_NATIVE_SEQUENCE` | `IMMEDIATE_PREEMPT` (`priority = 95`), retorna ao estado/look anterior |
| `WORKING` | `assets/anim/scene_computador/` | `PNG_SEQUENCE` (RGBA8) | 92 | 18 | 5.11 s | 320×569 | 9:16 | Sim | Sim | `AVAILABLE_NATIVE_SEQUENCE` | `COMPLETE_OR_PREEMPT_VOICE` (`priority = 75`), retorna ao anterior |
| `EATING` | `assets/gabi-comer.png` (alvo `anim/scene_comer/`) | `PNG_STATIC_FALLBACK` (RGBA8) | 1 | 0 | Contínuo | 768×1024 | 3:4 | Sim | Sim | `STATIC_BREATH_FALLBACK` (`ASSET_PENDING`) | Rotina 06h–10h (`priority = 20`) com respiração sutil real |
| `EATING_REACTION` | `assets/gabi-comer.png` (alvo `anim/scene_comendo_reacao/`) | `PNG_STATIC_FALLBACK` (RGBA8) | 1 | 0 | 3.0 s | 768×1024 | 3:4 | Sim | Não | `STATIC_BREATH_FALLBACK` (`ASSET_PENDING`) | `TRANSIENT_RETURN_TO_PREVIOUS` (`priority = 65`) |
| `READING` | `assets/gabi-ler.png` (alvo `anim/scene_ler/`) | `PNG_STATIC_FALLBACK` (RGBA8) | 1 | 0 | Contínuo | 768×1024 | 3:4 | Sim | Sim | `STATIC_BREATH_FALLBACK` (`ASSET_PENDING`) | Rotina 17h–20h ou look lilás (`priority = 20`) |
| `SLEEPING` | `assets/gabi-dormir.png` (alvo `anim/scene_dormir/`) | `PNG_STATIC_FALLBACK` (RGBA8) | 1 | 0 | Contínuo | 768×1024 | 3:4 | Sim | Sim | `STATIC_BREATH_FALLBACK` (`ASSET_PENDING`) | Rotina 21h–05h ou pijama estrelado (`priority = 20`) |
| `MUSIC_REACTION` | Reusa cena/look ativo (`anim/home_idle/` padrão; alvo `anim/scene_music_reaction/`) | `PNG_SEQUENCE` / look ativo | 86 | 18 | 4.78 s | 320×569 | 9:16 | Sim | Sim | `REUSED_APPROVED_SEQUENCE` (`ASSET_PENDING`) | Coexiste com look selecionado + caixinha de som animada |
| `TAP_REACTION` | Reusa `assets/avatar/talking/` (alvo `anim/scene_tap_reaction/`) | `WEBP_SEQUENCE` (`VP8X+ALPH`) | 353 | 24 | 1.8 s janela | 360×480 | 3:4 | Sim | Não | `REUSED_APPROVED_SEQUENCE` (`ASSET_PENDING`) | `TRANSIENT_RETURN_TO_PREVIOUS` (`priority = 55`) |
| `SUCCESS` | Reusa `assets/anim/home_idle/` (alvo `anim/scene_success/`) | `PNG_SEQUENCE` (RGBA8) | 86 | 18 | 2.6 s janela | 320×569 | 9:16 | Sim | Não | `REUSED_APPROVED_SEQUENCE` (`ASSET_PENDING`) | `TRANSIENT_RETURN_TO_PREVIOUS` (`priority = 70`) |
| `ERROR` | Reusa `assets/avatar/thinking/` (alvo `anim/scene_error/`) | `WEBP_SEQUENCE` (`VP8X+ALPH`) | 76 | 24 | 3.4 s janela | 360×480 | 3:4 | Sim | Não | `REUSED_APPROVED_SEQUENCE` (`ASSET_PENDING`) | `TRANSIENT_RETURN_TO_PREVIOUS` (`priority = 98`) |
| `CANCELLED` | Reusa `assets/avatar/idle/` (alvo `anim/scene_cancelled/`) | `WEBP_SEQUENCE` (`VP8X+ALPH`) | 353 | 24 | 1.8 s janela | 360×480 | 3:4 | Sim | Não | `REUSED_APPROVED_SEQUENCE` (`ASSET_PENDING`) | `TRANSIENT_RETURN_TO_PREVIOUS` (`priority = 100`) |
| `OUTFIT_CHANGE` | Reusa `assets/anim/home_idle/` (alvo `anim/scene_outfit_change/`) | `PNG_SEQUENCE` (RGBA8) | 86 | 18 | 0.9 s janela | 320×569 | 9:16 | Sim | Não | `REUSED_APPROVED_SEQUENCE` (`ASSET_PENDING`) | `TRANSIENT_RETURN_TO_PREVIOUS` (`priority = 60`), preserva o look escolhido |

> \*Nota sobre MP4s: No sandbox sem `ffprobe`, a leitura direta dos átomos ISO-BMFF (`mvhd`/`mdhd`/`stts`/`stsd`) confirmou `avc1`, `24.0 FPS`, `10.0 s / 240 quadros` (`2.473.203 B`) em `room_intro.mp4` e `8.0 s / 192 quadros` (`2.554.580 B`) em `room_bg_loop.mp4`. As dimensões declaradas em `HBAnimationCatalog` preservam o perfil vertical `9:16` dos assets de `voz-latencia` e serão confirmadas em aparelho quando `ffmpeg`/`ffprobe` estiverem disponíveis.

---

## 2. Assets estáticos e de guarda-roupa verificados em disco

| Arquivo | Dimensão | Tipo / Alfa | Tamanho (bytes) | Papel / Estado |
| :--- | :---: | :---: | ---: | :--- |
| `assets/gabi-idle.png` | 768×1024 | PNG RGBA8 | 564.836 | Look `vestido_vermelho` · fallback de repouso |
| `assets/gabi-listening.png` | 768×1024 | PNG RGBA8 | 563.624 | Fallback de escuta |
| `assets/gabi-talking.png` | 768×1024 | PNG RGBA8 | 574.499 | Fallback de fala |
| `assets/gabi-comer.png` | 768×1024 | PNG RGBA8 | 573.212 | Rotina `06h–10h` (`EATING` / `EATING_REACTION`) |
| `assets/gabi-computador.png` | 768×1024 | PNG RGBA8 | 586.190 | Look `sueter_computador` · fallback `WORKING`/`AtComputer` |
| `assets/gabi-ler.png` | 768×1024 | PNG RGBA8 | 575.127 | Look `vestido_lilas` · rotina `17h–20h` (`READING`) |
| `assets/gabi-dormir.png` | 768×1024 | PNG RGBA8 | 657.304 | Look `pijama_estrelas` · rotina `21h–05h` (`SLEEPING`) |
| `assets/avatar/room.webp` | 720×1280 | WebP | 150.118 | Cenário padrão do Quarto (`ROOM_WEBP`) |
| `assets/room-bg.png` | — | PNG | 3.119.190 | Cenário iluminado (`ROOM_PNG`) |
| `assets/avatar/idle/` (353 `.webp`) | 360×480 | WebP `VP8X+ALPH` | 6.622.396 | Sequência animada transparente base |
| `assets/avatar/listening/` (353 `.webp`) | 360×480 | WebP `VP8X+ALPH` | 5.975.008 | Sequência animada transparente de escuta |
| `assets/avatar/talking/` (353 `.webp`) | 360×480 | WebP `VP8X+ALPH` | 7.317.088 | Sequência animada transparente de fala |
| `assets/avatar/thinking/` (76 `.webp`) | 360×480 | WebP `VP8X+ALPH` | 1.534.524 | Sequência animada transparente de pensamento |
| `assets/anim/home_idle/` (86 `.png`) | 320×569 | PNG RGBA8 | 12.671.846 | Sequência animada transparente na Home / look vermelho |
| `assets/anim/scene_computador/` (92 `.png`) | 320×569 | PNG RGBA8 | 11.470.711 | Sequência animada transparente no notebook |

Looks pendentes de arte (declarados no catálogo com bloqueio honesto até entrega dos arquivos): `gabi-look-moletom.png`, `gabi-look-festa.png`, `gabi-look-pijama.png`.

---

## 3. Auditoria dos Players de Vídeo e Animação (Gate V1 — Antes vs Depois R1.2)

| Componente | Arquivo | Defeito Identificado na Auditoria R1.2 | Correção Implementada na R1.2 | Status |
| :--- | :--- | :--- | :--- | :--- |
| `RoomLoopBackgroundVideo` | `GabiAvatarViews.kt` + `res/layout/view_texture_player.xml` | Instanciava `PlayerView(ctx)` padrão (`SurfaceView`), com risco de perforação de janela / Z-order sobre a Camada 1 (`HBStageCharacter`) e sem respeito a `reduceMotion`. | Passou a inflar `R.layout.view_texture_player` com `app:surface_type="texture_view"`, `Lifecycle.Event.ON_RESUME`/`ON_PAUSE`/`ON_STOP`, `player.release()` no `onDispose`, fallback seguro em `onPlayerError` e exibição estática de `avatar/room.webp` quando `reduceMotion = true`. | `INTEGRATED_UNTESTED` |
| `RoomIntroVideo` | `GabiAvatarViews.kt` + `QuartoGabiScreen.kt` | Usava `SurfaceView` padrão e `QuartoGabiScreen` passava parâmetro `onFinished` incompatível com `onFinish` além de sobrepor botão duplicado. | Infla `view_texture_player.xml` (`TextureView`), respeita `ON_RESUME`/`ON_PAUSE`/`ON_STOP`, libera `ExoPlayer` no `onDispose` e alinha `onFinish` com controles únicos ("Pular" e "Não mostrar novamente"). | `INTEGRATED_UNTESTED` |
| `AvatarAnimation` | `GabiAvatarViews.kt` | Ignorava `reduceMotion` e continuava rodando loop de decodificação sem pausa explícita. | Recebe `reduceMotion`, usa `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` para pausar em segundo plano, exibe apenas o 1º quadro em acessibilidade reduzida e trata `OutOfMemoryError` na decodificação `ARGB_8888`. | `INTEGRATED_UNTESTED` |
| `FrameSequenceAnimation` | `GabiAvatarViews.kt` + `ComputerScreen.kt` | Ignorava `reduceMotion` e não pausava loop em `ON_PAUSE`. | Recebe `reduceMotion` (padrão `rememberReduceMotionEnabled()`), preserva compatibilidade com `frameCount` em `ComputerScreen`, usa `repeatOnLifecycle(Lifecycle.State.STARTED)` e protege contra `OutOfMemoryError`. | `INTEGRATED_UNTESTED` |
| `BreathingCharacter` & `HBStageCharacter` | `GabiAvatarViews.kt`, `QuartoGabiScreen.kt`, `HomeScreen.kt` | `rememberReduceMotionEnabled()` não lia `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` do Android. | `rememberReduceMotionEnabled()` consulta `Settings.Global.ANIMATOR_DURATION_SCALE` e desliga pulso/respiração contínua quando acessibilidade reduzida estiver ativa; inclui `HBActivity.OutfitChange` no pulso transitório. | `INTEGRATED_UNTESTED` |
| `HBStateResolver` & `HBCharacterController` | `HBCharacterModel.kt`, `HBCharacterController.kt` | `TapReaction` caía em PNG estático (`gabi-talking.png`) apesar de existir `avatar/talking/` (353 quadros); look escolhido ocultava indicação `MusicReaction` do Spotify; não havia transição `OUTFIT_CHANGE`. | `TapReaction` usa `avatar/talking/` animada; `OutfitChange` executa transição de 900 ms preservando o look escolhido em seguida; `MusicReaction` coexiste com look ativo sem substituir a roupa; todas as reações retornam automaticamente ao estado/look anterior ao expirar. | `INTEGRATED_UNTESTED` (coberto por JUnit4 + verificação Python) |

**Provenance & Licenciamento:** Todos os 1.324 arquivos de mídia de `voz-latencia` preservam proveniência MIT em `android/LICENSE`, com revisão comercial pendente apenas para redistribuição externa (`LICENSE_PENDING_OWNER.txt`). Zero assets do Tamagotchi/nanoMuse existem no pacote.
