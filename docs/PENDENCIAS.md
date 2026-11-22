# LISTA DE PENDÊNCIAS — NOVO_AGENTE_HB (R1.0 · 2026-11-21)

## 1. Bloqueios do Ambiente Sandbox (Execução Externa Necessária)

1. **Compilação do APK Debug (`assembleDebug`) e testes JVM (`testDebugUnitTest`):** Bloqueados neste sandbox por ausência de JDK 17, Android SDK 34 e acesso aos repositórios Maven/Google. O Gradle Wrapper 8.9 oficial já está instalado (`novo-agente-hb/android/gradlew*`). Seguir `docs/BUILD_LOCAL.md`.
2. **Testes em Emulador/Aparelho Físico e Rede Externa:** A sessão Gemini Live real (`AudioRecord`/`AudioTrack`/WebSocket), descoberta de modelos via `GET /v1beta/models`, leitura de notificações Spotify e renderização Compose dependem de compilação em estação local com Android SDK e chave `GEMINI_API_KEY`.
3. **Auditoria de Dependências Transitivas:** `evidence/SBOM.json` cobre 100% das dependências diretas de `libs.versions.toml` e o Gradle Wrapper 8.9; a árvore transitiva completa exige `./gradlew app:dependencies --configuration releaseRuntimeClasspath` em máquina com acesso Maven.

## 2. Ativos Visuais Opcionais Pendentes (`ASSET_PENDING`)

Conforme `docs/HB_ASSET_MAP.md`, estes itens não existem nas branches de `DaniloXPTEAM/Jarvis-Projeto` nem no Google Drive e foram isolados com fallback explícito:
- Sequências de frames `anim/scene_comer/`, `anim/scene_comendo_reacao/`, `anim/scene_ler/`, `anim/scene_dormir/` (usam `gabi-comer.png`, `gabi-ler.png` e `gabi-dormir.png` transparentes com respiração orgânica).
- Looks extras do guarda-roupa `gabi-look-moletom.png`, `gabi-look-festa.png`, `gabi-look-pijama.png` (exibidos como `"Arte pendente"` e bloqueados contra seleção falsa).

## 3. Decisões Exclusivas do Proprietário (Danilo)

1. Nome comercial final e licença definitiva (`LICENSE_PENDING_OWNER.txt`).
2. Revisão comercial dos 1.324 ativos visuais Gabi antes de eventual publicação em loja pública (`docs/LICENSE_PROVENANCE.md`).
3. Inserção local das chaves BYOK (`Gemini`, `Groq`, `OpenRouter`, `DeepSeek`, `Anthropic`, `ElevenLabs`, `Spotify`) diretamente na tela **Ajustes** (armazenadas apenas no `SecureKeyVault` AES256-GCM).
