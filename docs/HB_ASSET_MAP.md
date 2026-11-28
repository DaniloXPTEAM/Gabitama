# MAPA DE ATIVOS DA HB — `HB_ASSET_MAP.md` (R1.1)

**Data:** 2026-11-21  
**Branch de origem dos ativos:** `DaniloXPTEAM/Jarvis-Projeto@voz-latencia` (`android/LICENSE` = MIT)  
**Total de arquivos de ativos aprovados em `evidence/APPROVED_ASSETS_SHA256.txt`:** 1.324 arquivos  
**Regra de transparência R1.1:** Além de bloquear itens ausentes (`ASSET_PENDING`), este documento e a UI distinguem explicitamente o que é **look/pose existente reaproveitada** (vestido vermelho, vestido lilás com livro, suéter mostarda com notebook, pijama azul estrelado com travesseiro) do que seriam looks exclusivos em pé sem adereços (`ASSET_PENDING`).

---

## 1. Inspeção Real de Formato, Dimensão e Transparência (R1.1)

Verificado no sandbox via leitura binária de cabeçalho + inspeção visual das imagens:

| Arquivo verificado | Formato & Canal Alfa | Conteúdo visual real observado |
| --- | --- | --- |
| `assets/gabi-idle.png` | PNG `768x1024`, 8-bit **RGBA** (`color_type=6`) | Personagem Gabi em pé, vestido vermelho sem mangas, botas pretas, fundo transparente |
| `assets/gabi-ler.png` | PNG `768x1024`, 8-bit **RGBA** (`color_type=6`) | Personagem Gabi com **vestido lilás** segurando livro roxo aberto, fundo transparente |
| `assets/gabi-computador.png` | PNG `768x1024`, 8-bit **RGBA** (`color_type=6`) | Personagem Gabi com **suéter mostarda + calça preta** segurando notebook, fundo transparente |
| `assets/gabi-dormir.png` | PNG `768x1024`, 8-bit **RGBA** (`color_type=6`) | Personagem Gabi com **pijama azul estrelado** abraçando travesseiro bocejando, fundo transparente |
| `assets/gabi-comer.png` | PNG `768x1024`, 8-bit **RGBA** (`color_type=6`) | Personagem Gabi (vestido vermelho) sentada com tigela de cereal e colher, fundo transparente |
| `assets/gabi-listening.png` / `gabi-talking.png` | PNG `768x1024`, 8-bit **RGBA** (`color_type=6`) | Poses com mão na orelha / mão na cintura, fundo transparente |
| `assets/anim/home_idle/f001..f086.png` | PNG `320x569`, 8-bit **RGBA** (`color_type=6`) | Sequência animada em vestido vermelho com canal alfa |
| `assets/anim/scene_computador/f001..f092.png` | PNG `320x569`, 8-bit **RGBA** (`color_type=6`) | Sequência animada estudando/trabalhando com suéter mostarda e canal alfa |
| `assets/anim/scene_comer/f001..f048.webp` | WebP `360x480` com alfa, loop de 48 quadros | Respiração sobre `gabi-comer.png` (movimento procedural, ver `scripts/hb/gen_quarto_breathing_webp.py`) |
| `assets/anim/scene_comendo_reacao/f001..f024.webp` | WebP `360x480` com alfa, loop de 24 quadros | Pulso sobre `gabi-comer.png` (reação ao alimentar) |
| `assets/anim/scene_ler/f001..f060.webp` | WebP `360x480` com alfa, loop de 60 quadros | Respiração sobre `gabi-ler.png` |
| `assets/anim/scene_dormir/f001..f072.webp` | WebP `360x480` com alfa, loop de 72 quadros | Respiração mais lenta sobre `gabi-dormir.png` |
| `assets/avatar/{idle,listening,talking,thinking}/f*.webp` | WebP `VP8X` com chunk **`ALPH`** | Animações circulares/busto do avatar com transparência preservada |
| `assets/avatar/room.webp` / `assets/room-bg.png` | WebP / PNG `1024x1536` | Cenários de fundo independentes (camada 0 do Quarto da Gabi) |
| `res/raw/room_bg_loop.mp4` / `assets/avatar/room_intro.mp4` | MP4 H.264 mudo | Vídeo ambiente de fundo (camada 0 opcional) e vídeo de abertura pulável 1x |

---

## 2. Guarda-Roupa e Estados: Pose Real vs. `ASSET_PENDING`

| ID no Guarda-Roupa | Asset vinculado | Disponibilidade | Descrição honesta exibida ao usuário na UI (R1.1) |
| --- | --- | --- | --- |
| `vestido_vermelho` | `gabi-idle.png` (+ `anim/home_idle`) | **Disponível** | Vestido vermelho + botas pretas (pose base) — **usa pose dedicada existente**. |
| `vestido_lilas` | `gabi-ler.png` | **Disponível** | Vestido lilás — **reaproveita a pose de leitura existente** (`gabi-ler.png`, com livro aberto). |
| `sueter_computador` | `gabi-computador.png` (+ `anim/scene_computador`) | **Disponível** | Suéter mostarda + calça preta — **reaproveita a pose de estudo existente** (`gabi-computador.png`, com notebook). |
| `pijama_estrelas` | `gabi-dormir.png` | **Disponível** | Pijama azul estrelado — **reaproveita a pose de sono existente** (`gabi-dormir.png`, com travesseiro). |
| `moletom_preto` | `gabi-look-moletom.png` | **`ASSET_PENDING`** | **Bloqueado na UI ("Arte pendente")** — arquivo não existe em nenhuma branch. |
| `look_festa` | `gabi-look-festa.png` | **`ASSET_PENDING`** | **Bloqueado na UI ("Arte pendente")** — arquivo não existe em nenhuma branch. |
| `look_pijama_passeio` | `gabi-look-pijama.png` | **`ASSET_PENDING`** | **Bloqueado na UI ("Arte pendente")** — arquivo não existe em nenhuma branch. |

---

## 3. Fluxos Pendentes de Renderização em Dispositivo Real

- A composição das 5 camadas do Quarto (`RoomBackgroundLayer` -> `HBStageCharacter` -> `SpotifySpeakerBadge` -> `HBStatusHud` -> `RoomIntroOverlay`) está implementada e verificada estaticamente (`QuartoGabiScreen.kt`), porém o rendering visual em runtime (`DEVICE_PASS` / `EMULATOR_PASS`) depende de compilação local fora do sandbox.
- `NotificationFeedService` registra `MediaSessionManager.OnActiveSessionsChangedListener` + `MediaController.Callback` para detectar reprodução ativa do Spotify quando a permissão de leitor de notificações está concedida; sem a permissão, exibe explicitamente `Spotify desconectado (permissão pendente)`.
