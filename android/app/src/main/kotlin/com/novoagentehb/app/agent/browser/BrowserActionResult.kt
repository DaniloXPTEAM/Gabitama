package com.novoagentehb.app.agent.browser

/**
 * Resultado de uma ação do navegador.
 *
 * [success] indica se a ação foi executada. Quando falha, [errorCode] traz uma
 * categoria estável e [text] uma mensagem legível. Implementação própria (MIT).
 */
data class BrowserActionResult(
    val success: Boolean,
    val text: String,
    val pageUrl: String? = null,
    val errorCode: String? = null,
) {
    companion object {
        fun ok(text: String, pageUrl: String? = null) =
            BrowserActionResult(success = true, text = text, pageUrl = pageUrl)

        fun error(code: String, message: String, pageUrl: String? = null) =
            BrowserActionResult(success = false, text = message, pageUrl = pageUrl, errorCode = code)
    }
}
