package com.novoagentehb.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novoagentehb.app.data.prefs.AppPreferences
import com.novoagentehb.app.data.prefs.ProviderId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatModelPreservationAndroidTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun savedGeminiChatModelIsPreservedAcrossReopenAndProviderSwitch() = runBlocking {
        val prefs = AppPreferences(context)
        val originalProvider = prefs.primaryProvider.first()
        val oldChat = prefs.chatModel.first()
        val oldScreen = prefs.screenOpModel.first()
        val oldVision = prefs.visionModel.first()
        val oldLive = prefs.liveVoiceModel.first()
        try {
            // 1) An explicit, previously saved choice is returned unchanged after reopening the store.
            prefs.setPrimaryProvider(ProviderId.GEMINI)
            prefs.setModels("custom-chat", "custom-screen", "custom-vision", oldLive)
            assertEquals("custom-chat", AppPreferences(context).chatModel.first())

            // 2) Switching away and back keeps the Gemini chat choice (stored per provider).
            prefs.setPrimaryProvider(ProviderId.GROQ)
            prefs.setPrimaryProvider(ProviderId.GEMINI)
            val back = AppPreferences(context)
            assertEquals("custom-chat", back.chatModel.first())
            assertEquals("custom-screen", back.screenOpModel.first())
            assertEquals("custom-vision", back.visionModel.first())
            assertEquals(oldLive, back.liveVoiceModel.first())
        } finally {
            prefs.setPrimaryProvider(originalProvider)
            prefs.setModels(oldChat, oldScreen, oldVision, oldLive)
        }
    }
}
