package com.novoagentehb.app.bridges

import java.text.Normalizer

/**
 * Interpreta pedidos da Gabi que mencionam o Spotify. Implementação própria do GabiTama (MIT).
 *
 * Três intenções diferentes, que nunca se confundem:
 *  - Open: abrir o aplicativo ("abre o Spotify").
 *  - Search: procurar conteúdo ("procura uma campanha de RPG no Spotify").
 *  - PlayRequest: reproduzir algo ("toca minha playlist no Spotify"). Sem integração autorizada
 *    com a conta, o app não reproduz; o pedido é recusado com motivo, nunca simulado.
 */
object SpotifyCommandParser {

    sealed class Command {
        data class Open(val appName: String) : Command()
        data class Search(val query: String) : Command()
        data class PlayRequest(val target: String) : Command()
        object NotSpotify : Command()
    }

    private val wakeWord = Regex("^gabi\\b[\\s,:.!]*")
    private val spotifyPhrase = Regex("(?:\\b(?:no|na|pelo|pela|do|da|em|pro|pra)\\s+)?\\bspotify\\b")
    private val playVerb = Regex("^(toca|tocar|toque|reproduz|reproduza|reproduzir|poe|poem|coloca|colocar|coloque|ouvir|ouca|escuta|escutar)\\b\\s*")
    private val searchVerb = Regex("^(procura|procure|procurar|pesquisa|pesquise|pesquisar|busca|busque|buscar|acha|ache|achar|encontra|encontre|encontrar)\\b\\s*")
    private val openVerb = Regex("^(abre|abra|abrir|inicia|inicie|iniciar|entra|entre|entrar)\\b\\s*")
    private val leadingArticles = Regex("^(um|uma|uns|umas|o|a|os|as|de|do|da)\\s+")

    fun parse(raw: String): Command {
        var s = normalize(raw)
        s = wakeWord.replace(s, "").trim()
        if (!s.contains("spotify")) return Command.NotSpotify

        val rest = spotifyPhrase.replace(s, " ").replace(Regex("\\s+"), " ").trim().trimEnd('.', '!', '?')

        return when {
            searchVerb.containsMatchIn(rest) -> {
                val afterVerb = searchVerb.replace(rest, "")
                Command.Search(leadingArticles.replace(afterVerb, "").trim())
            }
            playVerb.containsMatchIn(rest) -> {
                val afterVerb = playVerb.replace(rest, "")
                Command.PlayRequest(afterVerb.trim())
            }
            openVerb.containsMatchIn(rest) -> Command.Open(APP_LABEL)
            rest.isEmpty() -> Command.Open(APP_LABEL)
            else -> Command.Search(leadingArticles.replace(rest, "").trim())
        }
    }

    /** Nome do aplicativo como o alias local espera (preserva a forma já aceita: "Spotify"). */
    const val APP_LABEL = "Spotify"

    internal fun normalize(raw: String): String =
        Normalizer.normalize(raw.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

/** Resultado de uma intenção do Spotify, sem depender de Android. */
data class SpotifyResult(
    val success: Boolean,
    /** Verdadeiro somente quando o app confirmou o efeito final (nunca nesta versão para Spotify). */
    val verified: Boolean,
    val errorCode: String?,
    val message: String,
)

object SpotifyOutcomeRules {
    const val PLAYBACK_UNAVAILABLE = "SPOTIFY_PLAYBACK_UNAVAILABLE"

    /** Abrir o app ou a busca é só handoff: sucesso sem verificação do conteúdo ou da reprodução. */
    fun forOpen(opened: Boolean, query: String?, failureMessage: String?): SpotifyResult =
        if (opened) {
            val what = if (query.isNullOrBlank()) "o aplicativo" else "a busca por \"$query\""
            SpotifyResult(
                success = true,
                verified = false,
                errorCode = null,
                message = "Abri $what no Spotify. Não confirmei o resultado. Nada foi reproduzido pela Gabi.",
            )
        } else {
            SpotifyResult(false, false, "SPOTIFY_FAILED", failureMessage ?: "Não consegui abrir o Spotify.")
        }

    /** Pedido de reprodução: recusado com motivo. Nada é aberto nem marcado como tocando. */
    fun forPlayRequest(target: String): SpotifyResult = SpotifyResult(
        success = false,
        verified = false,
        errorCode = PLAYBACK_UNAVAILABLE,
        message = "Não consigo iniciar a reprodução no Spotify: este app não tem integração autorizada com a sua conta. " +
            (if (target.isBlank()) "" else "Posso abrir a busca por \"$target\" para você escolher. ") +
            "Nada foi reproduzido.",
    )

    fun forBlankQuery(): SpotifyResult = SpotifyResult(
        success = false,
        verified = false,
        errorCode = "SPOTIFY_QUERY_EMPTY",
        message = "Diga o que devo procurar no Spotify.",
    )
}
