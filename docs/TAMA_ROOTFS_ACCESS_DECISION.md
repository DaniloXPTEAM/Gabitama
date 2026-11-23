# Decisão arquitetural: acesso da árvore Alpine ao processo isolado

Branch: `arena/1dba9faf-gabitama` · Base de evidências: `47b7bb1` · Status: **BLOQUEADO no desenho atual**

## 1. O que o Tama V2 original exige

Referência: `3711f582634721f0c428091489e3776674f90ff9` (cópia no repositório em
`android/app/src/main/kotlin/com/openminis/app/sandbox/`).

- `RootfsManager.kt`: `rootfsDir = filesDir/alpine-rootfs`; instala `alpine-minirootfs.tar.gz`
  (asset) e `proot-aarch64` (asset) no armazenamento privado do app.
- `PRootKernel.kt` (`buildProotCommand`): executa o PRoot com `-r <rootfsDir>`, `-b /dev`,
  `-b /proc`, `-b /sys`, `-w /root` e binds por sessão. Os binds apontam para
  `filesDir/minis-sessions/...` e `filesDir/minis-global`.
- `PersistentShell.kt`/`TerminalSession.kt`: shell persistente que roda **no processo do app**,
  com acesso a `filesDir` e ao rootfs.
- `NativeOffload.kt`: socket Unix dentro de `rootfsDir`.

Conclusão: o desenho original precisa de uma **árvore de diretórios real e gravável**, acessível
pelo processo que executa o PRoot, e roda com o UID do próprio app.

## 2. Restrições de isolamento do desenho proposto

O objetivo de segurança é que o processo que executa o shell (UID distinto, `isolatedProcess`):

- não acesse dados privados da Gabi (`filesDir`, `databases`, `shared_prefs`);
- não tenha permissão de INTERNET;
- não exija `chmod 777`, root ou acesso amplo.

## 3. Evidências medidas (CI)

| Run | Commit | Resultado relevante |
|---|---|---|
| `45270713716` | `20b7c47` | isolamento 26/29/34 aprovado; concessão de arquivo regular validada |
| `45279728001` | `4591a20` | `DeadObjectException` em `probeDescriptor` (FD de diretório, Binder) |
| `45280994980` | `47b7bb1` | 26/29/34 aprovados; sonda de diretório: `directoryTransfer=DeadObjectException`, `unixDirectory=IOException:EACCES`; `distinctUid=true`, `privateReadable=false`, `ownDataDirWritable=false`, `internetPermission=false`, `grantedPipeReadable=true` |

Interpretação: transferência de **descritor de diretório** falha por Binder e por socket Unix.
Arquivos regulares e pipes funcionam. A recuperação do worker após falha foi demonstrada, mas isso
não comprova acesso a diretórios.

## 4. Fatos de proveniência que também bloqueiam

- `alpine-minirootfs.tar.gz` **não está** no repositório nem em `assets/`.
- `proot-aarch64` **não está** no repositório nem em `assets/`.
- O host `dl-cdn.alpinelinux.org` **não está** na lista de hosts permitidos da sandbox.
- Nenhum binário de rootfs ou loader pode ser copiado sem origem comprovada e licença verificada
  (GPL/LGPL pendentes).

Consequência: mesmo sem o problema de isolamento, o Alpine não pode ser provisionado de forma
verificável a partir deste ambiente.

## 5. Opções avaliadas

| Opção | Descrição | Avaliação |
|---|---|---|
| A. PRoot no processo do app (original) | Rootfs e shell com UID do app | **Rejeitada**: expõe dados privados da Gabi ao shell. |
| B. Diretório passado por FD ao worker | Concessão de diretório via Binder/socket | **Bloqueada**: falha medida em `47b7bb1`. |
| C. Árvore Alpine dentro do armazenamento do worker, populada por tar em pipe | Extração com validação de caminhos no próprio worker | **Bloqueada por H1 refutada** (ver seção 8). |
| D. Worker com app separado (outro APK/UID) | Mover o shell para outro pacote | Mudança de arquitetura do produto; exige decisão do proprietário. |
| E. Permissões amplas em storage compartilhado | Qualquer `chmod` ou pasta pública | **Rejeitada** pelas restrições do projeto. |

