# Roteiro de homologação física — Gabi (Gemini Live)

Status: **DEVICE_NOT_TESTED**. Nenhum item abaixo foi executado. Este roteiro não autoriza instalar nem distribuir APK. Não usar chaves reais nem registrar prompts, respostas, áudio, identificadores ou payloads: anotar apenas categoria, tempo e resultado (PASS/FAIL/NÃO TESTADO).

Pré-requisito: CI verde no SHA candidato (JVM, IME API 34, APIs 26/29/34 do workflow `android-debug.yml`). APK candidato só é gerado depois de autorização explícita.

## Ambiente
- Aparelho Android físico (registrar modelo e versão do Android, sem número de série).
- Rede Wi-Fi e dados móveis disponíveis para troca controlada.
- Chave de teste de uso pessoal configurada apenas no aparelho, nunca em repositório.

## Itens
1. **Instalação segura e preservação de dados** — instalar por atualização sobre a versão anterior; confirmar que modelos, preferências, `gabi_24l`, voz B2/C2 e conversas existentes permanecem. Sem limpar dados.
2. **Permissão de microfone** — negar, verificar mensagem de categoria na UI; conceder, verificar que a captura inicia.
3. **Conexão Live** — abrir sessão com `gemini-3.8-live`; medir tempo até estado pronto.
4. **Captura** — falar uma frase curta; confirmar envio contínuo (indicador de estado, sem travamento).
5. **Reprodução audível** — ouvir a resposta com volume médio; registrar se é audível e sem cortes.
6. **Interrupção** — interromper a resposta falando por cima; confirmar que o áudio antigo para e não retorna.
7. **Stop e retomada** — pressionar Stop durante reprodução; confirmar silêncio imediato; iniciar nova sessão e ouvir nova resposta.
8. **Reconexão após perda de rede** — desligar Wi-Fi com sessão ativa; a app deve tentar uma reconexão e, se falhar, exibir erro bloqueante sem laço. Religar e verificar se a sessão volta a `Ready` sem áudio antigo.
9. **Segundo plano** — mandar o app ao fundo durante reprodução e após 30 s retornar; registrar comportamento de áudio e de estado.
10. **Latência da primeira resposta** — cronometrar do fim da fala até o primeiro áudio; registrar apenas o valor.
11. **Erros na UI** — provocar erro de chave inválida (chave de teste errada) e de rede; verificar mensagem por categoria, sem texto bruto ou chave mascarada parcialmente fora do padrão.
12. **Ausência de travamentos** — repetir 5 ciclos de sessão/Stop; sem ANR, sem crash no log do sistema.

## Registro
Para cada item: data, SHA do APK, aparelho (modelo/Android), resultado, observação em uma linha sem dados sensíveis.

## Não prova
Áudio real, microfone, barge-in e latência reais só existem após execução neste roteiro. Os resultados de CI não substituem este registro.
