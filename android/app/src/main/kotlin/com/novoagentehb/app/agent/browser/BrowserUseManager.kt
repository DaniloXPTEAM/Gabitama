package com.novoagentehb.app.agent.browser

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fachada única do navegador para o agente (chamada pelo executor de ferramentas).
 *
 * Regra: sucesso só é informado quando o WebView confirma o resultado (página carregada,
 * navegação iniciada, texto lido). Timeout, falha e cancelamento viram erro com código.
 */
@Singleton
class BrowserUseManager @Inject constructor(
    private val controller: BrowserSessionController,
) {
    /** Ações que o agente pode pedir nesta versão. Outras ações do enum são recusadas. */
    val supportedActions: Set<BrowserAction> = setOf(
        BrowserAction.BACK,
        BrowserAction.FORWARD,
        BrowserAction.RELOAD,
        BrowserAction.GET_TEXT,
        BrowserAction.GET_PAGE_INFO,
        BrowserAction.CLOSE_TAB,
    )

    /** Abre [rawUrl] e espera o WebView confirmar o carregamento. */
    suspend fun open(rawUrl: String, timeoutMs: Long = LOAD_TIMEOUT_MS): BrowserActionResult {
        val check = controller.open(rawUrl)
        if (check is UrlCheck.Rejected) {
            return BrowserActionResult.error("URL_REJECTED", "Não abri a página: ${check.reason}.")
        }
        return awaitLoad(timeoutMs, "Página aberta")
    }

    /** Executa uma ação suportada sobre a página aberta. */
    suspend fun perform(action: BrowserAction, actionTimeoutMs: Long = ACTION_TIMEOUT_MS): BrowserActionResult {
        if (action !in supportedActions) {
            return BrowserActionResult.error("UNSUPPORTED_ACTION", "Ação de navegador indisponível nesta versão: ${action.wireName}.")
        }
        if (!controller.isOpen()) {
            return BrowserActionResult.error("NO_SESSION", "Nenhuma página está aberta no navegador do app.")
        }
        return when (action) {
            BrowserAction.BACK -> navigate(BrowserCommand.NavigationKind.BACK, "Voltei à página anterior", actionTimeoutMs)
            BrowserAction.FORWARD -> navigate(BrowserCommand.NavigationKind.FORWARD, "Avancei para a página seguinte", actionTimeoutMs)
            BrowserAction.RELOAD -> navigate(BrowserCommand.NavigationKind.RELOAD, "Recarreguei a página", actionTimeoutMs)
            BrowserAction.GET_TEXT -> readText(actionTimeoutMs)
            BrowserAction.GET_PAGE_INFO -> pageInfo()
            BrowserAction.CLOSE_TAB -> {
                val url = controller.session.value?.url
                controller.close()
                BrowserActionResult.ok("Fechei a página no navegador do app.", url)
            }
            else -> BrowserActionResult.error("UNSUPPORTED_ACTION", "Ação indisponível: ${action.wireName}.")
        }
    }

    private suspend fun navigate(
        kind: BrowserCommand.NavigationKind,
        successText: String,
        timeoutMs: Long,
    ): BrowserActionResult {
        val moved = CompletableDeferred<Boolean>()
        if (!controller.requestNavigation(kind, moved)) {
            return BrowserActionResult.error("NO_SESSION", "Nenhuma página está aberta no navegador do app.")
        }
        val didMove = withTimeoutOrNull(timeoutMs) { moved.await() }
            ?: return BrowserActionResult.error("ACTION_TIMEOUT", "O navegador não respondeu ao comando em ${timeoutMs / 1000}s.")
        if (!didMove) {
            val message = when (kind) {
                BrowserCommand.NavigationKind.BACK -> "Não há página anterior no histórico desta sessão."
                BrowserCommand.NavigationKind.FORWARD -> "Não há página seguinte no histórico desta sessão."
                BrowserCommand.NavigationKind.RELOAD -> "Não foi possível recarregar a página."
            }
            return BrowserActionResult.error("NO_HISTORY", message, controller.session.value?.url)
        }
        return awaitLoad(timeoutMs, successText)
    }

    private suspend fun awaitLoad(timeoutMs: Long, successPrefix: String): BrowserActionResult {
        val terminal = withTimeoutOrNull(timeoutMs) {
            controller.loadState.first { it.isTerminal }
        }
        val pageUrl = controller.session.value?.url
        return when (terminal) {
            null -> BrowserActionResult.error(
                "LOAD_TIMEOUT",
                "A página não terminou de carregar em ${timeoutMs / 1000}s. Não confirmei o conteúdo.",
                pageUrl,
            )
            is BrowserLoadState.Loaded -> BrowserActionResult.ok(
                "$successPrefix: ${terminal.title?.takeIf { it.isNotBlank() } ?: terminal.url} (${terminal.url}).",
                terminal.url,
            )
            is BrowserLoadState.Failed -> BrowserActionResult.error(
                "LOAD_FAILED",
                "Falha ao carregar ${terminal.url}: ${terminal.reason}.",
                terminal.url,
            )
            is BrowserLoadState.Cancelled -> BrowserActionResult.error(
                "LOAD_CANCELLED",
                "O carregamento foi cancelado pela pessoa.",
                terminal.url,
            )
            else -> BrowserActionResult.error("LOAD_UNKNOWN", "Estado de carregamento inesperado.", pageUrl)
        }
    }

    private suspend fun readText(timeoutMs: Long): BrowserActionResult {
        val result = CompletableDeferred<String>()
        if (!controller.requestReadText(result)) {
            return BrowserActionResult.error("NO_SESSION", "Nenhuma página está aberta no navegador do app.")
        }
        val text = withTimeoutOrNull(timeoutMs) { result.await() }
            ?: return BrowserActionResult.error("ACTION_TIMEOUT", "Não consegui ler o texto da página em ${timeoutMs / 1000}s.")
        val url = controller.session.value?.url
        if (text.isEmpty()) {
            return BrowserActionResult.ok("A página não tem texto visível para ler.", url)
        }
        return BrowserActionResult.ok("Texto visível da página (${text.length} caracteres):\n$text", url)
    }

    private fun pageInfo(): BrowserActionResult {
        val session = controller.session.value
            ?: return BrowserActionResult.error("NO_SESSION", "Nenhuma página está aberta no navegador do app.")
        val state = when (val load = controller.loadState.value) {
            is BrowserLoadState.Loading -> "carregando"
            is BrowserLoadState.Loaded -> "carregada"
            is BrowserLoadState.Failed -> "falhou: ${load.reason}"
            is BrowserLoadState.Cancelled -> "cancelada"
            else -> "sem estado"
        }
        return BrowserActionResult.ok(
            "Título: ${session.title?.takeIf { it.isNotBlank() } ?: "(sem título)"}. URL: ${session.url}. Estado: $state.",
            session.url,
        )
    }

    companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
        const val ACTION_TIMEOUT_MS = 15_000L
    }
}
