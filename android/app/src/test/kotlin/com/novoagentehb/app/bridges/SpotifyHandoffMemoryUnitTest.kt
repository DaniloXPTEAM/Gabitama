package com.novoagentehb.app.bridges

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyHandoffMemoryUnitTest {

    @After
    fun clean() = SpotifyHandoffMemory.clear()

    @Test
    fun handoffIsRetomadoUmaUnicaVez() {
        SpotifyHandoffMemory.remember(SpotifyHandoffMemory.Kind.SEARCH, "campanha de rpg", 1_000L)
        val first = SpotifyHandoffMemory.takeForResume(2_000L)
        assertEquals("campanha de rpg", first?.query)
        assertNull("segunda retomada não pode repetir o aviso", SpotifyHandoffMemory.takeForResume(3_000L))
    }

    @Test
    fun handoffVencidoNaoEhRetomado() {
        SpotifyHandoffMemory.remember(SpotifyHandoffMemory.Kind.OPEN, null, 0L)
        assertNull(SpotifyHandoffMemory.takeForResume(SpotifyHandoffMemory.MAX_AGE_MILLIS + 1))
    }

    @Test
    fun relogioVoltandoNaoGeraAviso() {
        SpotifyHandoffMemory.remember(SpotifyHandoffMemory.Kind.OPEN, null, 5_000L)
        assertNull(SpotifyHandoffMemory.takeForResume(1_000L))
    }

    @Test
    fun avisoNuncaAfirmaReproducao() {
        val search = SpotifyHandoffMemory.resumeText(SpotifyHandoffMemory.Pending(SpotifyHandoffMemory.Kind.SEARCH, "rpg", 0L))
        val open = SpotifyHandoffMemory.resumeText(SpotifyHandoffMemory.Pending(SpotifyHandoffMemory.Kind.OPEN, null, 0L))
        assertTrue(search.contains("não confirmei"))
        assertTrue(open.contains("nada foi reproduzido"))
        assertTrue(!search.contains("tocando") && !open.contains("tocando"))
    }
}
