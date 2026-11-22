# RELATORIO_VISUAL_R1_4_1 — Inspeção Visual Real, Paleta, Proporções e Contraste

**Projeto:** `NOVO_AGENTE_HB` (`0.2.5-r1.4.1`, `versionCode = 7`)  
**Escopo visual avaliado:** Ativos PNG/WebP/MP4 reais do pacote, quadros extraídos do Google Drive `Avatar`, medalhões regenerados, fundo atmosférico R1.4.1, tokens `Theme.kt`/`colors.xml` e proporções Compose de todas as telas principais.  
**Restrição honesta declarada:** Nenhuma captura de tela de app compilado em emulador/aparelho existe porque o sandbox não possui JDK 17 nem Android SDK (`APK_NOT_BUILT`). Toda evidência visual aqui descrita vem de inspeção direta dos arquivos de imagem/vídeo reais renderizados com `PIL`/`numpy`/`av`.

---

## 1. Correção de Paleta: Equilíbrio Cobre, Carmesim e Dourado vs. Peso Azul Anterior

Na R1.4, as superfícies `NightBg (#0B1219)`, `SurfaceDark (#131D28)` e `SurfaceMid (#1A2734)` combinadas ao backdrop azul-petróleo deixavam a interface fria e azulada demais frente à identidade da HB (`bible.png`: cabelo cobre `#C7541E`, vestido carmesim `#C62828`, pele `#F6C7A7`, sardas `#E6A07A`, dourado `#FFCF8C`).

Em R1.4.1, a paleta foi ajustada de forma cirúrgica (sem refazer o design system):

| Token | R1.4 (Azul-noturno) | R1.4.1 (Obsidiana quente cobre/carmesim) | Impacto Visual |
| --- | --- | --- | --- |
| `NightBg` | `#0B1219` | `#120F11` | Fundo noturno neutro quente que valoriza o ruivo e o carmesim sem puxar para azul-marinho |
| `SurfaceDark` | `#131D28` | `#1B1618` | Cartões e barras com leitura quente e alto contraste para texto `#F5F1ED` |
| `SurfaceMid` | `#1A2734` | `#251E20` | Inputs e estados internos harmônicos com o vestido e cabelo da HB |
| `SurfaceWarm` | `#221D20` | `#2D201E` | Banners e alertas sutis alinhados com o cobre `#D96830` |
| `Hairline` | `#2B3B4B` | `#3E302E` | Bordas sutis de `1.dp` sem contorno azulado |
| `TextSecondary` | `#9FB1BC` | `#B5A9A5` | Subtítulos neutros quentes legíveis |
| `theme/gabitama_home_backdrop.png` | Aura azulada (`768×1376`) | Aura obsidiana cobre-carmesim-dourado (`768×1376`, `766 KB`) | Inspecionada visualmente: zero mancha azul ou verde-oliva; brilho cobre centralizado na zona do palco hero (`y ≈ 0.28`) |

---

## 2. Comparação Visual Direta dos 3 Medalhões (`avatar_circle.png`, `256×256` RGBA)

Na R1.4, os medalhões de `pre_adolescent` e `adolescent` usavam recortes fechados apenas de rosto/ombros, tornando as duas fases quase indistinguíveis em `32.dp`–`56.dp`. Em R1.4.1, os 3 arquivos foram regenerados a partir dos quadros reais verificados com enquadramentos diferenciados e bordas sutis por fase:

1. **`hb-stages/young_child/avatar_circle.png` (`88.886 B`):** extraído de `gabi-idle.png` sobre fundo escuro `#1B1618` com brilho cobre suave, aro dourado `#FFCF8C` e proporção infantil clássica de cabeça maior.
2. **`hb-stages/pre_adolescent/avatar_circle.png` (`92.531 B`):** extraído do quadro real `76` de `idade1.mp4` (`720×1280`), mostrando a mão direita erguida em gesto suave ao lado do torso e o ursinho na prateleira do quarto ao fundo, com aro âmbar `#E5A952`.
3. **`hb-stages/adolescent/avatar_circle.png` (`87.777 B`):** extraído do quadro real `56` de `idade2.mp4` (`720×1280`), em enquadramento de três quartos mostrando pescoço/tronco mais longilíneos, cintura marcada do vestido vermelho, mão estendida e cortinas rosadas do quarto, com aro cobre `#D96830`.

---

## 3. Palco Hero da Home, Quarto, Computador e Ergonomia Responsiva

| Área / Tela | Dimensões e Composição Verificadas em Código | Resultado da Revisão |
| --- | --- | --- |
| **Palco Hero da Home (`HomeScreen.kt`)** | Container `232.dp` · halo `220.dp` · `HBStageCharacter` `204.dp` (`CircleShape` `190.dp` com `StageLoopVideo` vivo para `idade1`/`idade2`; sequência transparente `avatar/idle` / estado ativo para `YOUNG_CHILD`) + pílula flutuante no rodapé do palco + legenda honesta abaixo da pílula. | Personagem em destaque com movimento real em **todas** as fases; controles (`Falar`, `Live`, `Quarto`) permanecem fora e abaixo da área da HB sem sobreposição. |
| **Barra Inferior (`GabiBottomBar.kt`)** | Cápsula `max 432.dp`, `50.dp` altura dos itens, 5 destinos (`Início`, `Avisos`, `Gabi` `32.dp` `GabiFaceAvatar`, `Quarto`, `Ajustes`) + `navigationBarsPadding()`. | Equilibrada para telas compactas e grandes sem cortar rótulos ou colidir com barra de gestos. |
| **Quarto da Gabi (`QuartoGabiScreen.kt`)** | Camada 0 (`fillMaxSize()` com `view_texture_player.xml` não clicável) · Camada 1 (`fillMaxWidth(0.84f)` × `fillMaxHeight(0.75f)` acima dos botões inferiores) · Camada 2 (status HUD + 4 discos `40.dp` + pílula `Conversar`) · Camada 3 (`SpotifySpeakerBadge`, `Parar`, `Alimentar`) · Camada 4 (`RoomIntroVideo` pulável). | Sem caixas retangulares sobre outros fundos; Z-order protegido contra interceptação de toque pelo `TextureView`. |
| **Chat (`ChatScreen.kt`) & Telas Internas** | Cabeçalho `44.dp` `GabiFaceAvatar` + ações circulares `40.dp`; raiz do `ChatScreen` com `.imePadding()` + `adjustResize` no `AndroidManifest.xml`; `PermissionsScreen` com `safeDrawingPadding()`. | Teclado virtual e barras do sistema respeitados sem esconder o campo de entrada nem botões de confirmação. |