## 6. Hipótese testável (C)

> H1: o processo isolado consegue atravessar (`traverse=true`) e gravar em seu `cacheDir`.

- Se **verdadeira**: a opção C é viável para testes de traversal e escrita. Próximo passo: extração
  de tar via pipe com rejeição de traversal e links, e só depois PRoot sobre essa árvore.
- Se **falsa**: a opção C é **bloqueada** pelo modelo de sandbox do Android (o diretório de dados
  do app não é atravessável pelo UID isolado). Nesse caso, a decisão passa a ser entre D e
  "sem shell no aparelho nesta arquitetura".

H1 é refutável pela sonda `storageProbe` (linhas `cacheDir traverse=...` e `cacheWrite=...`).

Reaproveitamento previsto para C: `RootfsManager.extractTar` (original) já possui validação de
traversal/links, coberta por `TamaRootfsSafetyAndroidTest`. Deve ser reutilizado, não reescrito.

## 7. Estado de execução

- Execução de shell: **desabilitada**. Nenhum componente original (`PRootKernel`,
  `PersistentShell`, `TerminalSession`, `ExecutionCoordinator`) é chamado pela UI; eles compilam
  mas estão dormentes.
- ARM64 real, isolamento completo e licenças: **não comprovados**.

## 8. Resultado da hipótese H1 (CI `45287297033`, commit `7bedc54`)

Sonda `storageProbe` no worker isolado (UID 99010), APIs 26 e 29 (job 34 com falha de configuração,
ver abaixo):

- `filesDir exists=false traverse=false read=false write=false`
- `cacheDir exists=false traverse=false read=false write=false`
- `codeCacheDir exists=false traverse=false read=false write=false`
- `noBackupFilesDir exists=false traverse=false read=false write=false`
- `/data/local/tmp exists=true traverse=false read=false write=false`
- `cacheWrite=FileNotFoundException: .../cache/storage-probe/block.bin: open failed: EACCES`

**H1 refutada.** O UID isolado não atravessa nenhum storage próprio do app nem a área temporária
compartilhada. A opção C não é viável. Nenhum diretório gravável está disponível ao worker, portanto
não há como colocar a árvore Alpine sob o processo isolado sem conceder acesso ao diretório de dados
do app ou a um diretório público, o que é vedado.

Falha da API 34 (`isolation (34)`, job `138086808468`): erro de configuração do Gradle no
`setup-java`/download, antes de qualquer teste. Não está relacionada ao código de produção. Já havia
ocorrido em `45276475836`. Não confirmada como transitória; a próxima execução com código alterado
deve confirmar.

## 9. Decisão necessária do proprietário

O bloqueio é arquitetural: um único APK não pode executar o PRoot com árvore gravável e, ao mesmo
tempo, isolar o shell dos dados privados da Gabi. As opções viáveis são:

1. **Shell em pacote separado (recomendado para avaliação).** O shell roda em outro APK com UID
   próprio e árvore Alpine em seu `filesDir`. A Gabi se comunica por Binder com permissão
   assinatura. É o modelo usado por Termux e exige decisão sobre distribuição, assinatura, licenças
   GPL/LGPL e mais um APK. Pode ser testado em CI sem alteração de produção.
2. **Manter a execução desabilitada** e tratar o computador como funcionalidade futura. Não há
   shell operacional nesta arquitetura.
3. **Executar o PRoot no processo do app (desenho original).** Rejeitado: expõe dados privados
   da Gabi ao shell.

Recomendação: opção 1 como experimento de viabilidade em branch separada, após aprovação.
