package com.novoagentehb.app

import com.novoagentehb.app.agent.models.FailureCategory
import com.novoagentehb.app.agent.models.ModelChatMessage
import com.novoagentehb.app.agent.models.ModelRouter
import com.novoagentehb.app.agent.models.ModelTurnResult
import com.novoagentehb.app.agent.tools.CapabilityRegistry
import com.novoagentehb.app.gabi.FrozenVoiceIdentitySpec
import com.novoagentehb.app.gabi.GeminiLiveEvent
import com.novoagentehb.app.gabi.GeminiLiveSessionReducer
import com.novoagentehb.app.gabi.GeminiLiveTurnAggregator
import com.novoagentehb.app.gabi.GeminiLiveVoiceClient
import com.novoagentehb.app.gabi.LiveReducerInput
import com.novoagentehb.app.gabi.LiveSessionState
import com.novoagentehb.app.gabi.SpeechSessionWatchdog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRouterAndVoiceUnitTest {

    @Test
    fun httpFailureClassificationMaps401And403And429AndMasksKey() {
        val f401 = ModelRouter.classifyHttpFailure("gemini", "gemini-2.5-flash", 401, "Invalid AIzaSySecretKey998877665544332211")
        assertEquals(FailureCategory.AUTH_REJECTED, f401.category)
        assertFalse(f401.sanitizedDetail.contains("AIza"))

        val f402 = ModelRouter.classifyHttpFailure("openrouter", "model-x", 402, "insufficient credits")
        assertEquals(FailureCategory.QUOTA_OR_CREDIT, f402.category)

        val f429 = ModelRouter.classifyHttpFailure("groq", "llama-3.3-70b-versatile", 429, "rate limit exceeded")
        assertEquals(FailureCategory.RATE_LIMITED, f429.category)

        val f413 = ModelRouter.classifyHttpFailure("gemini", "gemini-2.5-flash", 413, "payload too large")
        assertEquals(FailureCategory.PAYLOAD_TOO_LARGE, f413.category)
    }

    @Test
    fun geminiRequestAndResponseParsingSupportsToolsAndText() {
        val tools = CapabilityRegistry().capabilities.take(2)
        val reqJson = ModelRouter.buildGeminiRequestJson(
            systemPrompt = "Você é a Gabi.",
            history = listOf(ModelChatMessage("user", "Abra o Spotify")),
            tools = tools
        )
        assertTrue(reqJson.contains("functionDeclarations"))
        assertTrue(reqJson.contains("Abra o Spotify"))

        val toolRespJson = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "functionCall": { "name": "open_app", "args": { "app_name": "Spotify" } }
                  }]
                }
              }]
            }
        """.trimIndent()
        val parsedTool = ModelRouter.parseGeminiResponse("gemini", "gemini-2.5-flash", toolRespJson)
        assertTrue(parsedTool is ModelTurnResult.ToolCallReply)
        val calls = (parsedTool as ModelTurnResult.ToolCallReply).calls
        assertEquals("open_app", calls.first().name)
        assertEquals("Spotify", calls.first().args["app_name"])

        val textRespJson = """
            {
              "candidates": [{
                "content": { "parts": [{ "text": "Bom dia!" }] }
              }]
            }
        """.trimIndent()
        val parsedText = ModelRouter.parseGeminiResponse("gemini", "gemini-2.5-flash", textRespJson)
        assertTrue(parsedText is ModelTurnResult.TextReply)
        assertEquals("Bom dia!", (parsedText as ModelTurnResult.TextReply).text)
    }

    @Test
    fun openAiParserHandlesReasoningFallback() {
        val raw = """
            {
              "choices": [{
                "message": {
                  "content": null,
                  "reasoning_content": "Resposta recuperada."
                }
              }]
            }
        """.trimIndent()
        val parsed = ModelRouter.parseOpenAiResponse("groq", "llama", raw)
        assertTrue(parsed is ModelTurnResult.TextReply)
        assertEquals("Resposta recuperada.", (parsed as ModelTurnResult.TextReply).text)
    }

    @Test
    fun voiceSpecFreezesGabi24lAndB2C2WithoutSilentSystemTtsSwap() {
        val spec = FrozenVoiceIdentitySpec()
        assertEquals("gabi_24l", spec.voiceId)
        assertEquals("portuguese_24l", spec.pocketModelId)
        assertEquals(24_000, spec.sampleRateHz)
        assertFalse(spec.allowSilentSwapToSystemTts)

        val b2 = spec.profileById("B2_base")
        assertEquals(-4.0f, b2.eosThreshold)
        assertEquals(200, b2.maxTokens)

        val c2p = spec.profileById("C2_pausas")
        assertEquals(240, c2p.maxTokens)
    }

    @Test
    fun speechWatchdogPreventsOverlapAndTracksTimeouts() {
        val wd = SpeechSessionWatchdog()
        val s1 = wd.start(1_000L)
        assertNotNull(s1)
        assertNull(wd.start(1_100L))
        assertFalse(wd.isTimedOut(s1!!, 5_000L))
        assertTrue(wd.isTimedOut(s1, 11_000L))
        wd.markReady(s1, 2_000L)
        assertFalse(wd.isTimedOut(s1, 20_000L))
        wd.markPartial(s1, "oi gabi", 21_000L)
        assertFalse(wd.isTimedOut(s1, 30_000L))
        wd.markSpeechEnded(s1, 30_500L)
        assertTrue(wd.isTimedOut(s1, 39_000L))
        assertTrue(wd.finish(s1))
        assertFalse(wd.isActive)
    }

    @Test
    fun modelRouterParsesGeminiLiveBidiModels() {
        val raw = """
            {
              "models": [
                { "name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent"] },
                { "name": "models/gemini-2.5-flash-native-audio-preview-09-2025", "supportedGenerationMethods": ["bidiGenerateContent", "generateContent"] }
              ]
            }
        """.trimIndent()
        val live = ModelRouter.parseGeminiLiveModels(raw)
        assertEquals(listOf("gemini-2.5-flash-native-audio-preview-09-2025"), live)
    }

    @Test
    fun geminiLiveReducerHandlesBargeInToolCallsAndBoundedReconnect() {
        val ready = GeminiLiveSessionReducer.reduce(LiveReducerInput(LiveSessionState.CONNECTING, keepAlive = true), GeminiLiveEvent.Ready)
        assertEquals(LiveSessionState.ACTIVE, ready.nextState)
        assertTrue(ready.startMicStream)
        assertEquals(false, ready.isListening) // only after AudioRecord has actually started

        val audio = GeminiLiveSessionReducer.reduce(LiveReducerInput(LiveSessionState.ACTIVE, keepAlive = true), GeminiLiveEvent.AudioOutput(byteArrayOf(1, 2)))
        assertEquals(true, audio.isSpeaking)
        assertNotNull(audio.enqueuePcm24k)

        val bargeIn = GeminiLiveSessionReducer.reduce(LiveReducerInput(LiveSessionState.ACTIVE, keepAlive = true), GeminiLiveEvent.Interrupted)
        assertTrue(bargeIn.clearAudioQueue)
        assertEquals(false, bargeIn.isSpeaking)

        val toolEvt = GeminiLiveEvent.ToolInvocation("c1", "get_weather", mapOf("city" to "Fortaleza"))
        val toolDec = GeminiLiveSessionReducer.reduce(LiveReducerInput(LiveSessionState.ACTIVE, keepAlive = true), toolEvt)
        assertEquals("get_weather", toolDec.dispatchToolCall?.name)

        val reconn1 = GeminiLiveSessionReducer.reduce(LiveReducerInput(LiveSessionState.ACTIVE, keepAlive = true, reconnectAttempts = 0), GeminiLiveEvent.GoingAway)
        assertEquals(LiveSessionState.RECONNECTING, reconn1.nextState)
        assertTrue(reconn1.attemptReconnect)
        assertEquals(1, reconn1.nextReconnectAttempts)

        val reconn2 = GeminiLiveSessionReducer.reduce(LiveReducerInput(LiveSessionState.RECONNECTING, keepAlive = true, reconnectAttempts = 1), GeminiLiveEvent.GoingAway)
        assertEquals(LiveSessionState.DISCONNECTED, reconn2.nextState)
        assertFalse(reconn2.attemptReconnect)

        val failed = GeminiLiveSessionReducer.reduce(LiveReducerInput(LiveSessionState.CONNECTING, keepAlive = true), GeminiLiveEvent.ConnectionFailed("401 Unauthorized"))
        assertEquals(LiveSessionState.BLOCKED, failed.nextState)
        assertTrue(failed.stopMicStream)
        assertEquals("401 Unauthorized", failed.errorMessage)

        val parsedServer = GeminiLiveVoiceClient.parseServerMessage("""{"setupComplete":{},"serverContent":{"interrupted":true}}""")
        assertTrue(parsedServer.any { it is GeminiLiveEvent.Ready })
        assertTrue(parsedServer.any { it is GeminiLiveEvent.Interrupted })
    }

    @Test
    fun turnAggregatorCommitsOneUserAndOneAssistantMessagePerTurnAndDeduplicatesTools() {
        val agg = GeminiLiveTurnAggregator()

        val p1 = agg.onEvent(GeminiLiveEvent.InputTranscript("Oi"))
        assertNull(p1.committedUserText)
        val p2 = agg.onEvent(GeminiLiveEvent.InputTranscript("Oi Gabi, tudo bem?"))
        assertNull(p2.committedUserText)
        assertEquals("Oi Gabi, tudo bem?", p2.liveUserDraft)

        // Quando chega saída do agente, consolida a mensagem única do usuário sem duplicar
        val o1 = agg.onEvent(GeminiLiveEvent.OutputTranscript("Tudo ótimo"))
        assertEquals("Oi Gabi, tudo bem?", o1.committedUserText)
        assertNull(o1.committedAssistantText)

        val o2 = agg.onEvent(GeminiLiveEvent.OutputTranscript("por aqui!"))
        assertNull(o2.committedUserText)
        assertNull(o2.committedAssistantText)

        val done = agg.onEvent(GeminiLiveEvent.TurnComplete)
        assertNull(done.committedUserText)
        assertEquals("Tudo ótimo por aqui!", done.committedAssistantText)
        assertEquals(2L, agg.currentTurnId)

        // Deduplicação de ToolCall por callId após reconexão
        val call = GeminiLiveEvent.ToolInvocation("call-99", "set_reminder", mapOf("subject" to "Água"))
        val t1 = agg.onEvent(call)
        assertNotNull(t1.deduplicatedToolCall)
        val t2 = agg.onEvent(call)
        assertNull(t2.deduplicatedToolCall)

        // Barge-in marca turno parcial como interrompido
        agg.onEvent(GeminiLiveEvent.OutputTranscript("Vou começar a"))
        val interrupted = agg.onEvent(GeminiLiveEvent.Interrupted)
        assertEquals("Vou começar a [interrompido]", interrupted.committedAssistantText)
    }
}
