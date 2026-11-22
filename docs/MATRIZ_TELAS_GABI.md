# MATRIZ DE TELAS E NAVEGAÇÃO GABI/HB — R1.0

Referência visual e de UX: `DaniloXPTEAM/Jarvis-Projeto@voz-latencia` (`RoomScreen.kt`, `RoomViewModel.kt`, `HomeScreen.kt`, `ChatScreen.kt`, `Theme.kt`) sincronizada via `HBCharacterController`.

## 1. Barra de Navegação Inferior (`GabiBottomBar.kt`)

| Posição | Rótulo | Ícone / Avatar | Destino (`NavRoutes.kt`) |
| --- | --- | --- | --- |
| 1 | `Início` | `Icons.Default.Home` | `Screen.Home` (`"home"`) |
| 2 | `Avisos` | `Icons.Default.Notifications` | `Screen.Feed` (`"feed"`) |
| 3 (centro) | `Gabi` | `GabiFaceAvatar` (recorte aprovado de `gabi-idle.png`) | `Screen.Chat` (`"chat"`) |
| 4 | `Habitat` | `Icons.Default.MeetingRoom` | `Screen.Habitat` (`"habitat"` -> **Quarto da Gabi**) |
| 5 | `Ajustes` | `Icons.Default.Settings` | `Screen.Settings` (`"settings"`) |

## 2. Inventário das 10 Rotas em `NavRoutes.kt` (Pós-R1.0)

| Rota (`Screen.*`) | Composable | Comportamento implementado na R1.0 | Estado |
| --- | --- | --- | --- |
| `home` (`Screen.Home`) | `ui/home/HomeScreen.kt` | Observa `hbController.state` + `turnState` + `voiceState`; exibe `HBStageCharacter` (`home_idle` em repouso ou `Speaking/Listening/Thinking/Working/Success/Error/Cancelled`); botões `Live` (Gemini Live) e `Falar` + `Parar` + `ConfirmActionBar` | `IMPLEMENTED_STATIC_VERIFIED` |
| `chat` (`Screen.Chat`) | `ui/chat/ChatScreen.kt` | Cabeçalho com `GabiFaceAvatar` + anel colorido reativo ao estado da HB + legenda sincronizada + toggle `Gemini Live` + `/diagnostico`; sem dupla submissão de transcrição de voz | `IMPLEMENTED_STATIC_VERIFIED` |
| `habitat` (`Screen.Habitat`) | `ui/habitat/QuartoGabiScreen.kt` | Cena em 5 camadas: `RoomBackgroundLayer` (`avatar/room.webp`, `room-bg.png`, `room_bg_loop.mp4`, foto galeria), HB em primeiro plano transparente (`HBStageCharacter` + respiração + rotina por hora), toque na HB reage no próprio quarto sem abrir Chat, botão `Alimentar` (override 3s), `SpotifySpeakerBadge` com notas animadas somente quando Spotify toca + checagem de permissão ao retomar tela, `WardrobeDialog` separando looks disponíveis de `Arte pendente`, `RoomEnvironmentDialog` separado e `RoomIntroVideo` persistente (`introSeen` + `Pular abertura`) | `IMPLEMENTED_STATIC_VERIFIED` |
| `feed` (`Screen.Feed`) | `ui/feed/FeedScreen.kt` | Abas `Notificações` e `Recibos` (`TaskReceiptEntity`) | `IMPLEMENTED_STATIC_VERIFIED` |
| `goals` (`Screen.Goals`) | `ui/goals/GoalsScreen.kt` | Metas + lembretes rápidos (`AlarmManager` + `BootReceiver`) | `IMPLEMENTED_STATIC_VERIFIED` |
| `library` (`Screen.Library`) | `ui/library/LibraryScreen.kt` | Seletor SAF persistente + workspace privado do agente | `IMPLEMENTED_STATIC_VERIFIED` |
| `memory` (`Screen.Memory`) | `ui/memory/MemoryScreen.kt` | Memória única Room (`MemoryStore`) | `IMPLEMENTED_STATIC_VERIFIED` |
| `computer` (`Screen.Computer`) | `ui/computer/ComputerScreen.kt` | Classificação honesta por `RuntimeSupportTier`, card explícito `NOT_IMPLEMENTED` (Linux PRoot/Tela Genérica), execução real de `LOCAL_SKILL` no workspace com `TaskReceipt`, handshake MCP JSON-RPC 2.0 real (`tools/list`) e execução opt-in via `PolicyGate` | `IMPLEMENTED_STATIC_VERIFIED` |
| `settings` (`Screen.Settings`) | `ui/settings/SettingsScreen.kt` | 8 chaves BYOK no `SecureKeyVault` AES256-GCM (Gemini, Groq, OpenRouter, DeepSeek, Anthropic, ElevenLabs, Spotify Client ID & Secret), descoberta real `/v1beta/models` para `bidiGenerateContent`, motor de voz `gabi_24l`, trava paga, exportação e `/diagnostico` | `IMPLEMENTED_STATIC_VERIFIED` |
| `permissions` (`Screen.Permissions`) | `ui/permissions/PermissionsScreen.kt` | Layout com `safeDrawingPadding()` + status granular das permissões | `IMPLEMENTED_STATIC_VERIFIED` |
