package com.novoagentehb.app

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.novoagentehb.app.agent.tools.CapabilityRegistry
import com.novoagentehb.app.data.prefs.AppPreferences
import com.novoagentehb.app.data.security.SecureKeyVault
import com.novoagentehb.app.gabi.GeminiLiveVoiceClient
import com.novoagentehb.app.gabi.HBCharacterController
import com.novoagentehb.app.gabi.LiveSessionState
import com.novoagentehb.app.gabi.VoiceEngineCoordinator
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Gemini Live lifecycle on the real VoiceEngineCoordinator + GeminiLiveVoiceClient + OkHttp stack.
 * The Gemini endpoint is replaced by a local MockWebServer (synthetic events, no network, no key).
 * Microphone is disabled through the test seam: this proves session/socket logic only, NOT audio.
 * Not covered here (see classification in the handoff): microphone capture, AudioTrack playback,
 * AudioRecord/AudioTrack release, RECORD_AUDIO denial (revoking it kills the test process).
 */
@RunWith(AndroidJUnit4::class)
class GeminiLiveLifecycleAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = context.packageName

    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private lateinit var prefs: AppPreferences
    private lateinit var vault: SecureKeyVault
    private lateinit var live: GeminiLiveVoiceClient
    private lateinit var coordinator: VoiceEngineCoordinator

    private val connections = AtomicInteger()
    private val lastServerSocket = AtomicReference<WebSocket?>()
    private val setupReceived = AtomicReference(CountDownLatch(1))
    private val serverClosedCount = AtomicInteger()
    private val lastApiKeyHeader = AtomicReference<String?>()
    @Volatile private var holdSetupComplete = false
    private var originalKey = ""

    @Before
    fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.RECORD_AUDIO)
        server = MockWebServer().apply { start() }
        http = OkHttpClient.Builder().build()
        prefs = AppPreferences(context)
        vault = SecureKeyVault(context)
        originalKey = vault.get(SecureKeyVault.KEY_GEMINI)
        vault.set(SecureKeyVault.KEY_GEMINI, "synthetic-test-key")

        live = GeminiLiveVoiceClient(http, Gson())
        live.webSocketFactory = WebSocket.Factory { request, listener ->
            // Keep headers (x-goog-api-key) and redirect only the endpoint to the local server.
            http.newWebSocket(request.newBuilder().url(server.url("/live")).build(), listener)
        }
        coordinator = VoiceEngineCoordinator(
            context, prefs, vault, http, live, CapabilityRegistry(), HBCharacterController(context, prefs)
        ).apply { microphoneEnabled = false }
    }

    @After
    fun tearDown() {
        runCatching { coordinator.stopGeminiLiveSession() }
        vault.set(SecureKeyVault.KEY_GEMINI, originalKey)
        runCatching { server.shutdown() }
    }

    private fun enqueueConnection(autoSetupComplete: Boolean) {
        connections.incrementAndGet()
        setupReceived.set(CountDownLatch(1))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                lastServerSocket.set(webSocket)
                lastApiKeyHeader.set(response.request.header("x-goog-api-key"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"setup\"")) {
                    setupReceived.get().countDown()
                    if (autoSetupComplete && !holdSetupComplete) webSocket.send("{\"setupComplete\":{}}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                serverClosedCount.incrementAndGet()
                webSocket.close(code, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                serverClosedCount.incrementAndGet()
            }
        }))
    }

    private fun awaitState(state: LiveSessionState, timeoutMs: Long = 8_000): Boolean =
        awaitUntil(timeoutMs) { coordinator.status.value.liveSessionState == state }

    private fun awaitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(25)
        }
        return condition()
    }

    private fun waitMs(ms: Long) = Thread.sleep(ms)

    @Test
    fun missingKeyBlocksBeforeAnySocketIsOpened() {
        vault.set(SecureKeyVault.KEY_GEMINI, "")
        enqueueConnection(autoSetupComplete = true)

        coordinator.startGeminiLiveSession()

        assertTrue(awaitState(LiveSessionState.BLOCKED))
        assertNotNull(coordinator.status.value.liveConnectionError)
        waitMs(500)
        assertEquals(0, server.requestCount)
        assertEquals(0, connections.get())
    }

    @Test
    fun handshakeIsConnectingUntilSetupCompleteArrives() {
        holdSetupComplete = true
        enqueueConnection(autoSetupComplete = true)

        coordinator.startGeminiLiveSession()

        assertTrue("server should receive setup", setupReceived.get().await(8, TimeUnit.SECONDS))
        waitMs(300)
        assertEquals(LiveSessionState.CONNECTING, coordinator.status.value.liveSessionState)
        assertEquals("API key must travel in the header, not the URL", "synthetic-test-key", lastApiKeyHeader.get())

        lastServerSocket.get()!!.send("{\"setupComplete\":{}}")
        assertTrue("ACTIVE only after real setupComplete", awaitState(LiveSessionState.ACTIVE))
        assertTrue(coordinator.status.value.activeVoiceDisclosure.orEmpty().contains("pré-construída"))
        assertFalse(coordinator.status.value.activeVoiceDisclosure.orEmpty().startsWith("gabi_24l"))
    }

    @Test
    fun stopDuringConnectingClosesSocketAndNeverReconnects() {
        holdSetupComplete = true
        enqueueConnection(autoSetupComplete = true)
        coordinator.startGeminiLiveSession()
        assertTrue(setupReceived.get().await(8, TimeUnit.SECONDS))

        coordinator.stopGeminiLiveSession()

        assertEquals(LiveSessionState.DISCONNECTED, coordinator.status.value.liveSessionState)
        assertTrue("server must observe the client close", awaitUntil(5_000) { serverClosedCount.get() >= 1 })
        assertFalse(live.connected.value)
        waitMs(1_500)
        assertEquals("no reconnect after Stop", 1, connections.get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun stopWhileActiveClosesSocketAndKeepsDisconnected() {
        enqueueConnection(autoSetupComplete = true)
        coordinator.startGeminiLiveSession()
        assertTrue(awaitState(LiveSessionState.ACTIVE))

        coordinator.stopGeminiLiveSession()

        assertEquals(LiveSessionState.DISCONNECTED, coordinator.status.value.liveSessionState)
        assertTrue(awaitUntil(5_000) { serverClosedCount.get() >= 1 })
        assertFalse(live.connected.value)
        waitMs(1_500)
        assertEquals(1, connections.get())
        assertFalse(coordinator.status.value.isListening)
        assertFalse(coordinator.status.value.isSpeaking)
    }

    @Test
    fun networkDropReconnectsOnceThenBlocksWithoutLooping() {
        enqueueConnection(autoSetupComplete = true)
        enqueueConnection(autoSetupComplete = true)
        enqueueConnection(autoSetupComplete = true) // must never be used
        coordinator.startGeminiLiveSession()
        assertTrue(awaitState(LiveSessionState.ACTIVE))

        // First network drop: one bounded reconnect is allowed (policy MAX_RECONNECT_ATTEMPTS = 1).
        lastServerSocket.get()!!.cancel()
        assertTrue("reconnect should start", awaitUntil(5_000) { connections.get() == 2 })
        assertTrue("second session ACTIVE after reconnect", awaitState(LiveSessionState.ACTIVE))

        // Second drop: the budget is exhausted, so the session must block instead of looping.
        lastServerSocket.get()!!.cancel()
        assertTrue(awaitState(LiveSessionState.BLOCKED))
        waitMs(2_000)
        assertEquals("no third connection", 2, connections.get())
        assertNotNull(coordinator.status.value.liveConnectionError)
    }

    @Test
    fun stopDuringPendingReconnectCancelsIt() {
        enqueueConnection(autoSetupComplete = true)
        enqueueConnection(autoSetupComplete = true)
        coordinator.startGeminiLiveSession()
        assertTrue(awaitState(LiveSessionState.ACTIVE))

        lastServerSocket.get()!!.cancel()
        assertTrue(awaitState(LiveSessionState.RECONNECTING, timeoutMs = 3_000))
        coordinator.stopGeminiLiveSession()

        waitMs(1_500)
        assertEquals("stop must cancel the pending reconnect", 1, connections.get())
        assertEquals(LiveSessionState.DISCONNECTED, coordinator.status.value.liveSessionState)
    }

    @Test
    fun handshakeWithoutSetupCompleteTimesOutToBlocked() {
        holdSetupComplete = true
        enqueueConnection(autoSetupComplete = true)

        coordinator.startGeminiLiveSession()

        assertTrue("watchdog should block after ~15 s", awaitState(LiveSessionState.BLOCKED, timeoutMs = 25_000))
        assertTrue(coordinator.status.value.liveConnectionError.orEmpty().contains("Tempo limite"))
        assertFalse(live.connected.value)
    }
}
