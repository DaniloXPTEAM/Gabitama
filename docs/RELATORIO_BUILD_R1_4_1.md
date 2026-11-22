# RELATORIO_BUILD_R1_4_1 — Diagnóstico de Toolchain, Verificações Executadas e Guia PowerShell para APK no Windows

**Projeto:** `NOVO_AGENTE_HB` (`com.novoagentehb.app`, `0.2.5-r1.4.1`, `versionCode = 7`)  
**Status oficial do build nesta entrega:** **`APK_NOT_BUILT`** (nenhum APK foi fabricado ou assinado neste sandbox Linux; esta entrega é uma base **pré-APK** pronta para compilação local determinística).

---

## 1. Diagnóstico Real do Ambiente Sandbox vs. Pré-requisitos do Projeto

Verificado via `which` no sandbox `<repo-root>`:

| Ferramenta | Exigência do Projeto (`android/`) | Disponibilidade no Sandbox | Observação |
| --- | --- | --- | --- |
| **JDK 17** (`java`, `javac`) | `JavaVersion.VERSION_17` (`app/build.gradle.kts`) | `MISSING` | Necessário JDK 17 instalado no Windows (`JAVA_HOME`). |
| **Android SDK / Platform 34 / Build-Tools** | `compileSdk = 34`, `targetSdk = 34`, `minSdk = 26` | `MISSING` | Necessário `ANDROID_SDK_ROOT` / `ANDROID_HOME` com `platforms;android-34`. |
| **Gradle Wrapper 8.9** (`gradlew` / `gradlew.bat`) | `gradle-8.9-bin.zip` + `gradle-wrapper.jar` | **PRESENT** | Íntegro e validado por `verify_project_integrity.py` (baixa dependências na 1ª execução online). |
| **AGP / Kotlin / Compose / Hilt / KSP** | `agp 8.7.0`, `kotlin 2.0.0`, `composeBom 2024.08.00`, `hilt 2.52`, `ksp 2.0.0-1.0.24`, `media3 1.4.0` | Declarados em `libs.versions.toml` | Capturados no Gradle Wrapper no PC Windows. |
| **`kotlinc` / `adb` / `emulator` / `ffmpeg`** | Compilação, teste, instalação | `MISSING` | `ffmpeg` ausente; inspeção de mídia feita com `python3` + `av`/`PIL`/`numpy`. |

---

## 2. Verificações Executadas com Sucesso Nesta Sessão (Sem Mentiras)

1. `python3 novo-agente-hb/scripts/tests/verify_project_integrity.py` -> **`8/8 OK`**
   - Valida XML (`15` arquivos), `R.string`/`R.raw`/`R.layout`, `AndroidManifest.xml`, `view_texture_player.xml` (`texture_view` + `clickable=false`), `voice_profiles.json` (`gabi_24l`), `providers_catalog.json`, `1.324` SHA-256 originais `voz-latencia`, `23` registros Drive `Avatar`, `64` arquivos `.kt` (pacotes, delimitadores balanceados, zero `org.json` / `io.coil` / pacote legado), `versionCode = 7` / `0.2.5-r1.4.1`, `HBMotionTruth`, `RoomBackgroundCompatibility` e correção de `catalogStateKey` em `HBState`!
2. `python3 novo-agente-hb/scripts/license-audit/audit_ip_and_no_pet.py` -> **`4/4 OK`**
   - Zero código GPL/Pet de Tama V2 / nanoMuse / OpenMinis.
3. `python3 novo-agente-hb/scripts/tests/verify_mcp_and_skills_mock.py` -> **`2/2 OK`**
   - Handshake real MCP JSON-RPC sobre HTTP local + validação do cliente Kotlin.

**Observação importante corrigida nesta R1.4.1:** na inspeção estática de `HBCharacterStateUnitTest.kt`, detectou-se que o teste lia `hbAdolescentListening.catalogStateKey`, campo que não estava presente em `HBState` na R1.4. O campo `catalogStateKey` foi adicionado a `HBState` e `RoomUiState`, evitando erro de compilação ao rodar `:app:testDebugUnitTest` no Windows.

