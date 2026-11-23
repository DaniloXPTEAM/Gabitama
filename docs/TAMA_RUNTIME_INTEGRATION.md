# Integração original do runtime Tama V2

Referência: `DaniloXPTEAM/tama-v2` commit `3711f582634721f0c428091489e3776674f90ff9`.

## Primeiro componente integrado

`android/app/src/main/kotlin/com/openminis/app/sandbox/PtyBridge.kt` e `android/app/src/main/cpp/pty_bridge.c` são cópias sem modificação dos arquivos homônimos upstream. O pacote é mantido porque os símbolos JNI são vinculados a esse nome. O CMake foi adaptado ao projeto existente e compila a biblioteca nativa por ABI; a ponte não é inicializada automaticamente nem exposta para executar entrada arbitrária de agentes.

`ai/tama/sandbox/ShellMarkerDecoder.kt` e seus testes também vêm do mesmo commit. A única adaptação é a remoção de um import coringa não utilizado de um pacote ausente; o algoritmo foi preservado. O teste do PTY usa o decodificador original para um marcador sintético fragmentado, além de conferir o status real do processo.

A ponte fornece forkpty, leitura/escrita, resize, sinais e waitpid. **Ela não fornece sozinha PRoot, Alpine, shell persistente, isolamento, limites, controle de permissões ou um computador operacional**. Testes com um comando sintético fixo são apenas testes da ponte; não comprovam terminal completo ou segurança de scripts arbitrários.

## Componentes e dependências ainda necessários

O original usa `PersistentShell.kt` (processo PRoot, multiplexação e saída), `TerminalSession.kt` (sessão PTY), `RootfsManager.kt` (instalação do Alpine e montagem de diretórios), `PRootKernel.kt` (boot, bind mounts, loaders e offloads), `pty_bridge.c`, PRoot e loaders, e assets Alpine. Os arquivos principais de sessão dependem também de vários contratos do upstream; não foram substituídos por stubs.

O `RootfsManager.kt` original foi integrado com adaptações de segurança: o extrator original montava caminhos e criava symlinks a partir do tar sem verificar contenção, e a instalação removia um diretório existente não reconhecido. Agora há limites, validação de caminhos e links, extração em staging, promoção sem substituição e recusa de reset/restauração sem confirmação. Os testes usam tars sintéticos, não o Alpine real; não há prova ainda de compatibilidade integral com o arquivo oficial nem de segurança contra processos concorrentes com o mesmo UID. O módulo ainda não está ligado ao boot ou à interface.

A fonte original do fork PRoot, o script de build, o talloc e os dois loaders vendorizados estão preservados isoladamente em `third_party/tama-runtime/`, fora do APK. Uma rotina de CI compila o PRoot arm64 com o script upstream; sucesso de compilação não é teste de execução.

O repositório upstream não contém `libproot.so` nem o minirootfs Alpine pronto: são artefatos ignorados, produzidos por `android/deps/build_proot.sh` e `android/scripts/prepare_android_sandbox.sh`. O submódulo PRoot está fixado em `4002f4bd3dfc38eb17ab6070d4154b2843eed452`. O build original documenta payloads PRoot/Alpine apenas para arm64; os AVDs x86_64 atuais não validam o ambiente Alpine arm64. Os binários e rootfs precisam ser obtidos ou produzidos, verificados e acompanhados das respectivas fontes e licenças; não devem ser acrescentados como blobs sem proveniência.

## Proveniência e distribuição

O upstream identifica nanoMuse como GPL-3.0-or-later e sua base Android OpenMinis como GPL-3.0. O próprio documento `LICENCIAMENTO_TAMA.md` adverte que a MIT do proprietário cobre apenas suas contribuições próprias, não toda a árvore. Cópias dos textos GPL, `NOTICE`, avisos de terceiros e licença MIT estão em `android/app/src/main/assets/licenses/tama-runtime/`; o código-fonte integrado permanece neste repositório. O upstream lista PRoot como GPL-2.0, talloc estático como LGPL-3.0-or-later e Alpine como conjunto de licenças, inclusive BusyBox GPL-2.0; as licenças específicas e a compatibilidade de cada build ainda precisam ser verificadas no código e materiais efetivamente distribuídos. Esta nota não substitui revisão jurídica nem comprova cumprimento de todas as obrigações do pacote final. A licença final do conjunto continua pendente de definição e revisão do proprietário antes de distribuição.

Não houve inclusão de E2B, VPS, serviço pago, rootfs ou executável PRoot no APK. Os loaders de origem Termux permanecem apenas em `third_party`, e a fonte correspondente exata ainda precisa ser identificada antes de distribuição. Não há alegação de isolamento comprovado; PRoot não deve ser tratado como barreira de segurança suficiente para executar scripts não confiáveis com privilégios do app.
