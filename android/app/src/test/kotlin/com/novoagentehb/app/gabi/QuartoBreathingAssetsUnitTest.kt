package com.novoagentehb.app.gabi

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sequências do Quarto com movimento (respiração/pulso) geradas a partir das poses aprovadas.
 * Garante que as pastas existem, têm a quantidade de quadros do loop e são WebP.
 */
class QuartoBreathingAssetsUnitTest {

    private val assetsRoot = File("src/main/assets/anim")

    private fun frames(folder: String): List<File> =
        File(assetsRoot, folder).listFiles { f -> f.name.endsWith(".webp") }.orEmpty().sortedBy { it.name }

    @Test
    fun cenasDoQuartoTemMovimentoComQuadrosEsperados() {
        val expected = mapOf(
            "scene_comer" to 48,
            "scene_ler" to 60,
            "scene_dormir" to 72,
            "scene_comendo_reacao" to 24,
        )
        for ((folder, count) in expected) {
            val list = frames(folder)
            assertEquals("quadros de $folder", count, list.size)
            assertEquals("primeiro quadro de $folder", "f001.webp", list.first().name)
            assertTrue("arquivo vazio em $folder", list.all { it.length() > 0 })
        }
    }

    @Test
    fun pastaDeComerSemQuadrosNaoPassa() {
        assertTrue(frames("scene_inexistente").isEmpty())
    }
}
