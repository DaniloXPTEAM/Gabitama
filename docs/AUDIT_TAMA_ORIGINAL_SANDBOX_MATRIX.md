# Matriz de auditoria — computador original (pacote `com.openminis.app.sandbox`)

Branch: `arena/1dba9faf-gabitama`. Base: `1ee1ad4`. Data: 2026-11-24.
Escopo: somente leitura e classificação. Nenhum componente foi apagado, reescrito
nem relicenciado nesta etapa. Nenhum runtime, TAR ou Alpine foi distribuído.

Legenda das colunas: `Origem comprovada` = evidência direta no arquivo (cabeçalho,
import ou comentário). `Uso atual` = quem chama o componente no código fonte.
`Licença` = o que o repositório declara hoje; não é conclusão de conformidade.

## 1. Os 8 componentes pedidos

| Arquivo/componente | Origem comprovada | Uso atual | Licença | Ação | Evidência |
| --- | --- | --- | --- | --- | --- |
| `RootfsManager.kt` (561 linhas) | Sem referência a nanoMuse. Usa `com.novoagentehb.rootfs.TarRootfsExtractor` (código próprio) e `SessionDirectories` (`ai.tama.net`). Comentário `[T-android-seccomp…]` não se aplica aqui. | Chamado por `PRootKernel`, `PersistentShell`, `TerminalSession` e `TamaRootfsSafetyAndroidTest`. | Não declarada por arquivo; herda o status de `LICENSE_PENDING_OWNER.txt`. | **Adaptar** ao auxiliar (primeiro passo executável). Manter a extração e validação de traversal/link já testadas. | `grep` de referências; `androidTest/.../TamaRootfsSafetyAndroidTest.kt` |
| `PRootKernel.kt` (953 linhas) | Importa `FileMentionIndex` e `MountedFoldersStore` (dados de arquivos da Gabi); `ai.tama.net.NativeOffloadSession`; 3 ocorrências de nanoMuse. | `MountedFoldersStore`, `ExecutionCoordinator`, `PersistentShell`, `ShellExecutor`. `buildProotCommand` monta: `-0`, `--link2symlink`, `-r`, `-b /dev|/proc|/sys`, `-w /root`, binds de usuário e `--native-offload`. | Não declarada por arquivo. | **Adaptar** (`buildProotCommand` e `boot`). **Remover** do caminho do auxiliar qualquer bind de `MountedFoldersStore`/`FileMentionIndex` (acesso a dados da Gabi é proibido). | Leitura de `buildProotCommand` (linhas 610–685); imports |
| `PersistentShell.kt` (676 linhas) | `ai.tama.sandbox.ShellMarkerDecoder` ("nanoMuse: fragmented process output"); 20 ocorrências de nanoMuse. | Chamado só por `ExecutionCoordinator` e `PRootKernel`. | Não declarada por arquivo. | **Manter desabilitado** até validação. Não expor ao app nem ao auxiliar. | `grep` de referências |
| `TerminalSession.kt` (472 linhas) | Sem nanoMuse. Usa `SeccompFallbackPolicy`, `PtyBridge`, `ExecutionCoordinator`. | Só `ExecutionCoordinator`. | Não declarada por arquivo. | **Manter desabilitado** (terminal interativo). | `grep` de referências |
| `ExecutionCoordinator.kt` (389 linhas) | Importa `io.github.nanomuse.sandbox.FeedSandboxScope` e `EnvVarRepository` (dados do app); 25 ocorrências de nanoMuse. | Nenhum chamador fora de `sandbox/` (não alcançado pela UI). | Não declarada por arquivo. | **Não integrar** agora. Coordenação de execução só depois de validação do shell. | `grep` de referências |
| `ShellExecutor.kt` (217 linhas) | 5 ocorrências de nanoMuse; usa `SeccompFallbackPolicy` (desativado no app paralelo, conforme comentário). | `ExecutionCoordinator`, teste `SeccompFallbackPolicyTest`. | Não declarada por arquivo. | **Manter desabilitado** (execução arbitrária). | Comentário em `SeccompFallbackPolicy.kt` |
| `PtyBridge.kt` (66 linhas) | Sem cabeçalho próprio. Registrado em `LICENSE_PENDING_OWNER.txt` como incorporação do PTY do Tama V2/nanoMuse, com `pty_bridge.c`. | `TerminalSession` e `TamaOriginalPtyAndroidTest`. | GPL declarado no aviso do próprio `LICENSE_PENDING_OWNER.txt`; **não relicenciar**. | **Manter** com avisos. Não tratar como MIT. Desabilitado até validação. | `LICENSE_PENDING_OWNER.txt`; `androidTest/.../TamaOriginalPtyAndroidTest.kt` |
| `NativeOffload.kt` (395 linhas) | Importa `com.novoagentehb.app.BuildConfig` e `io.github.nanomuse.sandbox.OffloadSocketName`; 17 ocorrências de nanoMuse. | **Nenhuma referência fora do próprio arquivo.** Código morto no fluxo atual. | Não declarada por arquivo. | **Não integrar.** Candidato a limpeza futura, somente após prova de ausência de uso (não por namespace). | `grep` (0 referências externas) |

## 2. Componentes de apoio no mesmo pacote

| Arquivo | Uso atual | Observação |
| --- | --- | --- |
| `ShellTimeoutPolicy.kt` | Nenhuma referência externa. | Código sem uso; candidato a revisão. |
| `SeccompFallbackPolicy.kt` | `PersistentShell`, `ShellExecutor`, `TerminalSession`, `ai/tama/sandbox/ShellRetrySafety.kt`, teste unitário. | Cabeçalho cita GH#186. Ver investigação do `fork` em `docs/COMPUTER_HELPER_ALPINE_X86_PROOF.md`. |
| `TerminalSanitizer.kt` | `ExecutionCoordinator`, `ShellExecutor`, teste unitário. | — |
| `OffloadDeadlineInputStream.kt`, `OffloadOutputPolicy.kt`, `OffloadReplyFiles.kt`, `OffloadRequestBudget.kt`, `RootfsSize.kt`, `SessionDirectories.kt` | Apoio de offload/rootfs; testes unitários para parte deles. | Dependem de `NativeOffload`, que não é usado. |

## 3. Divergências documentais encontradas

1. `docs/LICENSE_PROVENANCE.md` afirma "0 ocorrências" de `nanoMuse` / `OpenMinis` / `tama-v2`. A varredura desta etapa encontrou 34 arquivos com "nanomuse" fora de `.git`. A auditoria anterior está desatualizada e não deve ser citada como prova.
2. `LICENSE_PENDING_OWNER.txt` registra a incorporação do PTY do Tama V2/nanoMuse com GPL preservado. Isso contradiz qualquer declaração de "0 código GPL" no documento antigo.
3. Nenhum componente acima tem SPDX ou cabeçalho de licença próprio. A origem de cada um é inferida de imports e comentários, não de procedência formal.

## 4. Decisões desta etapa

- Nenhum arquivo foi alterado. Esta matriz é o único artefato.
- Acesso a dados da Gabi (`FileMentionIndex`, `MountedFoldersStore`, `EnvVarRepository`) não entra no caminho do auxiliar.
- `PersistentShell`, `TerminalSession`, `ExecutionCoordinator`, `ShellExecutor` e `PtyBridge` permanecem desabilitados até validação.
- Não há declaração de conformidade de licença. Status: pendente do proprietário.
