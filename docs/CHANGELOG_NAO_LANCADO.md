# Changelog — versão não lançada (branch `arena/1dba9faf-gabitama`)

Base: `0ecdbe5` (main). Último commit verificado pela CI: `4790e8b`.

**Estado:** nenhum APK gerado, exportado ou distribuído. Nenhum release ou pré-release. Identificação da próxima versão: a definir pelo proprietário. Licença: MIT, com pendências de componentes GPL/LGPL herdados (ver `LICENSE_PENDING_OWNER.txt` e `docs/LICENSE_PROVENANCE.md`); o gate de licença de distribuição ainda não existe.

## Alterações efetivas

### Banco de dados (Room)
- Migração 1→2 aditiva (`browser_history`). Schemas exportados.
- `fallbackToDestructiveMigration` removido (`54d7c46`). Versão sem migração válida não apaga o banco: falha controlada, grava diagnóstico sem conteúdo privado e permite recuperação. Sem suporte a downgrade.
- Testes: JVM e migração instrumentada (API 34).

### Spotify (`7e4b8e7`, `b365f5b`, `b12b0e6`)
- Abrir o app ou pesquisar conteúdo é handoff: nunca declara reprodução verificada.
- Pedido de reprodução sem integração autorizada é recusado (`SPOTIFY_PLAYBACK_UNAVAILABLE`). Nada é aberto nem marcado como tocando.
- Busca não reproduz a primeira faixa. "por" deixou de ser verbo de reprodução.
- Retomada: handoff com validade de 30 min, consumido uma vez, aviso no chat ao voltar, sem afirmar reprodução.

### Voz (`eb64dcf`)
- Ao sair do app (`onStop`, sem rotação), a escuta, a fala e o Gemini Live são pausados. A conversa é preservada.
- A retomada é explícita: nada reabre o microfone sozinho; o chat registra aviso pedindo toque no microfone.
- Audio focus na fala: perda de foco interrompe a fala, sem retomada automática.

### Animações do HB (`4790e8b`)
- Quarto: sequências WebP com alfa para comer, reação de comer, ler e dormir, geradas por respiração/pulso a partir das poses aprovadas.
- Trabalho, repouso, escuta, fala e pensamento: sem alteração (sequências existentes).
- Pendentes: música, toque, sucesso, erro, cancelamento e troca de look.

### Provedores, skills, MCP, navegador e geração de imagem (F1–F4)
- F1: catálogo de provedores, provedor personalizado OpenAI-compatível, preset NVIDIA NIM, ativação só com consentimento (`d2e6290`, `7edcdc4`, `20661ac`, `75cb797`).
- F2: `run_local_skill` com confirmação e releitura por SHA-256; `mcp_call_tool` com resposta tratada como não verificada pelo app (`185281a`, `db3ee5d`, `55c8358`).
- F3: navegador embutido com histórico em Room; `browser_page_interact` para clicar e preencher campo com confirmação; senha, arquivo e campo oculto recusados (`f4a17d9`, `515e428`, `b8045c0`, `1aa23e9`, `edf722d`).
- F4: geração de imagem NVIDIA FLUX.1-schnell, exige consentimento por pedido (`93971e1`, `b4a96de`). Sem credencial; geração real não validada.

## Validação
- Gate de distribuição de licença: `scripts/license-audit/check-distribution-license.sh` (fail-closed, não ligado ao CI). Hoje bloqueia (sem `LICENSE`; `LICENSE_PENDING_OWNER.txt` presente).
- CI (GitHub Actions, sem artefatos exportados): JVM 307 testes, 0 falhas; migração Room instrumentada 4 testes, 0 falhas (run `46152871964`, commit `4790e8b`).
- Não validado no aparelho: Spotify, pausa de voz, audio focus, animações, navegador em sites reais, Gemini Live, geração de imagem.

## Não alterado
- Voz `gabi_24l` (especificação; runtime Pocket não embarcado), perfis B2/C2, modelos, Chat, Home e dados do usuário.
- PRoot, PTY, OpenMinis e computador.
