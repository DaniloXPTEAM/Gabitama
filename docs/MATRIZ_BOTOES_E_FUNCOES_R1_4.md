# MATRIZ_BOTOES_E_FUNCOES_R1_4 — Preservação Funcional Completa (10 Telas + Barra + Widget)

**Versão:** `0.2.4-r1.4` (`versionCode = 6`)  
**Objetivo:** Comprovar que **100%** dos botões, inputs, fluxos de voz, diálogos, permissões, armazenamento SAF, Skills, MCP, metas, lembretes e controles da HB presentes em R1.3 foram mantidos na remodelação visual R1.4.

---

## 1. Navegação Global (`GabiBottomBar` + `MainActivity`)

| Elemento | Ação / Destino | Estado em R1.4 |
| :--- | :--- | :--- |
| Cápsula flutuante `GabiBottomBar` | 5 itens fixos no rodapé (`Início`, `Chat`, **Medalhão central HB → Quarto**, `Feed`, `Metas`) | **Preservado + sincronizado** via `LocalHBStage` (`avatar_circle.png` muda conforme fase ativa) |
| `MainActivity` + `CompositionLocalProvider(LocalHBStage)` | Propaga fase ativa da HB para barra inferior sem acoplar rotas secundárias | **Adicionado em R1.4** |
| Widget Home (`HomeWidgetProvider`) + Quick Tile (`GabiTileService`) | Abre `MainActivity` com escuta automática / acesso rápido | **Preservado** (`colors.xml` e `widget_bg.xml` atualizados para tokens R1.4) |

---

## 2. Matriz por Tela (Todas as 10 Rotas em `NavRoutes.kt`)

