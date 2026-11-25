## Changelog — 0.2.6-r1.4.2 (versionCode 8) — prévia NÃO HOMOLOGADA

Base de comparação: `d1936d3` (última mudança de motor Live antes deste ciclo). Entre `d1936d3` e `0ef12e9` não houve mudança em telas, rotas, recursos visuais, Home, Chat ou HB.

### O que muda para o usuário nesta versão
- **Identificação da versão**: Ajustes > Motor de Voz mostra `Build 0.2.6-r1.4.2 (código 8)`. Builds de prévia (debug) mostram também "prévia debug, não homologada". O rótulo do app no launcher passa a ser "Gabi (HB) PRÉVIA", para não confundir com instalações anteriores.
- **Atualização sem perda de dados**: o pacote continua `com.novoagentehb.app.debug`. Ao instalar por cima, chaves, conversas, preferências e modelos são mantidos. Não é preciso desinstalar.
- **Gemini Live com modelo antigo salvo**: se o aparelho ainda guarda o modelo anterior (`gemini-2.5-flash-native-audio-preview-09-2025`), a falha de conexão agora informa a causa e o caminho para corrigir em Ajustes ("Aplicar padrão gemini-3.8-live"). O app não troca o modelo sozinho.

### Mudanças no motor de voz desde `d1936d3` (já presentes em `0ef12e9`, sem mudança visual)
- Captura do microfone em sessão testável, com encerramento único do gravador.
- Reprodução PCM 24 kHz não bloqueante, com cancelamento de áudio pendente em interrupção (barge-in) e no encerramento.
- Liberação da faixa de áudio ao bloquear ou desconectar; nova faixa na sessão seguinte.
- Durante a reconexão, áudio do socket antigo é descartado e não reativa a sessão. Política de reconexão mantida: 1 tentativa.
- Mensagens de erro sem chave e sem texto bruto do servidor.

### Ainda NÃO verificado
- Nenhum teste deste ciclo rodou em aparelho físico. Captura, resposta falada, interrupção e encerramento no aparelho continuam pendentes (roteiro em `docs/homologacao/gabi-gemini-live-roteiro-fisico.md`).
- API 34 do job de testes conectados segue pendente.

### Não incluído
- **Voz `gabi_24l` não está embarcada**: o runtime Pocket TTS e seus assets não estão no repositório. O Gemini Live usa a voz pré-construída `Kore`, e a tela informa isso.
- A chave Gemini precisa ser cadastrada em Ajustes. Nenhuma chave vem no APK.
