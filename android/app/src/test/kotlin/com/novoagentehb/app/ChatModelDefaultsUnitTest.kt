package com.novoagentehb.app

import com.novoagentehb.app.data.prefs.AppPreferences
import com.novoagentehb.app.data.prefs.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatModelDefaultsUnitTest {
    @Test fun geminiChatDefaultIsFlashLiteButVisionAndScreenKeepPreviousDefault() {
        assertEquals("gemini-3.5-flash-lite", AppPreferences.DEFAULT_GEMINI_CHAT_MODEL)
        assertEquals("gemini-3.5-flash-lite", ProviderId.GEMINI.defaultChatModel)
        // Vision and screen operation stay on the previous default until compatibility is confirmed.
        assertEquals("gemini-2.5-flash", ProviderId.GEMINI.defaultTextModel)
    }

    @Test fun otherProvidersKeepTheirOwnDefaultsForChat() {
        for (p in ProviderId.values().filter { it != ProviderId.GEMINI }) {
            assertEquals(p.defaultTextModel, p.defaultChatModel)
        }
    }

    @Test fun liveVoiceModelIsNotChangedByChatDefault() {
        assertEquals("gemini-2.5-flash-native-audio-preview-09-2025", AppPreferences.DEFAULT_GEMINI_LIVE_MODEL)
        assert(AppPreferences.DEFAULT_GEMINI_LIVE_MODEL != AppPreferences.DEFAULT_GEMINI_CHAT_MODEL)
    }
}
