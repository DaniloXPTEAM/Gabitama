package com.novoagentehb.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseVersionPolicyUnitTest {

    @Test
    fun missingOrEmptyFileIsFresh() {
        assertEquals(DatabaseVersionPolicy.Decision.Fresh, DatabaseVersionPolicy.decide(false, 0L, null))
        assertEquals(DatabaseVersionPolicy.Decision.Fresh, DatabaseVersionPolicy.decide(true, 0L, null))
    }

    @Test
    fun version1And2HaveMigrationPath() {
        assertEquals(DatabaseVersionPolicy.Decision.Ready(1), DatabaseVersionPolicy.decide(true, 4096L, 1))
        assertEquals(DatabaseVersionPolicy.Decision.Ready(2), DatabaseVersionPolicy.decide(true, 4096L, 2))
    }

    @Test
    fun futureVersionIsUnsupportedNotDestroyed() {
        val d = DatabaseVersionPolicy.decide(true, 4096L, 3)
        assertTrue(d is DatabaseVersionPolicy.Decision.Unsupported)
        assertEquals(3, (d as DatabaseVersionPolicy.Decision.Unsupported).foundVersion)
    }

    @Test
    fun zeroVersionWithContentAndUnreadableFileAreUnsupported() {
        assertTrue(DatabaseVersionPolicy.decide(true, 4096L, 0) is DatabaseVersionPolicy.Decision.Unsupported)
        assertTrue(DatabaseVersionPolicy.decide(true, 4096L, null) is DatabaseVersionPolicy.Decision.Unsupported)
    }

    @Test
    fun diagnosticContainsOnlyNumbersFixedNamesAndDate() {
        val line = DatabaseVersionPolicy.diagnosticLine(
            DatabaseVersionPolicy.Decision.Unsupported(3, "versão do banco é mais nova"),
            "2026-11-27T18:00:00Z",
        )
        assertTrue(line.contains("versao_encontrada=3"))
        assertTrue(line.contains("versao_esperada=2"))
        assertTrue(line.contains("acao=bloqueado_sem_alterar_dados"))
        assertFalse(line.contains("@"))
        assertFalse(line.contains("content", ignoreCase = true))
    }

    @Test
    fun currentVersionMatchesMigrationTable() {
        assertEquals(2, DatabaseVersionPolicy.CURRENT_VERSION)
        assertTrue(DatabaseVersionPolicy.MIGRATABLE_FROM.contains(DatabaseVersionPolicy.CURRENT_VERSION - 1))
    }
}
