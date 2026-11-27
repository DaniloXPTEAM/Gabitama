package com.novoagentehb.app.data.local

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Valida a migração 1→2 contra os schemas Room exportados pelo CI
 * (src/test/resources/room-schemas/1.json e 2.json, SHA-256 verificados).
 *
 * Não executa SQL: comprova que o SQL da migração é exatamente o schema v2
 * e que a v1 está contida na v2 sem alterações (migração aditiva, sem perda de dados).
 * O teste instrumentado com MigrationTestHelper continua pendente.
 */
class BrowserHistoryMigrationUnitTest {

    private fun loadSchema(version: Int): JsonObject {
        val stream = javaClass.getResourceAsStream("/room-schemas/$version.json")
        assertNotNull("schema $version ausente", stream)
        val root = JsonParser.parseReader(stream!!.reader(Charsets.UTF_8)).asJsonObject
        return root.getAsJsonObject("database")
    }

    private fun entities(db: JsonObject): Map<String, JsonObject> =
        db.getAsJsonArray("entities").associate { it.asJsonObject.get("tableName").asString to it.asJsonObject }

    private fun JsonObject.fingerprint(): String = toString()

    @Test
    fun schemaVersionsMatchMigrationPath() {
        assertEquals(1, loadSchema(1).get("version").asInt)
        assertEquals(2, loadSchema(2).get("version").asInt)
    }

    @Test
    fun appDatabaseDeclaresVersion2() {
        val source = File("src/main/kotlin/com/novoagentehb/app/data/local/AppDatabase.kt").readText()
        assertTrue("AppDatabase deve declarar version = 2", Regex("version\\s*=\\s*2\\b").containsMatchIn(source))
    }

    @Test
    fun migrationIsAdditiveAllV1TablesUnchangedInV2() {
        val v1 = entities(loadSchema(1))
        val v2 = entities(loadSchema(2))
        assertEquals(7, v1.size)
        for ((table, definition) in v1) {
            val other = v2[table]
            assertNotNull("tabela $table sumiu na v2", other)
            assertEquals("tabela $table alterada na v2", definition.fingerprint(), other!!.fingerprint())
        }
        assertEquals(setOf("browser_history"), v2.keys - v1.keys)
    }

    @Test
    fun browserHistoryCreateSqlMatchesExportedSchema() {
        val table = entities(loadSchema(2)).getValue("browser_history")
        val expected = table.get("createSql").asString.replace("\${TABLE_NAME}", "browser_history")
        assertEquals(expected, BROWSER_HISTORY_CREATE_SQL)
    }

    @Test
    fun browserHistoryColumnsMatchExportedSchema() {
        val table = entities(loadSchema(2)).getValue("browser_history")
        val fields = table.getAsJsonArray("fields")
        val expected = listOf(
            Triple("id", "INTEGER", true),
            Triple("url", "TEXT", true),
            Triple("title", "TEXT", true),
            Triple("visitedAt", "INTEGER", true)
        )
        assertEquals(expected, fields.map {
            val f = it.asJsonObject
            Triple(f.get("columnName").asString, f.get("affinity").asString, f.get("notNull").asBoolean)
        })
        assertEquals("id", table.getAsJsonObject("primaryKey").getAsJsonArray("columnNames").single().asString)
        assertTrue(table.getAsJsonObject("primaryKey").get("autoGenerate").asBoolean)
    }

    @Test
    fun browserHistoryIndexMatchesExportedSchema() {
        val indices: JsonArray = entities(loadSchema(2)).getValue("browser_history").getAsJsonArray("indices")
        assertEquals(1, indices.size())
        val index = indices[0].asJsonObject
        assertEquals("index_browser_history_visitedAt", index.get("name").asString)
        assertEquals(false, index.get("unique").asBoolean)
        assertEquals(listOf("visitedAt"), index.getAsJsonArray("columnNames").map { it.asString })
        val expected = index.get("createSql").asString.replace("\${TABLE_NAME}", "browser_history")
        assertEquals(expected, BROWSER_HISTORY_INDEX_SQL)
    }
}
