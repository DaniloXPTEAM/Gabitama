package com.novoagentehb.app.agent.browser

import java.net.URLDecoder

/**
 * Decodifica o retorno de `evaluateJavascript` do script de leitura.
 *
 * O script devolve `encodeURIComponent(texto)`, que não contém aspas nem barras invertidas;
 * por isso basta remover as aspas externas do JSON e decodificar o percentual.
 */
object BrowserTextCodec {
    const val MAX_TEXT_CHARS = 4000

    /** Script fixo; o agente não envia código. Limita o texto antes de codificar. */
    const val READ_TEXT_SCRIPT: String =
        "(function(){var t=document.body?document.body.innerText:'';" +
            "return encodeURIComponent(String(t).slice(0,20000));})()"

    fun decode(raw: String?): String {
        if (raw.isNullOrEmpty() || raw == "null") return ""
        val unquoted = raw.removeSurrounding("\"")
        val decoded = try {
            URLDecoder.decode(unquoted, "UTF-8")
        } catch (e: IllegalArgumentException) {
            return ""
        }
        return decoded.replace(Regex("\\s+"), " ").trim().take(MAX_TEXT_CHARS)
    }
}
