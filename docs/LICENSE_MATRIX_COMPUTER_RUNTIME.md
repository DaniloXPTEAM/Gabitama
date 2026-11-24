# Matriz de licenciamento e proveniência — computador (Tama V2) e runtime Alpine

Data: 2026-11-23. Branch `arena/1dba9faf-gabitama`.

**Escopo.** Este documento inventaria componentes e suas obrigações. **Não declara conformidade.**
Presença de código GPL/LGPL não impede uso no projeto; a análise de conformidade de uma eventual
distribuição é separada e cabe ao proprietário. Nenhum binário de runtime é distribuído: o Alpine
só existe como asset descartável de CI no APK de teste do auxiliar, e não é publicado.

Legenda de status: **verificado** = comprovado por arquivo ou CI neste repositório; **declarado** =
afirmado por outra fonte, não reproduzido; **pendente** = exige decisão ou verificação.

## 1. Componentes

| Componente | Versão / commit | Licença | Fonte | Onde está | Status | Obrigações / pendências |
|---|---|---|---|---|---|---|
| Tama V2 (código original) | commit `3711f582634721f0c428091489e3776674f90ff9` | MIT (`LICENSE-TAMA-MIT.txt`, Copyright 2026 DaniloXPTEAM) | repositório do proprietário | `android/app/src/main/assets/licenses/tama-runtime/` | declarado (arquivo presente) | Confirmar que o MIT cobre todo o código copiado do Tama. |
| nanoMuse (notices do Tama) | não identificado no repo | GPL-3.0 (`LICENSE-nanoMuse.txt`, `NOTICE.txt`) | notices do Tama | mesma pasta de assets | declarado | Dizer se algum código GPL-3.0 do nanoMuse entrou no APK. Os textos estão presentes. |
| `pty_bridge.c` / `PtyBridge.kt` | origem Tama/nanoMuse | **não identificada no arquivo** | `android/app/src/main/cpp/pty_bridge.c` | compilado no APK por `CMakeLists.txt` | **pendente** | Confirmar licença do arquivo. Vincula só bionic (`forkpty`). |
| PRoot (fork OpenMinis / upstream `nano-muse/proot`) | upstream `4002f4bd3dfc38eb17ab6070d4154b2843eed452` | **GPL-2.0** (`deps/proot/COPYING`) | upstream, sem metadados git | `third_party/tama-runtime/android/deps/proot/` | **verificado** (texto presente) | Distribuir binário exige fonte correspondente e aviso. Ver §3. |
| talloc (Samba) | 2.4.2 (script de build) | **LGPL-3.0-or-later** (`deps/talloc/COPYING.LESSER`) | samba.org (`download.samba.org/pub/talloc/talloc-2.4.2.tar.gz`) | `third_party/.../deps/talloc/` | **verificado** (texto presente) | LGPL: vinculação estática exige revisão. Ver §3. |
| Loaders Termux `libproot-loader*.so` | `termux/proot` `4dba3afbf3a63af89b4d9c1a59bf2bda10f4d10f` (candidato, não provado) | GPL-2.0 (PRoot) | Tama snapshot, bytes vendorados | `third_party/.../deps/` | **pendente** | Sem correspondência fonte↔binário comprovada. **Não incluir em release.** Não estão no APK. |
| Alpine Linux minirootfs | `3.21.3` aarch64 | agregado de pacotes | `https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.3-aarch64.tar.gz` | só CI (asset de teste do auxiliar) | **verificado** (SHA-256, GPG, run `45301152901`) | Licença por pacote: ver §2 (lida do APK DB do próprio tarball no CI). |
| BusyBox (dentro do Alpine) | versão a confirmar no APK DB (§2) | GPL-2.0-only (declarado no notice do Tama e no APK DB) | pacote Alpine | dentro do rootfs | declarado | Qualquer distribuição do rootfs exige avisos e fonte. |
| musl libc (dentro do Alpine) | versão a confirmar no APK DB (§2) | MIT (declarado e no APK DB) | pacote Alpine | dentro do rootfs | declarado | Avisos de copyright. |
| Demais pacotes Alpine | ver §2 | ver §2 | Alpine | dentro do rootfs | pendente | Cada licença de pacote deve ser revisada antes de distribuição. |
| Bibliotecas Gradle (AndroidX, Compose, Kotlin, OkHttp, Retrofit, Gson, Hilt etc.) | conforme `libs.versions.toml` | Apache-2.0 / MIT / EPL-1.0 (teste) | Maven/Google | APK | verificado | Já listadas em `THIRD_PARTY_NOTICES.md` §2. Árvore transitiva não resolvida offline. |
| Gradle Wrapper 8.9 | 8.9.0 | Apache-2.0 | gradle/gradle | `android/gradlew` | verificado | Listado em `THIRD_PARTY_NOTICES.md` §1. |
| Termux `termux-packages` (receita histórica) | `0ff4d49dad73ed7a62375504cf96326bb835e132` | GPL/LGPL (conforme recipe) | GitHub | referência | declarado | Usado só para investigação da origem dos loaders. |
| FFmpeg 6.1.2, LAME 3.100 | declarados pelo Tama | LGPL-2.1+ / LGPL-2.0+ | notices do Tama (iOS) | fora deste Android | declarado | Não encontrados no APK deste repositório. Confirmar ausência. |
| iSH (ARM64 fork) | submodule Tama | GPL-3.0 (com exceção App Store) | notices do Tama | fora deste repo | declarado | Não incluído. |
| Assets visuais Gabi / Quarto | `DaniloXPTEAM/Jarvis-Projeto@voz-latencia` | proprietário | `docs/LICENSE_PROVENANCE.md` | `android/app/src/main/assets/` | pendente | Direitos comerciais a confirmar pelo proprietário (já registrado). |

