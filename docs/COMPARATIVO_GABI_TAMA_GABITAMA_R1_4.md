# COMPARATIVO VISUAL E ESTRUTURAL — Gabi · voz-latencia · HGabi · Tama V2 · GabiTama R1.3 → R1.4

**Projeto:** `NOVO_AGENTE_HB` (GabiTama R1.4)  
**Data (UTC):** 2026-11-21  
**Escopo:** Gate D0 — Comparação real executada antes da reestruturação visual R1.4, com medições diretas nos repositórios, branches e mídias disponíveis.

---

## 1. Disponibilidade e Proveniência das 5 Referências

| Referência | Origem verificada | Status na verificação | Uso permitido |
|---|---|---|---|
| **Gabi original (`main`)** | `<repo-root>/android/` (`HomeScreen.kt`, `JavisBottomBar.kt`, `Theme.kt`) | Inspecionada no código real | Baseline funcional (MIT em `android/LICENSE`) |
| **Gabi `voz-latencia`** | `DaniloXPTEAM/Jarvis-Projeto@voz-latencia` (`/tmp/refs/voz_latencia/`) | Inspecionada no código real e assets | Base de mídias transparentes e Quarto (`RoomScreen.kt`) |
| **HGabi** | Busca em `gh repo list DaniloXPTEAM`, todas as branches de `Jarvis-Projeto`, `tamaaiv2`, Drive e `COPILOT_KIT_REV3` | **NÃO IDENTIFICADA** em nenhuma fonte acessível | Registrada explicitamente como ausente (sem suposições) |
| **Tama V2 (`tama-v2`)** | `DaniloXPTEAM/tama-v2@main` (`/tmp/refs/tama_v2/`, somente leitura) | Inspecionada em proporções e UX (`MuseHeader`, `MuseBottomBar`, `AvatarSize`, `MuseTones`, `MuseChrome`) | **Apenas referência funcional/visual/proporcional** — zero código ou assets GPL-3.0 copiados |
| **GabiTama R1.3** | `GabiTama_R1_3_avatares_integrados.zip` restaurado em `novo-agente-hb/` | Inspecionada integralmente + testes e assets | Base direta evoluída para R1.4 (`0.2.3-r1.3` → `0.2.4-r1.4`) |

---

## 2. Comparação Arquitetural, Proporcional e Cromática

