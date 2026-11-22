# GABITAMA_INTEGRACAO_R1_3 — Relatório Técnico da Integração de Avatares R1.3

**Projeto:** `NOVO_AGENTE_HB` (`com.novoagentehb.app`, `versionCode = 5`, `versionName = 0.2.3-r1.3`)  
**Data (UTC):** 2026-11-21  
**Escopo:** Integração dos 13 MP4s originais baixados da pasta `Avatar` do Google Drive (`OWNER_PRIVATE_DRIVE_AVATAR_FOLDER`), preservação integral dos originais + hashes, composição limpa sem retângulos de fundo, introdução das fases `idade1` (pré-adolescente) e `idade2` (adolescente) seguindo a convenção de presets/estágios do sistema base sem inventar regras de evolução, e preservação completa das funções Gabi + agente.

---

## 1. Padrão Estrutural Adotado (Sem Cópia GPL)

Seguindo a convenção estrutural já estabelecida entre `Jarvis-Projeto@voz-latencia` (sequências RGBA em `assets/anim/<cena>/`, vídeos `9:16` em `TextureView`) e a organização declarativa de estágios/proveniência de referência (`assets/hb-stages/<id>/source.json` + `preview.png` + enum `HBCharacterStage` + `HBStageCatalog`, sem copiar código GPL):

- **Originais preservados sem modificação:** `novo-agente-hb/assets_originals/drive_avatar/*.mp4` (`13` arquivos, `32.587.828` bytes), indexados com SHA-256 e MD5 em `evidence/DRIVE_AVATAR_ORIGINALS_SHA256.txt`.
- **1.324 assets de `voz-latencia` preservados intactos:** `evidence/APPROVED_ASSETS_SHA256.txt` permanece 100% íntegro; não foram recriadas animações que já existiam.
- **Novas sequências RGBA extraídas dos MP4s inéditos do Drive:**
  - `android/app/src/main/assets/anim/scene_listening_intent/f001..f096.png` (`320×569`, RGBA8, `12 fps`, originado de `Girl_listening_intently_202609081538.mp4`).
  - `android/app/src/main/assets/anim/scene_computador_pensando/f001..f096.png` (`320×569`, RGBA8, `16 fps`, originado de `magnific_json-prompt-animate-the-e_3z02plbREY.mp4`).
- **Novas fases etárias da HB (`assets/hb-stages/`):**
  - `hb-stages/young_child/{preview.png,source.json}` -> `HBCharacterStage.YOUNG_CHILD` ("Infância (Padrão)").
  - `hb-stages/pre_adolescent/{idle.mp4,preview.png,source.json}` -> `HBCharacterStage.PRE_ADOLESCENT` ("Pré-adolescente (idade1)").
  - `hb-stages/adolescent/{idle.mp4,preview.png,source.json}` -> `HBCharacterStage.ADOLESCENT` ("Adolescente (idade2)").

---

## 2. Arquivos Kotlin / Configuração / Testes Alterados

| Arquivo | Mudança R1.3 | Garantia / Preservação |
|---|---|---|
| `data/prefs/AppPreferences.kt` | Adicionados `quartoSelectedStageId` (`"young_child"`) e `setQuartoSelectedStageId(...)` | Persistência em DataStore sem nenhuma chave de Pet/evolução/morte |
| `gabi/HBCharacterModel.kt` | Adicionados `HBCharacterStage`, `HBStageCatalog`, `HBVisualSource.FullSceneStageVideo`, `isLiveListening`, `selectedStage`; atualizado `HBStateResolver` | Prioridade estrita mantida (`Cancelled > Error > Speaking > Listening > Thinking > Working > Success > Eating > TapReaction > OutfitChange > StagePresence > Wardrobe > Routine`); zero regras automáticas de evolução |
| `gabi/HBAnimationCatalog.kt` | Adicionados `LISTENING_INTENT`, `COMPUTER_THINKING`, `STAGE_PRE_ADOLESCENT_IDLE`, `STAGE_ADOLESCENT_IDLE`, `SPRITESHEET_OPTIONAL` e rastreabilidade R1.3 nas 17 entradas originais | `gabi-spritesheet.png` registrado explicitamente como `MISSING_FILE_R1_3` / `PENDING_FLOW_MEDIA` |
| `gabi/HBCharacterController.kt` | Observa `quartoSelectedStageId`, propaga `isLiveListening` de `VoiceStatusSnapshot`, expõe `selectStage` / `isStageAvailable` e evita contradição entre `selectOutfit` e estágio `idade1`/`idade2` | Estado único compartilhado entre Home, Chat e Quarto |
| `gabi/GabiAvatarViews.kt` | Adicionado `StageLoopVideo` (`ExoPlayer` + `view_texture_player.xml`, `volume = 0f`, pausa em `ON_PAUSE`, liberação em `onDispose`, fallback para `preview.png` e respeito a `reduceMotion`) e suporte em `HBStageCharacter` | Zero bloqueio de voz/Spotify e zero `SurfaceView` |
| `ui/habitat/{HabitatViewModel.kt,QuartoGabiScreen.kt}` | Adicionado seletor manual **"Fase da HB"** (`StageSelectorDialog` com miniaturas `preview.png`); `FullSceneStageVideo` ocupa Layer 0+1 em tela cheia sem retângulo e sem duplo `ExoPlayer` | Todas as funções do Quarto (Guarda-roupa, Ambiente, Intro, Spotify, Alimentar, Parar, toque, Chat, Computador) preservadas |
| `ui/home/HomeScreen.kt` | Passa a respeitar `FullSceneStageVideo` dentro do card arredondado da Home quando `idade1`/`idade2` está ativo | Mantém voz, Gemini Live, clima, metas, memória, arquivos e chat |
| `app/build.gradle.kts` | Bump para `versionCode = 5`, `versionName = "0.2.3-r1.3"` | Mantém `PROVISIONAL_INTERNAL_ID` e zero segredos |
| `test/.../HBCharacterStateUnitTest.kt` & `scripts/tests/verify_project_integrity.py` | Novos testes para estágios `idade1`/`idade2`, `scene_listening_intent`, `scene_computador_pensando`, integridade SHA-256 dos 13 MP4s do Drive e `hb-stages/` | Testes antigos preservados e ampliados |
