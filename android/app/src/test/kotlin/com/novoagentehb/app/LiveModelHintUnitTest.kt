package com.novoagentehb.app

import com.novoagentehb.app.data.prefs.AppPreferences
import com.novoagentehb.app.gabi.GeminiLiveVoiceClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dica de falha do Gemini Live para modelo legado salvo no aparelho (sem chamada de rede). */
class LiveModelHintUnitTest {

    @Test
    fun legacyStoredModelGetsActionableHintNamingTheModel() {
        val hint = GeminiLiveVoiceClient.liveModelHint(AppPreferences.LEGACY_GEMINI_LIVE_MODEL)
        assertTrue(hint != null && hint.contains(AppPreferences.LEGACY_GEMINI_LIVE_MODEL))
        assertTrue(hint!!.contains("Aplicar padrão gemini-3.8-live"))
    }

    @Test
    fun approvedDefaultModelGetsNoHint() {
        assertNull(GeminiLiveVoiceClient.liveModelHint(AppPreferences.DEFAULT_GEMINI_LIVE_MODEL))
    }

    @Test
    fun defaultModelIsTheApprovedOne() {
        assertEquals("gemini-3.8-live", AppPreferences.DEFAULT_GEMINI_LIVE_MODEL)
    }
}
