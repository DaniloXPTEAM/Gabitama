# computer-helper (experimental, debug/test only)

Pacote auxiliar do computador original. Não é distribuído a usuários. Operações fixas somente; não há shell,
caminho livre nem comando vindo do chamador.

## Transações (`HelperContract`)

- `TX_PROVISION_*`: instala o Alpine 3.21.3 aarch64 (produto/teste) no `filesDir` do auxiliar, verificado por sha256 fixado.
- `TX_TEST_X86_PROVISION_*`: variante Alpine 3.21.3 x86_64, **somente teste de CI no emulador**. Diretório e marcador próprios.
- `TX_PROOT_START|STATUS|CANCEL`: executa uma operação fixa com o PRoot original (`libproot.so` do `nativeLibraryDir`).
  - `PROOT_OP_VERSION`: `proot --version`.
  - `PROOT_OP_HOST_SLEEP`: teste de timeout e cancelamento.
  - `PROOT_OP_ALPINE_X86_PROBE`: `exec /bin/busybox cat /etc/alpine-release /etc/os-release` dentro do rootfs x86_64.
    Exige o rootfs de teste instalado. Não usa `fork`.

## Limites observados

- `fork()` no guest x86_64 retorna ENOSYS no emulador (runs `45381377014`, `45382920833`). Causa não diagnosticada.
  `PROOT_NO_SECCOMP=1` não altera o resultado.
- Limpeza: ao término, mata processos do próprio UID cujo executável está na árvore do rootfs. Há corrida de PID
  entre leitura de `/proc` e `kill`.
- Execução ARM64 não comprovada. Ver `docs/COMPUTER_HELPER_ALPINE_X86_PROOF.md`.
