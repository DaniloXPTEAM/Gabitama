# Hotfix do GitHub Actions #1 — falha em `compileDebugUnitTestKotlin`

O APK Debug foi gerado (`assembleDebug` passou), mas o conjunto de testes não compilou por causa da referência `com.sun.net.httpserver.HttpServer` em `AgentCoreUnitTest.kt`. O compilador Kotlin para testes Android não encontrou o módulo opcional de JDK `jdk.httpserver`.

Este patch mantém o teste de handshake HTTP MCP, substituindo somente o servidor de teste por `MockWebServer` na mesma versão do OkHttp (4.12.0). Nenhum código do aplicativo/produção é modificado.

Arquivos alterados:
- `android/app/src/test/kotlin/com/novoagentehb/app/AgentCoreUnitTest.kt`
- `android/app/build.gradle.kts`
- `android/gradle/libs.versions.toml`

Aplicação local na raiz do repositório:
```bash
git apply GabiTama_Actions_1_Hotfix_Testes.patch
git diff --check
git add android/app/src/test/kotlin/com/novoagentehb/app/AgentCoreUnitTest.kt android/app/build.gradle.kts android/gradle/libs.versions.toml
git commit -m "fix: replace JDK HttpServer with MockWebServer in unit tests"
git push origin main
```

**Atenção:** Os comandos acima são para execução consciente pelo proprietário no repositório GitHub. O patch não foi aplicado remotamente nem validado em Gradle nesta sessão. Depois de aplicar, rodar novamente Actions → Build Android Debug APK → Run workflow. Podem surgir novas falhas de testes; não declarar sucesso sem verificar os logs.
