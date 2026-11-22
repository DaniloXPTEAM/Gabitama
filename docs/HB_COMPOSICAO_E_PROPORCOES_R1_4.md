# HB_COMPOSICAO_E_PROPORCOES_R1_4 — Composição, Transparência e Proporções da HB

**Projeto:** `NOVO_AGENTE_HB` (GabiTama R1.4)  
**Data (UTC):** 2026-11-21  
**Base oficial verificada:** `bible.png` (`1448×1086`), `gabi-spritesheet.png` (`5376×1024`), `gabi-*.png` (`768×1024`), `avatar/*/` (`360×480` WebP), `anim/*/` (`320×569` PNG) e vídeos de fase `idade1.mp4` / `idade2.mp4` (`720×1280`).

---

## 1. Medições Reais de Enquadramento e Bounding Boxes (Alpha / Rosto)

| Recurso | Dimensão | Aspect Ratio | Canal Alpha Real | Bounding Box útil (`x0,y0 → x1,y1`) | Centro X relativo (`cx/w`) | Altura útil (%) | Observação técnica R1.4 |
|---|---:|---:|:---:|---|---:|---:|---|
| `gabi-idle.png` | `768×1024` | `3:4` | Sim (`RGBA8`) | `(160, 31 → 607, 992)` | `0.499` | `93.8%` | Referência principal centrada para pose em pé e recorte circular |
| `gabi-talking.png` | `768×1024` | `3:4` | Sim (`RGBA8`) | `(123, 46 → 644, 992)` | `0.499` | `92.4%` | Confirmada idêntica à do Drive (`dacccf07...`) |
| `gabi-listening.png` | `768×1024` | `3:4` | Sim (`RGBA8`) | `(149, 60 → 618, 992)` | `0.499` | `91.0%` | Mão esquerda ao ouvido; sombra limpa |
| `gabi-comer.png` | `768×1024` | `3:4` | Sim (`RGBA8`) | `(143, 270 → 624, 992)` | `0.499` | `70.5%` | Sentada com tigela; alinhamento inferior preservado (`y1=992`) |
| `gabi-computador.png` | `768×1024` | `3:4` | Sim (`RGBA8`) | `(99, 235 → 668, 992)` | `0.499` | `73.9%` | Sentada com notebook; alinhamento inferior preservado |
| `gabi-ler.png` | `768×1024` | `3:4` | Sim (`RGBA8`) | `(155, 33 → 612, 992)` | `0.499` | `93.6%` | Vestido lilás com livro |
| `gabi-dormir.png` | `768×1024` | `3:4` | Sim (`RGBA8`) | `(124, 38 → 643, 992)` | `0.499` | `93.2%` | Pijama estrelado com travesseiro |
| `gabi-spritesheet.png` | `5376×1024` | `7 × 3:4` | Sim (`RGBA8`) | 7 células `768×1024` | `0.50` / célula | `~70–94%` | **Integrada na R1.4:** poses 0..6 (`idle, listening, talking, dormir, comer, computador, ler`) |
| `anim/home_idle/` (86q) | `320×569` | `9:16` | Sim (`RGBA8`) | `(11, 84 → 253, 529)` | `0.413` | `78.2%` | Leve deslocamento à esquerda; compensação visual centrada no palco |
| `anim/scene_computador/` (92q) | `320×569` | `9:16` | Sim (`RGBA8`) | `(11, 168 → 279, 529)` | `0.453` | `63.4%` | Alinhada na base (`Alignment.BottomCenter`) |
| `anim/scene_computador_pensando/` (96q) | `320×569` | `9:16` | Sim (`RGBA8`) | `(7, 5 → 319, 563)` | `0.509` | `98.1%` | Enquadramento amplo sem retângulo preto |
| `anim/scene_listening_intent/` (96q) | `320×569` | `9:16` | Sim (`RGBA8`) | `(21, 78 → 255, 533)` | `0.431` | `80.0%` | Recorte RGBA limpo; mão direita + sorriso |
| `avatar/{idle,listening,talking,thinking}/` | `360×480` | `3:4` | Sim (`VP8X+ALPH`) | `(71–74, 12–28 → 284–290, 464–466)` | `0.500` | `91–94%` | Recorte limpo, excelente contorno nos cachos ruivos e botas pretas |
| `hb-stages/pre_adolescent/idle.mp4` (`idade1.mp4`) | `720×1280` | `9:16` | Vídeo opaco | Cena quarto completa; rosto `(358, 368 ± 258)` | `0.497` | `~78%` corpo | Fundo de quarto completo: proibido exibir como caixa retangular sobre outro fundo |
| `hb-stages/adolescent/idle.mp4` (`idade2.mp4`) | `720×1280` | `9:16` | Vídeo opaco | Cena quarto completa; rosto `(372, 332 ± 252)` | `0.517` | `~82%` corpo | Proporção mais esguia e alta; enquadramento próprio recalibrado |

