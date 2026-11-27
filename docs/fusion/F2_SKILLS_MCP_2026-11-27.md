# F2 — Skills e MCP no fluxo do agente (27/11/2026)

Branch: `arena/1dba9faf-gabitama`. Implementação própria do GabiTama (MIT). Sem código de terceiros.

## O que o agente faz agora

| Capacidade | Ferramenta | Gate | Efeito real | Evidência JVM |
|---|---|---|---|---|
| Skill local com adaptador verificado | `run_local_skill` | confirmação explícita (LOCAL_MUTATION) | grava `skills/<id>-documento-verificado.md`, relê e confere SHA-256 | `SkillRunEvidenceUnitTest` (6 casos) |
| Ferramenta de servidor MCP | `mcp_call_tool` (novo) | confirmação explícita (EXTERNAL_SIDE_EFFECT) | handshake JSON-RPC real + `tools/call` com `Mcp-Session-Id` | `McpChatToolRunnerUnitTest` (9 casos, HTTP real) |

## Limites honestos

- Skill de instrução livre: só gera manifesto (`ARTIFACT_PREPARED`); nunca é tratada como ação verificada.
- MCP: a semântica é `REMOTE_RESPONSE_ONLY`. O app confirma a resposta do servidor, não o efeito externo. O texto volta ao modelo marcado como conteúdo externo não confiável.
- Servidor MCP só é usado se estiver cadastrado, ativado pela pessoa (opt-in) e do tipo `MCP_SERVER`. LAN local só com o escopo `opt_in_lan_or_external`.
- Não há execução de shell, stdio ou WebSocket.

## Não validado

- Nenhum teste em aparelho. Nenhum servidor MCP de terceiros foi testado; o servidor usado é o MockWebServer local.
