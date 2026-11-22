# Matriz real — planejamento; nenhuma execução nesta revisão

Todas as linhas: versão base 0ecdbe5 + alterações locais; resultado observado e evidência: inexistentes. NOT_TESTED não significa aprovado.

| Tela/área | Pré-condição / ação | Esperado | Status |
|---|---|---|---|
| Instalação | AVD limpo / instalar APK | sucesso e launcher | NOT_TESTED |
| Abertura | iniciar e esperar 8s | tela útil, processo vivo sem fatal/ANR | NOT_TESTED |
| Permissões | instalação limpa / Continuar | Home sem autorizar permissões | NOT_TESTED |
| Home | visualizar e tocar avatar, Quarto, Ajustes, voz | resposta correspondente | NOT_TESTED |
| Chat | digitar, teclado, Enviar, histórico, limpar, Voltar | mensagens e UI visíveis | NOT_TESTED |
| Quarto | intro, Pular, Alimentar, Fase, Guarda-roupa, Ambiente, Parar | resposta e diálogos verificáveis | NOT_TESTED |
| Computador | diagnóstico, skills e MCP fake | resultado correto, confirmação antes de ação | NOT_TESTED |
| Feed | ler/limpar avisos e recibos de teste | estado atualizado | NOT_TESTED |
| Metas | adicionar, avançar, excluir | estado persistente correto | NOT_TESTED |
| Memória | adicionar/ler/excluir fatos fictícios | persistência e exclusão | NOT_TESTED |
| Biblioteca | arquivos e SAF em sandbox | leitura/escrita confirmadas | NOT_TESTED |
| Ajustes | navegar, alterar preferências sem credenciais | persistência e retorno | NOT_TESTED |
| Navegação | todos os destinos, voltar, alternância rápida | tela esperada em cada ação | NOT_TESTED |
| HB | estados, loop, fundos, intro, transições, reduceMotion | animação correta sem opacidade indevida | NOT_TESTED |
| Voz | negar/conceder/revogar microfone, PCM sintético, barge-in | liberação segura e feedback | NOT_TESTED |
| Gemini Live | servidor fake, desconexão e timeout | falhas tratadas sem chave | NOT_TESTED |
| Agente | tarefas, cancelar, idempotência, recibos e comandos PT-BR | respostas reais, consentimento | NOT_TESTED |
| Segurança/MCP | mocks SSRF, erros, conteúdo hostil, segredos | isolamento e consentimento | NOT_TESTED |
| Integrações externas | WhatsApp, chamadas, agenda, Spotify em mocks | nenhuma ação real | NOT_TESTED |
| Sistema | widget, tile, boot, alarmes/notificações | resposta correta | NOT_TESTED |
| Persistência | atualizar sem limpar, reiniciar processo, migração | dados mantidos | NOT_TESTED |
| Acessibilidade | TalkBack, foco, teclado, contraste, toque | usabilidade | NOT_TESTED |
| Configuração | rotação, fonte, DPI, tema, resolução | interface utilizável | NOT_TESTED |
| Robustez | rede offline/lenta, memória baixa, estresse, background | sem crash/ANR | NOT_TESTED |
| Desempenho | cold/warm start, CPU, RAM e frames | medições reais | NOT_TESTED |
| Compatibilidade | APIs 26, 29, 34 e aparelho autorizado | instalação e navegação | NOT_TESTED |

Esta é uma matriz inicial por cenário, não o inventário completo por controle exigido; enumerar controles e evidências antes da homologação. Serviço real com chave e teste físico dependem de consentimento; não executar gastos ou mensagens reais automaticamente.
