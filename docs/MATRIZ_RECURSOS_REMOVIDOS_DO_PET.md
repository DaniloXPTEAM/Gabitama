# MATRIZ DE RECURSOS REMOVIDOS DO PET E SUBSTITUIÇÃO PELO QUARTO DA GABI — GATE 1

## 1. Comprovação de Exclusão 100% da Camada Pet / Tamagotchi

| Elemento da referência Pet antiga | Presença em `NOVO_AGENTE_HB` | Onde foi substituído / evidência |
| --- | --- | --- |
| Dragão / criatura / avatar de mascote | **0% — REMOVIDO** | Nenhum arquivo, asset, classe ou string em `android/`; auditado por `audit_ip_and_no_pet.py` (`test_zero_pet_tamagotchi_implementation_or_assets`) |
| Ovo, eclosão (`hatching`) e 6 estágios evolutivos | **0% — REMOVIDO** | Substituídos pelo ciclo de estados reais da Gabi (`AvatarState`: `IDLE`, `LISTENING`, `THINKING`, `TALKING`) em `GabiAvatarViews.kt` |
| Morte por fome (72h), luto e memorial | **0% — REMOVIDO** | Inexistente em `Entities.kt`, `AppPreferences.kt` e `QuartoGabiScreen.kt` |
| Gerações sucessivas de mascote | **0% — REMOVIDO** | Nenhuma chave `pet_*`, `generation`, `hunger` ou `memorial` em `AppPreferences.kt` |
| XP do Tama e 9 missões de criação de pet | **0% — REMOVIDO** | Substituído por `GoalsScreen` (`GoalItemEntity` — metas pessoais reais da pessoa) |
| Capacidades `tama.pet.*` | **0% — REMOVIDO E BLOQUEADO** | Ausentes de `CapabilityRegistry.kt` (27 capacidades) e bloqueadas explicitamente em `PolicyGate.kt` caso solicitadas via texto/tool call |
| URIs `tama://`, `minis://`, `nanomuse://` | **0% — REMOVIDO** | Esquema exclusivo `novoagentehb://` em `AndroidManifest.xml` e `MainActivity.kt` |

## 2. Especificação do Substituto: Quarto da Gabi (`Screen.Habitat` -> `QuartoGabiScreen`)

| Aspecto | Implementação no `NOVO_AGENTE_HB` | Arquivo real |
| --- | --- | --- |
| Acesso na barra inferior | Botão **Habitat** (`Icons.Default.MeetingRoom`) na barra principal de 5 abas | `design/GabiBottomBar.kt` |
| Rota de navegação | `Screen.Habitat.route` (`"habitat"`) renderiza exclusivamente `QuartoGabiScreen` | `app/navigation/NavRoutes.kt`, `ui/habitat/QuartoGabiScreen.kt` |
| Entrada animada | Reproduz `asset:///avatar/room_intro.mp4` sem áudio ao abrir o Quarto e transiciona automaticamente | `gabi/GabiAvatarViews.kt` (`RoomIntroVideo`) |
| Ambiente animado e estático | Alterna entre `res/raw/room_bg_loop.mp4`, `assets/avatar/room.webp` e fundo escolhido pelo usuário na galeria | `ui/habitat/QuartoGabiScreen.kt`, `GabiUriImage` |
| Estados persistidos no DataStore | Apenas `quarto_last_care_at`, `quarto_first_visit_at`, `quarto_unlock_interval_days`, `quarto_selected_outfit_id`, `quarto_custom_background_uri` | `data/prefs/AppPreferences.kt`, `ui/habitat/HabitatViewModel.kt` |
| Ações reais do Quarto | Escolher clima/estilo (`Aconchego`, `Leitura`, `Noturno`), alternar vídeo ambiente, escolher fundo da galeria, conversar direto no Chat e abrir a tela Computador | `ui/habitat/QuartoGabiScreen.kt` |
