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
| `NativeOffload.kt` (395 linhas) | Objeto `NativeOffloadServer`. Importa `BuildConfig` e `OffloadSocketName` (nanoMuse); 17 ocorrências de nanoMuse. | **Dependência ativa entre componentes originais.** `PRootKernel.boot` chama `NativeOffloadServer.start(rootfsDir)`; `PRootKernel` e `PersistentShell` leem `registeredHandlers`/`socketName` para montar `--native-offload`; `TerminalSession` faz o mesmo. Não é alcançado pela UI atual (nenhum uso fora de `sandbox/`). | Não declarada por arquivo. | **Integrar por etapas** (ver §4). Não apagar. Remoção só com prova de que nenhum caminho o alcança. | `grep` de `NativeOffloadServer` em `PRootKernel.kt`, `PersistentShell.kt`, `TerminalSession.kt` |

## 2. Componentes de apoio no mesmo pacote

| Arquivo | Uso atual | Observação |
| --- | --- | --- |
| `ShellTimeoutPolicy.kt` | Nenhuma referência externa. | Código sem uso; candidato a revisão. |
| `SeccompFallbackPolicy.kt` | `PersistentShell`, `ShellExecutor`, `TerminalSession`, `ai/tama/sandbox/ShellRetrySafety.kt`, teste unitário. | Cabeçalho cita GH#186. Ver investigação do `fork` em `docs/COMPUTER_HELPER_ALPINE_X86_PROOF.md`. |
| `TerminalSanitizer.kt` | `ExecutionCoordinator`, `ShellExecutor`, teste unitário. | — |
| `OffloadDeadlineInputStream.kt`, `OffloadOutputPolicy.kt`, `OffloadReplyFiles.kt`, `OffloadRequestBudget.kt`, `RootfsSize.kt`, `SessionDirectories.kt` | Apoio de offload/rootfs; testes unitários para parte deles. | Dependem de `NativeOffload`, que não é usado. |

## 3. Classificação em quatro categorias (corrigida em 24/11/2026)

A versão anterior desta matriz marcava `NativeOffload` como "sem referências". Isso estava errado: o componente é usado pelo `PRootKernel` original. A classificação correta separa:

| Categoria | Componentes |
| --- | --- |
| **a. Dependência entre componentes originais** | `NativeOffload` (← `PRootKernel`, `PersistentShell`, `TerminalSession`); `SeccompFallbackPolicy` (← `ShellExecutor`, `PersistentShell`, `TerminalSession`); `TerminalSanitizer` (← `ExecutionCoordinator`, `ShellExecutor`); `RootfsManager` (← `PRootKernel`, `PersistentShell`, `TerminalSession`) |
| **b. Alcançado pela interface atual** | Nenhum componente do pacote é chamado diretamente pela UI. O caminho alcançado é `ComputerViewModel` → `ComputerHelperClient` → auxiliar, que não usa essas classes. |
| **c. Código não integrado ao fluxo de produção** | `PRootKernel` (`boot` e `buildProotCommand`, após esta etapa ligados ao auxiliar via `ProotCommandLine`), `PersistentShell`, `TerminalSession`, `ExecutionCoordinator`, `ShellExecutor`, `PtyBridge`, `NativeOffload` |
| **d. Comprovadamente desnecessário** | Nenhum. `ShellTimeoutPolicy.kt` não tem referências externas, mas não há prova de desuso (não foi removido). |

## 3a. Atualização desta etapa

- `PRootKernel.buildProotCommand` agora delega a `ProotCommandLine` (módulo `android/sandbox-proot-core`), que contém o corpo original do método sem dependências da Gabi. O app mantém os logs e o guard `isBooted`.
- O auxiliar usa o mesmo `ProotCommandLine` nas operações de probe e de diagnóstico de fork. Não existe segundo construtor de argv.
- `PersistentShell` e `TerminalSession` ainda montam o próprio argv de offload (linhas ~242 e ~461). Esse código duplicado é original e fica para a fase de integração de cada um, quando for consolidado em `ProotCommandLine`.

## 4. Divergências documentais encontradas

1. `docs/LICENSE_PROVENANCE.md` afirma "0 ocorrências" de `nanoMuse` / `OpenMinis` / `tama-v2`. A varredura desta etapa encontrou 34 arquivos com "nanomuse" fora de `.git`. A auditoria anterior está desatualizada e não deve ser citada como prova.
2. `LICENSE_PENDING_OWNER.txt` registra a incorporação do PTY do Tama V2/nanoMuse com GPL preservado. Isso contradiz qualquer declaração de "0 código GPL" no documento antigo.
3. Nenhum componente acima tem SPDX ou cabeçalho de licença próprio. A origem de cada um é inferida de imports e comentários, não de procedência formal.

## 5. Decisões desta etapa

- Nenhum arquivo foi alterado. Esta matriz é o único artefato.
- Acesso a dados da Gabi (`FileMentionIndex`, `MountedFoldersStore`, `EnvVarRepository`) não entra no caminho do auxiliar.
- `PersistentShell`, `TerminalSession`, `ExecutionCoordinator`, `ShellExecutor` e `PtyBridge` permanecem desabilitados até validação.
- Não há declaração de conformidade de licença. Status: pendente do proprietário.
