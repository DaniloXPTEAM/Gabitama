# HB_FUNDOS_E_TRANSPARENCIA_R1_4_1 — Auditoria de Transparência, Recorte Alfa e Fundos do Quarto

**Projeto:** `NOVO_AGENTE_HB` (`0.2.5-r1.4.1`, `versionCode = 7`)  
**Regra R1.4.1 aplicada:** *"`RGBA` com alfa opaco não vale como recorte. Não inventar recortes/recriações que alterem o rosto, a idade ou a roupa."*

---

## 1. Inspeção Real Pixel a Pixel de Alfa (Sobre Fundo Escuro `#120F11` e Fundo Claro `#F3EFEA`)

Medição executada via `PYTHONPATH=/tmp/pytools python3` (`PIL` + `numpy`) inspecionando os canais RGBA reais de cada ativo em `android/app/src/main/assets/`:

| Ativo / Sequência | Dimensão & Formato | Pixels `alpha = 0` | Semitransparentes (`0 < alpha < 255`) | Opacos (`alpha = 255`) | Resultado Visual em Fundo Claro e Escuro |
| --- | --- | --- | --- | --- | --- |
| `avatar/idle/f040.webp` (`353q`) | `360×480` WebP RGBA | `62.1%` | `3.7%` | `34.2%` | **TRANSPARENTE LIMPO** — cabelo, olhos e botas pretas íntegros em fundo claro e escuro. |
| `avatar/listening/f040.webp` (`353q`) | `360×480` WebP RGBA | `64.9%` | `3.7%` | `31.3%` | **TRANSPARENTE LIMPO** — mão elevada, cabelo e botas sem halo. |
| `avatar/talking/f040.webp` (`353q`) | `360×480` WebP RGBA | `62.4%` | `4.0%` | `33.6%` | **TRANSPARENTE LIMPO** — contorno limpo nos dois fundos. |
| `avatar/thinking/f040.webp` (`76q`) | `360×480` WebP RGBA | `63.2%` | `3.5%` | `33.4%` | **TRANSPARENTE LIMPO** — contorno limpo nos dois fundos. |
| `anim/scene_listening_intent/f050.png` (`96q`) | `320×569` PNG RGBA | `59.9%` | `3.9%` | `36.2%` | **TRANSPARENTE LIMPO** — recorte R1.3 preservando botas pretas sólidas e fios do cabelo. |
| `anim/scene_computador_pensando/f050.png` (`96q`) | `320×569` PNG RGBA | `70.1%` | `2.4%` | `27.4%` | **TRANSPARENTE LIMPO** — recorte R1.3 com calça e botas escuras 100% sólidas no claro e escuro. |
| `anim/home_idle/f040.png` (`86q`, `voz-latencia`) | `320×569` PNG RGBA | `72.5%` | `2.5%` | `25.0%` | **TRANSPARENTE COM RESSALVA LEVE** — ativo original `voz-latencia`; há pequeno brilho de *luma-key* na bota direita sobre fundo branco. Mantido íntegro pelo SHA-256 aprovado. |
| `anim/scene_computador/f045.png` (`92q`, `voz-latencia`) | `320×569` PNG RGBA | `72.1%` | `4.0%` | `23.9%` | **TRANSPARENTE COM RESSALVA** — ativo original `voz-latencia` sofreu *luma-key* parcial nas botas/perna preta quando visto sobre fundo muito claro (`#F3EFEA`). Mantido íntegro pelo SHA-256 aprovado. |
| `gabi-idle.png`, `gabi-talking.png`, `gabi-ler.png`, `gabi-dormir.png`, `gabi-comer.png` | `768×1024` PNG RGBA | `59.5%–65.9%` | `1.4%–1.7%` | `32.7%–38.8%` | **TRANSPARENTE LIMPO** — todas as 5 poses fixas têm recorte limpo nos dois fundos. |
| `hb-stages/*/avatar_circle.png` (R1.4.1) | `256×256` PNG RGBA | `24.0%` | `4.6%` | `71.5%` | **MEDALHÃO CIRCULAR LIMPO** — borda circular chanfrada com alfa `0` nos cantos externos. |

