# GABITAMA_PROMPTS_FLOW_ANIMACOES_PENDENTES — Especificação Completa para Google Flow (Atualizada R1.4)

**Projeto:** `NOVO_AGENTE_HB` (`com.novoagentehb.app`, `versionCode = 6`, `0.2.4-r1.4`)  
**Data (UTC):** 2026-11-21  
**Status honesto de mídias na R1.4:**  
- **Resolvidos diretamente do Google Drive (`Avatar`) na R1.4:** `bible.png` (`BIBLE_CHARACTER_SHEET`), `gabi-spritesheet.png` (`SPRITESHEET_OPTIONAL`, 7 poses RGBA), e confirmação byte a byte de `gabi-talking.png`.  
- **Pendentes para geração externa no Google Flow:** As **10 cenas animadas** detalhadas abaixo (`EATING`, `EATING_REACTION`, `READING`, `SLEEPING`, `MUSIC_REACTION`, `TAP_REACTION`, `SUCCESS`, `ERROR`, `CANCELLED`, `OUTFIT_CHANGE`) e os **3 looks extras** (`moletom_preto`, `look_festa`, `look_pijama_passeio`), além de eventuais clipes interativos específicos das fases `Pré-adolescente` e `Adolescente`. Este sandbox continua sem gerador de vídeo por IA, `ffmpeg`/`ffprobe` ou *alpha-matting* neural, mantendo honestidade absoluta sem inventar mídia falsa.

Este guia segue **100% os 17 pontos obrigatórios** para o proprietário gerar cada cena no **Google Flow** mantendo a HB idêntica (`bible.png`, `gabi-spritesheet.png`, `gabi-idle.png`, `gabi-comer.png`, `gabi-ler.png`, `gabi-dormir.png`, `gabi-talking.png`), extrair os quadros transparentes e integrá-los no app sem alterar código Kotlin de roteamento — `HBStateResolver` e `HBAnimationCatalog` já detectam automaticamente as pastas listadas abaixo assim que populadas!

---

## Regras Globais Obrigatórias (Aplicar a TODAS as cenas abaixo)

1. **Identidade visual travada (Character Sheet):** Menina 3D estilizada (estilo animação moderna suave), cabelos longos ondulados **ruivo-acobreados** divididos próximos ao centro caindo abaixo da cintura, olhos grandes **castanho-escuros** expressivos com cílios delicados e sobrancelhas macias castanho-ruivas, **sardas suaves** no nariz e bochechas rosadas, sorriso amigável e proporções idênticas às imagens de referência (`assets/gabi-*.png`).
2. **Proibições absolutas:** Nunca transformar em personagem realista, anime 2D, chibi deformado, adulta ou mascote/animal; nunca encurtar nem alisar o cabelo; nunca alterar vestido/pijama/botas fora do especificado na cena; nunca incluir móveis, texto ou marcas d'água na cena da personagem (o Quarto já existe em camada separada no app).
3. **Câmera e enquadramento:** Câmera fixa frontal (`eye-level`, 0% zoom/pan/corte), corpo inteiro dos pés à cabeça visível com ~10% de margem acima do topo da cabeça e ~5% abaixo das botas/pantufas, iluminação suave quente difusa compatível com o Quarto (`room.webp`).
4. **Pós-processamento pós-Flow (extração de transparência + sequência para `assets/anim/<pasta>/`):**
   Quando o Google Flow gerar o `.mp4` com fundo verde/preto sólido (`#00FF00` ou `#000000` uniforme sem sombra no chão):
   ```bash
   # Exemplo com fundo verde uniforme (#00B140 / chromakey + dehalo) em 360x480 ou 320x569 a 18 fps:
   mkdir -p novo-agente-hb/android/app/src/main/assets/anim/<PASTA_ALVO>
   ffmpeg -i flow_output.mp4 -vf "fps=18,colorkey=0x00FF00:0.28:0.12,despill=green,scale=320:569:force_original_aspect_ratio=decrease,pad=320:569:(ow-iw)/2:(oh-ih)/2:color=0x00000000" \
     -pix_fmt rgba "novo-agente-hb/android/app/src/main/assets/anim/<PASTA_ALVO>/f%03d.png"
   ```
   *(Para sequências `WEBP_SEQUENCE` transparentes `360x480` a `24 fps`, usar `-c:v libwebp -lossless 0 -q:v 82` com nomes `f001.webp`..`fNNN.webp`.)*

