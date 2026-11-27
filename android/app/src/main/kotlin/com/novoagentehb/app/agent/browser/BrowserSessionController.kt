package com.novoagentehb.app.agent.browser

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Sessão atual do navegador embutido. [requestId] muda a cada abertura pedida. */
data class BrowserSession(
    val requestId: Long,
    val url: String,
    val title: String? = null,
)

/** Estado real do carregamento, relatado pelo WebView (nunca presumido). */
sealed class BrowserLoadState {
    abstract val isTerminal: Boolean

    object Idle : BrowserLoadState() {
        override val isTerminal = false
    }

    data class Loading(val url: String) : BrowserLoadState() {
        override val isTerminal = false
    }

    data class Loaded(val url: String, val title: String?) : BrowserLoadState() {
        override val isTerminal = true
    }

    data class Failed(val url: String, val reason: String) : BrowserLoadState() {
        override val isTerminal = true
    }

    data class Cancelled(val url: String) : BrowserLoadState() {
        override val isTerminal = true
    }
}

/**
 * Comandos enviados ao WebView. Os resultados voltam por [CompletableDeferred]:
 * o WebView confirma o que fez (ou não fez) antes de o agente relatar sucesso.
 */
sealed class BrowserCommand {
    enum class NavigationKind { BACK, FORWARD, RELOAD }

    /** [moved] vira true se a navegação foi iniciada; false se não havia para onde ir. */
    data class Navigate(
        val kind: NavigationKind,
        val moved: CompletableDeferred<Boolean>,
    ) : BrowserCommand()

    /** Lê o texto visível da página com script fixo (nunca código enviado pelo agente). */
    data class ReadText(val result: CompletableDeferred<String>) : BrowserCommand()

    /** [done] vira true somente após o WebView executar stopLoading(). */
    data class Stop(val done: CompletableDeferred<Boolean>) : BrowserCommand()

    /** Clique ou preenchimento; o código do script volta por [result]. Só após confirmação da pessoa. */
    data class Interact(
        val request: BrowserInteractionRequest,
        val result: CompletableDeferred<String>,
    ) : BrowserCommand()
}

/**
 * Ponte entre o agente, a interface e o WebView. Não faz rede: guarda a sessão,
 * o estado de carregamento e a fila de comandos executada pela interface.
 */
@Singleton
class BrowserSessionController @Inject constructor() {

    private val mutableSession = MutableStateFlow<BrowserSession?>(null)
    val session: StateFlow<BrowserSession?> = mutableSession.asStateFlow()

    private val mutableLoad = MutableStateFlow<BrowserLoadState>(BrowserLoadState.Idle)
    val loadState: StateFlow<BrowserLoadState> = mutableLoad.asStateFlow()

    private val commandChannel = Channel<BrowserCommand>(Channel.UNLIMITED)
    val commands: Flow<BrowserCommand> = commandChannel.receiveAsFlow()

    private var lastRequestId = 0L

    fun isOpen(): Boolean = mutableSession.value != null

    /** Valida [rawUrl] e pede a abertura. Fica em Loading até o WebView relatar o resultado. */
    fun open(rawUrl: String): UrlCheck {
        val check = BrowserUrlPolicy.check(rawUrl)
        if (check is UrlCheck.Allowed) {
            lastRequestId += 1
            mutableSession.value = BrowserSession(requestId = lastRequestId, url = check.url)
            mutableLoad.value = BrowserLoadState.Loading(check.url)
        }
        return check
    }

    /** Pede voltar, avançar ou recarregar. Retorna false se não há sessão aberta. */
    fun requestNavigation(kind: BrowserCommand.NavigationKind, moved: CompletableDeferred<Boolean>): Boolean {
        val current = mutableSession.value ?: return false
        mutableLoad.value = BrowserLoadState.Loading(current.url)
        return commandChannel.trySend(BrowserCommand.Navigate(kind, moved)).isSuccess
    }

    /** Pede o texto visível da página atual. Retorna false se não há sessão aberta. */
    fun requestReadText(result: CompletableDeferred<String>): Boolean {
        if (mutableSession.value == null) return false
        return commandChannel.trySend(BrowserCommand.ReadText(result)).isSuccess
    }

    /** Pede clique ou preenchimento na página atual. Retorna false se não há sessão aberta. */
    fun requestInteract(request: BrowserInteractionRequest, result: CompletableDeferred<String>): Boolean {
        if (mutableSession.value == null) return false
        return commandChannel.trySend(BrowserCommand.Interact(request, result)).isSuccess
    }

    /**
     * Pede a parada do carregamento. Não marca Cancelled aqui: quem marca é o WebView,
     * depois de executar stopLoading() (ver [onLoadCancelled]).
     * Retorna false se não há sessão ou se nada está carregando.
     */
    fun requestStop(done: CompletableDeferred<Boolean>): Boolean {
        if (mutableSession.value == null || mutableLoad.value !is BrowserLoadState.Loading) {
            done.complete(false)
            return false
        }
        return commandChannel.trySend(BrowserCommand.Stop(done)).isSuccess
    }

    /** Relato do WebView: a parada foi executada para o carregamento em curso. */
    fun onLoadCancelled() {
        val current = mutableSession.value ?: return
        mutableLoad.value = BrowserLoadState.Cancelled(current.url)
    }

    // --- Relatos vindos do WebView -------------------------------------------------

    fun onLoadStarted(url: String) {
        if (mutableSession.value == null) return
        mutableLoad.value = BrowserLoadState.Loading(url)
    }

    fun onPageLoaded(url: String, title: String?) {
        val current = mutableSession.value ?: return
        mutableSession.value = current.copy(url = url, title = title)
        mutableLoad.value = BrowserLoadState.Loaded(url, title)
    }

    fun onLoadFailed(url: String, reason: String) {
        if (mutableSession.value == null) return
        mutableLoad.value = BrowserLoadState.Failed(url, reason)
    }

    /** True após [requestStop]; o WebView usa isto para não sobrescrever o cancelamento. */
    fun currentLoadIsCancelled(): Boolean = mutableLoad.value is BrowserLoadState.Cancelled

    fun close() {
        mutableSession.value = null
        mutableLoad.value = BrowserLoadState.Idle
        // Descarta comandos pendentes para não serem executados numa sessão nova.
        while (commandChannel.tryReceive().isSuccess) {
            // drenar
        }
    }
}
