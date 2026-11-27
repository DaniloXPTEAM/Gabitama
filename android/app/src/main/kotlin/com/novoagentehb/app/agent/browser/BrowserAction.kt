package com.novoagentehb.app.agent.browser

import java.net.URI
import java.net.URISyntaxException

/**
 * Ações de navegador que o agente pode solicitar.
 *
 * Implementação própria do GabiTama (licença do projeto: MIT). Não deriva de código
 * de outros projetos; o conjunto de ações foi definido pelos requisitos do agente.
 *
 * [changesPage] indica ações que alteram a página ou as abas e, portanto, exigem
 * que o resultado seja relido antes de ser tratado como estado atual.
 */
enum class BrowserAction(val wireName: String, val changesPage: Boolean) {
    NAVIGATE("navigate", changesPage = true),
    BACK("back", changesPage = true),
    FORWARD("forward", changesPage = true),
    RELOAD("reload", changesPage = true),
    SCROLL("scroll", changesPage = true),
    NEW_TAB("new_tab", changesPage = true),
    CLOSE_TAB("close_tab", changesPage = true),
    SCREENSHOT("screenshot", changesPage = false),
    GET_TEXT("get_text", changesPage = false),
    GET_PAGE_INFO("get_page_info", changesPage = false),
    LIST_TABS("list_tabs", changesPage = false);

    companion object {
        private val byWireName: Map<String, BrowserAction> = entries.associateBy { it.wireName }

        /** Converte o nome enviado pelo agente; retorna null para ações desconhecidas. */
        fun fromWireName(name: String): BrowserAction? = byWireName[name.trim().lowercase()]

        /** Nomes aceitos, para mensagens de erro e para o prompt do agente. */
        val wireNames: List<String> get() = entries.map { it.wireName }
    }
}

enum class ScrollDirection(val wireName: String) {
    UP("up"),
    DOWN("down");

    companion object {
        fun fromWireName(name: String): ScrollDirection? =
            entries.find { it.wireName == name.trim().lowercase() }
    }
}

/** Resultado da validação de uma URL solicitada ao navegador. */
sealed class UrlCheck {
    data class Allowed(val url: String) : UrlCheck()
    data class Rejected(val reason: String) : UrlCheck()
}

/**
 * Política de URL do navegador: apenas http/https, sem credenciais embutidas, tamanho
 * limitado e sem endereços locais ou de rede privada. Esquemas como file:, javascript:,
 * intent: e content: são recusados.
 */
object BrowserUrlPolicy {
    const val MAX_URL_LENGTH = 2048
    private val allowedSchemes = setOf("http", "https")

    fun check(raw: String): UrlCheck {
        val candidate = raw.trim()
        if (candidate.isEmpty()) return UrlCheck.Rejected("URL vazia")
        if (candidate.length > MAX_URL_LENGTH) return UrlCheck.Rejected("URL longa demais")
        val uri = try {
            URI(candidate)
        } catch (e: URISyntaxException) {
            return UrlCheck.Rejected("URL inválida")
        }
        val scheme = uri.scheme?.lowercase()
            ?: return UrlCheck.Rejected("Esquema ausente; use http:// ou https://")
        if (scheme !in allowedSchemes) return UrlCheck.Rejected("Esquema não permitido: $scheme")
        if (uri.host.isNullOrBlank()) return UrlCheck.Rejected("Host ausente")
        if (uri.userInfo != null) return UrlCheck.Rejected("Credenciais na URL não são permitidas")
        if (isLocalOrPrivateHost(uri.host)) {
            return UrlCheck.Rejected("Endereço local ou de rede privada não é permitido")
        }
        return UrlCheck.Allowed(uri.toString())
    }

    /**
     * Recusa loopback, link-local, redes privadas e nomes locais, para que o agente não
     * use o navegador para acessar roteadores ou serviços do aparelho e da rede.
     * Verificação apenas pelo texto do host; não resolve DNS.
     */
    fun isLocalOrPrivateHost(rawHost: String): Boolean {
        val host = rawHost.lowercase().removePrefix("[").removeSuffix("]")
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return true
        if (host.contains(':')) {
            // IPv6 literal: loopback, não especificado, unique-local (fc00::/7) e link-local (fe80::/10)
            return host == "::1" || host == "::" || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80")
        }
        val parts = host.split(".")
        if (parts.size != 4 || parts.any { it.toIntOrNull()?.let { n -> n in 0..255 } != true }) return false
        val a = parts[0].toInt()
        val b = parts[1].toInt()
        return a == 0 || a == 10 || a == 127 ||
            (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 100 && b in 64..127)
    }
}