---

## Cenas Pendentes (17 Pontos Completos por Cena)

### 1. `EATING` — HB Tomando Café da Manhã (`anim/scene_comer/`)

1. **Imagem/personagem de referência:** `novo-agente-hb/android/app/src/main/assets/gabi-comer.png` (anexar como *Subject Reference / Start Frame* no Flow).
2. **Descrição física travada:** Mesma personagem HB 3D ruiva de cabelos longos ondulados, olhos castanhos grandes e sardas nas bochechas; exatamente o mesmo rosto de `gabi-comer.png`.
3. **Roupa/look travado:** Roupa da imagem `gabi-comer.png` preservada sem alteração de cor ou corte.
4. **Câmera/enquadramento:** Frontal estático de corpo inteiro (`9:16`), personagem centralizada, sem corte nas pontas do cabelo nem nos pés.
5. **Regra de fundo:** Fundo verde sólido uniforme (`#00FF00`) totalmente liso e sem sombras projetadas no chão, para remoção limpa de fundo alfa.
6. **Formato de saída:** MP4 no Flow -> convertido em `PNG_SEQUENCE` RGBA transparente (`f001.png`..`f086.png`).
7. **FPS alvo:** `18 fps`.
8. **Duração alvo:** `4,5 s` a `5,0 s` (~80–90 quadros).
9. **Resolução e razão de aspecto:** `9:16` (exportar para `320×569` px ou `768×1024` `3:4` alinhado com `gabi-comer.png`).
10. **Regra de loop:** Loop contínuo perfeito (*seamless*): último quadro volta à mesma postura e posição das mãos do quadro 1.
11. **Pós-processamento de transparência:** `colorkey` + `despill` preservando fios ondulados ruivos sem halo verde/preto.
12. **Ação quadro a quadro:** Quadros 1–25: HB segura suavemente o item do café da manhã e sorri tranquila; 26–55: leva levemente à boca / dá pequeno gole satisfeito com leve inclinação de cabeça e piscar natural; 56–85: abaixa de volta à postura inicial com expressão serena.
13. **Variações proibidas:** Não trocar a xícara/comida por outro objeto a cada quadro, não mudar o quarto ao fundo (deve ser fundo sólido isolado), não encurtar o cabelo.
14. **Prompt em Português:**  
    `Animação 3D estilizada idêntica à imagem de referência gabi-comer.png. A mesma menina ruiva de cabelos longos ondulados, olhos castanho-escuros grandes e sardas suaves segura seu café da manhã com calma, pisca naturalmente, leva suavemente à boca em um gole delicado, sorri satisfeita e retorna exatamente à postura inicial em loop perfeito. Corpo inteiro visível e centralizado, câmera frontal fixa sem movimento de câmera, iluminação suave difusa, fundo verde sólido uniforme #00FF00 sem sombras no chão, sem texto, sem objetos extras.`
15. **Prompt em Inglês:**  
    `Stylized 3D character animation locked exactly to reference image gabi-comer.png. Same young red-haired girl with long wavy copper hair, big dark-brown eyes, light freckles, and exact outfit from reference holds her breakfast gently, blinks naturally, takes a calm small bite or sip with a subtle happy head tilt, and smoothly returns to the exact starting pose for a seamless loop. Full body centered head-to-toe, locked static front camera, soft diffused lighting, pure uniform chroma-key green background #00FF00 with zero floor shadows, no room furniture, no text.`
16. **Pasta e nome de integração:** `novo-agente-hb/android/app/src/main/assets/anim/scene_comer/f001.png` (detectado automaticamente por `HBStateResolver.routineForHour` das 06h às 10h).
17. **Critério de QA visual antes de aceitar:** Verificar `f001.png` e `f045.png` sobre `room.webp` sem halo verde no cabelo; confirmar zero troca de rosto/roupa e transição suave do último quadro para `f001.png`.

