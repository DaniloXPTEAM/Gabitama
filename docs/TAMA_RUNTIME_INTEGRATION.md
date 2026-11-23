# Integração original do runtime Tama V2

Referência: `DaniloXPTEAM/tama-v2` commit `3711f582634721f0c428091489e3776674f90ff9`.

## Primeiro componente integrado

`android/app/src/main/kotlin/com/openminis/app/sandbox/PtyBridge.kt` e `android/app/src/main/cpp/pty_bridge.c` são cópias sem modificação dos arquivos homônimos upstream. O pacote é mantido porque os símbolos JNI são vinculados a esse nome. O CMake foi adaptado ao projeto existente e compila a biblioteca nativa por ABI; a ponte não é inicializada automaticamente nem exposta para executar entrada arbitrária de agentes.

`ai/tama/sandbox/ShellMarkerDecoder.kt` e seus testes também vêm do mesmo commit. A única adaptação é a remoção de um import coringa não utilizado de um pacote ausente; o algoritmo foi preservado. O teste do PTY usa o decodificador original para um marcador sintético fragmentado, além de conferir o status real do processo.

A ponte fornece forkpty, leitura/escrita, resize, sinais e waitpid. **Ela não fornece sozinha PRoot, Alpine, shell persistente, isolamento, limites, controle de permissões ou um computador operacional**. Testes com um comando sintético fixo são apenas testes da ponte; não comprovam terminal completo ou segurança de scripts arbitrários.

## Componentes e dependências ainda necessários

O original usa `PersistentShell.kt` (processo PRoot, multiplexação e saída), `TerminalSession.kt` (sessão PTY), `RootfsManager.kt` (instalação do Alpine e montagem de diretórios), `PRootKernel.kt` (boot, bind mounts, loaders e offloads), `pty_bridge.c`, PRoot e loaders, e assets Alpine. Os arquivos principais de sessão dependem também de vários contratos do upstream; não foram substituídos por stubs.

O `RootfsManager.kt` original foi integrado com adaptações de segurança: o extrator original montava caminhos e criava symlinks a partir do tar sem verificar contenção, e a instalação removia um diretório existente não reconhecido. Agora há limites, validação de caminhos e links, extração em staging, promoção sem substituição e recusa de reset/restauração sem confirmação. Testes sintéticos cobrem traversal, links, truncamento, checksum tar e CRC gzip. A extração do minirootfs Alpine 3.21.3 aarch64 original também passou em AVD API 34 x86_64 ([run 45180134117](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45180134117)); SHA-256 `ead8a4b37867bd19e7417dd078748e2312c0aea364403d96758d63ea8ff261ea`, verificado contra o checksum publicado e fixado no workflow. Isto não valida execução ARM, instalação completa, reset, restauração ou segurança contra processos concorrentes com o mesmo UID. O reset upstream, mesmo com confirmação, ainda tem operações destrutivas e cópia de dados com symlinks que precisam de revisão/testes; não ligá-lo à interface. O módulo ainda não está ligado ao boot ou à interface.

A fonte original do fork PRoot, o script de build, o talloc e os dois loaders vendorizados estão preservados isoladamente em `third_party/tama-runtime/`, fora do APK. Uma rotina de CI compila o PRoot arm64 com o script upstream; sucesso de compilação não é teste de execução.

O repositório upstream não contém `libproot.so` nem o minirootfs Alpine pronto: são artefatos ignorados, produzidos por `android/deps/build_proot.sh` e `android/scripts/prepare_android_sandbox.sh`. O submódulo PRoot está fixado em `4002f4bd3dfc38eb17ab6070d4154b2843eed452`. O build original documenta payloads PRoot/Alpine apenas para arm64; os AVDs x86_64 atuais não validam o ambiente Alpine arm64. O PRoot foi compilado isoladamente em CI e o rootfs oficial foi utilizado em teste de extração, mas nenhum deles está no APK. Antes de distribuição, os binários e rootfs devem ser acompanhados das respectivas fontes e licenças; não devem ser acrescentados como blobs sem proveniência.

## Proveniência e distribuição

