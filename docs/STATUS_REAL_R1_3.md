# STATUS_REAL_R1_3 — Estado Verdadeiro da Revisão R1.3 (Sem Omissão)

**Projeto:** `NOVO_AGENTE_HB` (`com.novoagentehb.app`, `versionCode = 5`, `versionName = 0.2.3-r1.3`)  
**Data (UTC):** 2026-11-21  
**Status global:** **`INTEGRADO PARCIALMENTE — PENDENTE ARQUIVO AUSENTE NO DRIVE (`gabi-spritesheet.png`) E COMPILAÇÃO ANDROID FORA DO SANDBOX`**

---

## 1. Situação dos 15 Arquivos do Checklist R1.3

- **13 / 15 presentes na pasta `Avatar` do Google Drive (`OWNER_PRIVATE_DRIVE_AVATAR_FOLDER`):** todos baixados, preservados intactos em `novo-agente-hb/assets_originals/drive_avatar/`, verificados por MD5/SHA-256 e devidamente mapeados/integrados:
  - **8 matrizes correspondentes a ativos já integrados em `voz-latencia` + 1 variante de iluminação:** preservadas sem recriação redundante (`melhorzinhoparadaidle.mp4`, `melhorzinhoparaescultando.mp4`, `melhorzinhoparafalando.mp4`, `melhorzinhoparapensando.mp4`, `gabinastelas.mp4`, `magnific_faca-uam-animacao-normal-_dtfosfxXSL.mp4`, `magnific_prompt-..._WDB0lzjcXe.mp4`, `magnific_prompt-..._EbrFq0BuuO.mp4`, `magnific_prompt-..._WDB0lzjcXe2.mp4`).
  - **2 novas animações com composição limpa transparente sobre o Quarto:** `Girl_listening_intently_202609081538.mp4` -> `assets/anim/scene_listening_intent/` e `magnific_json-prompt-animate-the-e_3z02plbREY.mp4` -> `assets/anim/scene_computador_pensando/`.
  - **2 novas fases etárias integradas em tela cheia sem retângulo e sem regras de evolução:** `idade1.mp4` (`Pré-adolescente`) -> `assets/hb-stages/pre_adolescent/idle.mp4` e `idade2.mp4` (`Adolescente`) -> `assets/hb-stages/adolescent/idle.mp4`.
- **1 / 15 ausente no Drive `Avatar`, porém JÁ existente no pacote (`voz-latencia`):**
  - `gabi-talking.png` -> não consta na pasta `Avatar` do Drive, porém já existe em `novo-agente-hb/android/app/src/main/assets/gabi-talking.png` (`574.499` B, SHA-256 `dacccf07fe6cd9502e5acbbfb518a5c127a32000d71b61a1bc35a8a4c1af1529`).
- **1 / 15 AUSENTE em todas as fontes verificadas (impede declarar R1.3 100% completa):**
  - **`gabi-spritesheet.png`** -> **NÃO encontrado** na pasta `Avatar` do Google Drive (relistada integralmente 2x), nem na busca global do Drive, nem em nenhuma branch do repositório `DaniloXPTEAM/Jarvis-Projeto`, nem no diretório local `novo-agente-hb/`. Registrado explicitamente em `HBAnimationCatalog.kt` (`HBCatalogStateId.SPRITESHEET_OPTIONAL`, `PENDING_FLOW_MEDIA`).

---

## 2. O Que Permanece Pendente (Honestidade Obrigatória)

1. **Arquivo pendente do checklist:** `gabi-spritesheet.png` (ausente no Drive `Avatar` e no repositório).
2. **Animações específicas por fase (`idade1` / `idade2`):** atualmente os arquivos enviados (`idade1.mp4`, `idade2.mp4`) cobrem o estado de presença/repouso no quarto em tela cheia; reações de voz/tarefa continuam funcionando normalmente mantendo todas as capacidades da Gabi e do agente, usando os sprites transparentes disponíveis até que eventuais variações dedicadas por idade sejam fornecidas.
3. **Looks pendentes no Guarda-roupa (herdados de R1.1/R1.2):** `gabi-look-moletom.png`, `gabi-look-festa.png`, `gabi-look-pijama.png` continuam bloqueados honestamente na UI como `"Arte pendente"`.
4. **Compilação & testes Android no aparelho:** `./gradlew testDebugUnitTest` e `./gradlew assembleDebug` devem ser executados localmente em máquina com JDK 17 + Android SDK 34.