---

### 2. `EATING_REACTION` — Reação ao Botão "Alimentar" (`anim/scene_comendo_reacao/`)

1. **Referência:** `assets/gabi-comer.png`.
2. **Descrição física travada:** Mesma HB 3D ruiva ondulada, olhos castanhos, sardas nas bochechas.
3. **Roupa/look travado:** Idêntico a `gabi-comer.png`.
4. **Câmera:** Frontal fixa corpo inteiro (`9:16`), centralizada.
5. **Fundo:** Chroma-key sólido `#00FF00` liso sem chão/móveis.
6. **Formato:** MP4 -> `PNG_SEQUENCE` RGBA (`f001.png`..`f054.png`).
7. **FPS:** `18 fps`.
8. **Duração:** `3,0 s` (54 quadros, exatamente igual a `HBStateResolver.EATING_DURATION_MS = 3000L`).
9. **Resolução / Razão:** `9:16` (`320×569` px RGBA).
10. **Loop:** Não (`loop = false`; retorna para a rotina/look anterior após 3 s).
11. **Transparência:** RGBA sem franjas no contorno das mãos e cabelos.
12. **Ação quadro a quadro:** 0–18: brilho alegre nos olhos e pequeno movimento entusiasmado ao receber o lanche; 19–38: dá mordida/gole feliz com sorriso amplo e leve balanço dos ombros; 39–54: acena suavemente em agradecimento voltando à posição neutra.
13. **Proibições:** Sem corações/confetes gerados por cima do rosto; sem mudança de vestido; sem mudança de rosto.
14. **Prompt PT-BR:**  
    `Animação 3D estilizada idêntica à referência gabi-comer.png. Mesma personagem ruiva de cabelos longos ondulados e sardas reage com alegria genuína ao ganhar seu lanche, abre sorriso animado, saboreia rapidamente com leve aceno satisfeito e encerra voltando à postura neutra em 3 segundos. Corpo inteiro centralizado, câmera estática frontal, fundo verde sólido uniforme #00FF00 sem sombras ou cenário.`
15. **Prompt EN:**  
    `Stylized 3D character animation strictly matching reference gabi-comer.png. Same red-haired girl with long wavy hair and light freckles reacts happily upon receiving food, smiles warmly, enjoys a quick bite with gentle joyful shoulder bounce, and settles back to neutral pose within 3 seconds. Full body centered, static front camera, uniform chroma-key green #00FF00 background, no props outside reference, no floor shadow, no text.`
16. **Pasta de integração:** `novo-agente-hb/android/app/src/main/assets/anim/scene_comendo_reacao/f001.png`.
17. **QA:** Confirmar duração ~3,0 s e retorno limpo para a cena anterior sem salto de escala.

---

### 3. `READING` — HB Lendo Livro em Vestido Lilás (`anim/scene_ler/`)

1. **Referência:** `assets/gabi-ler.png`.
2. **Descrição física travada:** Mesma HB 3D ruiva ondulada, olhos castanhos, sardas suaves.
3. **Roupa/look travado:** Vestido lilás sem mangas com livro lilás nas mãos e botas pretas de `gabi-ler.png`.
4. **Câmera:** Frontal fixa corpo inteiro (`9:16`).
5. **Fundo:** Chroma-key sólido `#00FF00` liso.
6. **Formato:** MP4 -> `PNG_SEQUENCE` RGBA (`f001.png`..`f090.png`).
7. **FPS:** `18 fps`.
8. **Duração:** `5,0 s` (~90 quadros).
9. **Resolução / Razão:** `9:16` (`320×569` px).
10. **Loop:** Sim (`seamless`).
11. **Transparência:** RGBA limpa ao redor do livro, mãos, vestido lilás e cabelo.
12. **Ação quadro a quadro:** Quadros 1–30: olha concentrada para o livro lilás aberto com leve movimento ocular; 31–60: vira suavemente uma página com a mão direita e sorri de canto de boca; 61–90: retoma posição inicial de leitura em loop.
13. **Proibições:** Não alterar cor do vestido lilás para azul/rosa; não trocar o livro por tablet ou celular.
14. **Prompt PT-BR:**  
    `Animação 3D estilizada idêntica à referência gabi-ler.png. A mesma menina ruiva de cabelos longos ondulados usando vestido lilás e botas pretas segura o livro lilás aberto, move suavemente os olhos lendo, vira uma página com delicadeza, pisca naturalmente e retorna exatamente à postura inicial em loop contínuo. Corpo inteiro centralizado, câmera fixa frontal, fundo verde sólido #00FF00 sem sombras ou cenário.`
