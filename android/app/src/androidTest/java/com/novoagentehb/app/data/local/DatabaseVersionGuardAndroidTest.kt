package com.novoagentehb.app.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Versão desconhecida (futura) não pode ser apagada: o guard lança erro controlado,
 * preserva o arquivo byte a byte e grava diagnóstico sem conteúdo.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseVersionGuardAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile: File = context.getDatabasePath("guard-test-${System.nanoTime()}.db")
    private val diagDir: File = context.cacheDir

    @After
    fun cleanup() {
        dbFile.delete()
        File(diagDir, DatabaseVersionGuard.DIAGNOSTIC_FILE_NAME).delete()
        File(dbFile.parentFile, dbFile.name + "-journal").delete()
    }

    @Test
    fun futureVersionIsBlockedWithoutChangingData() {
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("CREATE TABLE chat_messages (id INTEGER PRIMARY KEY, content TEXT)")
            db.execSQL("INSERT INTO chat_messages (content) VALUES ('mensagem privada que não pode vazar')")
            db.version = 3
        }
        val before = dbFile.readBytes()

        try {
            DatabaseVersionGuard.checkOrThrow(dbFile, diagDir, "2026-11-27T18:00:00Z")
            fail("Versão 3 deveria ser bloqueada")
        } catch (e: DatabaseVersionUnsupportedException) {
            assertEquals(3, e.foundVersion)
            assertTrue(e.message.orEmpty().contains("Nenhum dado foi apagado"))
        }

        assertTrue("arquivo do banco deve existir", dbFile.exists())
        assertTrue("bytes do banco devem ser preservados", before.contentEquals(dbFile.readBytes()))
        val diag = File(diagDir, DatabaseVersionGuard.DIAGNOSTIC_FILE_NAME).readText()
        assertTrue(diag.contains("versao_encontrada=3"))
        assertFalse("diagnóstico não pode conter conteúdo do usuário", diag.contains("mensagem privada"))
    }

    @Test
    fun version2IsAcceptedAndUntouched() {
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("CREATE TABLE t (id INTEGER PRIMARY KEY)")
            db.version = 2
        }
        val before = dbFile.readBytes()
        DatabaseVersionGuard.checkOrThrow(dbFile, diagDir, "2026-11-27T18:00:00Z")
        assertTrue(before.contentEquals(dbFile.readBytes()))
        assertFalse(File(diagDir, DatabaseVersionGuard.DIAGNOSTIC_FILE_NAME).exists())
    }
}
