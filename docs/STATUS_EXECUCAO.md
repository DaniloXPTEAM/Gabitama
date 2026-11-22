# STATUS DE EXECUÇÃO POR GATES — NOVO_AGENTE_HB (R0 + R1.0)

Data: 2026-11-21  
Branch ativa: `arena/cc3c891a-jarvis-projeto` (base `dcae193c02737a34e6f7f0fff8d4051d490e50d4`)

| Gate | Descrição | Estado atual | Evidência concreta | Bloqueios honestos |
| --- | --- | --- | --- | --- |
| **C0** | Auditoria profunda de `NOVO_AGENTE_HB_projeto_completo.zip` e inventário visual de 1.324 assets | **CONCLUÍDO** | `docs/HB_ASSET_MAP.md`, `docs/GABITAMA_CORRECOES_R1.md`, inspeção visual direta de PNGs/WebPs | Nenhum |
| **C1** | Reconstrução da HB, `HBCharacterController`, Quarto da Gabi em camadas e sincronia Home/Chat/Quarto | **IMPLEMENTADO E VALIDADO ESTATICAMENTE / UI `NOT_TESTED`** | `HBCharacterModel.kt`, `HBCharacterController.kt`, `GabiAvatarViews.kt`, `QuartoGabiScreen.kt`, `HabitatViewModel.kt`, `HomeScreen.kt`, `ChatScreen.kt`, `HBCharacterStateUnitTest.kt` | Renderização visual Compose em aparelho aguarda compilação local com SDK |
| **C2** | Integração HB ↔ `TaskOrchestrator` ↔ `VoiceEngineCoordinator`, Gemini Live ponta a ponta, `McpJsonRpcClient`, `LocalSkillExecutor` e classificação honesta | **IMPLEMENTADO E VALIDADO ESTATICAMENTE / REDE E ÁUDIO `BLOCKED`** | `VoiceEngineCoordinator.kt` (`GeminiLiveSessionReducer`, `AudioRecord`, `Channel` -> `AudioTrack`), `GeminiLiveVoiceClient.kt`, `ModelRouter.kt` (`discoverGeminiLiveModels`), `McpJsonRpcClient.kt`, `LocalSkillExecutor.kt`, `ComputerScreen.kt` | WebSocket real, mic/alto-falante e chave `GEMINI_API_KEY` dependem de aparelho com rede |
| **C3** | Gradle Wrapper 8.9 oficial, remoção de segredos em `BuildConfig`, expansão `Ajustes` e testes JUnit4/Python | **CONCLUÍDO NO CÓDIGO / BUILD APK `BLOCKED`** | `gradlew`, `gradlew.bat`, `gradle-wrapper.jar` (Apache-2.0 `gradle/gradle@v8.9.0`), zero chaves em `BuildConfig`, `BUILD_LOCAL.md` Windows/Linux | Sem JDK 17 / Android SDK 34 no sandbox (`APK_NOT_BUILT`) |
| **C4** | SBOM v2 honesto, reauditoria IP/integridade, `SHA256SUMS.txt`, envio ao Drive `GabiTama` preservando arquivo R0 | **CONCLUÍDO** | `evidence/SBOM.json`, `audit_ip_and_no_pet.py` PASS, `verify_project_integrity.py` PASS, `docs/STATUS_REAL.md`, pasta Drive `GabiTama` | Nenhum para artefatos-fonte e evidências |
