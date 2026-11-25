# Roteiro de homologação física — Gabi (Gemini Live)

Status: **DEVICE_NOT_TESTED**. Nenhum item abaixo foi executado. Este roteiro não autoriza instalar nem distribuir APK. Só começa com autorização explícita do proprietário, com um APK candidato verificado (ver seção "Candidato").

Objetivo do teste: verificar o funcionamento real, não apenas os testes simulados. Fluxo principal: **abrir Gabi → iniciar Live → falar → receber resposta falada → interromper → falar novamente → encerrar.**

Regras de registro: anotar apenas categoria, tempo e resultado (PASS / FAIL / NÃO TESTADO). Não registrar chaves, prompts, respostas, áudio, identificadores de conta, número de série ou payloads. Não usar chave real de produção; a chave de teste fica somente no aparelho.

## 1. Candidato (a preencher antes da autorização)

| Campo | Valor a registrar |
|---|---|
| Commit exato | SHA do código (ex.: `1301bdb` ou posterior, sem alterações não commitadas) |
| Run de CI que gerou o APK | ID do run e nome do artefato `shared-apks-<SHA>` |
| Arquivo | `app-debug.apk` (dentro do artefato); caminho local após download |
| SHA-256 | Conferir com `SHA256SUMS` do próprio artefato (`sha256sum -c`) |
| applicationId | `com.novoagentehb.app.debug` (sufixo `.debug` do buildType debug) |
| versionCode / versionName | 7 / `0.2.5-r1.4.1` (de `build.gradle.kts`; confirmar com `aapt dump badging`) |
| minSdk / targetSdk | 26 / 34 (de `build.gradle.kts`; confirmar com `aapt`) |
| ABI | **a confirmar** com `unzip -l app-debug.apk \| grep lib/`. Não há `abiFilters` no build; o CMake gera bibliotecas nativas. |
| Assinatura | **debug**, com a chave debug da máquina que compilou (sem `signingConfigs` no projeto). Confirmar com `apksigner verify --print-certs`. |

Diferenças entre os binários:
- **Teste (`app-debug-androidTest.apk`)**: instrumentação para o emulador. Não é o candidato. Nunca instalar no aparelho para homologação.
- **Debug (`app-debug.apk`)**: o candidato deste roteiro. Gerado pelo CI com `assembleDebug`. Como usa a chave debug de cada máquina de CI, **APKs de runs diferentes podem ter assinaturas diferentes**.
- **Release**: buildType com minificação e redução de recursos, sem `signingConfig`. Não há APK release assinado no repositório nem no CI. Não é candidato de entrega; exige chave de assinatura do proprietário, que não está no repositório e não deve ser criada aqui.

## 2. Instalação e atualização (sem apagar dados)

Regras:
- Não desinstalar o app para contornar incompatibilidade de assinatura. Não usar `pm clear`, `adb uninstall` nem limpar dados.
- O sufixo `.debug` faz este pacote coexistir com um eventual release `com.novoagentehb.app`, sem conflito.
- Antes de instalar sobre um `.debug` existente: comparar a assinatura do APK instalado com a do candidato (`apksigner` ou `dumpsys package`). Se forem diferentes, **parar e reportar**; não instalar por cima.

Passos (somente após autorização):
1. Registrar a versão instalada atual (versionName/versionCode) e a assinatura, sem dados sensíveis.
2. Instalar com `adb install -r` apenas se a assinatura bater. Caso contrário, registrar INSTALL_BLOCKED e encerrar este item.
3. Confirmar que modelos, preferências, conversas, `gabi_24l`, voz B2/C2 e escolhas salvas continuam iguais.

## 3. Itens de validação

1. **Abrir Gabi** — a tela principal abre sem travar. Registrar tempo até a tela interativa.
2. **Permissão de microfone negada** — recusar; a UI deve mostrar mensagem de categoria ("permissão necessária"), sem travar e sem sessão iniciada.
3. **Permissão concedida e início da Live** — conceder; iniciar Live com `gemini-3.8-live` (modelo salvo sem troca automática). Medir tempo até o estado pronto.
4. **Falar** — frase curta; confirmar captura contínua (indicador de estado) sem travamento.
5. **Receber resposta falada** — ouvir a resposta com volume médio; registrar se é audível, sem cortes graves e sem eco.
6. **Interromper (barge-in)** — falar por cima da resposta; o áudio atual deve parar logo e **não** retomar a fala antiga.
7. **Falar novamente** — após interromper, fazer nova pergunta; confirmar nova resposta sem áudio residual da anterior.
8. **Encerrar (Stop)** — pressionar Stop durante a reprodução; silêncio imediato; microfone liberado (indicador desligado). Iniciar nova sessão e ouvir nova resposta.
9. **Perda de rede** — desligar Wi-Fi e dados com a sessão ativa. Esperar: uma tentativa de reconexão (política aprovada: `MAX_RECONNECT_ATTEMPTS = 1`); se falhar, erro bloqueante na UI, sem laço.
10. **Recuperação de rede** — religar; se a reconexão ocorrer, a sessão volta a pronta **sem** áudio antigo. Se bloqueou, nova sessão deve funcionar.
11. **Segundo plano** — durante reprodução, mandar o app ao fundo por 30 s e voltar. Registrar: áudio parou ou continuou, e se a sessão está consistente ao voltar.
12. **Latência da primeira resposta** — cronometrar do fim da fala até o primeiro áudio audível. Registrar apenas o valor.
13. **Erro de chave ou de serviço** — usar chave de teste inválida (somente no aparelho). A UI deve mostrar mensagem por categoria, sem texto bruto, sem chave e sem travamento.
14. **Repetição de estabilidade** — 5 ciclos de: iniciar → falar → interromper → Stop. Sem ANR, sem crash no log do sistema, sem vazamento perceptível de áudio.
15. **Preservação** — conferir que Chat, Home, HB e configurações continuam como estavam após o teste.

## 4. Registro

Para cada item: data, SHA e nome do APK, aparelho (modelo e versão do Android, sem número de série), resultado (PASS / FAIL / NÃO TESTADO), observação em uma linha sem dados sensíveis.

## 5. Critério para liberar a homologação

O APK é considerado pronto para iniciar a homologação física quando, ao mesmo tempo:
- o candidato tem SHA, applicationId, ABI, minSdk/targetSdk e assinatura registrados e conferidos;
- a CI do SHA está verde nas APIs disponíveis (26 e 29 aprovadas em `45681792164`; API 34 pendente);
- o proprietário autoriza a instalação.

API 34 pendente não bloqueia a homologação física, mas precisa ser registrada como pendência.

## Não prova

Áudio real, microfone, barge-in, latência e recuperação de rede só existem após execução deste roteiro. Resultados de CI e de testes simulados não substituem este registro.
