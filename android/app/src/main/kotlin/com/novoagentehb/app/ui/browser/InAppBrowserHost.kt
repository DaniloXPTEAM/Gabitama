package com.novoagentehb.app.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novoagentehb.app.agent.browser.BrowserCommand
import com.novoagentehb.app.agent.browser.BrowserHistoryStore
import com.novoagentehb.app.agent.browser.BrowserLoadState
import com.novoagentehb.app.agent.browser.BrowserSessionController
import com.novoagentehb.app.agent.browser.BrowserTextCodec
import com.novoagentehb.app.agent.browser.BrowserUrlPolicy
import com.novoagentehb.app.agent.browser.UrlCheck
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 * Navegador embutido do GabiTama: WebView num diálogo de tela cheia sobre o grafo atual.
 *
 * - Executa os comandos do agente (voltar, avançar, recarregar, ler texto, parar) e confirma
 *   cada um pelo próprio WebView antes de responder.
 * - Progresso enquanto carrega; erro da página principal com "Tentar de novo"; botão Parar.
 * - Navegação só segue para http/https permitidos (BrowserUrlPolicy).
 * - Cookies de terceiros desativados; acesso a arquivos e conteúdo desligado.
 * - Cada página carregada é gravada no histórico Room.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppBrowserHost(
    controller: BrowserSessionController,
    historyStore: BrowserHistoryStore,
) {
    val session by controller.session.collectAsState()
    val load by controller.loadState.collectAsState()
    val current = session ?: return
    val scope = rememberCoroutineScope()

    key(current.requestId) {
        var webView by remember { mutableStateOf<WebView?>(null) }

        DisposableEffect(Unit) {
            onDispose {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
            }
        }

        LaunchedEffect(Unit) {
            controller.commands.collect { command ->
                val view = webView
                when (command) {
                    is BrowserCommand.Navigate -> {
                        if (view == null) {
                            command.moved.complete(false)
                            controller.onLoadFailed(current.url, "navegador não está pronto")
                        } else {
                            performNavigation(view, command, controller, current.url)
                        }
                    }
                    is BrowserCommand.ReadText -> {
                        if (view == null) {
                            command.result.complete("")
                        } else {
                            view.evaluateJavascript(BrowserTextCodec.READ_TEXT_SCRIPT) { raw ->
                                command.result.complete(BrowserTextCodec.decode(raw))
                            }
                        }
                    }
                    BrowserCommand.Stop -> webView?.stopLoading()
                }
            }
        }

        BackHandler {
            val view = webView
            if (view != null && view.canGoBack()) view.goBack() else controller.close()
        }

        Dialog(
            onDismissRequest = { controller.close() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = current.title?.takeIf { it.isNotBlank() } ?: current.url,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (load is BrowserLoadState.Loading) {
                            TextButton(onClick = { controller.requestStop() }) { Text("Parar") }
                        } else {
                            TextButton(onClick = { controller.close() }) { Text("Fechar") }
                        }
                    }
                    if (load is BrowserLoadState.Loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    val failure = load as? BrowserLoadState.Failed
                    if (failure != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Não consegui carregar: ${failure.reason}",
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                controller.requestNavigation(BrowserCommand.NavigationKind.RELOAD, CompletableDeferred())
                            }) { Text("Tentar de novo") }
                        }
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.setGeolocationEnabled(false)
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        // true = cancela a navegação (esquema ou endereço não permitido)
                                        return BrowserUrlPolicy.check(request.url.toString()) !is UrlCheck.Allowed
                                    }

                                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                        url?.let { controller.onLoadStarted(it) }
                                    }

                                    override fun onPageFinished(view: WebView, url: String?) {
                                        val loaded = url ?: return
                                        if (controller.currentLoadIsCancelled()) return
                                        controller.onPageLoaded(loaded, view.title)
                                        scope.launch { historyStore.record(loaded, view.title) }
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        error: WebResourceError,
                                    ) {
                                        if (request.isForMainFrame) {
                                            controller.onLoadFailed(
                                                request.url.toString(),
                                                error.description?.toString()?.takeIf { it.isNotBlank() } ?: "erro de rede",
                                            )
                                        }
                                    }
                                }
                                webView = this
                                loadUrl(current.url)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Executa voltar/avançar/recarregar e informa se a navegação realmente começou. */
private fun performNavigation(
    view: WebView,
    command: BrowserCommand.Navigate,
    controller: BrowserSessionController,
    fallbackUrl: String,
) {
    when (command.kind) {
        BrowserCommand.NavigationKind.BACK -> {
            if (view.canGoBack()) {
                view.goBack()
                command.moved.complete(true)
            } else {
                command.moved.complete(false)
                controller.onPageLoaded(view.url ?: fallbackUrl, view.title)
            }
        }
        BrowserCommand.NavigationKind.FORWARD -> {
            if (view.canGoForward()) {
                view.goForward()
                command.moved.complete(true)
            } else {
                command.moved.complete(false)
                controller.onPageLoaded(view.url ?: fallbackUrl, view.title)
            }
        }
        BrowserCommand.NavigationKind.RELOAD -> {
            view.reload()
            command.moved.complete(true)
        }
    }
}