| Tela / Rota | Botões, Controles e Diálogos Preservados / Evoluídos | ViewModel / Controller |
| :--- | :--- | :--- |
| **`HomeScreen`** (`home`) | Brasão `gabitama_brand_crest.png`; Botão **Quarto da Gabi** (`MeetingRoom`); Botão **Ajustes** (`Settings`); Palco Hero clicável (`navigate(Habitat)`) com medalhão circular automático em `PRE_ADOLESCENT`/`ADOLESCENT`; Pílula flutuante fase + transcrição; Botões **Falar** (`onMicTap` + permissão `RECORD_AUDIO`), **Live** (`onLiveTap`), **Quarto**; `ActiveExecutionBanner` (**Parar**); `ConfirmActionBar` (Confirmar/Cancelar); `ProviderFailureCard` + `ProviderFailureDetailDialog`; Cards **Clima** (`refreshWeather`), **Metas**, **Memória**, **Arquivos**, **Computador**; Chips rápidos (`Clima`, `Notificações`, `Agenda`, `/diagnostico`); Campo **Peça algo à Gabi…** + **Enviar**; Conversa recente + **Chat completo →**. | `HomeViewModel` |
| **`QuartoGabiScreen`** (`habitat`) | Camada 0 `RoomBackgroundLayer` / `StageLoopVideo` em tela cheia; Camada 1 `HBStageCharacter` clicável (`onCharacterTapped`) com balão animado; Top HUD: badge de fase + `careLastAt` + nota honesta `pendingAsset` + botões circulares **Fase da HB** (`StageSelectorDialog` + botão **Ver Bíblia & Spritesheet**), **Guarda-roupa** (`WardrobeDialog`: Rotina + 7 looks), **Ambiente do Quarto** (`RoomEnvironmentDialog`: 4 fundos + atalho **Voltar p/ Infância** quando outra fase está ativa + **Rever abertura**), **Computador**, **Conversar**; Bottom HUD: `notice`, `ConfirmActionBar`, `SpotifySpeakerBadge` (com atalho p/ Permissões), **Parar** (`stopAll`), **Alimentar** (`feed`); `RoomIntroVideo` pulável; `CharacterBibleDialog`. | `HabitatViewModel` |
| **`ChatScreen`** (`chat`) | `GabiTopBar`: Voltar, Medalhão `GabiFaceAvatar(stage = hbState.stage)` clicável (`Habitat`), botão **Gemini Live** (`toggleLiveSession`), chip **`/diag`**, botão **Limpar conversa**; `ActiveExecutionBanner`, `ConfirmActionBar`, `ProviderFailureCard` + `ProviderFailureDetailDialog`; `GabiEmptyStateCard` quando vazio; Lista reversa de mensagens com tag provedor/modelo; Compositor em cápsula (`Mic`, campo texto, `Send`). | `ChatViewModel` |
| **`FeedScreen`** (`feed`) | `GabiTopBar` (Voltar + Limpar notificações); Pílula segmentada **Notificações** / **Recibos**; `GabiEmptyStateCard` quando vazio; Cards de auditoria (`verifiedOutcome`, `durationMs`, `status`). | `FeedViewModel` |
| **`GoalsScreen`** (`goals`) | `GabiTopBar` (Voltar); Formulário **Nova meta** (`addGoal`); `GabiEmptyStateCard` se vazio; Cards de meta com **Avançar** (`bumpProgress`), **Excluir** (`deleteGoal`) e barra de progresso; Formulário **Lembretes agendados (`+15m`)** (`addReminderInMinutes`); Lista com status de alarme. | `GoalsViewModel` |
| **`LibraryScreen`** (`library`) | `GabiTopBar` (Voltar navega breadcrumb SAF ou `popBackStack`, **`+ Pasta`**, **SAF** `OpenDocumentTree`); Cartão de escopo autorizado; Formulário **Criar nota no Workspace** (`createNote`); `GabiEmptyStateCard` se vazio; Lista de arquivos/pastas com **Abrir**, **Compartilhar** (`shareEntry`), **Renomear** (`renameSafEntry`), **Remover** (`deleteEntry`); Diálogos de Prévia, Nova Pasta SAF e Renomear. | `LibraryViewModel` |
| **`MemoryScreen`** (`memory`) | `GabiTopBar` (Voltar + Apagar toda a memória c/ diálogo de confirmação); Formulário **Registrar novo fato** (`addMemory`); `GabiEmptyStateCard` se vazio; Cards de memória com origem, confiança e **Remover** (`deleteMemory`). | `MemoryViewModel` |
| **`ComputerScreen`** (`computer`) | `GabiTopBar` (Voltar + **Diagnóstico**); `ActiveExecutionBanner`, `ConfirmActionBar`; Palco hero coerente com a fase ativa (`scene_computador` para Infância ou medalhão da fase em `PRE_ADOLESCENT`/`ADOLESCENT`); `statusNotice`; `mcpLastLog`; Resumo de `CapabilityRegistry`; Recursos não implementados; Formulário **Adicionar Skill local** + switch `workspace_write_verify`; Formulário **Registrar servidor MCP** + switch LAN; Lista com `Switch` opt-in, remover, executar Skill, testar Handshake MCP e executar tools MCP. | `ComputerViewModel` |
| **`SettingsScreen`** (`settings`) | `GabiTopBar` (Voltar + **Permissões**); Cartão **Identidade Visual GabiTama** + botão **Abrir Bíblia** (`CharacterBibleDialog`); Seleção de Provedor Principal; 8 campos **Chaves isoladas BYOK** (Gemini, Groq, OpenRouter, DeepSeek, Anthropic, ElevenLabs, Spotify ID/Secret); Modelos ativos + **Verificar modelos Live**; Motor de Voz e Prosódia (`gabi_24l`); Switch **Liberar chamadas pagas sem confirmação**; Cidade padrão; **Exportar JSON** + **Diagnóstico** + diálogos. | `SettingsViewModel` |
| **`PermissionsScreen`** (`permissions`) | `safeDrawingPadding()`; Cabeçalho com brasão `gabitama_brand_crest.png`; 6 linhas de permissão (`Microfone`, `Contatos e Ligações`, `Agenda`, `Leitor de Notificações`, `Acessibilidade`, `Alarmes exatos`); Botões **Solicitar padrão** e **Continuar**. | `PermissionsViewModel` |
