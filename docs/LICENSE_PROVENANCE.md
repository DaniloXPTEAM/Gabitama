> **Atualização após R1.0:** a tabela histórica abaixo não descreve mais o estado atual. O PTY original do Tama V2/nanoMuse foi incorporado com código-fonte e avisos GPL preservados. A antiga afirmação de zero arquivos GPL ou ausência de referências ao upstream ficou superada. Veja `docs/TAMA_RUNTIME_INTEGRATION.md`; licença e obrigações do conjunto devem ser verificadas antes de distribuição.

# AUDITORIA DE LICENÇAS E PROVENIÊNCIA — LICENSE_PROVENANCE.md (R1.0)

Data da auditoria: 2026-11-21  
Escopo: `novo-agente-hb/` (`com.novoagentehb.app`, identificador interno provisório `NOVO_AGENTE_HB`)

## 1. Resultado Global da Auditoria IP

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

## 2. Limite Honesto da Varredura Offline (SBOM v2)

`evidence/SBOM.json` (`schema: novo_agente_hb_sbom_v2`) declara explicitamente:
- `license_audit_scope = "DIRECT_TOML_DECLARED_ONLY"`
- `transitive_audit_status = "UNVERIFIED_OFFLINE_REQUIRES_GRADLE_DEPENDENCIES"`

Como o sandbox não possui JDK/Android SDK nem acesso aos repositórios Maven (`dl.google.com`, `repo.maven.apache.org`), o projeto não finge ter auditado dependências transitivas sem tê-las resolvido. Ao compilar localmente, recomenda-se rodar `./gradlew app:dependencies --configuration releaseRuntimeClasspath` para fechar a árvore transitiva completa.