15. **Prompt EN:**  
    `Stylized 3D animation locked to reference image gabi-ler.png. Same red-haired girl with long wavy hair wearing her lilac dress and black ankle boots holds her open lilac book, gently scans the pages, turns one page smoothly with her hand, blinks naturally, and returns seamlessly to the starting pose. Full body centered, static front camera, pure chroma-key green background #00FF00, zero shadows, no room background.`
16. **Pasta de integração:** `novo-agente-hb/android/app/src/main/assets/anim/scene_ler/f001.png`.
17. **QA:** Verificar continuidade das páginas do livro entre último e primeiro quadro e fidelidade exata do tom lilás.

---

### 4. `SLEEPING` — HB em Pijama Azul Estrelado (`anim/scene_dormir/`)

1. **Referência:** `assets/gabi-dormir.png`.
2. **Descrição física travada:** Mesma HB 3D ruiva ondulada e sardas, expressão sonolenta serena.
3. **Roupa/look travado:** Pijama azul estampado com estrelas, pantufas e travesseiro de `gabi-dormir.png`.
4. **Câmera:** Frontal fixa corpo inteiro (`9:16`).
5. **Fundo:** Chroma-key sólido `#00FF00` liso.
6. **Formato:** MP4 -> `PNG_SEQUENCE` RGBA (`f001.png`..`f090.png`).
7. **FPS:** `18 fps`.
8. **Duração:** `5,0 s` (~90 quadros).
9. **Resolução / Razão:** `9:16` (`320×569` px).
10. **Loop:** Sim (`seamless`).
11. **Transparência:** RGBA limpa no cabelo ruivo solto e contorno do travesseiro.
12. **Ação quadro a quadro:** Respiração lenta profunda nos ombros, olhos quase fechados ou piscando devagar, leve inclinação aconchegada no travesseiro e retorno suave à postura 1.
13. **Proibições:** Sem cama gerada ao fundo; sem remoção do travesseiro; sem letras "Zzz" desenhadas no ar.
14. **Prompt PT-BR:**  
    `Animação 3D estilizada idêntica à referência gabi-dormir.png. Mesma menina ruiva de cabelos longos ondulados vestindo o pijama azul com estrelas, pantufas e segurando seu travesseiro. Ela boceja ou fecha os olhos sonolenta devagar, respira tranquilamente abraçada ao travesseiro e volta exatamente à postura inicial em loop suave. Corpo inteiro, câmera fixa, fundo verde sólido #00FF00 sem móveis ou texto.`
15. **Prompt EN:**  
    `Stylized 3D animation strictly locked to gabi-dormir.png. Same red-haired girl with long wavy hair wearing the blue star-patterned pajama and slippers while hugging her pillow. Gentle sleepy breathing motion, slow drowsy blink, subtle head rest toward the pillow, seamless loop back to frame 1. Full body centered, static front camera, solid #00FF00 background, no bed, no floating text.`
16. **Pasta de integração:** `novo-agente-hb/android/app/src/main/assets/anim/scene_dormir/f001.png`.
17. **QA:** Conferir que estrelas do pijama não cintilam com artefatos entre quadros.

---

### 5. `MUSIC_REACTION` — Curtindo Música / Spotify (`anim/scene_music_reaction/`)

