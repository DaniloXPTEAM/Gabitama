package com.novoagentehb.app.bridges

/**
 * Memória do último handoff para o Spotify. Serve para retomar a conversa quando o usuário
 * volta ao GabiTama: a Gabi não consegue acompanhar o app de música em segundo plano, então
 * registra o que foi pedido e avisa, explicitamente, o que não foi confirmado.
 *
 * Implementação própria do GabiTama (MIT). Sem Android; testável na JVM.
 */
object SpotifyHandoffMemory {
    const val MAX_AGE_MILLIS = 30 * 60 * 1000L

    enum class Kind { OPEN, SEARCH }

    data class Pending(val kind: Kind, val query: String?, val createdAtMillis: Long)

    private val lock = Any()
    private var pending: Pending? = null

    fun remember(kind: Kind, query: String?, nowMillis: Long) {
        synchronized(lock) { pending = Pending(kind, query?.take(200), nowMillis) }
    }

    /** Retorna o handoff pendente uma única vez e apenas se ainda estiver dentro do prazo. */
    fun takeForResume(nowMillis: Long): Pending? {
        synchronized(lock) {
            val current = pending ?: return null
            pending = null
            return if (nowMillis - current.createdAtMillis > MAX_AGE_MILLIS || nowMillis < current.createdAtMillis) null else current
        }
    }

    fun clear() = synchronized(lock) { pending = null }

    fun resumeText(p: Pending): String = when (p.kind) {
        Kind.OPEN -> "Voltei ao GabiTama. Você abriu o Spotify, mas eu não acompanhei o que aconteceu lá: nada foi reproduzido nem confirmado. Quer que eu faça outra coisa?"
        Kind.SEARCH -> "Voltei ao GabiTama. Abri a busca no Spotify por \"${p.query.orEmpty()}\", mas não confirmei os resultados nem a reprodução. Quer que eu tente outra busca?"
    }
}
