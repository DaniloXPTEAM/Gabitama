# Matriz de referências a nanoMuse / OpenMinis (24/11/2026)

Escopo: todo arquivo fora de `.git` com a palavra `nanomuse` (contagem: 33 arquivos, após a remoção abaixo).
Esta matriz não declara conformidade de licença. Ela separa **origem comprovada** de **licença do código específico**,
porque um arquivo GPLv3 isolado (ex.: `LICENSE-nanoMuse.txt`) não prova a licença de cada arquivo copiado.

Legenda de ação: **Mantido** (uso atual ou aviso exigido), **Removido** (sem uso comprovado), **Corrigido** (texto
desatualizado), **Pendente** (depende de análise do proprietário).

| Arquivo / componente | Origem comprovada | Uso atual | Licença | Ação | Evidência |
|---|---|---|---|---|---|
| `android/app/src/main/kotlin/io/github/nanomuse/hub/HubClient.kt` | Namespace nanoMuse; sem aviso de licença no arquivo | **Nenhum**: sem instanciação; só o tipo `Device` era referenciado | não comprovada (código de origem nanoMuse) | **Removido** (24/11/2026) | `grep -rn "HubClient("` vazio antes da remoção; única referência externa era `FileMentionIndex.Entry.nmDevice` |
| `android/app/src/main/kotlin/com/openminis/app/data/FileMentionIndex.kt` | Tama V2 (OpenMinis); comentário `// nanoMuse:` | Original, em uso | GPL do upstream; análise pendente | **Corrigido**: parâmetro `nmDevice` (sempre `null`, nunca lido) removido | `grep nmDevice` após a remoção: vazio; as 3 construções de `Entry` não passam o parâmetro |
| `android/app/src/main/kotlin/io/github/nanomuse/sandbox/FeedSandboxScope.kt` | Namespace nanoMuse | Em uso: `PRootKernel`, `ExecutionCoordinator` (Feed da Gabi) | não comprovada | **Mantido** (namespace não é removido sem prova de substituição) | `grep -rn FeedSandboxScope` |
| `android/app/src/main/kotlin/io/github/nanomuse/sandbox/OffloadSocketName.kt` | Namespace nanoMuse; nome do socket por app (#271) | Em uso: `NativeOffload` | não comprovada | **Mantido** | `NativeOffload.kt` importa e usa `OffloadSocketName.forApp` |
| `android/app/src/main/kotlin/com/openminis/app/sandbox/NativeOffload.kt` | Tama V2; comentários `// nanoMuse:` (divergências do upstream) | **Dependência ativa** de `PRootKernel`, `PersistentShell`, `TerminalSession` | GPL do upstream; análise pendente | **Mantido** (comentários são proveniência) | `docs/AUDIT_TAMA_ORIGINAL_SANDBOX_MATRIX.md` |
| `android/app/src/main/kotlin/com/openminis/app/sandbox/PRootKernel.kt` | Tama V2 (OpenMinis); comentários `nanoMuse:` | Original, em uso | GPL do upstream; análise pendente | **Mantido** | grep `nanomuse` |
| `android/app/src/main/kotlin/com/openminis/app/sandbox/PersistentShell.kt` | Tama V2; comentários `nanoMuse:` | Original (desabilitado para execução arbitrária) | GPL do upstream; análise pendente | **Mantido** | grep `nanomuse` |
| `android/app/src/main/kotlin/com/openminis/app/sandbox/ShellExecutor.kt` | Tama V2; comentários `nanoMuse:` | Original | GPL do upstream; análise pendente | **Mantido** | grep `nanomuse` |
| `android/app/src/main/kotlin/com/openminis/app/sandbox/TerminalSession.kt` | Tama V2 | Original | GPL do upstream; análise pendente | **Mantido** | — |
| `android/app/src/main/kotlin/com/openminis/app/sandbox/ExecutionCoordinator.kt` | Tama V2; comentários `nanoMuse:` | Original | GPL do upstream; análise pendente | **Mantido** | grep `nanomuse` |
| `android/app/src/main/kotlin/com/openminis/app/sandbox/SeccompFallbackPolicy.kt` | Projeto (política de retry); comentário nanoMuse | Em uso | própria, a confirmar | **Mantido** | grep `nanomuse` |
| `android/app/src/main/kotlin/com/openminis/app/sandbox/TerminalSanitizer.kt` | comentário `nanoMuse:` (privacidade de log) | Em uso | a confirmar | **Mantido** | grep `nanomuse` |
| `android/app/src/main/kotlin/com/openminis/app/data/repository/EnvVarRepository.kt` | comentários `nanoMuse:` (privacidade) | Em uso | a confirmar | **Mantido** | grep `nanomuse` |
| `android/app/src/main/kotlin/com/openminis/app/logging/AppLogger.kt` | comentário `nanoMuse:` | Em uso | a confirmar | **Mantido** | grep `nanomuse` |
| `android/app/src/test/.../OffloadPeerAdmissionTest.kt`, `TerminalSanitizerTest.kt` | comentários/testes nanoMuse | Testes unitários | a confirmar | **Mantido** | grep `nanomuse` |
| `android/app/src/main/assets/licenses/tama-runtime/LICENSE-nanoMuse.txt`, `NOTICE.txt`, `THIRD_PARTY_NOTICES.md` | Texto de licença e avisos do upstream | Avisos legais | GPLv3 (texto integral) e avisos de terceiros | **Mantido** (aviso exigido; não remover) | arquivos em `assets/licenses/` |
| `LICENSE_PENDING_OWNER.txt` | Registro do proprietário sobre PTY/GPL | Documento | — | **Mantido** | — |
| `scripts/license-audit/audit_ip_and_no_pet.py` | Auditoria do projeto | Falha em 2 testes (ver `REVISAO_PUBLICACAO_GITHUB.md`) | — | **Pendente**: a regra "zero nanomuse" não reflete mais o código; não alterar a regra para passar | execução local em 24/11/2026 |
| `docs/*.md` (históricos) | Relatórios de sessões anteriores | Histórico | — | **Mantido**; entradas que afirmam "0 ocorrências" marcadas como superadas | — |

## Resumo

- Removido: 1 arquivo (`HubClient.kt`, código morto) e 1 parâmetro sem uso (`nmDevice`).
- Mantido por uso ou por aviso: os demais, incluindo os comentários de proveniência `nanoMuse:`.
- Não comprovado: a licença específica de cada arquivo copiado. Pendente do proprietário.
- Não declarada: conformidade comercial. Nada foi publicado.

## Fixture de segredo (não é credencial)

`android/app/src/androidTest/java/com/novoagentehb/app/SafetyPolicyAndMockIsolationAndroidTest.kt` contém
`AIzaSyFake…`, usado para testar a redação de logs. A auditoria o marca por padrão. Não é chave real.
