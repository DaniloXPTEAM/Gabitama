# INSTRUÇÕES DE COMPILAÇÃO, COMPATIBILIDADE E BLOQUEIO DE AMBIENTE — `BUILD_LOCAL.md` (R1.1)

## 1. Diagnóstico Real do Sandbox Arena (`APK_NOT_BUILT / BLOCKED`)

Tentativa reproduzida no sandbox em `2026-11-21`:
- Comando: `cd novo-agente-hb/android && ./gradlew --version --offline`
- Saída real: `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.` (`exit code 1`)
- Inventário do ambiente:
  - `java` / `javac` / `kotlinc` / `gradle` / `sdkmanager` / `adb`: **ausentes** (`/usr/lib/jvm` inexistente, `~/.gradle` e `~/.android` inexistentes).
  - Internet: restrita (`dl.google.com`, `repo.maven.apache.org`, `services.gradle.org` bloqueados).
- Conclusão honesta: o sandbox não consegue gerar APK nem rodar `gradlew testDebugUnitTest`. Status de build no sandbox: **`APK_NOT_BUILT / BLOCKED`**.

---

## 2. Verificação Manual de Compatibilidade Gradle / Kotlin / KSP / KAPT / Hilt / Room (R1.1)

Auditoria manual executada sobre `gradle/wrapper/gradle-wrapper.properties`, `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts` e 62 arquivos `.kt`:

| Componente | Versão configurada | Compatibilidade & Correções R1.1 aplicadas |
| --- | --- | --- |
| **Gradle Wrapper** | `8.9` (`gradle-wrapper.jar` Apache-2.0) | Compatível com AGP `8.7.0` (mínimo exigido pelo AGP 8.7.x é Gradle `8.7+`). |
| **Android Gradle Plugin (AGP)** | `8.7.0` | Compatível com `compileSdk = 34`, `targetSdk = 34`, `minSdk = 26`, JDK 17. |
| **Kotlin + Compose Compiler Plugin** | `2.0.0` (`org.jetbrains.kotlin.plugin.compose`) | Correto para Kotlin 2.0+ (Compose compiler integrado via plugin, sem `kotlinCompilerExtensionVersion` legado). |
| **Compose BOM** | `2024.08.00` | Compatível com Kotlin `2.0.0` e `material3` / `material-icons-extended`. |
| **KSP** | `2.0.0-1.0.24` (`room-compiler` 2.6.1) | Match exato com Kotlin `2.0.0`. |
| **Hilt + KAPT** | `2.52` (`hilt-android-compiler` via `kotlin-kapt`) | Compatível com Kotlin `2.0.0` + `kapt { correctErrorTypes = true }` adicionado na R1.1 para evitar falha de tipos gerados com `@HiltViewModel` / Room. |
| **Testes Unitários JVM (`testDebugUnitTest`)** | JUnit `4.13.2` + Coroutines Test `1.8.1` | `testOptions { unitTests.isReturnDefaultValues = true }` adicionado na R1.1; única referência a `org.json.JSONObject` em `VoiceEngineCoordinator.kt` substituída por `com.google.gson.JsonObject` (0 `org.json.*` restantes no projeto). |
| **Dependências limpas** | `accompanist-permissions` removida | Dependência não utilizada removida de `libs.versions.toml` e `app/build.gradle.kts`. |
| **Build determinístico & versão** | `versionCode = 3`, `versionName = "0.2.1-r1.1"` | `BUILD_TIME = "2026-11-21-r1.1"` fixado para evitar recompilações sujas do `BuildConfig`. |

---

## 3. Como Compilar e Testar Localmente (Windows / macOS / Linux)

### Pré-requisitos
- **JDK 17** (`JAVA_HOME` apontando para JDK 17)
- **Android SDK 34** (`platforms;android-34`, `build-tools;34.0.0`, `platform-tools`)
- Criar `novo-agente-hb/android/local.properties` com `sdk.dir=...`

### Comandos (Windows PowerShell / CMD)
```bat
cd novo-agente-hb\android

REM 1) Testes unitários JVM (incluindo servidor HTTP loopback para MCP e LocalSkillExecutor):
.\gradlew.bat testDebugUnitTest

REM 2) Compilação do APK debug independente (com.novoagentehb.app.debug):
.\gradlew.bat assembleDebug

REM 3) Auditoria completa de dependências transitivas:
.\gradlew.bat app:dependencies --configuration releaseRuntimeClasspath

REM 4) Conferir hash SHA-256 do APK:
certutil -hashfile app\build\outputs\apk\debug\app-debug.apk SHA256
```

### Comandos (Linux / macOS)
```bash
cd novo-agente-hb/android
./gradlew testDebugUnitTest
./gradlew assembleDebug
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

### Instalação e teste em aparelho/emulador (`adb`)
```bash
adb devices
adb install -g app/build/outputs/apk/debug/app-debug.apk
adb shell am start -a android.intent.action.VIEW -d "novoagentehb://habitat"
```