| Dimensão | Gabi (`main`) | Gabi (`voz-latencia`) | HGabi | Tama V2 (ref. visual/ergonômica) | GabiTama R1.3 | **GabiTama R1.4 (alvo)** |
|---|---|---|---|---|---|---|
| **Presença na Home** | Orbe `220.dp` em Canvas | `AvatarAnimation` `260.dp` livre sobre cards | Não identificada | Disco circular `76.dp` (`EXTRA_LARGE`, variações `44/56/66/76dp`) | Caixa retangular escura `254.dp` com borda dourada | **Palco hero intermediário `196.dp` transparente** sem retângulo escuro (`YOUNG_CHILD`) ou **medalhão circular `168.dp`** com aro de fase (`PRE_ADOLESCENT`/`ADOLESCENT`) + halo radial real |
| **Etiqueta / Status sob personagem** | Texto direto abaixo do orbe | Chips/título acima do palco | Não identificada | `MuseNamePill` flutuante sobrepondo `10.dp` a base do disco (`44.dp` altura reservada) | Barra superior separada + badge interno ao card | **Pílula flutuante de status/fase (`10.dp` sobreposição suave)** com identidade de fase e estado de voz/tarefa |
| **Cabeçalhos secundários** | Barra simples topo | Barra horizontal com gradiente | Não identificada | `MuseTopAppBar` (`56.dp`): botão voltar circular `40.dp` + título centralizado + ações nas pontas | Barra retangular sólida ocupando largura total | **`GabiTopBar` unificada (`58.dp`)**: disco circular `40.dp` para voltar, título limpo e pílulas contextuais (sem faixa retangular pesada) |
| **Barra inferior** | 5 itens planos com foto central | 5 itens planos com foto central | Não identificada | `MuseBottomBar` pílula flutuante (`CircleShape`, `PILL_MAX_WIDTH = 420.dp`, margem `16.dp`, altura `46dp + 12dp`) | `GabiBottomBar` docked plana (`SurfaceDark 96%`) + foto `30.dp` da Gabi | **Pílula flutuante inspirada em Tama V2** (`RoundedCornerShape(30.dp)`, `widthIn(max = 432.dp)`, margem `14.dp`, borda hairline cobre/ouro), preservando 5 abas e **avatar circular `34.dp` fiel à fase selecionada** |
| **Paleta / Tema** | Azul noite escuro (`#060911`) + ciano (`#00E5FF`) | Azul noite + ciano/roxo | Não identificada | Creme quente (`#FFF8E9`) / café escuro (`#19130F`) + âmbar (`#87410B` / `#FFCA74`) | Noturno frio (`#0A0F16`) + ouro (`#FFCF8C`) + teal (`#5FB8AC`) | **Sistema unificado inspirado na `bible.png` + atmosfera noturna:** Ardósia crepuscular (`#0B1219`), cards acetinados (`#131D28`), Cobre HB (`#C7541E` / `#D96830`), Mel Dourado (`#FFCF8C`), Vermelho Assinatura (`#C62828`) e Verde-Água Crepúsculo (`#5FB8AC`) |
| **Composição no Quarto** | N/A | `fillMaxWidth(0.62f)` com `padding(bottom = 96.dp)` sobre `room.webp` | Não identificada | Tela dedicada `PetScreen` (`132.dp` disco + cards modulares) | `fillMaxWidth(0.84f) × fillMaxHeight(0.76f)` sobre `room.webp` ou `StageLoopVideo` full-screen | **Camada 0 + Camada 1 unificadas sem retângulo**, sombra de contato sob as botas, HUD superior em discos `40.dp` + pílula Conversar, rodapé Spotify + Parar + Alimentar acima da barra flutuante |
| **Continuidade de Fases** | Fase única | Fase única | Não identificada | N/A (estágios não aplicáveis aqui) | **Falha detectada na R1.3:** `PRE_ADOLESCENT`/`ADOLESCENT` voltavam para sprites infantis ao falar/escutar/pensar/trabalhar | **Continuidade estrita em R1.4:** `PRE_ADOLESCENT` (`idade1`) e `ADOLESCENT` (`idade2`) permanecem na fase selecionada em **todos** os estados interativos e no avatar circular |

---

## 3. Decisões Derivadas do Gate D0 (Arquitetura Intermediária R1.4)

1. **Não encolher a Gabi a um ícone de `76.dp`:** a identidade central do produto é a presença da personagem HB viva. Na Home, ela ocupa `196.dp` de altura transparente (`YOUNG_CHILD`) ou medalhão circular `168.dp` (`PRE_ADOLESCENT` / `ADOLESCENT`), muito mais nobre que os `76.dp` do Tama V2 e livre do caixote escuro de `254.dp` da R1.3.
2. **Adotar a elegância estrutural de Tama V2 (apenas linguagem visual, zero código/assets GPL):**
   - Pílula de navegação inferior flutuante com respiro de `navigationBarsPadding()` e borda sutil (`1.dp`).
   - Botões de ação circulares (`40–42.dp`) no topo das telas e do Quarto, substituindo barras pesadas.
   - Agrupamento claro em cartões translúcidos/acetinados com textura própria (`gabitama_card_surface.png`), espaçamento consistente (`12–16.dp`) e estados vazios ilustrados (`gabitama_empty_scene.png`).
3. **Preservar 100% da força funcional da Gabi:** voz (Gemini Live + Android TTS / perfil `gabi_24l`), 10 telas completas, Quarto com guarda-roupa/alimentação/Spotify/ambiente/fases, Skills/MCP, Memória, Metas/Lembretes, Arquivos/SAF e Configurações BYOK.
