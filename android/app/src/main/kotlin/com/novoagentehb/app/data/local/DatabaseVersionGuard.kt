package com.novoagentehb.app.data.local

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.Instant

/**
 * Decisão sobre a versão do banco antes de o Room abri-lo. Sem migração destrutiva:
 * versão desconhecida, futura (downgrade) ou ilegível NUNCA é apagada.
 *
 * Implementação própria do GabiTama (MIT).
 */
object DatabaseVersionPolicy {
    /** Versão atual do schema (deve coincidir com AppDatabase.version). */
    const val CURRENT_VERSION = 2

    /** Versões com caminho de migração explícito até [CURRENT_VERSION]. */
    val MIGRATABLE_FROM: Set<Int> = setOf(1, 2)

    sealed class Decision {
        /** Arquivo inexistente ou vazio: o Room cria o banco na versão atual. */
        object Fresh : Decision()

        /** Versão com migração explícita (ou já atual): o Room abre normalmente. */
        data class Ready(val foundVersion: Int) : Decision()

        /** Sem migração válida: o app deve parar com erro controlado; nada é alterado. */
        data class Unsupported(val foundVersion: Int, val reason: String) : Decision()
    }

    fun decide(fileExists: Boolean, fileLength: Long, foundVersion: Int?): Decision {
        if (!fileExists || fileLength == 0L) return Decision.Fresh
        if (foundVersion == null) {
            return Decision.Unsupported(-1, "arquivo ilegível como banco SQLite")
        }
        if (foundVersion == CURRENT_VERSION) return Decision.Ready(foundVersion)
        if (foundVersion in MIGRATABLE_FROM && foundVersion < CURRENT_VERSION) return Decision.Ready(foundVersion)
        if (foundVersion > CURRENT_VERSION) {
            return Decision.Unsupported(foundVersion, "versão do banco é mais nova que esta versão do app (downgrade sem migração)")
        }
        return Decision.Unsupported(foundVersion, "versão sem caminho de migração")
    }

    /** Texto de diagnóstico sem conteúdo privado: apenas números, nomes fixos e data. */
    fun diagnosticLine(decision: Decision.Unsupported, nowIso: String): String =
        "data=$nowIso; banco=${AppDatabase.DATABASE_NAME}; versao_encontrada=${decision.foundVersion}; " +
            "versao_esperada=$CURRENT_VERSION; motivo=${decision.reason}; acao=bloqueado_sem_alterar_dados"
}

/** Erro controlado: o app não abre o banco e nada é apagado. */
class DatabaseVersionUnsupportedException(val foundVersion: Int, message: String) : IllegalStateException(message)

object DatabaseVersionGuard {
    const val DIAGNOSTIC_FILE_NAME = "db_versao_diagnostico.txt"

    /** Lê PRAGMA user_version em modo somente leitura. Retorna null se o arquivo não for um banco legível. */
    fun readUserVersion(file: File): Int? = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA user_version", null).use { c -> if (c.moveToFirst()) c.getInt(0) else null }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Verifica o arquivo antes de abrir o Room. Se não houver caminho de migração, grava o
     * diagnóstico (sem conteúdo) em [diagnosticDir] e lança [DatabaseVersionUnsupportedException].
     * O arquivo do banco não é tocado em nenhum caso.
     */
    fun checkOrThrow(databaseFile: File, diagnosticDir: File, nowIso: String = Instant.now().toString()) {
        val exists = databaseFile.exists()
        val length = if (exists) databaseFile.length() else 0L
        val found = if (exists && length > 0L) readUserVersion(databaseFile) else null
        when (val decision = DatabaseVersionPolicy.decide(exists, length, found)) {
            is DatabaseVersionPolicy.Decision.Fresh, is DatabaseVersionPolicy.Decision.Ready -> Unit
            is DatabaseVersionPolicy.Decision.Unsupported -> {
                runCatching {
                    File(diagnosticDir, DIAGNOSTIC_FILE_NAME).appendText(
                        DatabaseVersionPolicy.diagnosticLine(decision, nowIso) + "\n"
                    )
                }
                throw DatabaseVersionUnsupportedException(
                    decision.foundVersion,
                    "Banco com versão ${decision.foundVersion} não suportado (esperada ${DatabaseVersionPolicy.CURRENT_VERSION}). Nenhum dado foi apagado.",
                )
            }
        }
    }
}