1. **Referência:** `assets/gabi-idle.png`.
2. **Descrição física travada:** Mesma HB 3D ruiva ondulada, sardas, vestido vermelho e botas pretas de `gabi-idle.png`.
3. **Roupa/look travado:** Vestido vermelho sem mangas e botas pretas (nota: se outro look do guarda-roupa estiver ativo, `HBStateResolver` preserva o look ativo com badge musical).
4. **Câmera:** Frontal fixa corpo inteiro (`9:16`).
5. **Fundo:** Chroma-key sólido `#00FF00` liso.
6. **Formato:** MP4 -> `PNG_SEQUENCE` RGBA (`f001.png`..`f072.png`).
7. **FPS:** `18 fps`.
8. **Duração:** `4,0 s` (72 quadros).
9. **Resolução / Razão:** `9:16` (`320×569` px).
10. **Loop:** Sim (`seamless`).
11. **Transparência:** RGBA limpa para compor sobre qualquer fundo do Quarto.
12. **Ação quadro a quadro:** Balanço leve e rítmico da cabeça e dos ombros no ritmo da música, leve passo discreto no lugar com sorriso alegre, retornando perfeitamente à posição inicial.
13. **Proibições:** Sem fones de ouvido não aprovados; sem notas musicais pintadas (o app já anima a caixinha Spotify na Camada 3).
14. **Prompt PT-BR:**  
    `Animação 3D estilizada idêntica à referência gabi-idle.png. Mesma menina ruiva de cabelos longos ondulados em vestido vermelho e botas pretas balança suavemente cabeça e ombros curtindo uma música calma com sorriso animado e balanço discreto do vestido em loop perfeito. Corpo inteiro centralizado, câmera fixa frontal, fundo verde sólido #00FF00 sem fones e sem notas desenhadas.`
15. **Prompt EN:**  
    `Stylized 3D animation locked to gabi-idle.png. Same red-haired girl with long wavy hair in her sleeveless red dress and black boots sways her head and shoulders gently to music with a cheerful smile and subtle dress motion, looping seamlessly back to frame 1. Full body centered, static front camera, uniform #00FF00 background, no headphones, no drawn music icons.`
16. **Pasta de integração:** `novo-agente-hb/android/app/src/main/assets/anim/scene_music_reaction/f001.png`.
17. **QA:** Confirmar que pés mantêm contato firme no mesmo plano vertical do Quarto sem deslizar (*foot sliding*).

---

### 6. `TAP_REACTION` — Reação ao Toque no Quarto (`anim/scene_tap_reaction/`)

1. **Referência:** `assets/gabi-talking.png` + `assets/gabi-idle.png`.
2. **Descrição física travada:** Mesma HB 3D ruiva ondulada, olhos castanho-escuros e sardas.
3. **Roupa/look travado:** Vestido vermelho sem mangas e botas pretas.
4. **Câmera:** Frontal fixa corpo inteiro (`9:16`).
5. **Fundo:** Chroma-key sólido `#00FF00` liso.
6. **Formato:** MP4 -> `PNG_SEQUENCE` RGBA (`f001.png`..`f036.png`).
7. **FPS:** `18 fps` (ou `24 fps` · 43 quadros).
8. **Duração:** `1,8 s` (`HBStateResolver.TAP_REACTION_DURATION_MS = 1800L`).
9. **Resolução / Razão:** `9:16` (`320×569` px).
10. **Loop:** Não (`loop = false`, retorna à rotina/look após 1,8 s).
11. **Transparência:** RGBA limpa.
12. **Ação quadro a quadro:** Quadros 1–12: surpresa gentil ao ser tocada com pequeno aceno com a mão; 13–26: sorriso caloroso e piscadela amigável; 27–36: abaixa a mão de volta à postura neutra.
13. **Proibições:** Zero mudança de look; sem balões de texto dentro do vídeo (o app renderiza o balão no Compose).
14. **Prompt PT-BR:**  
    `Animação 3D estilizada idêntica à gabi-idle.png. A mesma menina ruiva em vestido vermelho e botas pretas reage a um toque amigável olhando para a câmera, dá um aceno curto com a mão direita e um sorriso acolhedor, voltando à postura parada em 1,8 segundos. Corpo inteiro, câmera fixa, fundo verde sólido #00FF00 sem balões de fala.`
