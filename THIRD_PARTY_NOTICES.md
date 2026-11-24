# THIRD-PARTY NOTICES — NOVO_AGENTE_HB

Este arquivo registra bibliotecas de terceiros declaradas no build Android
(`android/gradle/libs.versions.toml` e `android/app/build.gradle.kts`),
ferramentas de empacotamento Gradle e referências históricas sob licença
permissiva.

> **Correção factual (2026-11-23).** A frase anterior, "Nenhum componente GNU GPL, LGPL ou AGPL é
> declarado ou incorporado", estava **desatualizada e não deve ser usada**. O repositório contém código
> e textos sob GPL/LGPL: `third_party/tama-runtime/android/deps/proot` (GPL-2.0), `deps/talloc`
> (LGPL-3.0-or-later), `android/app/src/main/cpp/pty_bridge.c` (compilado no APK via CMake, origem Tama,
> licença do arquivo a confirmar) e textos GPL-3.0 em `android/app/src/main/assets/licenses/tama-runtime/`.
> O inventário completo, com fontes e pendências, está em `docs/LICENSE_MATRIX_COMPUTER_RUNTIME.md`.
> Presença de código GPL/LGPL não significa proibição de uso; a conformidade de qualquer distribuição
> será analisada separadamente pelo proprietário.

> **Escopo honesto de auditoria:** `evidence/SBOM.json` lista todas as coordenadas
> diretas declaradas em `libs.versions.toml` e o Gradle Wrapper 8.9. A árvore
> transitiva completa do Maven Central / Google Maven não foi resolvida offline
> no sandbox e exige execução local de `./gradlew app:dependencies`.

## 1. Ferramenta de Build (Gradle Wrapper 8.9)

| Artefato | Origem | Licença | Verificação |
| --- | --- | --- | --- |
| `android/gradlew`, `android/gradlew.bat`, `android/gradle/wrapper/gradle-wrapper.jar` | `gradle/gradle@v8.9.0` (release oficial) | **Apache-2.0** | `META-INF/LICENSE` validado dentro de `gradle-wrapper.jar` |

## 2. Dependências diretas declaradas em Gradle (Android / Kotlin / Compose)

| Biblioteca / Grupo | Licença declarada | Uso neste projeto |
| --- | --- | --- |
| AndroidX Core KTX (`androidx.core:core-ktx`) | Apache-2.0 | APIs utilitárias Android |
| AndroidX Lifecycle (`androidx.lifecycle:*`) | Apache-2.0 | ViewModel, Runtime Compose |
| AndroidX Activity Compose (`androidx.activity:activity-compose`) | Apache-2.0 | Integração Activity/Compose |
| Jetpack Compose BOM / UI / Material3 (`androidx.compose.*`) | Apache-2.0 | Interface gráfica declarativa |
| AndroidX Navigation Compose (`androidx.navigation:navigation-compose`) | Apache-2.0 | Navegação entre rotas |
| AndroidX Room (`androidx.room:*`) | Apache-2.0 | Persistência SQLite local |
| AndroidX DataStore (`androidx.datastore:datastore-preferences`) | Apache-2.0 | Preferências locais |
| AndroidX Security Crypto (`androidx.security:security-crypto`) | Apache-2.0 | EncryptedSharedPreferences / Android Keystore |
| AndroidX DocumentFile (`androidx.documentfile:documentfile`) | Apache-2.0 | Storage Access Framework (SAF) |
| AndroidX Media3 ExoPlayer (`androidx.media3:*`) | Apache-2.0 | Reprodução de abertura e vídeo ambiente do Quarto |
| Hilt (`com.google.dagger:hilt-*`, `androidx.hilt:*`) | Apache-2.0 | Injeção de dependência |
| Kotlin / Kotlinx Coroutines (`org.jetbrains.kotlin*`, `org.jetbrains.kotlinx*`) | Apache-2.0 | Linguagem e concorrência estruturada |
| Retrofit & Converter-Gson (`com.squareup.retrofit2:*`) | Apache-2.0 | Cliente HTTP declarativo |
| OkHttp & Logging Interceptor (`com.squareup.okhttp3:*`) | Apache-2.0 | HTTP, MCP JSON-RPC e WebSocket (Gemini Live) |
| Gson (`com.google.code.gson:gson`) | Apache-2.0 | Serialização JSON |
| Accompanist Permissions (`com.google.accompanist:accompanist-permissions`) | Apache-2.0 | Fluxo granular de permissões |
| JUnit 4 (`junit:junit`) | EPL-1.0 (somente testImplementation) | Suíte de testes unitários JVM |
| AndroidX Test / Espresso (`androidx.test.*`) | Apache-2.0 (somente androidTestImplementation) | Suíte de testes instrumentados |

## 3. Referência conceitual / código-fonte Gabi (MIT)

O projeto anterior do proprietário `DaniloXPTEAM/Jarvis-Projeto` inclui
`android/LICENSE` sob **MIT License (`Copyright (c) 2024 JAVIS Assistant`)**.
Embora `NOVO_AGENTE_HB` reimplemente do zero o grafo de navegação expandido,
a camada de agência (orquestrador, policy gate, roteador, registry, recibos,
diagnóstico), o cliente Gemini Live, o cliente MCP JSON-RPC 2.0 e o
armazenamento unificado Room, o fluxo de UX/paleta "Céu de Outono", os
contratos de ações voltadas à pessoa e o comportamento do Quarto da Gabi
preservam o padrão aprovado daquela base MIT:

```text
MIT License — Copyright (c) 2024 JAVIS Assistant
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

## 4. Especificação de voz local (não distribuída em binário)

As configurações `gabi_24l` e perfis de prosódia `B2`/`C2` (e referência histórica
`B3`/`C3`) referem-se a ensaios com Kyutai Pocket TTS (`portuguese_24l`,
**MIT License**). Nenhum peso de modelo nem gravação de áudio pessoal é
incorporado neste pacote.

## 5. Mídias visuais da Gabi e do Quarto da Gabi

Os arquivos gráficos em `android/app/src/main/assets/` e
`android/app/src/main/res/raw/room_bg_loop.mp4` provêm dos recursos do
proprietário em `DaniloXPTEAM/Jarvis-Projeto@voz-latencia` (ver
`docs/HB_ASSET_MAP.md`, `docs/LICENSE_PROVENANCE.md` e `evidence/APPROVED_ASSETS_SHA256.txt`).
