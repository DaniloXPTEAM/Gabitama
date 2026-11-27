package com.novoagentehb.app.agent.browser

/**
 * Regras puras do histórico de navegação (sem Android, testáveis na JVM).
 *
 * O armazenamento é feito pela tabela Room `browser_history`; estas regras decidem
 * o que entra nela e quanto fica guardado.
 */
object BrowserHistoryPolicy {
    const val MAX_ENTRIES = 500
    const val MAX_TITLE_LENGTH = 200

    /** Não registra recarregamento ou repetição imediata da mesma URL. */
    fun shouldRecord(previousUrl: String?, newUrl: String): Boolean = previousUrl != newUrl

    /** Título limpo: espaços colapsados, limite de tamanho e URL como reserva. */
    fun sanitizeTitle(rawTitle: String?, url: String): String {
        val collapsed = rawTitle?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        return (collapsed.ifEmpty { url }).take(MAX_TITLE_LENGTH)
    }

    /** Quantas entradas mais antigas devem ser removidas para respeitar [MAX_ENTRIES]. */
    fun excessCount(currentCount: Int): Int = maxOf(0, currentCount - MAX_ENTRIES)
}