15. **Prompt EN:**  
    `Stylized 3D animation locked to gabi-idle.png. Same red-haired girl in her red dress and black boots reacts warmly to a gentle tap, looks toward camera, gives a brief friendly hand wave and bright smile, then settles back to neutral pose within 1.8 seconds. Full body centered, static front camera, solid #00FF00 background, no speech bubbles.`
16. **Pasta de integração:** `novo-agente-hb/android/app/src/main/assets/anim/scene_tap_reaction/f001.png` (até então o app usa `avatar/talking/` 353 quadros para reação animada real).
17. **QA:** Checar contorno dos dedos no aceno sem *ghosting*.

---

### 7. `SUCCESS` — Comemoração de Tarefa Concluída (`anim/scene_success/`)

1. **Referência:** `assets/gabi-idle.png`.
2. **Descrição física travada:** Mesma HB 3D ruiva ondulada e sardas.
3. **Roupa/look travado:** Vestido vermelho sem mangas e botas pretas.
4. **Câmera:** Frontal fixa corpo inteiro (`9:16`).
5. **Fundo:** Chroma-key sólido `#00FF00`.
6. **Formato:** MP4 -> `PNG_SEQUENCE` RGBA (`f001.png`..`f048.png`).
7. **FPS:** `18 fps`.
8. **Duração:** `2,6 s` (`HBStateResolver.SUCCESS_REACTION_DURATION_MS = 2600L`).
9. **Resolução / Razão:** `9:16` (`320×569` px).
10. **Loop:** Não (`loop = false`).
11. **Transparência:** RGBA limpa.
12. **Ação quadro a quadro:** Comemora discretamente erguendo as duas mãos em pequeno gesto de vitória/aplauso curto, sorri orgulhosa e retorna à postura base.
13. **Proibições:** Sem confetes, troféus ou ícones de check desenhados no vídeo.
14. **Prompt PT-BR:**  
    `Animação 3D estilizada idêntica à gabi-idle.png. A mesma menina ruiva em vestido vermelho e botas pretas comemora missão concluída com sorriso radiante, pequeno gesto positivo erguendo brevemente as mãos e retornando à pose neutra em 2,6 segundos. Corpo inteiro, câmera fixa frontal, fundo verde sólido #00FF00 sem efeitos ou texto.`
15. **Prompt EN:**  
    `Stylized 3D animation locked to gabi-idle.png. Same red-haired girl in sleeveless red dress and black boots celebrates a completed task with a proud smile, brief small celebratory gesture with both hands, and returns cleanly to neutral standing pose within 2.6 seconds. Full body centered, static front camera, solid #00FF00 background, no confetti or overlays.`
16. **Pasta de integração:** `novo-agente-hb/android/app/src/main/assets/anim/scene_success/f001.png`.
17. **QA:** Verificar retorno limpo à postura base ao final dos 2,6 s.

---

### 8. `ERROR` — Sinalização Honesta de Erro (`anim/scene_error/`)

1. **Referência:** `assets/gabi-idle.png`.
2. **Descrição física travada:** Mesma HB 3D ruiva ondulada e sardas.
3. **Roupa/look travado:** Vestido vermelho sem mangas e botas pretas.
4. **Câmera:** Frontal fixa corpo inteiro (`9:16`).
5. **Fundo:** Chroma-key sólido `#00FF00`.
6. **Formato:** MP4 -> `PNG_SEQUENCE` RGBA (`f001.png`..`f060.png`).
7. **FPS:** `18 fps`.
8. **Duração:** `3,4 s` (`HBStateResolver.ERROR_REACTION_DURATION_MS = 3400L`).
9. **Resolução / Razão:** `9:16` (`320×569` px).
10. **Loop:** Não (`loop = false`).
11. **Transparência:** RGBA limpa.
12. **Ação quadro a quadro:** Sobrancelhas levemente franzidas com expressão empática de "ops, algo falhou", pequeno balanço negativo suave de cabeça e gesto aberto com uma palma para cima, voltando à postura inicial.
13. **Proibições:** Sem cara assustada exagerada, lágrimas ou símbolos "X" vermelhos desenhados.
14. **Prompt PT-BR:**  
    `Animação 3D estilizada idêntica à gabi-idle.png. A mesma menina ruiva em vestido vermelho reage com expressão gentil preocupada quando algo falha, inclina levemente a cabeça com pequeno gesto explicativo de mão e volta ao repouso em 3,4 segundos. Corpo inteiro, câmera fixa, fundo verde #00FF00 sem ícones ou texto.`
