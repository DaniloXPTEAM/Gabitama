# Prova de conceito: computador auxiliar em pacote separado (opção 1, experimental)

Branch: `arena/1dba9faf-gabitama`. Autorização: avaliação experimental. **Não** é arquitetura
de produto aprovada, não autoriza distribuição nem instalação pública, e não concede acesso a dados
privados da Gabi.

## 1. O que foi construído

| Parte | Caminho | Papel |
|---|---|---|
| Contrato compartilhado (sem Android) | `android/computer-contract/src/main/kotlin/.../contract/` | Descritor Binder, códigos de transação e resultado, limites, lista de clientes permitidos, validação de caminho e política pura. |
| Auxiliar | `android/computer-helper/` (`com.novoagentehb.computerhelper`) | Pacote separado, `minSdk 26`, **sem INTERNET**, permissão `USE_HELPER` com `protectionLevel="signature"`, serviço exportado restrito pela permissão. Opera apenas em `filesDir/computer-poc-tree`. |
| Cliente na Gabi | `android/app/src/main/kotlin/.../computer/helper/ComputerHelperClient.kt` | Intent explícita para o pacote auxiliar, timeouts reais, cancelamento, reconexão, retentativa somente para leitura. Não é usado pela UI. |
| Permissão na Gabi | `android/app/src/debug/AndroidManifest.xml` | `uses-permission` apenas no build de depuração. |
| Testes JVM | `android/computer-contract/src/test/...` (executados pelo módulo `app`) | Traversal, limites, quota, política de autorização. |
| Teste instrumentado | `android/app/src/androidTest/.../computer/helper/ComputerHelperPocAndroidTest.kt` | 10 testes, executados no processo da Gabi contra o auxiliar real. |
| CI | `.github/workflows/test-computer-helper-poc.yml` + `scripts/android/computer-helper-poc/` | Matriz API 26, 29 e 34, APKs descartáveis. |

## 2. Operações (fixas)

`STATUS`, `WRITE(relativo, bytes)`, `READ(relativo)`, `SHA256(relativo)` e
`ACCESS_PROBE(pacote-do-chamador)`. Não existe shell, nem caminho absoluto, nem execução.
`ACCESS_PROBE` devolve somente metadados (errno de leitura) dos diretórios privados do próprio
chamador e nunca conteúdo.

## 3. Controles de segurança implementados

- **Identidade:** cada transação verifica `checkCallingPermission(USE_HELPER)` e se algum pacote do
  UID chamador está na lista `ALLOWED_CLIENT_PACKAGES` (`com.novoagentehb.app` e `.debug`).
- **Por que não em `onBind`:** dentro de `onBind`, `Binder.getCallingUid()` identifica o processo de
  sistema, não o cliente. A primeira versão checava ali e negava todas as chamadas (CI `45291979919`).
  O bind é protegido pelo `android:permission` do serviço; a autorização real é por transação.
- **Caminhos:** rejeita absolutos, `..`, `.`, barra invertida, nulo, segmentos ocultos, mais de 4
  níveis e caracteres fora de `[A-Za-z0-9._-]`. Também rejeita ancestrais que são links simbólicos e
  canônicos fora da árvore.
- **Limites:** payload 64 KiB, arquivo 256 KiB, árvore 4 MiB, 256 entradas, 2 operações simultâneas
  (`BUSY` quando excedido), timeout de 3 s.
- **Escrita atômica:** arquivo temporário, `fsync`, `rename` no mesmo diretório.
- **Cliente:** deadline por agendador (não só `withTimeout`), interrupção do `Future` no
  cancelamento, descarte da conexão após timeout ou `DeadObjectException`, escrita nunca repetida
  automaticamente.
- **Permissões:** o auxiliar não declara INTERNET nem permissões de armazenamento.

## 4. Evidências

Ver a seção 6 (preenchida após o CI). Critérios da aprovação do proprietário, situação atual:

