# RELATORIO_VISUAL_R1_4 — Auditoria Visual e Composição GabiTama

**Data (UTC):** 2026-11-21  
**Build / Versão:** `0.2.4-r1.4` (`versionCode = 6`)

---

## 1. Ativos Oficiais Integrados do Google Drive (`Pasta Avatar`)

Todos os 3 arquivos verificados na rodada R1.4 em `OWNER_PRIVATE_DRIVE_AVATAR_FOLDER` foram validados por leitura visual direta e integridade de hash:
- **`assets/bible.png` (`1448×1086` RGB, `1.989.359` bytes):** Bíblia oficial da HB extraída com paleta canônica (`#C7541E` cabelo cobre-ruivo, `#F6C7A7` pele, `#E6A07A` sardas, `#C62828` vestido carmesim, `#1E1E1E` botas), turnaround frontal/lateral/costas e folha de expressões; também inspecionável no app via `CharacterBibleDialog` em Ajustes e Fases da HB.
- **`assets/gabi-spritesheet.png` (`5376×1024` RGBA, `4.086.244` bytes):** Planilha oficial com 7 poses transparentes (`768×1024` por célula: idle, listening, talking, dormir, comer, computador, ler), catalogada como `HBCatalogStateId.SPRITESHEET_OPTIONAL` (`AVAILABLE_STATIC_REFERENCE`) e exibível no `CharacterBibleDialog`.
- **`assets/gabi-talking.png` (`768×1024` RGBA, `574.499` bytes):** Validada byte a byte (`MD5 919e6153be68430f5397243870935d98`) como idêntica à versão já empacotada no projeto.

---

## 2. Gráficos de UI e Medalhões Gerados e Pós-Processados (Gate D1)

| Arquivo em `android/app/src/main/assets/` | Dimensão / Modo | Bytes | Uso Real na Interface |
| :--- | :--- | ---: | :--- |
| `theme/gabitama_home_backdrop.png` | `720×1280` RGB | `557.505` | Atmosfera radial crepuscular atrás de todas as telas (`GabiAtmosphericBackdrop`) |
| `theme/gabitama_stage_halo.png` | `512×512` RGBA | `351.797` | Halo orgânico transparente atrás da HB na `HomeScreen` |
| `theme/gabitama_card_surface.png` | `512×288` RGB | `121.640` | Textura de superfície para painéis em destaque (`GabiFeaturedSurface`) |
| `theme/gabitama_brand_crest.png` | `256×256` RGBA | `84.595` | Brasão circular no topo da `HomeScreen`, `SettingsScreen` e `PermissionsScreen` (recorte alfa circular limpo `rmax = 86 px`) |
| `theme/gabitama_empty_scene.png` | `512×512` RGB | `244.689` | Ilustração de apoio para estados vazios (`GabiEmptyStateCard`) em Chat, Feed, Metas, Arquivos e Memória |
| `hb-stages/young_child/avatar_circle.png` | `256×256` RGBA | `92.313` | Medalhão circular fechado da fase **Infância** (barra inferior, Chat, seletor de fases) |
| `hb-stages/pre_adolescent/avatar_circle.png` | `256×256` RGBA | `102.147` | Medalhão circular fechado da fase **Pré-adolescente** (barra inferior, Home medalhão, Chat, Computador) |
| `hb-stages/adolescent/avatar_circle.png` | `256×256` RGBA | `100.578` | Medalhão circular fechado da fase **Adolescente** (barra inferior, Home medalhão, Chat, Computador) |

---

## 3. Regras de Zero-Retângulo e Continuidade de Fase

1. **Zero caixa escura ao redor da personagem (`HomeScreen`):** A personagem em repouso (`avatar/idle` `cx = 0.500` ou pose ativa) posa sobre `gabitama_stage_halo.png` transparente e recebe elipse sutil de sombra de contato no chão (`alpha = 0.28f`) dentro de `HBStageCharacter`.
2. **Proibição de retângulo 9:16 em `PRE_ADOLESCENT` / `ADOLESCENT` fora do Quarto:** Como `idade1.mp4` e `idade2.mp4` possuem cenário opaco (`720×1280`), a `HomeScreen` ativa `preferCircularStageMedallion = true`, exibindo um medalhão circular limpo (`168.dp`) com anel semântico de atividade em vez de estampar uma miniatura retangular sobre o fundo. Já no `QuartoGabiScreen`, `StageLoopVideo` ocupa 100% da área 9:16 do palco sem borda retangular.
3. **Continuidade rígida de fase (`HBStateResolver`):** Ao selecionar `PRE_ADOLESCENT` ou `ADOLESCENT`, todos os estados interativos (`Listening`, `Speaking`, `Thinking`, `Working`, `Success`, `Error`, `Cancelled`, `Eating`, `MusicReaction`, `TapReaction`) preservam a fase ativa (`stage_${id}_$stateSlug`), mantendo o medalhão inferior (`GabiBottomBar`) e o avatar do `ChatScreen` sincronizados via `LocalHBStage`.
4. **Acessibilidade e conforto visual:** Todas as 10 telas usam `GabiTokens.NightBg` (`#0B1219`), contrastes altos (`#F3F4EF` sobre `#131D28`), espaçamento livre de `innerPadding` acima da cápsula flutuante inferior e largura máxima centralizada (`widthIn(max = 720.dp)`) para celulares, foldables e tablets.