## 2. Inventário de pacotes Alpine 3.21.3 aarch64

O inventário sai da base `lib/apk/db/installed` **dentro do próprio tarball verificado**, lido pelo
`scripts/android/alpine-provenance/verify.sh` no CI (`::notice title=Alpine package::`). Os nomes,
versões e licenças abaixo devem ser copiados do log do job `provenance` do run `45301152901` ou de um
run posterior que execute a versão atual do script. Esta seção fica **pendente** até essa cópia, para não
transcrever de memória.

## 3. Obrigações não resolvidas antes de qualquer distribuição

1. **GPL-2.0 (PRoot, BusyBox, apk-tools e demais pacotes GPL do Alpine):** avisos, texto da licença e
   oferta de fonte correspondente. Para o PRoot, o fork e o commit precisam ser ligados ao binário.
2. **LGPL-3.0 (talloc):** se linkado estaticamente ao `proot`, a obrigação de permitir relinkagem
   precisa ser atendida ou o link precisa ser dinâmico. Decisão de arquitetura do proprietário.
3. **Loaders Termux:** sem prova de fonte/binário. Bloqueados para distribuição.
4. **`pty_bridge.c`:** licença do arquivo não identificada; confirmar antes de publicar o APK.
5. **Textos GPL-3.0 presentes em assets:** confirmar se acompanham código GPL-3.0 realmente distribuído.
6. **Licença do projeto:** pendente de decisão do proprietário (`LICENSE_PENDING_OWNER.txt`).

## 4. O que este documento não prova

- Não prova conformidade de licença de nenhum componente.
- Não prova que o PRoot de `third_party` é o binário usado (não há binário compilado no repositório).
- Não prova que o Alpine é seguro para distribuição; só que o arquivo oficial foi verificado.

## 5. Desvios de compilação no host de teste (não Android; só CI privado)

Aplicados apenas à cópia temporária do PRoot no job `prove-proot-linux-arm64.yml` (fonte preservada, não modificada):

- `HAS_LOADER_32BIT=`: sem loader de 32 bits; o teste roda só guests aarch64.
- `--rosegment` removido do link do loader, com o `ld` padrão do host. Medido: o loader ligado com gold e `--rosegment` sai com SIGSEGV (139) ao ser executado diretamente; o mesmo loader ligado pelo `ld` padrão executa o userland Alpine sob o PRoot (exit 0). `--rosegment` é uma opção que o GNU ld 2.40 não aceita.
- Shim `linux/ashmem.h` apenas de compilação: a extensão ashmem é exclusiva do Android e fica inerte sem `--ashmem-memfd`, que este teste nunca usa.

Nenhum desses desvios é distribuído. A compilação Android mantém as flags upstream. Estes desvios não provam que o loader upstream funciona no Android.
