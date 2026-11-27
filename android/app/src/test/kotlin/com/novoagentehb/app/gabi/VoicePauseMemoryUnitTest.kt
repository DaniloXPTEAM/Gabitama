package com.novoagentehb.app.gabi

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pausa da voz ao sair do app: consumida uma vez, sem afirmar escuta contínua. */
class VoicePauseMemoryUnitTest {

    @After
    fun clean() = VoicePauseMemory.clear()

    @Test
    fun pausaEhConsumidaUmaUnicaVez() {
        VoicePauseMemory.markPaused()
        assertTrue(VoicePauseMemory.consumePaused())
        assertFalse("segunda retomada não pode repetir o aviso", VoicePauseMemory.consumePaused())
    }

    @Test
    fun semPausaNaoHaAviso() {
        assertFalse(VoicePauseMemory.consumePaused())
    }

    @Test
    fun avisoExigeToqueExplicitoEMencionaSegundoPlano() {
        val notice = VoicePauseMemory.resumeNotice()
        assertTrue(notice.contains("Toque no microfone"))
        assertTrue(notice.contains("segundo plano"))
        assertFalse(notice.contains("continuo ouvindo"))
    }
}