15. **Prompt EN:**  
    `Stylized 3D animation locked to gabi-idle.png. Same red-haired girl in red dress and black boots shows gentle empathetic concern for an error, slightly tilts head with one subtle open-palm gesture and calm recovery back to standing pose within 3.4 seconds. Full body centered, static front camera, solid #00FF00 background, no icons or text.`
16. **Pasta de integração:** `novo-agente-hb/android/app/src/main/assets/anim/scene_error/f001.png`.
17. **QA:** Garantir expressão amigável (não caricata) e volta suave à pose inicial.

---

### 9. `CANCELLED` — Interrupção / Parar Tarefa (`anim/scene_cancelled/`)

1. **Referência:** `assets/gabi-idle.png`.
2. **Descrição física travada:** Mesma HB 3D ruiva ondulada e sardas.
3. **Roupa/look travado:** Vestido vermelho sem mangas e botas pretas.
4. **Câmera:** Frontal fixa corpo inteiro (`9:16`).
5. **Fundo:** Chroma-key sólido `#00FF00`.
6. **Formato:** MP4 -> `PNG_SEQUENCE` RGBA (`f001.png`..`f032.png`).
7. **FPS:** `18 fps`.
8. **Duração:** `1,8 s` (`HBStateResolver.CANCELLED_REACTION_DURATION_MS = 1800L`).
9. **Resolução / Razão:** `9:16` (`320×569` px).
10. **Loop:** Não (`loop = false`).
11. **Transparência:** RGBA limpa.
12. **Ação quadro a quadro:** Breve gesto calmo assentindo com as mãos abaixando suavemente como "tudo bem, parei", retomando postura relaxada.
13. **Proibições:** Sem expressão irritada; sem símbolos de stop.
14. **Prompt PT-BR:**  
    `Animação 3D estilizada idêntica à gabi-idle.png. A mesma menina ruiva em vestido vermelho assente calmamente entendendo o cancelamento, baixa suavemente as mãos em gesto tranquilo e retorna à pose neutra em 1,8 segundos. Corpo inteiro, câmera estática, fundo verde #00FF00.`
15. **Prompt EN:**  
    `Stylized 3D animation locked to gabi-idle.png. Same red-haired girl in red dress and black boots nods calmly acknowledging a stop command, gently lowers both hands into a relaxed standing pose within 1.8 seconds. Full body centered, static front camera, solid #00FF00 background.`
16. **Pasta de integração:** `novo-agente-hb/android/app/src/main/assets/anim/scene_cancelled/f001.png`.
17. **QA:** Confirmar transição instantânea limpa para o look ou rotina ativa após 1,8 s.

---

### 10. `OUTFIT_CHANGE` — Transição Rápida de Troca de Roupa (`anim/scene_outfit_change/`)

