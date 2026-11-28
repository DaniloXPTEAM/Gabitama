# LISTA DE PENDÊNCIAS — NOVO_AGENTE_HB (R1.0 · 2026-11-21)

## 1. Bloqueios do Ambiente Sandbox (Execução Externa Necessária)

1. **Compilação do APK Debug (`assembleDebug`) e testes JVM (`testDebugUnitTest`):** Atualização (28/11): a CI do branch `arena/1dba9faf-gabitama` compila e executa os testes JVM (run `46152871964` em `4790e8b`: 307 testes, 0 falhas) e a migração Room instrumentada. O sandbox continua sem JDK 17 e Android SDK 34; para build local, seguir `docs/BUILD_LOCAL.md`.
2. **Testes em Emulador/Aparelho Físico e Rede Externa:** A sessão Gemini Live real (`AudioRecord`/`AudioTrack`/WebSocket), descoberta de modelos via `GET /v1beta/models`, leitura de notificações Spotify e renderização Compose dependem de compilação em estação local com Android SDK e chave `GEMINI_API_KEY`.
3. **Auditoria de Dependências Transitivas:** `evidence/SBOM.json` cobre 100% das dependências diretas de `libs.versions.toml` e o Gradle Wrapper 8.9; a árvore transitiva completa exige `./gradlew app:dependencies --configuration releaseRuntimeClasspath` em máquina com acesso Maven.

## 2. Ativos Visuais Opcionais Pendentes (`ASSET_PENDING`)

Conforme `docs/HB_ASSET_MAP.md`, estes itens não existem nas branches de `DaniloXPTEAM/Jarvis-Projeto` nem no Google Drive e foram isolados com fallback explícito:
- Concluído em 28/11 (commit `4790e8b`): sequências `anim/scene_comer/`, `anim/scene_comendo_reacao/`, `anim/scene_ler/` e `anim/scene_dormir/`, geradas por movimento de respiração/pulso a partir das poses aprovadas (`scripts/hb/gen_quarto_breathing_webp.py`). Validação visual no aparelho: pendente.
- Ainda marcadas como pendentes: música, toque, sucesso, erro, cancelamento e troca de look. Hoje reaproveitam sequências existentes. Os 13 MP4 da pasta Avatar no Drive foram inspecionados em 28/11 e nenhum cobre essas cenas.
- Looks extras do guarda-roupa `gabi-look-moletom.png`, `gabi-look-festa.png`, `gabi-look-pijama.png` (exibidos como `"Arte pendente"` e bloqueados contra seleção falsa).

## 3. Decisões Exclusivas do Proprietário (Danilo)

1. Nome comercial final e licença definitiva (`LICENSE_PENDING_OWNER.txt`).
2. Revisão comercial dos 1.324 ativos visuais Gabi antes de eventual publicação em loja pública (`docs/LICENSE_PROVENANCE.md`).
3. Inserção local das chaves BYOK (`Gemini`, `Groq`, `OpenRouter`, `DeepSeek`, `Anthropic`, `ElevenLabs`, `Spotify`) diretamente na tela **Ajustes** (armazenadas apenas no `SecureKeyVault` AES256-GCM).
