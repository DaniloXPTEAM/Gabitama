package com.novoagentehb.app

import com.google.gson.Gson
import com.novoagentehb.app.gabi.GeminiLiveVoiceClient
import com.novoagentehb.app.gabi.GeminiLiveEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class GeminiLiveSocketLifecycleUnitTest {
    private class Socket(overrideRequest: Request) : WebSocket {
        private val req = overrideRequest
        val sent = mutableListOf<String>()
        var closed = false
        override fun request() = req
        override fun queueSize() = 0L
        override fun send(text: String): Boolean { sent.add(text); return !closed }
        override fun send(bytes: ByteString) = !closed
        override fun close(code: Int, reason: String?): Boolean { closed = true; return true }
        override fun cancel() { closed = true }
    }
    private class Factory : WebSocket.Factory {
        val sockets = mutableListOf<Socket>()
        val listeners = mutableListOf<WebSocketListener>()
        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
            val socket = Socket(request); sockets.add(socket); listeners.add(listener); return socket
        }
        fun open(index: Int) = listeners[index].onOpen(sockets[index], Response.Builder()
            .request(sockets[index].request()).protocol(Protocol.HTTP_1_1).code(101).message("Switching Protocols").build())
        fun ready(index: Int) = listeners[index].onMessage(sockets[index], "{\"setupComplete\":{}}")
    }

    @Test fun lateCallbacksCannotReviveOrDisconnectNewSession() {
        val factory = Factory()
        val client = GeminiLiveVoiceClient(OkHttpClient(), Gson()).apply { webSocketFactory = factory }
        assertTrue(client.connect("synthetic-key", systemInstruction = "test"))
        factory.open(0)
        assertFalse(client.connected.value)
        assertFalse(client.sendTextTurn("before ready"))
        assertTrue(client.connect("synthetic-key", systemInstruction = "test"))
        assertTrue(factory.sockets[0].closed)
        factory.open(1)
        factory.ready(0) // delayed Ready from the closed socket
        assertFalse(client.connected.value)
        factory.ready(1)
        assertTrue(client.connected.value)
        factory.listeners[0].onFailure(factory.sockets[0], IOException("old network failure"), null)
        factory.listeners[0].onClosed(factory.sockets[0], 1000, "old close")
        assertTrue(client.connected.value)
        assertTrue(client.sendTextTurn("new session"))
        client.disconnect()
        factory.ready(1) // delayed Ready after cancellation
        assertFalse(client.connected.value)
        assertFalse(client.sendPcm16AudioChunk(byteArrayOf(1, 2)))
    }

    @Test fun onlyEstablishedIoFailuresAreMarkedRetryable() = runBlocking {
        val factory = Factory()
        val client = GeminiLiveVoiceClient(OkHttpClient(), Gson()).apply { webSocketFactory = factory }
        assertTrue(client.connect("synthetic-key", systemInstruction = "test"))
        factory.open(0)
        val handshakeFailure = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1000) { client.events.first { it is GeminiLiveEvent.ConnectionFailed } }
        }
        factory.listeners[0].onFailure(factory.sockets[0], IOException("network"), null)
        assertFalse((handshakeFailure.await() as GeminiLiveEvent.ConnectionFailed).retryable)
        assertFalse(client.connected.value)
        assertTrue(client.connect("synthetic-key", systemInstruction = "test"))
        factory.open(1)
        factory.ready(1)
        val loss = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1000) { client.events.first { it is GeminiLiveEvent.ConnectionFailed } }
        }
        factory.listeners[1].onFailure(factory.sockets[1], IOException("network"), null)
        assertTrue((loss.await() as GeminiLiveEvent.ConnectionFailed).retryable)
        assertFalse(client.connected.value)
        client.disconnect()
    }

    @Test fun fragmentedAudioAndInterruptionPreserveEventOrdering() {
        val events = GeminiLiveVoiceClient.parseServerMessage("""{"serverContent":{"interrupted":true,"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AQI="}},{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AwQ="}}]},"turnComplete":true}}""")
        assertTrue(events[0] is GeminiLiveEvent.Interrupted)
        assertArrayEquals(byteArrayOf(1, 2), (events[1] as GeminiLiveEvent.AudioOutput).pcm24k)
        assertArrayEquals(byteArrayOf(3, 4), (events[2] as GeminiLiveEvent.AudioOutput).pcm24k)
        assertTrue(events[3] is GeminiLiveEvent.TurnComplete)
    }

    @Test fun serverErrorCannotBeOverriddenBySetupCompleteInSameMessage() = runBlocking {
        val factory = Factory()
        val client = GeminiLiveVoiceClient(OkHttpClient(), Gson()).apply { webSocketFactory = factory }
        assertTrue(client.connect("synthetic-key", systemInstruction = "test"))
        factory.open(0)
        val failure = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1000) { client.events.first { it is GeminiLiveEvent.ConnectionFailed } }
        }
        factory.listeners[0].onMessage(factory.sockets[0], """{"error":{"message":"model unavailable"},"setupComplete":{}}""")
        assertTrue(failure.await() is GeminiLiveEvent.ConnectionFailed)
        assertFalse(client.connected.value)
        assertTrue(factory.sockets[0].closed)
        factory.ready(0)
        assertFalse(client.connected.value)
    }
}