1. **Referência:** `assets/gabi-idle.png`.
2. **Descrição física travada:** Mesma HB 3D ruiva ondulada e sardas.
3. **Roupa/look travado:** Vestido vermelho (transição curta; o app já troca para o look escolhido logo após os 900 ms).
4. **Câmera:** Frontal fixa corpo inteiro (`9:16`).
5. **Fundo:** Chroma-key sólido `#00FF00`.
6. **Formato:** MP4 -> `PNG_SEQUENCE` RGBA (`f001.png`..`f018.png`).
7. **FPS:** `18 fps` (ou `24 fps`, ~22 quadros).
8. **Duração:** `0,9 s` (`HBStateResolver.OUTFIT_CHANGE_DURATION_MS = 900L`).
9. **Resolução / Razão:** `9:16` (`320×569` px).
10. **Loop:** Não (`loop = false`; mantém o look escolhido após concluir).
11. **Transparência:** RGBA limpa durante todo o giro suave.
12. **Ação quadro a quadro:** Pequeno giro leve e gracioso no próprio eixo ou ajeitada alegre na saia com sorriso confiante em menos de 1 segundo, parando de frente.
13. **Proibições:** Não fazer o cabelo atravessar o corpo em *clipping*; sem brilhos mágicos que deixem artefatos no corte alfa.
14. **Prompt PT-BR:**  
    `Animação 3D estilizada idêntica à gabi-idle.png. A mesma menina ruiva em vestido vermelho dá uma pequena ajeitada alegre no look com sorriso confiante em 0,9 segundo, terminando perfeitamente de frente. Corpo inteiro, câmera estática, fundo verde #00FF00 sem brilhos ou partículas.`
15. **Prompt EN:**  
    `Stylized 3D animation locked to gabi-idle.png. Same red-haired girl in sleeveless red dress performs a quick cheerful outfit adjustment and gentle confident pose turn within 0.9 seconds, finishing squarely facing camera. Full body centered, static front camera, solid #00FF00 background, no sparkles.`
16. **Pasta de integração:** `novo-agente-hb/android/app/src/main/assets/anim/scene_outfit_change/f001.png`.
17. **QA:** Confirmar que no quadro final a HB termina alinhada na mesma posição central dos looks `gabi-*.png`.

---

### 11–13. Looks Extras Pendentes do Guarda-Roupa (`moletom_preto`, `look_festa`, `look_pijama_passeio`)

Estes 3 itens já estão catalogados como `ASSET_PENDING` em `HBWardrobeCatalog` (`HBCharacterModel.kt`) e aparecerão liberáveis somente quando suas imagens transparentes (`768×1024` PNG RGBA8) e opcionais sequências animadas forem adicionadas:

| ID no Guarda-Roupa | Pose RGBA (`768×1024`) Alvo | Pasta Animada Alvo (`320×569`, 18 fps) | Prompt Resumido PT-BR / EN (Travado em `gabi-idle.png`) |
| :--- | :--- | :--- | :--- |
| `moletom_preto` | `assets/gabi-look-moletom.png` | `assets/anim/scene_outfit_moletom_preto/` | **PT:** Mesma HB 3D ruiva ondulada de `gabi-idle.png` vestindo moletom confortável preto moderno e calça jogger preta com tênis casual escuro, pose tranquila sorrindo, fundo verde `#00FF00`. **EN:** Exact same red-haired girl from `gabi-idle.png` wearing cozy black hoodie, black jogger pants, and dark sneakers, relaxed pose, solid `#00FF00`. |
| `look_festa` | `assets/gabi-look-festa.png` | `assets/anim/scene_outfit_look_festa/` | **PT:** Mesma HB 3D ruiva ondulada de `gabi-idle.png` usando vestido festivo elegante dourado/bordô discreto e sapatos pretos, sorriso alegre, corpo inteiro, fundo `#00FF00`. **EN:** Exact same red-haired girl from `gabi-idle.png` in tasteful festive burgundy-gold dress and black shoes, cheerful pose, full body, solid `#00FF00`. |
| `look_pijama_passeio` | `assets/gabi-look-pijama.png` | `assets/anim/scene_outfit_look_pijama_passeio/` | **PT:** Mesma HB 3D ruiva ondulada de `gabi-idle.png` usando conjunto pijama de algodão confortável pastel com meias fofas, sorriso leve, fundo `#00FF00`. **EN:** Exact same red-haired girl from `gabi-idle.png` wearing comfy pastel cotton loungewear pajama set with cozy socks, gentle smile, solid `#00FF00`. |

*(Para cada um dos 3 looks extras, aplicar os mesmos 17 parâmetros da seção global: `3:4` `768×1024` RGBA8 para pose e `9:16` `320×569` `18 fps` `4,5 s` `loop=true` para sequência animada).*
