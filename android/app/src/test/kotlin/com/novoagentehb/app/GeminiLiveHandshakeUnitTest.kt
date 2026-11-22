package com.novoagentehb.app

import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import com.novoagentehb.app.gabi.GeminiLiveVoiceClient
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GeminiLiveHandshakeUnitTest {
    @Test fun socketOpenIsNotAReadyLiveSession() {
        MockWebServer().use { server ->
            val remote = AtomicReference<WebSocket>()
            val setupReceived = CountDownLatch(1)
            val closed = CountDownLatch(1)
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { remote.set(webSocket) }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("\"setup\"")) setupReceived.countDown()
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.countDown() }
            }))
            server.start()
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                // Redirect only this JVM test to the local fake. No credentials or external network.
                chain.proceed(chain.request().newBuilder().url(server.url("/")) .build())
            }.build()
            val client = GeminiLiveVoiceClient(http, Gson())
            try {
                assertTrue(client.connect("synthetic-key", systemInstruction = "test"))
                assertTrue("setup was not sent", setupReceived.await(10, TimeUnit.SECONDS))
                assertFalse("WebSocket open is not setupComplete", client.connected.value)
                assertFalse("audio must not be sent before setupComplete", client.sendPcm16AudioChunk(byteArrayOf(1, 2)))
                assertNotNull(remote.get())
                assertTrue(remote.get().send("{\"setupComplete\":{}}"))
                val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (!client.connected.value && System.nanoTime() < end) Thread.yield()
                assertTrue("setupComplete must make session ready", client.connected.value)
                assertTrue(client.sendPcm16AudioChunk(byteArrayOf(1, 2)))
            } finally {
                client.disconnect()
                assertTrue("local socket did not close", closed.await(10, TimeUnit.SECONDS))
                http.dispatcher.executorService.shutdownNow()
            }
        }
    }

    @Test fun rejectedHandshakeReportsFailureWithoutReady() = kotlinx.coroutines.runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            server.start()
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/")).build())
            }.build()
            val client = GeminiLiveVoiceClient(http, Gson())
            val failure = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                kotlinx.coroutines.withTimeout(10000) {
                    client.events.first { it is com.novoagentehb.app.gabi.GeminiLiveEvent.ConnectionFailed }
                }
            }
            try {
                assertTrue(client.connect("synthetic-key", systemInstruction = "test"))
                val event = failure.await() as com.novoagentehb.app.gabi.GeminiLiveEvent.ConnectionFailed
                assertTrue(event.reason.contains("401"))
                assertFalse(event.retryable)
                assertFalse(client.connected.value)
                assertFalse(client.sendTextTurn("test"))
            } finally {
                client.disconnect()
                http.dispatcher.executorService.shutdownNow()
            }
        }
    }
}
