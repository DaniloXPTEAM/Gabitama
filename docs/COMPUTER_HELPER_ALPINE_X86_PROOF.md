# Auxiliar do computador: execução de Alpine no guest via PRoot original (prova x86_64, somente CI)

Status: **Android x86_64 comprovado em CI (emulador); Android ARM64 NÃO comprovado.**

## O que foi provado

- Commit `816b35a` (cadeia de workflow `test-computer-helper-poc.yml`): PoC com `ComputerProotRunAndroidTest` p01–p04.
- p04: o PRoot original (`libproot.so`, x86_64, compilado de `third_party/tama-runtime/android/deps/proot`
  por `scripts/android/proot-original/build-original.sh`) executa `/bin/sh` de um rootfs Alpine 3.21.3 **x86_64**
  instalado no auxiliar. A saída do guest contém `3.21.3` e `NAME="Alpine Linux"`, que existem só na árvore Alpine.
  Exit 0, `leftover=0`.
- Variante x86_64: `alpine-minirootfs-3.21.3-x86_64.tar.gz`, sha256 `1a694899e406ce55d32334c47ac0b2efb6c06d7e878102d1840892ad44cd5239`
  (valor oficial do `.sha256` em dl-cdn.alpinelinux.org/alpine/v3.21/releases/x86_64/), assinatura GPG verificada com a
  chave publicada (`0482 D840 22F5 2DF1 C4E7 CD43 293A CD09 07D9 495A`), `bin/busybox` com `e_machine=0x3e`.
  A verificação roda em `scripts/android/alpine-provenance/verify.sh` com `ALPINE_ARCH=x86_64`.
- Separação: asset `alpine-minirootfs-x86_64.bin` (debug, fora do Git), diretório `alpine-rootfs-x86_64-test`,
  marcador próprio. O rootfs aarch64 de produto, seu marcador e seu histórico não foram alterados.

## Falhas reproduzidas e o que foi descartado

| Run | Commit | Resultado |
|---|---|---|
| `45381377014` | `9542114` | p04 falhou: `/bin/sh: can't fork: Function not implemented` (exit 2) após o `echo` inicial. |
| `45382920833` | `f27bfae` | Mesma falha com `PROOT_NO_SECCOMP=1`. Emulador x86_64 nas APIs 26, 29 e 34. |
| `45384389673` | `816b35a` | Sucesso: guest usa `exec` (sem `fork`) e lê `/etc/alpine-release` e `/etc/os-release`. |
| `45385840869` | `4271db0` | Build APK falhou só no job API 26 (`chatImeInputDiagnosticsPersistsAcrossRestartAndClears`). |
| `—` | `f2dd770` | Commit sem alteração de código; o job API 26 passou nessa reexecução. |
| `45388725054` | `1ee1ad4` | Build APK **success** em JVM, IME e APIs 26, 29 e 34. A falha de IME não permanece no último build aprovado. |
| `45409567326` | `2964af8` | PoC **success** nas APIs 26, 29 e 34 (p01–p05). Diagnóstico BPF ativo nesta cópia de teste; trace do `fork` reproduzido. |
| `45409567582` | `2964af8` | Build APK **failure** só em `Testes IME isolados API 34`: `chatImeInputDiagnosticsPersistsAcrossRestartAndClears` (mesmo teste instável de `4271db0`). Não relacionado a PRoot/ambiente; não reexecutado. |

Hipótese testada e não suficiente: `PROOT_NO_SECCOMP=1` (`f27bfae`) não removeu o `ENOSYS`. Isso mostra que a flag,
sozinha, não corrige o problema. **Não exclui** os caminhos de `SYSCALL_AVOIDER` do próprio PRoot (ver abaixo).
O teste `exec` (`816b35a`) contorna `fork` e não diagnostica a causa.

### Diagnóstico do `fork` (instrumentado, CI `45406137846`, APIs 29 e 34)

Operação `PROOT_OP_ALPINE_X86_FORK_TRACE` (`p05`): o mesmo guest Alpine x86_64 roda `/bin/true` a partir de `/bin/sh -c`,
com `PROOT_VERBOSE=4` gravado em arquivo privado. O resumo do log, lido pelas anotações do job, mostra a sequência:

1. `proot info: ptrace acceleration (seccomp mode 2, new syscall order) enabled`
2. `proot info: vpid 1: seccomp SIGSYS: fork(...) = 0x39`
3. `proot info: Setting result after SIGSYS to 0xffffffffffffffda` (`-38`, ENOSYS)
4. `/bin/sh: can't fork: Function not implemented`

**Comprovado (código + log):**

- O `fork` do guest chega ao PRoot como SIGSYS com `SYS_SECCOMP` (`tracee/event.c`, caso `SIGSYS`).
- `tracee/seccomp.c`, `handle_seccomp_event_common`: o `switch` por syscall não tem `case` para `PR_fork`, `PR_vfork`
  nem `PR_clone`. O `default:` faz `set_result_after_seccomp(tracee, -ENOSYS)`. Esse é o ENOSYS observado.
