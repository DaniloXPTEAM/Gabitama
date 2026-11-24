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

Hipótese testada e não suficiente: `PROOT_NO_SECCOMP=1` (`f27bfae`) não removeu o `ENOSYS`. Isso mostra que a flag,
sozinha, não corrige o problema. **Não exclui** os caminhos de `SYSCALL_AVOIDER` do próprio PRoot (ver abaixo).
O teste `exec` (`816b35a`) contorna `fork` e não diagnostica a causa.

### Mapa de caminhos do PRoot que produzem `-ENOSYS` (leitura do código vendorizado, 24/11/2026)

- `syscall/sysnum.c`: `detranslate_sysnum` devolve `SYSCALL_AVOIDER` quando a syscall não está na tabela do ABI.
- `arch.h`: `SYSCALL_AVOIDER` é `-2` em x86_64 (`-1` em ARM64). O kernel responde `-ENOSYS` a esse número.
- `syscall/enter.c`: `execveat` com fd não-`AT_FDCWD` devolve `-ENOSYS` explicitamente.
- `tracee/seccomp.c`: `ftruncate` com `SYSCALL_AVOIDER` devolve `-ENOSYS`.
- `tracee/event.c:658`: o caminho `seccomp_after_ptrace_enter` depende de `PROOT_ASSUME_NEW_SECCOMP` e da ordem das paradas.
- `tracee/tracee.c:400`: `clone` e `clone3` são lidos para flags de filho; `clone` e `clone3` estão em `sysnums-x86_64.h` (56 e 435).

Nenhum desses caminhos trata `fork`/`vfork` do guest diretamente. A causa **não está diagnosticada**. A operação
`PROOT_OP_ALPINE_X86_FORK_TRACE` (diagnóstico `p05`) registra o log verboso do PRoot e devolve um resumo filtrado
(`trace lines=… sigsys=…`, linhas de `clone`, `fork`, `vfork`, `seccomp` e `SIGSYS`).

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
| 1 | Causa do `ENOSYS` em `fork` do guest | x86_64 | Em investigação. Instrumentação `p05` (`FORK_TRACE`) adicionada; aguarda evidência de CI. |
| 2 | Integração do `PRootKernel` original | x86_64 | Em andamento: construção de argv em `ProotCommandLine`, usada pelo app e pelo auxiliar. |
| 3 | `RootfsManager`, `PersistentShell`, `TerminalSession`, `ExecutionCoordinator`, `ShellExecutor`, `PtyBridge`, `NativeOffload` | x86_64 | Pendente por etapa; ver `docs/AUDIT_TAMA_ORIGINAL_SANDBOX_MATRIX.md`. |
| 4 | Execução de Alpine **aarch64** em Android ARM64 | ARM64 | Não comprovada (sem KVM e sem emulador Linux aarch64 no runner). Não repetir sondagem sem condição nova. |
| 5 | Licenciamento (GPL/LGPL) para qualquer distribuição | Ambas | Pendente do proprietário. Nada publicado. |

Itens já confirmados, que **não** são pendências: build APK em `1ee1ad4` (APIs 26/29/34); proveniência e assinatura
do Alpine x86_64 (`816b35a`); PoC p04 (`816b35a`, `1ee1ad4`).