O upstream identifica nanoMuse como GPL-3.0-or-later e sua base Android OpenMinis como GPL-3.0. O próprio documento `LICENCIAMENTO_TAMA.md` adverte que a MIT do proprietário cobre apenas suas contribuições próprias, não toda a árvore. Cópias dos textos GPL, `NOTICE`, avisos de terceiros e licença MIT estão em `android/app/src/main/assets/licenses/tama-runtime/`; o código-fonte integrado permanece neste repositório. O upstream lista PRoot como GPL-2.0, talloc estático como LGPL-3.0-or-later e Alpine como conjunto de licenças, inclusive BusyBox GPL-2.0; as licenças específicas e a compatibilidade de cada build ainda precisam ser verificadas no código e materiais efetivamente distribuídos. Esta nota não substitui revisão jurídica nem comprova cumprimento de todas as obrigações do pacote final. A licença final do conjunto continua pendente de definição e revisão do proprietário antes de distribuição.

Não houve inclusão de E2B, VPS, serviço pago, rootfs ou executável PRoot no APK. Os loaders de origem Termux permanecem apenas em `third_party`, e a fonte correspondente exata ainda precisa ser identificada antes de distribuição. Não há alegação de isolamento comprovado; PRoot não deve ser tratado como barreira de segurança suficiente para executar scripts não confiáveis com privilégios do app.

## Portabilidade das sessões originais (em andamento)

`PRootKernel`, `PersistentShell`, `TerminalSession`, `ExecutionCoordinator`,
`ShellExecutor`, as políticas de timeout/seccomp, o servidor de offload e suas
dependências de repositório e privacidade foram trazidos do mesmo commit upstream.
Foram adaptados o namespace de `BuildConfig`, a serialização Kotlin e o modo de
proteção de sessões/privacidade do upstream, habilitado para este aplicativo.
A integração de preferências de mirrors upstream ainda está pendente; não foi
iniciada descoberta automática de mirrors. O código portado não é registrado na
interface nem como ferramenta do agente. PRoot e rootfs ainda não são incluídos
no APK de produção, e o isolamento de execução de entrada arbitrária, controles
de permissão e testes arm64 permanecem obrigatórios antes da ativação.

## Evidências de execução e bloqueio de isolamento

Em [45204514747](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45204514747),
uma fixture descartável no AVD API 34 x86_64 iniciou o shell original com Alpine
3.21.3 e PRoot experimental, executou processo externo e verificou registradores
após fork, persistência, erro, timeout, cancelamento e stop da sessão PTY. O
PRoot da fixture tem backports experimentais específicos de x86; não são o
binário de produção nem prova para ARM. O término usa SIGQUIT tratado pelo
PRoot para matar os tracees conhecidos antes do fallback forçado; o teste não
comprova eliminação de todos os descendentes em todas as corridas ou falhas.

A tentativa de iniciar um `isolatedProcess` usando a Application existente falhou
em [45207454876](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45207454876):
a inicialização Hilt atingiu `SecureKeyVault` e o serviço Keystore não estava
disponível naquele processo isolado. Em uma fixture descartável que troca a
Application por `android.app.Application`, [45208717977](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45208717977)
verificou UID diferente, impossibilidade de ler um arquivo privado fixo do app,
permissão INTERNET não concedida e diretório privado do app não gravável. Esta
sonda não executa shell, não prova que PRoot funcione sob UID isolado e não é
uma arquitetura pronta para produção. Antes de ativar execução arbitrária é
necessário resolver inicialização sem acesso a credenciais, provisionamento e
limpeza de rootfs próprio, IPC autenticado e limitado, quotas, disponibilidade
nativa e testes adversariais. Não compartilhar os diretórios privados do app
com o processo executor apenas para contornar essa falha.

Na fixture isolada subsequente [45210360551](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45210360551),
um pipe explicitamente entregue por Binder pôde ser lido pelo UID isolado,
enquanto o arquivo privado fixo permaneceu inacessível. Isso demonstra somente
um canal de transferência de dados concedido, não a instalação ou execução de
Alpine/PRoot nem contenção de processos.

O servidor de offload agora recusa, antes de ler o protocolo, conexões sem peer
PRoot registrado pelo host. A sessão PTY original ainda não registra seu peer;
por isso seu offload está indisponível por segurança, sem afetar o código de UI
existente, que ainda não usa este runtime. Foram adicionados limites de workers,
leitura e tamanho acumulado da requisição; esses limites não constituem
isolamento. [45222729352](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45222729352)
exercitou rejeição de peer, 16 conexões simultâneas, limite acumulado e timeout.
[45233631814](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45233631814)
validou na fixture x86 um prazo absoluto para entrada lenta e a rejeição de uma
saída de handler excessiva. Isso não limita a memória já alocada pelo handler
nem o tempo de execução dele, e não comprova isolamento geral.

