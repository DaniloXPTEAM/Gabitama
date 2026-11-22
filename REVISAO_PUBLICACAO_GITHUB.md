# REVISÃO DE PUBLICAÇÃO NO GITHUB PÚBLICO — GabiTama (`NOVO_AGENTE_HB`)

**Status de Publicação:** **`BLOCKED_PENDING_OWNER_REVIEW`** *(etiqueta `READY_FOR_PUBLICATION` explicitamente bloqueada até decisão final do proprietário nos 2 itens da Seção 2)*  
**Escopo avaliado:** Código-fonte, recursos `res/` e `assets/`, scripts, `.gitignore`, workflow `.github/workflows/android-debug.yml` e conteúdo do Pacote B (`GabiTama_GITHUB_PUBLICO_BUILD_PRONTO.zip`).

---

## 1. Checklist Técnico Auditado

| Critério Obrigatório | Resultado | Evidência Verificada |
|---|---|---|
| **Segredos, tokens e chaves API** | **APROVADO (`0` vazamentos)** | `audit_ip_and_no_pet.py` (`4/4 PASS`): zero chaves `AIza...`, `AQ....`, `sk-*`, `gsk_*` fora de mocks sintéticos em `src/test/`; `BuildConfig` sem campos de chave; cofre `EncryptedSharedPreferences` (`novo_agente_hb_secure_prefs`) excluído em `backup_rules.xml` e `data_extraction_rules.xml` |
| **Arquivos locais e keystores** | **APROVADO (`0` arquivos locais)** | `local.properties`, `.gradle/`, `build/`, `*.jks`, `*.keystore` ausentes e ignorados em `.gitignore` + `android/.gitignore` |
| **Caminhos locais do sandbox (`<workspace-root>/`) e IDs privados do Google Drive** | **APROVADO no Pacote B (`sanitizado`)** | No Pacote B (`GabiTama_GITHUB_PUBLICO_BUILD_PRONTO.zip`), todas as referências `<workspace-root>/...` e os IDs internos de pastas privadas do Google Drive foram substituídos por marcadores neutros; note que a coluna `drive_file_id` em `DRIVE_AVATAR_ORIGINALS_SHA256.txt` contém IDs de arquivos de mídia originais (inacessíveis sem autenticação Google Drive), sem qualquer credencial |
| **Áudios privados e dados pessoais** | **APROVADO (`0` ocorrências)** | Áudios privados (ex.: `Gabi Voz isolada.mp3` ou clones vocais `.wav`) explicitamente ausentes e bloqueados por teste automático; zero e-mails pessoais, telefones ou endereços reais |
| **Zero herança GPL / nanoMuse / camada Pet** | **APROVADO (`0` ocorrências)** | `audit_ip_and_no_pet.py`: `0` menções a `nanoMuse`, `OpenMinis`, `tama://`, `GPL/AGPL`, `tamagotchi`, `dragon`; bibliotecas diretas do TOML são Apache-2.0 (`junit` é EPL-1.0 somente em teste) |
| **`.gitignore` e workflow `.github/workflows/android-debug.yml`** | **PRONTO PARA TESTE REMOTO (`WORKFLOW_NOT_TESTED`)** | Workflow `workflow_dispatch` configurado com JDK 17 Temurin, checagem do Android SDK 34 pré-instalado (sem `android-actions/setup-android@v3`), cache Gradle `setup-gradle@v4`, `chmod +x ./gradlew`, `./gradlew testDebugUnitTest assembleDebug` e upload do APK; execução real no GitHub Actions depende do primeiro push do proprietário |

---

## 2. Bloqueios Explícitos que Impedem `READY_FOR_PUBLICATION` Automático

Conforme política estrita do projeto (`LICENSE_PENDING_OWNER.txt` e `docs/LICENSE_PROVENANCE.md`), a publicação pública do repositório **depende exclusivamente de 2 decisões/ações do proprietário** antes ou no momento do push:

1. **Escolha e adição do arquivo `LICENSE` definitivo na raiz (`LICENSE_PENDING_OWNER.txt`):**
   - Nenhuma licença open-source ou proprietária definitiva (ex.: MIT, Apache-2.0, ou All Rights Reserved) foi aplicada autonomamente. O repositório contém apenas `LICENSE_PENDING_OWNER.txt` avisando que o licenciamento definitivo aguarda o proprietário (`Danilo`).
   - *Ação antes de publicar:* decidir a licença do código e criar o arquivo `LICENSE` na raiz do repositório (ou manter explícito caso opte por licença restritiva de código/mídia).
2. **Aprovação final de redistribuição pública dos assets visuais/vídeo (`assets/` e `res/raw/`):**
   - Os arquivos visuais originais de `DaniloXPTEAM/Jarvis-Projeto@voz-latencia` estavam sob `android/LICENSE` (MIT no código), porém as novas mídias integradas a partir da pasta `Avatar` do proprietário e gerações de IA (`idade1.mp4`, `idade2.mp4`, `bible.png`, `gabi-spritesheet.png`, `scene_listening_intent`, `scene_computador_pensando`, `theme/*.png`) não possuem licença pública declarada de redistribuição no código.
   - *Ação antes de publicar:* o proprietário deve confirmar que detém direito de publicação pública dessas mídias (ou mantê-las em licença separada/restrita em `LICENSE` / `THIRD_PARTY_NOTICES.md`).

> **Observações complementares pós-push (não bloqueiam o push, mas são recomendadas na 1ª execução):**
> - **Auditoria transitiva de dependências (`evidence/SBOM.json`):** `transitive_audit_status` está como `UNVERIFIED_OFFLINE_REQUIRES_GRADLE_DEPENDENCIES`. Após o primeiro build bem-sucedido no PC ou CI, executar `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` para fechar a auditoria transitiva completa.
> - **Validar o workflow em Actions:** após subir o Pacote B para o repositório público no GitHub, abrir a aba **Actions → Build Android Debug APK (GabiTama) → Run workflow** e confirmar que `testDebugUnitTest` e `assembleDebug` concluem com status verde e geram o artefato `gabitama-debug-apk`.