- `syscall/seccomp.c` (`proot_sysnums`) não lista `fork`, `vfork` nem `clone`. Por desenho, syscalls não listadas
  recebem `SECCOMP_RET_ALLOW`. Logo, o SIGSYS de `fork` **não** é explicado pela lista do PRoot.

**Reprodução em `2964af8` (`45409567326`, anotações amostradas):** SIGSYS para `fork` (`0x39` = 57) repetido no vpid 1, seguido de
`Setting result after SIGSYS to -38`. Na mesma execução, `open` e `stat` também chegam como SIGSYS e são tratados normalmente.
Isso **contraria** a premissa de que `fork` está fora do filtro: o filtro entrega trap para `fork`. Ainda não se sabe
se o trap vem do filtro do PRoot ou de um filtro instalado por outro componente. O dump do BPF (`GABITAMA_BPF`, impresso antes
do `prctl`) está ativo no build de teste, mas não apareceu nas anotações amostradas; o artefato completo não é baixável
neste ambiente (`gh run download` com `EOF`).

**Não comprovado:** por que o kernel do emulador x86_64 entrega SIGSYS para `fork` quando o filtro do PRoot não o trata.
Hipótese a verificar (não testada): diferença entre o filtro instalado e o esperado para o ABI do guest, ou ação
de filtro vinda de outro componente do sistema. Próximo passo: dump do BPF instalado (`/proc/<pid>/status` `Seccomp`,
e leitura do programa) no mesmo emulador, sem alterar flags.

**Correções avaliadas e NÃO aplicadas:**

- Reiniciar `fork` com o mesmo número (`restart_syscall_after_seccomp`): risco de loop, porque a syscall volta a
  cair no filtro que gerou o SIGSYS.
- `PROOT_NO_SECCOMP=1` (`f27bfae`): já testado; não bastou.
- Caminho `exec` (`816b35a`): contorna `fork`; não corrige nada.

Nenhuma correção foi incorporada ao PRoot. `third_party/` não foi alterado.

## Limites conhecidos (não resolvidos)

- `fork()` no guest falha no emulador x86_64 (acima). Qualquer programa que faça fork (pipelines, `sh -c` com comandos externos)
  ainda não é suportado.
- Limpeza: após o término, o helper mata processos do próprio UID cujo executável está dentro da árvore do rootfs
  (captura tracees reparentados). Há corrida de PID entre leitura de `/proc` e `kill`; processos fora do UID não são vistos.
- PRoot não é sandbox de segurança. Não há comando livre, terminal nem `PersistentShell` habilitados.
- Execução de Alpine **aarch64** em Android ARM64: não comprovada. O runner `ubuntu-24.04-arm` não tem KVM e o
  catálogo oficial não tem emulador Linux aarch64 (ver `docs/LICENSE_MATRIX_COMPUTER_RUNTIME.md` e runs de sonda).

## Gabi

`ComputerViewModel` lê o estado real do auxiliar pelo `ComputerHelperClient` e mostra as fases
`HelperPhase` (auxiliar não instalado/indisponível, rootfs ausente/instalado, PRoot disponível, diagnóstico em
execução/concluído/erro). O botão "Diagnóstico" executa a operação fixa do Alpine x86_64 **somente quando o rootfs de teste
está instalado** (build de CI). Não há entrada de comando.
Testes JVM: `HelperStatusMapperTest`.

## Pendências (atualizado em 24/11/2026)

Classificação: **x86_64 (CI emulador)** e **ARM64 (dispositivo/emulador ARM64)**.

| # | Item | Classe | Estado |
|---|---|---|---|
| 1 | Causa do `ENOSYS` em `fork` do guest | x86_64 | Localizado: SIGSYS `SYS_SECCOMP` em `fork` → `default: -ENOSYS` (`tracee/seccomp.c`). Falta explicar por que o filtro entrega SIGSYS; o dump BPF está ativo em `2964af8`, leitura pendente do artefato. |
| 2 | Integração do `PRootKernel` original | x86_64 | Em andamento: argv em `ProotCommandLine` e ambiente em `PRootEnvironment` (`sandbox-proot-core`), usados pelo app e pelo auxiliar. `p04` prova que `LD_LIBRARY_PATH` chega ao guest. |
| 3 | `RootfsManager`, `PersistentShell`, `TerminalSession`, `ExecutionCoordinator`, `ShellExecutor`, `PtyBridge`, `NativeOffload` | x86_64 | Pendente por etapa; ver `docs/AUDIT_TAMA_ORIGINAL_SANDBOX_MATRIX.md`. |
| 4 | Execução de Alpine **aarch64** em Android ARM64 | ARM64 | Não comprovada (sem KVM e sem emulador Linux aarch64 no runner). Não repetir sondagem sem condição nova. |
| 5 | Licenciamento (GPL/LGPL) para qualquer distribuição | Ambas | Pendente do proprietário. Nada publicado. |

Itens já confirmados, que **não** são pendências: build APK em `1ee1ad4` (APIs 26/29/34); proveniência e assinatura
do Alpine x86_64 (`816b35a`); PoC p04 (`816b35a`, `1ee1ad4`).
