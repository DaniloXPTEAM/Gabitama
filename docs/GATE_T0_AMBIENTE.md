# GATE T0 — Auditoria Real do Ambiente (Sandbox Arena)

**Data:** 2026-11-21 (UTC)  
**Workspace:** `novo-agente-hb/` (restaurado de `GabiTama_R1_4_1_pre_apk.zip`, SHA-256 `c057c151e71e991af06d35072a98e91a0881d9e1435aa3135dae9bf2c3776e1d`)  
**Veredito Gate T0:** **`PASS` (auditoria/correções estáticas + testes Python/MCP) / `BLOCKED` (compilação Android, Gradle JVM, emulador Android/KVM e ADB)**

---

## 1. Inventário de Hardware, SO e Virtualização

Comandos executados diretamente no sandbox:

```text
$ uname -a
Linux e2b.local 6.1.158+ #1 SMP PREEMPT_DYNAMIC Mon May 11 18:48:24 UTC 2026 x86_64 GNU/Linux

$ cat /etc/os-release | grep -E '^(PRETTY_NAME|VERSION_ID)='
PRETTY_NAME="Debian GNU/Linux 12 (bookworm)"
VERSION_ID="12"

$ nproc
2

$ free -h
               total        used        free      shared  buff/cache   available
Mem:           3.8Gi       269Mi       3.3Gi       1.0Mi       518Mi       3.6Gi
Swap:             0B          0B          0B

$ df -h
/dev/root        21G  1.2G   19G   6% /

$ ls -l /dev/kvm
ls: cannot access '/dev/kvm': No such file or directory
```

## 2. Ferramentas Android / Java / Virtualização Instaladas

```text
$ which java javac kotlinc gradle adb emulator sdkmanager qemu-system-x86_64 docker ffmpeg
(saída vazia — código de retorno 1; nenhum dos binários está instalado)

$ ls -la /usr/lib/jvm
ls: cannot access '/usr/lib/jvm': No such file or directory
```

**Observação:** O projeto possui `android/gradlew`, `android/gradlew.bat`, `android/gradle/wrapper/gradle-wrapper.jar` e `android/gradle/wrapper/gradle-wrapper.properties` (`gradle-8.9-bin.zip`), porém **sem JDK 17** e **sem Android SDK 34** no sandbox não é possível executar `./gradlew`.

## 3. Conectividade de Rede do Sandbox

```text
https://dl.google.com/android/repository/repository2-1.xml    -> curl: (35) SSL_ERROR_SYSCALL (HTTP 000)
https://services.gradle.org/distributions/gradle-8.9-bin.zip  -> curl: (35) SSL_ERROR_SYSCALL (HTTP 000)
https://repo.maven.apache.org/maven2/                         -> curl: (35) SSL_ERROR_SYSCALL (HTTP 000)
https://deb.debian.org/debian/                                -> curl: (35) SSL_ERROR_SYSCALL (HTTP 000)
https://github.com                                            -> HTTP 200
https://api.github.com                                        -> HTTP 200
```

Por política do sandbox, apenas `github.com`, `codeload.github.com`, `api.github.com`, `registry.npmjs.org`, `pypi.org` e `files.pythonhosted.org` são alcançáveis; repositórios Maven/Google/Gradle e pacotes `apt` estão bloqueados.

## 4. Integridade da Base Restaurada (`R1.4.1`)

- Arquivo restaurado do Google Drive (`GabiTama_R1_4_1_pre_apk.zip`): **104.433.551 bytes**, SHA-256 conferido byte a byte (`c057c151e71e991af06d35072a98e91a0881d9e1435aa3135dae9bf2c3776e1d`).
- Referência remota comparada com `DaniloXPTEAM/Jarvis-Projeto@voz-latencia` (`c6e14c0a`, 2026-11-17) para verificar padrões de build CI (`build-apk.yml` sem `android-actions/setup-android@v3`).
- Gate T1 (`gradlew`, JDK 17, `testDebugUnitTest`, `assembleDebug`) e Gate T3 (`emulator`, `connectedDebugAndroidTest`) permanecem honestamente **`BLOCKED`** no sandbox e devem ser executados em PC Windows/Linux (Pacote A) ou GitHub Actions (Pacote B).
