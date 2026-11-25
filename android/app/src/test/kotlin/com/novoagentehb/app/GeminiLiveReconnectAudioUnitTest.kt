package com.novoagentehb.app

import com.novoagentehb.app.gabi.GeminiLiveEvent
import com.novoagentehb.app.gabi.GeminiLiveSessionReducer
import com.novoagentehb.app.gabi.LiveReducerInput
import com.novoagentehb.app.gabi.LiveSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audio from the superseded socket between GoingAway and the reconnect must not revive ACTIVE nor reach playback.
 * The reconnect policy itself (one attempt, reset on Ready) is covered by ModelRouterAndVoiceUnitTest and is unchanged.
 */
class GeminiLiveReconnectAudioUnitTest {

    private val reconnecting = LiveReducerInput(LiveSessionState.RECONNECTING, keepAlive = true, reconnectAttempts = 1)

    @Test
    fun audioDuringReconnectWindowIsDroppedAndDoesNotReviveActive() {
        val decision = GeminiLiveSessionReducer.reduce(reconnecting, GeminiLiveEvent.AudioOutput(byteArrayOf(1, 2, 3)))

        assertEquals(LiveSessionState.RECONNECTING, decision.nextState)
        assertNull("old-session PCM must not be enqueued", decision.enqueuePcm24k)
        assertEquals(1, decision.nextReconnectAttempts)
    }

    @Test
    fun interruptionDuringReconnectWindowKeepsReconnecting() {
        val decision = GeminiLiveSessionReducer.reduce(reconnecting, GeminiLiveEvent.Interrupted)

        assertEquals(LiveSessionState.RECONNECTING, decision.nextState)
        assertEquals(1, decision.nextReconnectAttempts)
    }

    @Test
    fun audioWhileActiveStillPlaysAndKeepsActive() {
        val active = LiveReducerInput(LiveSessionState.ACTIVE, keepAlive = true)
        val decision = GeminiLiveSessionReducer.reduce(active, GeminiLiveEvent.AudioOutput(byteArrayOf(9)))

        assertEquals(LiveSessionState.ACTIVE, decision.nextState)
        assertEquals(1, decision.enqueuePcm24k?.size)
    }
}