A gravação de respostas de offload no `/tmp` compartilhado foi alterada para
nome aleatório e criação exclusiva com `O_NOFOLLOW`; o teste de symlink em AVD passou em
[45212341786](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45212341786). Isso protege apenas a abertura do componente final: um
convidado com o mesmo UID ainda pode alterar a árvore ou substituir a resposta.
Não é uma interface apropriada para entrada não confiável. Além disso,
a rejeição de peers e os limites de protocolo não resolvem corridas no filesystem,
revogação de operações já iniciadas nem esgotamento por pedidos lentos de peers
admitidos. A ativação segue bloqueada até revisar e testar esses casos com uma
fronteira de UID apropriada.

## Incremento Hilt e estados reais do coordenador

A Application agora injeta os dois componentes via `dagger.Lazy`, mantendo a
inicialização explícita e imediata no processo normal, mas não instancia a Gabi,
o orquestrador ou o cofre no UID isolado. O serviço de produção não exportado é
somente um endpoint de ciclo de vida e identidade diagnóstica, sem qualquer
interface de comandos. [45222655478](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45222655478)
validou o bind no AVD API 34 com a Application Hilt real, além de confirmar a
inicialização da Gabi e do orquestrador no processo principal. A identidade
retornada pelo serviço não é autenticação contra um processo comprometido.

[45224335875](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45224335875)
validou na fixture x86 o `ExecutionCoordinator` original sobre PRoot e shell
persistente: criação, estado persistente, erro e encerramento. Isso não conecta
o coordenador à UI, não valida ARM nem justifica executar comandos não confiáveis.
Na fixture corrigida [45230243304](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45230243304),
o envio de um FD de diretório retornou `DeadObjectException` nas APIs 29 e 34;
a causa exata (transferência, morte do serviço ou outro limite Binder) ainda não
foi estabelecida. Um pipe de dados fixos foi transferido, mas não é um filesystem
executável. Resultados anteriores que indicaram FD de diretório ausente eram
inválidos: a fixture havia colocado o FD no Bundle errado; isso foi corrigido.

O PRoot upstream recebe `exit_code` do offload, mas o código reescreve o comando
como `/bin/cat` e não devolve esse código ao shell. A fixture x86 aplica uma
adaptação experimental em cópia descartável, usando argumentos separados sem
interpolação de entrada, e [45225217490](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45225217490)
passou com um handler fixo de retorno 7. Essa correção não está aplicada ao
PRoot de produção e precisa de revisão de compatibilidade, segurança e ARM.

A preparação de um teste ARM64 fixo está em `scripts/android/arm64-shell-fixture/`;
o workflow só prepara e compila uma fixture descartável, sem publicá-la nem
executá-la ([45229478136](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45229478136),
incluindo o teste PTY). O script de execução só aceita um AVD ARM64 descartável
e comandos fixos; ainda não foi executado. Os loaders vendorizados permanecem sem comprovação de fontes exatas;
nenhum resultado de preparação constitui validação física ou autorização para
distribuição.


Em [45231567097](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45231567097),
a Application Hilt real e o endpoint isolado foram testados nas APIs 26, 29 e
34; o UID do endpoint foi distinto do principal. O processo isolado leu um
pipe explicitamente concedido, não leu o arquivo privado fixo e não recebeu
INTERNET. A tentativa de transferir um FD de diretório retornou
`DeadObjectException` nas três APIs; não se sabe ainda a causa. Um único
executável fixo `/system/bin/true` terminou com código zero nas APIs 26 e 29;
na API 34 ocorreu `IOException`, ainda sem causa classificada. Isso não testa
PRoot, ptrace, acesso a rootfs nem segurança de execução arbitrária. O erro
anterior no teste API 26 foi causado pela limpeza de logcat na fixture, não
por uma falha comprovada do serviço; a rotina agora tolera essa limitação.

A fixture ARM64 compilou em [45231495275](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45231495275)
com Alpine fixado e a adaptação experimental de status do offload aplicada apenas
na cópia de build. O workflow não publica nem executa o APK de teste; o resultado
não é validação ARM ou solução das obrigações de distribuição.


Em [45234107903](https://github.com/DaniloXPTEAM/Gabitama/actions/runs/45234107903),
a fixture executou um binário nativo fixo, sem entradas ou efeitos, empacotado
apenas no APK descartável da CI. Ele retornou zero sob o UID isolado nas APIs 26,
29 e 34. Na API 34, `/system/bin/true` separado retornou `IOException:EACCES`.
Estes resultados não demonstram PRoot, ptrace, execução de Alpine ou segurança.