---

## 2. Regra de Composição por Tela e por Fase (Zero Retângulo Artificial)

### 2.1 Palco da Home (`HomeScreen.kt`)
- **Fase `YOUNG_CHILD`:**
  - Altura do palco central: **`196.dp`** (equilíbrio intermediário entre o disco `76.dp` de Tama V2 e a caixa `254.dp` da R1.3).
  - Fundo do componente da HB: **`Color.Transparent`** (nenhum retângulo sólido ou borda de cartão ao redor do corpo).
  - Apoio visual de solo: halo radial transparente (`theme/gabitama_stage_halo.png`, `alpha 0.65f`) + sombra elíptica de contato (`28%` preto sob as botas).
- **Fases `PRE_ADOLESCENT` (`idade1.mp4`) e `ADOLESCENT` (`idade2.mp4`):**
  - Como `idade1.mp4` e `idade2.mp4` possuem cenário completo de quarto embutido, exibí-los em caixa retangular dentro da Home criaria um retângulo artificial.
  - Solução R1.4: exibição em **medalhão circular `168.dp` (`CircleShape`)** com anel semântico de estado (`2.5.dp` dourado/verde/vermelho) e `avatar_circle.png` calibrado por fase (ou `StageLoopVideo` recortado em círculo com `ContentScale.Crop` / foco no tronco superior quando reduzido movimento estiver desligado), mantendo zero retângulo no layout e continuidade fiel da fase!

### 2.2 Quarto da Gabi (`QuartoGabiScreen.kt`)
- **Fase `YOUNG_CHILD`:**
  - Camada 0 (`RoomBackgroundLayer`) ocupa `100%` da área útil (`room.webp`, `room-bg.png`, `room_bg_loop.mp4` ou foto da galeria).
  - Camada 1 (`HBStageCharacter`) ocupa `fillMaxWidth(0.84f)` × `fillMaxHeight(0.75f)` ancorada em `Alignment.BottomCenter`, `padding(bottom = 84.dp)` (acima da pílula flutuante e dos controles inferiores), sobre sombra suave no tapete e transparência RGBA real.
- **Fases `PRE_ADOLESCENT` / `ADOLESCENT`:**
  - `StageLoopVideo` preenche `100%` da cena do Quarto (`fillMaxSize()`, `resize_mode="zoom"`, sem áudio concorrente) unificando cenário + personagem da fase sem caixas ou bordas.
  - No diálogo `Ambiente do Quarto`, caso o usuário esteja em `PRE_ADOLESCENT` ou `ADOLESCENT`, é exibida indicação clara de que a fase utiliza sua cena completa própria e um atalho de 1 toque para voltar à fase `Infância` caso deseje aplicar fotos da galeria ou vídeo ambiente separado.

### 2.3 Miniatura Circular (`GabiFaceAvatar` em `GabiBottomBar` e `ChatScreen`)
- Agora recebe `stage: HBCharacterStage` e carrega diretamente:
  - `hb-stages/young_child/avatar_circle.png` (`256×256` RGBA, centro `cx=384, cy=258, half=252`)
  - `hb-stages/pre_adolescent/avatar_circle.png` (`256×256` RGBA, centro `cx=358, cy=368, half=258`)
  - `hb-stages/adolescent/avatar_circle.png` (`256×256` RGBA, centro `cx=372, cy=332, half=252`)
- **Resultado:** até no cabeçalho do Chat (`42.dp`) e no botão central da barra inferior (`34.dp`), a Gabi mantém rigorosamente a fase selecionada, sem enquadramentos cortando topo do cabelo ou queixo.