---

## 2. Análise Técnica dos Vídeos `idade1.mp4` (`PRE_ADOLESCENT`) e `idade2.mp4` (`ADOLESCENT`)

- **Dimensão e natureza real dos arquivos:** ambos são `720×1280` H.264/MP4 (`240` quadros, `24 fps`, RGB opaco sem canal alfa) mostrando a HB em quarto completo iluminado (parede bege/rosa, cortinas, quadros, cama com travesseiros, ursinho de pelúcia e pufe amarelo).
- **Por que NÃO foi feito recorte automático heurístico neste ambiente:**
  1. Os tons da parede, cortina e madeira do quarto sobrepõem diretamente os valores de pele (`#F6C7A7`), sardas (`#E6A07A`), cabelo cobre (`#C7541E`) e sombra do vestido.
  2. O ambiente Linux não dispõe de modelo neural de *alpha matting* nem `ffmpeg`; corte por cor/movimento causaria erosão severa nos cabelos, dedos e borda do vestido, alterando a aparência aprovada da HB (o que é expressamente proibido pela ordem R1.4.1).
  3. Gerar `PNG` RGBA com `alpha = 255` sólido seria desonesto e colocaria um retângulo artificial sobre os fundos do Quarto.

---

## 3. Como R1.4.1 Resolve os Fundos no `QuartoGabiScreen.kt` (Modo Honesto + Preferência Preservada)

1. **Fase `YOUNG_CHILD` (`RoomBackgroundCompatibility.INDEPENDENT_TRANSPARENT_LAYERS`):**
   - `RoomBackgroundLayer` ocupa a Camada 0 em tela cheia atrás da HB transparente (`RoomBackgroundOption.ROOM_WEBP`, `ROOM_PNG`, `ROOM_MP4` `res/raw/room_bg_loop.mp4` ou `CUSTOM_URI` da galeria).
   - A personagem flutua na Camada 1 com sombra oval suave de contato (`HBStageCharacter`), sem nenhum retângulo de fundo.

2. **Fases `PRE_ADOLESCENT` e `ADOLESCENT` (`RoomBackgroundCompatibility.OPAQUE_FULL_SCENE_BLOCKS_CUSTOM_BG`):**
   - Enquanto não houver sequência RGBA transparente para `idade1`/`idade2`, `QuartoGabiScreen` exibe explicitamente o vídeo `FullSceneStageVideo` em tela cheia na Camada 0 (modo identificado, zero retângulo artificial sobre outros fundos).
   - **Divulgação honesta na UI:**
     - O cartão HUD superior do Quarto informa diretamente: `"Cena completa (pre_adolescent/adolescent) · fundo '<nome>' preservado p/ Infância"`.
     - O diálogo `Ambiente do Quarto` (`RoomEnvironmentDialog`) explica que a cena completa opaca está ativa e oferece 1 toque em **"Voltar p/ Infância e aplicar fundo"**, mantendo todos os botões de escolha (`Quarto Clássico`, `Quarto Iluminado`, `Vídeo Ambiente`, `Foto da Galeria`) funcionais para salvar a preferência.
   - **Preservação real de preferências:** `HBCharacterController.selectStage()` **não apaga nem sobrescreve** `prefs.quartoBackgroundId` nem `prefs.quartoCustomBackgroundUri` no DataStore. Ao voltar para `YOUNG_CHILD`, o fundo anteriormente escolhido (inclusive foto da galeria via `OpenDocument` com permissão persistente) volta imediatamente a aparecer atrás da HB.

3. **Camadas de Z-order e bloqueio de toque:**
   - `view_texture_player.xml` agora declara explicitamente `android:clickable="false"`, `android:focusable="false"` e `android:focusableInTouchMode="false"`, além de `app:surface_type="texture_view"` e `app:use_controller="false"`.
   - Dessa forma, os vídeos em `Layer 0` nunca interceptam cliques da `Layer 1` (toque na HB), `Layer 2` (discos superiores), `Layer 3` (caixinha Spotify, Parar, Alimentar), `Layer 4` (Pular / Não mostrar abertura) nem da `GabiBottomBar`.
