package com.novoagentehb.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Uma visita registrada no histórico do navegador embutido. */
@Entity(
    tableName = "browser_history",
    indices = [Index(value = ["visitedAt"], name = "index_browser_history_visitedAt")]
)
data class BrowserHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long
)

@Dao
interface BrowserHistoryDao {
    @Query("SELECT * FROM browser_history ORDER BY visitedAt DESC, id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<BrowserHistoryEntity>

    @Query("SELECT url FROM browser_history ORDER BY visitedAt DESC, id DESC LIMIT 1")
    suspend fun lastUrl(): String?

    @Insert
    suspend fun insert(entry: BrowserHistoryEntity): Long

    /** Mantém apenas as [keep] visitas mais recentes. */
    @Query(
        "DELETE FROM browser_history WHERE id NOT IN " +
            "(SELECT id FROM browser_history ORDER BY visitedAt DESC, id DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM browser_history")
    suspend fun clearAll()
}

/**
 * Migração aditiva da versão 1 para 2: cria apenas a tabela `browser_history`.
 * Não altera nem remove tabelas existentes (chat, memória, metas, lembretes, etc.).
 */
/** SQL da migração 1→2; o teste de schema compara estas constantes com o schema Room v2 exportado. */
internal const val BROWSER_HISTORY_CREATE_SQL =
    "CREATE TABLE IF NOT EXISTS `browser_history` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`url` TEXT NOT NULL, " +
        "`title` TEXT NOT NULL, " +
        "`visitedAt` INTEGER NOT NULL)"

internal const val BROWSER_HISTORY_INDEX_SQL =
    "CREATE INDEX IF NOT EXISTS `index_browser_history_visitedAt` " +
        "ON `browser_history` (`visitedAt`)"

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(BROWSER_HISTORY_CREATE_SQL)
        db.execSQL(BROWSER_HISTORY_INDEX_SQL)
    }
}
