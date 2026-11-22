# BUILD E TESTES NO WINDOWS / PC — GabiTama (`NOVO_AGENTE_HB`)

Este guia descreve exatamente como abrir, compilar, testar e instalar o **GabiTama (`0.2.5-r1.4.1`, `versionCode = 7`)** no Windows 10/11 usando o **Pacote A (`GabiTama_PC_FONTE_COMPLETA.zip`)**.

---

## 1. Pré-requisitos no Windows

1. **JDK 17 (Eclipse Temurin / Adoptium 17)** — verifique com:
   ```powershell
   java -version
   ```
   *(Precisa exibir `17.x.x`. Se usar outro JDK padrão, defina `JAVA_HOME` apontando para o JDK 17 antes do Gradle).*
2. **Android Studio (Ladybug ou mais recente)** com os seguintes componentes instalados em **Settings → Languages & Frameworks → Android SDK**:
   - **Android SDK Platform 34** (`API 34`)
   - **Android SDK Build-Tools 34.0.0** (ou mais recente `34.x`)
   - **Android SDK Platform-Tools** (`adb`)
   - **Android Emulator** + uma imagem de sistema **API 34 (x86_64 ou arm64 com Google Play / Google APIs)** para testar sem aparelho físico.

---

## 2. Descompactar e Abrir no Android Studio

1. Extraia `GabiTama_PC_FONTE_COMPLETA.zip` para uma pasta curta sem espaços ou acentos (recomendado para evitar limite de caminho do Windows/AAPT2), por exemplo:
   ```text
   C:\Dev\GabiTama\
   ```
2. **Via Android Studio (interface gráfica):**
   - Clique em **Open** e selecione a pasta `C:\Dev\GabiTama\novo-agente-hb\android` (onde ficam `settings.gradle.kts` e `build.gradle.kts`).
   - Aguarde o **Gradle Sync** baixar as dependências Maven (primeira execução leva alguns minutos).
   - O Android Studio gera automaticamente `local.properties` apontando para o seu `sdk.dir` (já ignorado pelo `.gitignore`).

---

## 3. Compilar e Rodar Todos os Testes pelo PowerShell

Abra o **PowerShell** na pasta `C:\Dev\GabiTama\novo-agente-hb\android`:

```powershell
# 1. Garantir ANDROID_HOME (ajuste se seu SDK estiver em outra pasta)
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:Path"

# 2. (Opcional, se tiver Python 3 instalado) rodar auditorias locais
python ..\scripts\tests\verify_project_integrity.py
python ..\scripts\license-audit\audit_ip_and_no_pet.py
python ..\scripts\tests\verify_mcp_and_skills_mock.py

# 3. Rodar TODOS os testes unitários Kotlin JVM e gerar o APK Debug
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --stacktrace
```

**Saídas esperadas em caso de sucesso:**
- **APK Debug:** `app\build\outputs\apk\debug\app-debug.apk`
- **Relatório HTML de testes unitários:** `app\build\reports\tests\testDebugUnitTest\index.html`

> **Dica de auditoria transitiva (opcional após o primeiro sync):**  
> `.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath > ..\evidence\gradle_dependencies_tree.txt`

---

## 4. Instalar e Testar no Emulador ou Celular Android (via `adb`)

Com um emulador aberto (ou celular com **Depuração USB** ativada):

```powershell
# Verificar dispositivo conectado
adb devices

# Instalar o APK gerado
adb install -r .\app\build\outputs\apk\debug\app-debug.apk

# Abrir o aplicativo
adb shell am start -n com.novoagentehb.app/.app.MainActivity

# (Opcional) rodar testes instrumentados se existirem em androidTest
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

---

## 5. Roteiro Rápido de Teste Funcional no Aparelho / Emulador

1. **Ajustes → Provedores:** cole sua chave Google Gemini (`AIza...` ou `AQ....`) e toque em **Testar**.
2. **Home + Chat + Voz:** teste envio por texto, microfone e modo **Gemini Live** (barge-in falando durante a resposta da Gabi).
3. **Quarto da Gabi (`Habitat`):**
   - Teste o botão **SKIP** na introdução e o toque direto sobre a personagem;
   - Alterne as 3 fases (`Infância`, `Pré-adolescente / idade1`, `Adolescente / idade2`) e confirme que a fase continua na Home (`medalhão circular`) e na barra inferior;
   - Volte para `Infância`, teste trocar roupas (`Vestido Vermelho`, `Vestido Lilás`, `Suéter Computador`, `Pijama Estrelas`) e os fundos (incluindo foto da galeria);
   - Teste `Alimentar Gabi` e `Bíblia da Personagem`.
4. **Permissões (`Ajustes → Permissões`):** conceda Microfone, Acesso a Notificações (para prévias de Gmail/notificações e status do Spotify) e Acessibilidade (para automação explícita de WhatsApp com confirmação prévia).
5. **Diagnóstico:** envie exatamente `/diagnostico` no Chat e verifique que o relatório técnico não expõe nenhuma chave ou token.