| Critério | Situação |
|---|---|
| 1. Coexistência dos dois pacotes | Testado: `separatePackageHasOwnUidAndNoInternet` |
| 2. UID e armazenamento próprios | UIDs distintos testados; armazenamento próprio exercitado por WRITE/READ |
| 3. Auxiliar acessa a própria árvore | `writeReadAndDigestInsideHelperTree` |
| 4. Gabi pede operação fixa e recebe resultado | `authorizedClientReceivesStructuredStatusFromOwnTree` e demais |
| 5. Clientes não autorizados recusados | **Parcial.** Recusa pelo sistema (permissão de assinatura) e pela política (`HelperPolicyTest`) é testada em JVM. **Não há teste instrumentado com um cliente real não autorizado** (exigiria terceiro APK assinado com outra chave). |
| 6. Auxiliar não lê dados privados da Gabi | `helperCannotReadGabiPrivateDirectories`: nenhum diretório legível. Errno observado: ver seção 6. |
| 7. APIs 26, 29 e 34 | Ver seção 6 |
| 8. Falha, desconexão, morte, cancelamento | Morte por `am force-stop` e reconexão, cancelamento, desconexão explícita e chamadas concorrentes testados. **Não testado:** morte no meio de uma escrita e timeout real por transação bloqueada. |

## 5. O que esta prova NÃO demonstra

- Não demonstra PRoot, Alpine, PersistentShell, terminal ou qualquer execução de comando.
- Não demonstra ARM64 real.
- Não demonstra isolamento completo (SELinux, sandbox de rede do processo, limites de CPU e memória
  de execução). Os limites atuais são de operação de dados, não de processo de execução.
- Não demonstra instalação, atualização ou experiência de usuário de dois pacotes.
- Não demonstra conformidade GPL/LGPL nem proveniência de runtime.

## 6. Resultados de CI

Commit de referência: `e912d37`. Run: `45294872474` (`test-computer-helper-poc.yml`), **sucesso nas APIs 26, 29 e 34**,
`OK (10 tests)` em cada uma.

| API | UID gabi / auxiliar | `internet` do auxiliar | errno do auxiliar sobre `files/`, `databases/`, `shared_prefs/` da Gabi |
|---|---|---|---|
| 26 | 10078 / 10077 | false | EACCES / EACCES / EACCES |
| 29 | 10147 / 10146 | false | EACCES / EACCES / EACCES |
| 34 | 10193 / 10192 | false | ENOENT / ENOENT / ENOENT |

Na API 34, o auxiliar não consegue sequer enxergar o caminho (`ENOENT`). Nas APIs 26 e 29 o caminho
existe e o acesso é recusado (`EACCES`). Nas três, nenhum diretório é legível. Tratar esse resultado
como negação, não como equivalência de mecanismo.

Concorrência: 5 chamadas simultâneas retornaram OK em todas as APIs. Morte por `am force-stop`, seguida
de nova chamada e leitura de dado gravado antes da morte, passou nas três APIs.

Registro de falhas que levaram a correções (não ocultadas):

- `45291979919`: negação total causada por autorização em `onBind` (corrigida em `79d91a9`).
- `45291130466`, `45291979914`: falha de compilação por declaração removida acidentalmente (`8aae70c`).
- `45293751255`: API 34 exigia errno específico (corrigido em `e912d37`, com a propriedade "não legível").

Build JVM e APKs do mesmo commit: ver run `45294872548` (`Build Android Debug APK`).

Suíte compartilhada (`Build Android Debug APK`), correção registrada:

- `45294872548` e `45300861864` falharam em 26/29/34 com 9 testes com falha cada. Os 9 são da
  `ComputerHelperPocAndroidTest`: a suíte compartilhada não instala `computer-helper-debug.apk`,
  então o cliente não consegue se vincular. O validador também estava com contagem fixa (29), o que
  mascarava a falha como "Incomplete run" ao contar os 39 `@Test` (correção em `226ae8a`).
- Correção em `5439f0d`: a classe do helper é excluída de `connected-tests.sh` (`notClass`) e do
  validador (contagem derivada das fontes, 29), e o validador falha se essa classe executar.
- Run `45302512564` (`5439f0d`): sucesso em 26, 29 e 34, `tests=29 failures=0 errors=0 skipped=0`.
  A prova do helper continua coberta pelo seu próprio workflow (`e912d37`, `OK (10 tests)`).

Proveniência do Alpine (CI apenas, sem extrair nem executar): `.github/workflows/verify-alpine-provenance.yml`
(`5915b02`, run `45301152901`, sucesso). Verifica SHA-256 do `alpine-minirootfs-3.21.3-aarch64.tar.gz`
(`ead8a4b3…261ea`), assinatura GPG com a chave fixada por impressão digital
(`0482 D840 22F5 2DF1 C4E7 CD43 293A CD09 07D9 495A`) e inspeciona as entradas do tar. O relatório
não pôde ser baixado do sandbox (artefato com `EOF`), então só o sucesso dos passos é registrado aqui.
Isso não licencia distribuição do runtime (seção 8).

