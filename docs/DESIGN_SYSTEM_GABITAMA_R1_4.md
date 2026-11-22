# DESIGN_SYSTEM_GABITAMA_R1_4 — Sistema Visual Unificado GabiTama R1.4

**Projeto:** `NOVO_AGENTE_HB` (GabiTama `0.2.4-r1.4`, `versionCode = 6`)  
**Data (UTC):** 2026-11-21  
**Fonte oficial de identidade cromática:** `assets/bible.png` (*Bíblia da Personagem — Gabi*).

---

## 1. Identidade de Marca & Paleta Oficial (`GabiTokens`)

### 1.1 Âncoras Oficiais da Personagem HB (`bible.png`)
- **Cabelo Ruivo Acobreado:** `#C7541E`
- **Pele Acolhedora:** `#F6C7A7`
- **Sardas Suaves:** `#E6A07A`
- **Vestido Vermelho Assinatura:** `#C62828`
- **Botas Pretas / Grafite:** `#1E1E1E`

### 1.2 Tokens de Superfície, Tipografia e Acento R1.4 (`Theme.kt`)
| Token | Hex | Função na Interface |
|---|---|---|
| `NightBg` | `#0B1219` | Fundo principal noturno em todas as telas (combina profundidade e leitura confortável) |
| `SurfaceDark` | `#131D28` | Cartões principais, barra de navegação flutuante e diálogos |
| `SurfaceMid` | `#1A2734` | Cartões secundários, bolhas do usuário, chips e áreas interativas |
| `SurfaceWarm` | `#221D20` | Superfície levemente aquecida pelo cobre HB para cartões de destaque e seletores de fase |
| `Hairline` | `#2B3B4B` | Divisórias finas (`0.8.dp–1.dp`) inspiradas na leveza estrutural do Tama V2 |
| `GoldAccent` | `#FFCF8C` | Acento primário quente (foco, ícones ativos, seleções confirmadas) |
| `CopperAccent` | `#D96830` | Acento identitário ruivo-cobre derivado de `#C7541E` (fase ativa, selos da HB) |
| `AmberAccent` | `#E5A952` | Avisos operacionais, lembretes armados e estados pendentes honestos |
| `CrimsonBadge` | `#C62828` | Derivado direto do vestido oficial (`#C62828`); destaque afetivo e botão de atenção |
| `TealAccent` | `#5FB8AC` | Acento de inteligência/agente, status Live/MCP e equilíbrio frio-quente |
| `TextPrimary` | `#F3F4EF` | Texto principal alto contraste (`> 13:1` sobre `NightBg`) |
| `TextSecondary` | `#9FB1BC` | Legendas, metadados e rótulos auxiliares (`> 6.2:1` sobre `NightBg`) |
| `SuccessGreen` | `#3FB950` | Fala ativa, verificações concluídas e metas avançadas |
| `ErrorRed` | `#F2635A` | Microfone ouvindo / erro / botão Parar |
| `SpotifyGreen` | `#1DB954` | Indicador real de música no Spotify no Quarto e na Home |
| `VioletAccent` | `#B292F6` | Cartão e seção de Memória da Gabi |

---

## 2. Escala Tipográfica e Hierarquia

- **`headlineLarge` (`26.sp`, Bold):** título editorial de tela (Home, Permissões).
- **`headlineMedium` (`22.sp`, SemiBold):** cabeçalhos de destaque e diálogos.
- **`titleMedium` (`16.sp`, SemiBold):** barras de navegação superiores (`GabiTopBar`), títulos de cartões e seções.
- **`bodyMedium` (`14.5.sp`, Normal, `20.sp` entrelinha):** mensagens do Chat, descrições de metas/arquivos/skills.
- **`bodySmall` (`12.5.sp`):** sublinhas de status, metadados de provedor e instruções.
- **`labelSmall` (`11.sp`, Medium):** pílulas, badges e rótulos da barra inferior.

---

## 3. Assets Gráficos Customizados R1.4 (`assets/theme/`)

Todos os assets abaixo foram criados e calibrados para R1.4, inspecionados visualmente, otimizados para Android, integrados no Compose e registrados em SHA-256:

