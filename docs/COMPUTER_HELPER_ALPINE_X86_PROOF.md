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
| `45382920833` | `f27bfae` | Mesma falha com `PROOT_NO_SECCOMP=1` (fallback documentado do PRoot). Emulador x86_64 nas APIs 26, 29 e 34. |
| `45384389673` | `816b35a` | Sucesso: guest usa `exec` (sem `fork`) e lê `/etc/alpine-release` e `/etc/os-release`. |

Hipótese descartada: o filtro seccomp do PRoot. `PROOT_NO_SECCOMP=1` desliga só a aceleração, e o `fork` continuou
com ENOSYS. O PRoot não tem tratamento de `clone` que explique o erro. A causa do ENOSYS no `fork` do guest
permanece **não diagnosticada**; o teste evita `fork`. Nenhum binário do PRoot foi alterado.

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

## Pendências

1. Diagnosticar e corrigir `fork()` no guest x86_64 (investigação no PRoot com ptrace/clone; teste dedicado).
2. Adaptar `PRootKernel`, `RootfsManager`, `ExecutionCoordinator`, `ShellExecutor`, `PtyBridge`, `TerminalSession` e
   `PersistentShell` ao auxiliar, mantendo execução arbitrária desabilitada.
3. Execução ARM64 real (depende de infraestrutura com KVM ou dispositivo autorizado).
4. Licenciamento (GPL/LGPL) para qualquer distribuição futura. Nada disto está publicado.
