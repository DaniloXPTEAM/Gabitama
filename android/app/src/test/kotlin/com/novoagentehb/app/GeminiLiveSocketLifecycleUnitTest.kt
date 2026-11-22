package com.novoagentehb.app

import com.google.gson.Gson
import com.novoagentehb.app.gabi.GeminiLiveVoiceClient
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
}