## 7. Computador original (PRootKernel, PersistentShell, TerminalSession, ExecutionCoordinator, ShellExecutor, RootfsManager, PtyBridge)

Estado: **presentes no repositório** em `android/app/src/main/kotlin/com/openminis/app/sandbox/`
(cópia do Tama V2), **não integrados** ao auxiliar nem à UI. Nenhuma nova versão simplificada foi
criada. A integração depende das etapas abaixo, nesta ordem:

1. Provisionar a árvore Alpine **dentro do pacote auxiliar**. Hoje o `RootfsManager` original espera
   `alpine-minirootfs.tar.gz` e `proot-aarch64` em `assets/`, e nenhum dos dois existe no repositório.
   O `RootfsManager.extractTar` original já valida traversal e links (`TamaRootfsSafetyAndroidTest`) e
   deve ser reutilizado no auxiliar, não reescrito.
2. Trocar `filesDir` por `filesDir` do auxiliar em `RootfsManager` e `PRootKernel`. Hoje ambos
   assumem o processo do app.
3. Expor a execução pelo contrato do auxiliar com operação **fixa** (ex.: "rodar o bootstrap"),
   nunca comando arbitrário. Antes, validar sem shell a extração e o `proot --version` no auxiliar.
4. Manter `PersistentShell` e `TerminalSession` desabilitados até cumprir: isolamento de rede,
   limites de CPU/memória/armazenamento de execução, cancelamento/morte verificados e teste ARM64.

## 8. Licenciamento e proveniência (não há conformidade declarada)

- `android/deps/proot/COPYING` (PRoot): GPL-2.0. Distribuir binário exige fornecer fonte
  correspondente e os avisos.
- `android/deps/talloc/COPYING.LESSER` (talloc): LGPL-3.0. Linkagem estática exige atender à LGPL.
- Loaders Termux e `proot-aarch64`: **proveniência não comprovada no repositório**. Não copiar sem
  origem verificada.
- Alpine minirootfs: licença e checksums não verificados neste ambiente (CDN não permitido).
- `THIRD_PARTY_NOTICES.md` afirma "nenhum componente GPL/LGPL". Essa afirmação está **desatualizada**
  diante de `third_party/tama-runtime` e de `PtyBridge` do Tama V2 e precisa de revisão do proprietário
  antes de qualquer distribuição.
- Não há evidência de ARM64 real neste ciclo. A prova atual roda em emulador x86_64.

## 9. Resultados desta fase (Alpine 3.21.3 aarch64, CI privado)

| Evidência | Onde | Resultado |
|---|---|---|
| Provisionamento no pacote auxiliar (API 26, 29, 34; x86_64) | PoC `511f973` | sucesso: `state=INSTALLED release=3.21.3 arch=aarch64 files=522 sha256=ead8a4b3…ff261ea` em `filesDir` privado do auxiliar |
| Idempotência (a02) | PoC `511f973` | inode do marcador igual antes e depois da segunda chamada (sem reextração) |
| Recuperação após morte do processo (a03) | PoC `511f973` | após `am force-stop`, `state=INSTALLED` |
| Leitura do filesDir pelo teste (`run-as`) | PoC `511f973` | funciona só sem metacaracteres de shell (`UiAutomation` não usa shell); antes, todas as leituras voltavam vazias |
| PRoot original executa Alpine aarch64 (Nível C) | Nível C/D `73001d1` | sucesso em `ubuntu-24.04-arm` (Linux ARM64 real, não Android) |
| Bootstrap diagnóstico fixo (Nível D) | Nível C/D `73001d1` | sucesso (busybox, release, `/bin/sh`) |

Desvios de host do Nível C/D (somente CI privado, documentados em `docs/LICENSE_MATRIX_COMPUTER_RUNTIME.md` §5):
o loader ligado com gold e `--rosegment` sai com SIGSEGV ao rodar diretamente; a mesma fonte ligada pelo `ld` padrão, sem `--rosegment`, executa o Alpine. Esses desvios não provam o loader upstream nem o PRoot no Android.

O que continua NÃO comprovado: execução ARM64 no Android (bionic, loaders Termux), PTY, shell persistente, isolamento do rootfs dentro do app, distribuição de runtime e conformidade de licença. A integração com o computador original segue bloqueada pela seção 7.
