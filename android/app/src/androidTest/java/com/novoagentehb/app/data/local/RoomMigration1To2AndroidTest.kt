package com.novoagentehb.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste instrumentado da migração 1→2 (tabela browser_history).
 *  1. Cria um banco v1 real com linhas em todas as tabelas (SQL executado de verdade).
 *  2. MigrationTestHelper aplica MIGRATION_1_2 e valida o schema v2 contra o 2.json exportado.
 *  3. Abre o banco com Room SEM fallbackToDestructiveMigration (caminho de produção) e confirma
 *     que nenhuma linha foi perdida e que browser_history funciona.
 */
@RunWith(AndroidJUnit4::class)
class RoomMigration1To2AndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-1-2-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @After
    fun cleanup() {
        context.deleteDatabase(dbName)
    }

    private fun seedVersion1() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO chat_messages (sessionId, role, content, taskId, providerUsed, modelUsed, createdAt) VALUES ('s1','USER','olá Gabi',NULL,NULL,NULL,1000)")
            db.execSQL("INSERT INTO chat_messages (sessionId, role, content, taskId, providerUsed, modelUsed, createdAt) VALUES ('s1','ASSISTANT','Oi!','t1','gemini','flash',2000)")
            db.execSQL("INSERT INTO memory_items (category, memoryKey, value, provenance, confidence, useCount, lastUsedAt, createdAt) VALUES ('USER_NAME','nome','Danilo','user_conversation',0.9,2,1000,1000)")
            db.execSQL("INSERT INTO goals (title, description, category, status, linkedTaskIds, progressPercent, createdAt, updatedAt) VALUES ('meta','desc','cat','ACTIVE','[]',10,1,1)")
            db.execSQL("INSERT INTO reminders (title, triggerAtMillis, triggered, createdAt) VALUES ('lembrete',5000,0,1)")
            db.execSQL("INSERT INTO feed_events (sourcePackage, sourceLabel, title, body, kind, isRead, createdAt) VALUES ('pkg','rótulo','t','b','k',0,1)")
            db.execSQL("INSERT INTO task_receipts (taskId, idempotencyKey, goalId, capabilityName, riskLevel, status, summary, verifiedOutcome, errorCode, providerUsed, durationMs, createdAt) VALUES ('task1','key1',NULL,'cap','LOW','DONE','resumo',1,NULL,NULL,12,1)")
            db.execSQL("INSERT INTO skill_records (skillId, name, sourceType, version, summary, scope, manifestOrigin, enabledByUser, untrustedContentFlag, updatedAt) VALUES ('skill1','nome','LOCAL','1.0','resumo','escopo','origem',1,0,1)")
        }
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Long =
        db.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); c.getLong(0) }

    @Test
    fun migrationKeepsEveryRowAndValidatesSchemaV2() {
        seedVersion1()
        // Valida o schema resultante contra o 2.json exportado pelo build (identityHash inclusive).
        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).use { db ->
            assertEquals(2L, count(db, "chat_messages"))
            assertEquals(1L, count(db, "memory_items"))
            assertEquals(1L, count(db, "goals"))
            assertEquals(1L, count(db, "reminders"))
            assertEquals(1L, count(db, "feed_events"))
            assertEquals(1L, count(db, "task_receipts"))
            assertEquals(1L, count(db, "skill_records"))
            assertEquals(0L, count(db, "browser_history"))
            db.query("SELECT content FROM chat_messages ORDER BY createdAt").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("olá Gabi", c.getString(0))
                assertTrue(c.moveToNext())
                assertEquals("Oi!", c.getString(0))
            }
        }
    }

    @Test
    fun roomOpensMigratedDatabaseWithoutDestructiveFallback() = runBlocking {
        seedVersion1()
        // Caminho de produção: MIGRATION_1_2 e nenhum fallbackToDestructiveMigration.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(2L, db.openHelper.readableDatabase.let { raw ->
                raw.query("SELECT COUNT(*) FROM chat_messages").use { c -> c.moveToFirst(); c.getLong(0) }
            })
            db.browserHistoryDao().insert(BrowserHistoryEntity(url = "https://example.com", title = "Exemplo", visitedAt = 3000))
            val recent = db.browserHistoryDao().getRecent(10)
            assertEquals(1, recent.size)
            assertEquals("https://example.com", recent.first().url)
            // Dados antigos continuam intactos depois da migração e da escrita nova.
            assertEquals(1L, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM memory_items").use { c -> c.moveToFirst(); c.getLong(0) })
        } finally {
            db.close()
        }
    }
}
