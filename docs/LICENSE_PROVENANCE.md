> **Atualização após R1.0:** a tabela histórica abaixo não descreve mais o estado atual. O PTY original do Tama V2/nanoMuse foi incorporado com código-fonte e avisos GPL preservados. A antiga afirmação de zero arquivos GPL ou ausência de referências ao upstream ficou superada. Veja `docs/TAMA_RUNTIME_INTEGRATION.md`; licença e obrigações do conjunto devem ser verificadas antes de distribuição.

# AUDITORIA DE LICENÇAS E PROVENIÊNCIA — LICENSE_PROVENANCE.md (R1.0)

Data da auditoria: 2026-11-21  
Escopo: `novo-agente-hb/` (`com.novoagentehb.app`, identificador interno provisório `NOVO_AGENTE_HB`)

## 0. Estado atual (24/11/2026) — substitui as seções 1 e 2 abaixo

**As tabelas das seções 1 e 2 são HISTÓRICAS (obsoletas).** Não as cite como conclusão atual.

| Tema | Estado atual (verificado nesta data) | Evidência |
| --- | --- | --- |
| Referências a nanoMuse no código | **Presentes.** 34 arquivos com "nanomuse" fora de `.git`. Textos visíveis (`res/`, `AndroidManifest.xml`) não citam nanoMuse. | `grep -ri nanomuse` (ver `docs/AUDIT_TAMA_ORIGINAL_SANDBOX_MATRIX.md`) |
| Namespace `io.github.nanomuse` | Três classes: `hub/HubClient.kt` (sem uso fora do pacote), `sandbox/FeedSandboxScope.kt` (usada pelo Feed da Gabi via `PRootKernel` e `ExecutionCoordinator`), `sandbox/OffloadSocketName.kt` (usada por `NativeOffload`). | imports e chamadas |
| Código GPL | **Há código de origem nanoMuse/Tama V2 sob GPL.** O PTY (`PtyBridge.kt`, `pty_bridge.c`) está registrado em `LICENSE_PENDING_OWNER.txt` com GPL preservado. | `LICENSE_PENDING_OWNER.txt`; `android/app/src/main/assets/licenses/tama-runtime/LICENSE-nanoMuse.txt` (texto integral da GPLv3) |
| PRoot | GPL-2.0, vendorizado em `third_party/tama-runtime/android/deps/proot`. | `THIRD_PARTY_NOTICES.md` |
| Conformidade comercial | **Não declarada.** Pendente do proprietário. | `LICENSE_PENDING_OWNER.txt` |

Avisos legais que **não podem ser removidos** enquanto o código correspondente existir: `LICENSE-nanoMuse.txt`, `NOTICE.txt`, `THIRD_PARTY_NOTICES.md`, os cabeçalhos de licença do PRoot e o aviso do PTY. Renomear um namespace não altera a licença do código.

Histórico: a versão original desta auditoria (2026-11-21) afirmava 0 referências a nanoMuse e 0 arquivos GPL. Essa afirmação foi superada pelo PTY incorporado e pelo código de offload e Feed com origem nanoMuse. Mantida apenas como registro.

---

## 1. Resultado Global da Auditoria IP (HISTÓRICO — 2026-11-21, OBSOLETO)

| Critério | Resultado | Evidência concreta |
| --- | --- | --- |
| Código GNU GPL / LGPL / AGPL herdado | **0 arquivos** | Reimplementação 100% original em `novo-agente-hb/android/app/src/main/kotlin/com/novoagentehb/app/` + `scripts/license-audit/audit_ip_and_no_pet.py` (`4/4 PASS`) |
| Referências `nanoMuse` / `OpenMinis` / `tama-v2` / `tamaaiv2` | **0 ocorrências** | Varredura regex automatizada em toda a árvore `android/` |
| URIs e IDs proibidos (`nanomuse://`, `minis://`, `tama://`, `ai.tama.assistant`, `io.github.nanomuse.*`) | **0 ocorrências** | `AndroidManifest.xml` usa exclusivamente `novoagentehb://` e `com.novoagentehb.app` |
| Segredos / chaves de API em `BuildConfig` ou código | **0 ocorrências** | `app/build.gradle.kts`, `ModelRouter.kt` e `SpotifyBridge.kt` leem apenas de `SecureKeyVault` (AES256-GCM) |
| Gradle Wrapper 8.9 (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) | **Apache-2.0 verificado** | Extraído de `gradle/gradle@v8.9.0`; `META-INF/LICENSE` validado dentro de `gradle-wrapper.jar` |
| Dependências diretas no TOML / SBOM | **0 dependências GPL declaradas** (`DIRECT_TOML_DECLARED_ONLY`) | Todas as coordenadas diretas declaradas em `libs.versions.toml` (`evidence/SBOM.json`); **árvore transitiva não resolvida offline** (`./gradlew app:dependencies` necessário em máquina local) |
| Licença comercial/final do novo produto | **PENDENTE DO PROPRIETÁRIO** | Registrada em `LICENSE_PENDING_OWNER.txt`; nenhuma licença definitiva foi assumida ou publicada |
| Direitos comerciais externos da arte visual Gabi | **REQUER REVISÃO FINAL DO DONO ANTES DE PUBLICAÇÃO PÚBLICA** | Os 1.324 ativos provêm de `DaniloXPTEAM/Jarvis-Projeto@voz-latencia` (veja `docs/HB_ASSET_MAP.md`), permanecendo o app privado até revisão final do proprietário |

## 2. Limite Honesto da Varredura Offline (SBOM v2) (HISTÓRICO)

`evidence/SBOM.json` (`schema: novo_agente_hb_sbom_v2`) declara explicitamente:
- `license_audit_scope = "DIRECT_TOML_DECLARED_ONLY"`
- `transitive_audit_status = "UNVERIFIED_OFFLINE_REQUIRES_GRADLE_DEPENDENCIES"`

Como o sandbox não possui JDK/Android SDK nem acesso aos repositórios Maven (`dl.google.com`, `repo.maven.apache.org`), o projeto não finge ter auditado dependências transitivas sem tê-las resolvido. Ao compilar localmente, recomenda-se rodar `./gradlew app:dependencies --configuration releaseRuntimeClasspath` para fechar a árvore transitiva completa.
