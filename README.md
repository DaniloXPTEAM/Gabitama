# GabiTama (`NOVO_AGENTE_HB`)

Assistente pessoal Android em **Jetpack Compose / Material 3** centrada na personagem **Gabi / HB**, combinando orquestração de agente com provedores de IA BYOK (**Google Gemini** como pilha principal + Groq, OpenRouter, DeepSeek e Anthropic), modo de voz bidirecional **Gemini Live**, Quarto interativo da Gabi e controles explícitos de segurança e transparência.

> **Status do código & licenciamento (`LICENSE_PENDING_OWNER.txt`):**  
> O identificador `NOVO_AGENTE_HB` / `com.novoagentehb.app` é técnico e provisório. A escolha e publicação do arquivo `LICENSE` definitivo e a liberação pública dos assets visuais de mídia pertencem exclusivamente ao proprietário. Veja `REVISAO_PUBLICACAO_GITHUB.md` e `docs/LICENSE_PROVENANCE.md` antes de redistribuir binários ou mídias.

---

## Principais Recursos

- **Experiência Gabi / HB (`Home`, `Chat`, `Quarto da Gabi`, `Computador`, barra inferior dinâmica):**
  - Tema **Céu de Outono** (`#120F11` obsidiana, `#D96830` cobre, `#C62828` carmesim, `#FFCF8C` dourado);
  - **3 fases selecionáveis pelo usuário** (`Infância` com camadas transparentes RGBA/WebP + guarda-roupa + fundos personalizados; `Pré-adolescente` / `idade1.mp4`; `Adolescente` / `idade2.mp4`);
  - **Transparência explícita de movimento (`HBMotionTruth`):** distingue estados com clipe dedicado de estados em que a fase é preservada usando o clipe idle (`STAGE_PRESERVED_IDLE_ONLY`), e marca looks sem arte como `Arte pendente`.
- **Agente, Memória e Ferramentas (`27` capacidades + `PolicyGate`):**
  - Memória local, metas e lembretes persistidos em **Room** (`allowBackup="false"`), com cofre de chaves isolado em **`EncryptedSharedPreferences` (AES256-GCM)**;
  - Confirmação contextual obrigatória antes de efeitos externos (WhatsApp, ligações, agenda) ou chamadas pagas, e bloqueio rígido de OTP/senhas;
  - Cliente **MCP Streamable HTTP** (JSON-RPC 2.0 + SSE + `Mcp-Session-Id` + proteção SSRF) e execução local de skills com verificação SHA-256 pós-escrita (`ARTIFACT_PREPARED` vs `TASK_EXECUTED`).
- **Voz e Áudio:**
  - Sessão **Gemini Live** (WebSocket `v1beta`, captura PCM 16kHz e playback 24kHz com fila `Channel.UNLIMITED` + flush imediato em *barge-in* `Interrupted`);
  - Disclosure explícito quando `gabi_24l` (Pocket TTS) não está embarcado no dispositivo, acionando TTS do Android sem troca silenciosa.

---

## Como Compilar Localmente (Windows / Linux / macOS)

Requisitos: **JDK 17**, **Android SDK Platform 34**, **Build-Tools 34** e **Gradle Wrapper 8.9** (já incluído em `android/`).

```bash
cd android
chmod +x ./gradlew   # no Linux/macOS
./gradlew testDebugUnitTest assembleDebug --no-daemon
```

No **Windows PowerShell**, consulte o passo a passo completo em [`BUILD_WINDOWS_GABITAMA.md`](BUILD_WINDOWS_GABITAMA.md):

```powershell
cd android
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon
```

O APK de debug é gerado em `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## Compilação na Nuvem via GitHub Actions

O repositório inclui o workflow manual [`.github/workflows/android-debug.yml`](.github/workflows/android-debug.yml):

1. Abra a aba **Actions** no GitHub;
2. Selecione **Build Android Debug APK (GabiTama)**;
3. Clique em **Run workflow** (`workflow_dispatch`);
4. Ao finalizar, baixe o artefato **`gabitama-debug-apk`** contendo o APK debug e os relatórios do `testDebugUnitTest`.

*(Nota: nenhum APK nem workflow foi compilado/executado dentro do ambiente isolado de geração deste código; o primeiro build de CI deve ser disparado após o push pelo proprietário.)*

---

## Auditorias Locais Inclusas

```bash
python3 scripts/tests/verify_project_integrity.py
python3 scripts/license-audit/audit_ip_and_no_pet.py
python3 scripts/tests/verify_mcp_and_skills_mock.py
```

Consulte também a documentação completa em `docs/` (`GATE_T0_AMBIENTE.md`, `RELATORIO_BUILD_E_APK.md`, `MATRIZ_TESTES_ANDROID_COMPLETA.md`, `RELATORIO_UI_E_ANIMACOES.md`, `MATRIZ_REGRESSAO_FUNCOES.md`, `REVISAO_PUBLICACAO_GITHUB.md`).