| Asset | Dimensão / Formato | Onde é integrado no código |
|---|---|---|
| `assets/theme/gabitama_home_backdrop.png` | `720×1280` RGB (`557 KB`) | `GabiAtmosphericBackdrop` (usado na `HomeScreen`, `PermissionsScreen` e fundo sutil das telas) |
| `assets/theme/gabitama_stage_halo.png` | `512×512` RGBA (`351 KB`, alfa radial real) | `HBHeroStage` na `HomeScreen` (halo luminoso atrás da HB transparente sem retângulo escuro) |
| `assets/theme/gabitama_card_surface.png` | `512×288` RGB (`121 KB`, contraste calibrado) | `GabiFeaturedSurface` / cartões de destaque em `HomeScreen`, `ComputerScreen`, `SettingsScreen` |
| `assets/theme/gabitama_brand_crest.png` | `256×256` RGBA (`50 KB`, recorte circular limpo) | `SettingsScreen` (card Identidade & Bíblia da HB), `PermissionsScreen` (cabeçalho de boas-vindas) e `HomeScreen` |
| `assets/theme/gabitama_empty_scene.png` | `512×512` RGB (`244 KB`) | `GabiEmptyStateCard` exibido quando `FeedScreen`, `GoalsScreen`, `MemoryScreen` ou `LibraryScreen` estão sem itens |

Além disso, os arquivos oficiais do Drive `Avatar` foram integrados:
- `assets/bible.png` (`1448×1086` RGB) — visualizável no diálogo **"Bíblia da HB"** acessível em `SettingsScreen` e no seletor de fases do Quarto.
- `assets/gabi-spritesheet.png` (`5376×1024` RGBA, 7 poses) — promovida no `HBAnimationCatalog` e inspecionável no diálogo de Bíblia/Referências.
- `assets/hb-stages/{young_child,pre_adolescent,adolescent}/avatar_circle.png` (`256×256` RGBA) — miniaturas fiéis por fase na barra inferior, Chat e palco circular.

---

## 4. Linguagem de Componentes e Navegação (Arquitetura Intermediária R1.4)

1. **`GabiBottomBar` (Pílula Flutuante Unificada):**
   - Pílula flutuante centralizada (`widthIn(max = 432.dp)`, `RoundedCornerShape(30.dp)`), preenchimento translúcido escuro (`SurfaceDark @ 94%`) + borda hairline (`1.dp` cobre-dourado `24%`) + elevação suave (`8.dp`), respeitando `navigationBarsPadding()` + `padding(bottom = 10.dp)`.
   - Destinos: **Início · Avisos · Gabi (avatar circular `34.dp` sincronizado com a fase ativa) · Quarto · Ajustes**.
   - Indicador ativo em pílula quente (`GoldAccent @ 18%` / `CopperAccent @ 26%`) — inspiração ergonômica direta em Tama V2 com identidade própria HB.
2. **`GabiTopBar` (Cabeçalho Limpo Unificado):**
   - Substitui barras retangulares cheias em `ChatScreen`, `FeedScreen`, `GoalsScreen`, `LibraryScreen`, `MemoryScreen`, `ComputerScreen` e `SettingsScreen`.
   - Botão Voltar em disco circular `40.dp` (`SurfaceMid`, borda `Hairline 1.dp`), título central/alinhado claro e ações finais em pílulas ou discos coerentes.
3. **Cartões, Chips e Campos de Entrada:**
   - Cantos padrão `18.dp` para cartões principais e `14.dp` para subcartões, sempre com borda sutil (`1.dp`) e hierarquia clara entre superfície, conteúdo e ações.
   - Botões de voz (`Falar` / `Gemini Live`) e confirmação (`ConfirmActionBar` / `ActiveExecutionBanner`) com alto contraste semântico e área mínima de toque `>= 48.dp`.
4. **Responsividade em 3 Classes de Largura:**
   - Compacto (`< 600.dp`, ex.: `360×800`, `412×915`): layout vertical fluido, grade de cartões `2 + 3` ou responsiva, sem esmagar chips nem cortar rótulos.
   - Médio (`600–839.dp`) e Expandido (`>= 840.dp`): conteúdo limitado por `widthIn(max = 720.dp)` centralizado na tela, mantendo proporções da pílula (`max 432.dp`) e legibilidade sem esticar formulários indefinidamente.
