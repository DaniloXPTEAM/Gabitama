package com.novoagentehb.app.agent.browser

import com.novoagentehb.app.data.local.BrowserHistoryDao
import com.novoagentehb.app.data.local.BrowserHistoryEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Histórico de navegação persistido em Room (tabela `browser_history`). */
@Singleton
class BrowserHistoryStore @Inject constructor(
    private val dao: BrowserHistoryDao,
) {
    /**
     * Registra uma visita, aplicando [BrowserHistoryPolicy]: ignora repetição imediata
     * da mesma URL, normaliza o título e mantém o limite de entradas.
     * Retorna true se a visita foi gravada.
     */
    suspend fun record(url: String, title: String?, now: Long = System.currentTimeMillis()): Boolean {
        val previous = dao.lastUrl()
        if (!BrowserHistoryPolicy.shouldRecord(previous, url)) return false
        dao.insert(
            BrowserHistoryEntity(
                url = url,
                title = BrowserHistoryPolicy.sanitizeTitle(title, url),
                visitedAt = now,
            )
        )
        val recent = dao.getRecent(BrowserHistoryPolicy.MAX_ENTRIES + 1)
        if (BrowserHistoryPolicy.excessCount(recent.size) > 0) {
            dao.trimTo(BrowserHistoryPolicy.MAX_ENTRIES)
        }
        return true
    }

    suspend fun recent(limit: Int = 50): List<BrowserHistoryEntity> =
        dao.getRecent(limit.coerceIn(1, BrowserHistoryPolicy.MAX_ENTRIES))

    /** Apaga todo o histórico de navegação; não afeta outras tabelas. */
    suspend fun clear() = dao.clearAll()
}
