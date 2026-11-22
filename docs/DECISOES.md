# DECISÕES ARQUITETURAIS E DE PRODUTO — NOVO_AGENTE_HB

Data: 2026-11-21
Referência normativa: `ARENA_ORDEM_MESTRA_NOVO_PRODUTO_TAMA_V2_ESTRUTURA_GABI_HB_2026-11-21.md` (Versão 1.0 no workspace)

## Decisões Registradas

| ID | Tema | Decisão adotada | Alternativa rejeitada | Status |
| --- | --- | --- | --- | --- |
| D01 | Isolamento de repositório e branch | Implementar todo o terceiro produto em `novo-agente-hb/` na branch atual `arena/cc3c891a-jarvis-projeto`, preservando `src/`, `android/`, `tama-v2`, `tamaaiv2`, sem criar novo repo nem dar `git push` | Modificar `android/` legado ou criar repositório público no GitHub | `EXECUTED` |
| D02 | Identificador e pacote Android | `applicationId = "com.novoagentehb.app"`, identificador interno `NOVO_AGENTE_HB`, esquema `novoagentehb://`, `android:allowBackup="false"`, banco `novo_agente_hb.db`, cofre `novo_agente_hb_secure_prefs` | Reusar `com.javis.assistant`, `ai.tama.assistant`, `io.github.nanomuse.app` ou `tama://` | `EXECUTED` |
| D03 | Origem limpa e zero GPL | Reimplementação clean-room em Kotlin/Compose/Hilt/Room com bibliotecas Apache-2.0 (e `junit` EPL-1.0 somente em testes) | Portar, adaptar ou ocultar créditos de `nanoMuse`, `OpenMinis`, `tama-v2` ou `tamaaiv2` (GPL-3.0) | `EXECUTED` |
| D04 | Pilha de IA ativa | **Google Gemini** como provedor primário (`gemini-2.5-flash` para texto/visão/operação de tela e `gemini-3.8-live` WebSocket Bidi para voz), mantendo adaptadores BYOK para Groq, OpenRouter, DeepSeek e Anthropic | Assumir assinatura ChatGPT ou cota gratuita comercial implícita | `EXECUTED` |
| D05 | Botão `Habitat` e Quarto da Gabi | Rota `habitat` mapeada diretamente para `QuartoGabiScreen`, usando apenas os 1.324 ativos aprovados da Gabi (`room_intro.mp4`, `res/raw/room_bg_loop.mp4`, `room.webp`) e zero mecânica Pet | Exibir dragão, ovo, fantasma, memorial, fome 72h, 6 estágios ou XP Pet | `EXECUTED` |
| D06 | Voz congelada `gabi_24l` e perfis `B2`/`C2` | Manter especificação exata (`voice_profiles.json` + `FrozenVoiceIdentitySpec`), nunca trocar silenciosamente para TTS do sistema e explicitar quando o runtime Pocket TTS local não estiver embarcado | Regravar áudio, usar arquivo privado `Gabi Voz isolada.mp3` ou mascarar TTS Android como `gabi_24l` | `EXECUTED` |
| D07 | Runtime local Pocket TTS (`portuguese_24l`) | Documentar `NOT_BUNDLED` no pacote Android atual; `VoiceEngineCoordinator` informa explicitamente o fallback ao usuário enquanto o binário ONNX/JNI não for integrado | Fingir inferência local inexistente ou incluir gravação privada de menor | `EXECUTED` |
| D08 | Licenciamento do novo código | Criar `LICENSE_PENDING_OWNER.txt` e `THIRD_PARTY_NOTICES.md` sem publicar licença comercial ou open-source definitiva sem escolha do proprietário | Escolher licença final ou marca comercial autonomamente | `EXECUTED` |
| D09 | Compilação APK no sandbox Arena | Não violar restrições nem usar GitHub Actions/CI remoto como contorno; documentar `BLOCKED` de toolchain/rede no sandbox e entregar instruções exatas de build local | Inventar APK ou disparar build via GitHub Actions | `BLOCKED_ENV_DOCUMENTED` |
| D10 | Entrega no Google Drive | Criar pasta privada `NOVO_AGENTE_HB_Entrega_Arena_2026-11-21` no Google Drive (pré-autorizada pelo usuário) com ZIP completo, docs, matrizes, testes, SBOM e SHA-256 | Publicar artefatos ou chaves em repositório GitHub público | `IN_PROGRESS` |
