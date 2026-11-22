# PLANO DE TESTES — NOVO_AGENTE_HB (GATES G0–G6)

## 1. Camadas de Teste Definidas

| Suíte | Localização | Ferramenta | Execução atual no sandbox |
| --- | --- | --- | --- |
| T1 — Auditoria IP / zero GPL / zero Pet / SBOM | `scripts/license-audit/audit_ip_and_no_pet.py` | Python `unittest` | **EXECUTADA E APROVADA (4/4 PASS)** |
| T2 — Integridade XML, JSON, 1.324 SHA-256, Kotlin, rotas e `strings.xml` | `scripts/tests/verify_project_integrity.py` | Python `unittest` | **EXECUTADA E APROVADA (5/5 PASS)** |
| T3 — Testes unitários JVM de agência, política, diagnóstico, router e voz | `android/app/src/test/kotlin/com/novoagentehb/app/{AgentCoreUnitTest,ModelRouterAndVoiceUnitTest}.kt` | JUnit 4 (`./gradlew testDebugUnitTest`) | **ESCRITA (`NOT_EXECUTED_IN_SANDBOX` — sem JDK/Gradle)** |
| T4 — Build Debug & Verificação de Pacote/Assinatura | `android/` (`./gradlew assembleDebug`) | Gradle / AGP 8.7.0 / Android SDK 34 | **BLOCKED neste sandbox (`java`/`gradle`/`sdk` ausentes)** |
| T5 — Smoke de instalação paralela e testes em emulador/dispositivo real | `adb install`, logcat sanitizado, telas Home/Chat/Habitat/Permissões/Ajustes | Android Emulator ou aparelho físico autorizado | **`NOT_TESTED` (aguarda build local e autorização para dispositivo)** |

## 2. Casos Críticos de Aceite (A01–A16)

1. **A01/A15 (Instalação paralela):** Confirmar via `adb shell pm list packages` que `com.novoagentehb.app.debug` instala sem conflitar com `com.javis.assistant` nem `ai.tama.assistant` e sem alterar bancos/Keystore dos antigos.
2. **A02/A03/A04/A05 (Navegação e Quarto da Gabi):** Tocar na aba `Habitat` e confirmar abertura do **Quarto da Gabi** (`room_intro.mp4` -> `room_bg_loop.mp4` / `room.webp`), sem qualquer elemento de dragão/ovo/fome/XP/memorial.
3. **A07 (Voz congelada `gabi_24l` e B2/C2):** Alternar entre `Gemini Live`, `Gabi 24L local` e `TTS do sistema`, comprovando que `Gabi 24L local` exibe aviso explícito quando o runtime ONNX não está embarcado e nunca mascara TTS do sistema como voz `gabi_24l`.
4. **A09/A10/A12 (Agente, confirmação e stop):** Acionar ferramenta `send_whatsapp_message` e confirmar exibição de `ConfirmActionBar`; clicar `Cancelar` e verificar `TaskReceiptEntity` sem envio; acionar `ask_claude` sem consentimento pago e confirmar bloqueio.
5. **A11 (`/diagnostico` e segredos):** Executar `/diagnostico` no Chat e conferir ausência de `AIza...`, `sk-...`, histórico de conversa ou contatos no relatório.