---

## 3. Guia Completo Windows PowerShell (Preflight + Testes Unitários + Geração do APK)

Abra o **PowerShell** na pasta extraída de `novo-agente-hb\android` (ou no clone local `Jarvis-Projeto\novo-agente-hb\android`) e execute:

```powershell
# =====================================================================
# GABITAMA R1.4.1 — Preflight, Testes JVM e Build APK no Windows
# =====================================================================
$ErrorActionPreference = "Stop"

# 1. Ajuste aqui caso seu JDK 17 ou Android SDK esteja em outro diretório:
if (-not $env:JAVA_HOME) {
    $candidates = @(
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Eclipse Adoptium\jdk-17*",
        "C:\Program Files\Java\jdk-17*"
    )
    foreach ($pattern in $candidates) {
        $found = Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $env:JAVA_HOME = $found.FullName; break }
    }
}
if (-not $env:ANDROID_SDK_ROOT -and $env:LOCALAPPDATA) {
    $sdkDefault = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $sdkDefault) { $env:ANDROID_SDK_ROOT = $sdkDefault }
}
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:Path"

Write-Host "JAVA_HOME        = $env:JAVA_HOME"
Write-Host "ANDROID_SDK_ROOT = $env:ANDROID_SDK_ROOT"

# 2. Preflight obrigatório
java -version
if (-not (Test-Path "$env:ANDROID_SDK_ROOT\platforms\android-34")) {
    throw "Android SDK Platform 34 nao encontrado em $env:ANDROID_SDK_ROOT\platforms\android-34. Instale via Android Studio SDK Manager."
}
if (-not (Test-Path "local.properties")) {
    "sdk.dir=$($env:ANDROID_SDK_ROOT -replace '\\','\\')" | Out-File -Encoding ascii local.properties
    Write-Host "Gerado local.properties apontando para $env:ANDROID_SDK_ROOT"
}

# 3. Verificar Gradle Wrapper e compilar código + rodar testes unitários JVM
.\gradlew.bat --version
.\gradlew.bat :app:compileDebugKotlin --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon

# 4. Gerar APK Debug (e opcionalmente Release)
.\gradlew.bat :app:assembleDebug --no-daemon

$apk = Get-Item "app\build\outputs\apk\debug\app-debug.apk"
$hash = (Get-FileHash $apk.FullName -Algorithm SHA256).Hash.ToLower()
Write-Host "APK gerado com sucesso: $($apk.FullName) ($($apk.Length) bytes)"
Write-Host "SHA-256: $hash"

# 5. (Opcional) Instalar no aparelho via ADB com depuração USB ativada:
# adb install -r $apk.FullName
```

### Checklist rápido pós-build no aparelho/emulador
1. **Home:** trocar fase para `Pré-adolescente` e `Adolescente` no Quarto -> voltar para `Home` e confirmar que o círculo `190.dp` reproduz o vídeo em movimento (não congelado) e mostra o aviso honesto de vídeo `idle` provisório. Ativar *Remover animações* nas Configurações de Acessibilidade do Android e confirmar fallback estático imediato.
2. **Quarto:** escolher `Foto da Galeria` em `Infância`, trocar para `Adolescente` (verificar tela cheia limpa e aviso de incompatibilidade com fundo salvo), voltar para `Infância` e confirmar que a foto escolhida retorna automaticamente sem reconfigurar.
3. **Chat / Teclado:** abrir o Chat, tocar em *Escreva para a Gabi…* e confirmar que `.imePadding()` mantém o campo e o botão `Enviar` visíveis acima do teclado virtual.
4. **Regressão:** validar microfone, `Diagnóstico`, criação de meta/lembrete `+15m`, `Permissões` e `Widget` / `Quick Tile`.
