# F3 — Navegador embutido (GabiTama, licença MIT)

Atualizado em 2026-11-27 (revisão após a fatia 5 e a fatia de interação). Substitui a versão anterior deste plano, que citava o código-fonte de outro projeto como base e ainda registrava a fatia 5 como não iniciada.

## Licença e origem
- GabiTama permanece **MIT**. Nenhum arquivo do navegador tem cabeçalho GPL. A implementação é reescrita a partir dos requisitos do agente; não copia código de outro projeto.
- A referência ao projeto Tama V2 (GPL-3.0) serviu apenas para o levantamento de requisitos e não é base de código.
- Componentes de terceiros herdados (não são do navegador): `pty_bridge.c`/`PtyBridge.kt`, `deps/proot` (GPL-2.0), `deps/talloc` (LGPL-3.0+), pacote `com.openminis`, textos em `assets/licenses/tama-runtime/`. Avisos preservados; não são removidos nem reclassificados aqui. A regularização desses componentes segue a decisão do proprietário registrada em `LICENSE_PENDING_OWNER.txt`.
- Gate de licença: o script `check-distribution-license.sh` citado no workflow não existe no branch. Pendência real; não há gate automático de distribuição até ele ser criado ou substituído.

## Estado por fatia
1. **Modelo de ação** (`BrowserAction`, `BrowserActionResult`, `BrowserUrlPolicy`): entregue. Política de URL: só http/https, sem credenciais, sem endereços locais ou privados.
2. **Histórico em Room** (`browser_history`): entregue. Migração 1→2 aditiva; teste instrumentado no emulador API 34 (CI `46048294600`: 2 testes, 0 falhas). `fallbackToDestructiveMigration` mantido até decisão do proprietário.
3. **WebView** (`InAppBrowserHost`): entregue. Progresso, falha, parar, tentar de novo.
4. **Capacidade `browser_page_action`** (abrir, voltar, avançar, recarregar, ler texto, estado, parar, fechar): entregue; risco LOCAL_MUTATION.
5. **Gerenciador `BrowserUseManager`** com ações de página: entregue para as ações acima. `SCROLL`, `NEW_TAB` e `SCREENSHOT` existem no enum, mas não têm suporte.
6. **Clicar e preencher campo** (`browser_page_interact`): implementado neste ciclo.
   - Risco EXTERNAL_SIDE_EFFECT: a PolicyGate exige confirmação explícita com o seletor e, no preenchimento, o texto.
   - Seletor CSS validado; literais JavaScript escapados; script fixo.
   - Nunca envia formulário por conta própria. Enviar exige clicar no botão, que é uma ação separada e confirmada.
   - Recusa senha, arquivo e campos ocultos; preenchimento só aceita texto, busca, e-mail, URL, telefone, número e textarea.
   - Preenchimento é confirmado só se o WebView relê o mesmo valor. Clique é reportado como executado, sem verificação do efeito no site.

## Pendências reais (não validadas)
- Nada disto foi testado em aparelho. A CI executa testes JVM e compilação, não o WebView real.
- Formulários de múltiplos passos, seleção de opções, upload e captcha: fora do escopo.
- Verificação do efeito de cliques (ex.: "mensagem enviada") depende de leitura posterior pelo agente.
- Não declarar navegador autônomo completo.
