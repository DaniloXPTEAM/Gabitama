## Changelog — 0.2.6-r1.4.3 (versionCode 9) — prévia NÃO HOMOLOGADA

### Mudança funcional (Chat > Gemini Live)
- Se o microfone for negado ao tocar em **Gemini Live**, a tela agora mostra o motivo e como conceder a permissão nas configurações do aparelho. Antes, nada aparecia e o toque parecia não fazer nada.

### O que NÃO mudou nesta versão
- Motor de voz: idêntico ao da 0.2.6-r1.4.2. Nenhuma alteração em captura, reprodução, interrupção ou reconexão.
- Telas, Home, HB, Chat (além da mensagem acima), modelos, dados e chaves: sem alteração.
- Identificação da build (versão, rótulo "Gabi (HB) PRÉVIA"): já existia na 0.2.6-r1.4.2 e não é melhoria funcional.

### Ainda NÃO verificado
- Nenhum teste deste candidato rodou em aparelho físico. O fluxo completo de voz (captura, resposta falada, interrupção, encerramento) continua pendente de homologação. Roteiro: `docs/homologacao/gabi-gemini-live-roteiro-fisico.md`.
- A mensagem de microfone negado não tem teste automatizado; foi verificada por leitura de código e compilação na CI.

### Não incluído
- **Voz `gabi_24l` não está embarcada.** O Gemini Live usa a voz pré-construída `Kore`, que não é a voz aprovada.
- A chave Gemini é cadastrada em Ajustes. Nenhuma chave vem no APK.
- A assinatura da CI pode mudar entre runs. Instalar por cima de uma instalação com assinatura diferente é recusado pelo Android.
