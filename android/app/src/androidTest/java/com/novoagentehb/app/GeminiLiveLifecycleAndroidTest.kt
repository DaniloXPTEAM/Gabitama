package com.novoagentehb.app

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
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
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

    private lateinit var server: MockWebServer
    /** Computed on the test thread in setUp: MockWebServer.url() may resolve the host, which is not allowed on main. */
    private lateinit var liveUrl: okhttp3.HttpUrl
    private lateinit var serverCertificates: HandshakeCertificates
    private lateinit var clientCertificates: HandshakeCertificates
    private lateinit var http: OkHttpClient
    private lateinit var prefs: AppPreferences
    private lateinit var vault: SecureKeyVault
    private lateinit var live: GeminiLiveVoiceClient
    private lateinit var coordinator: VoiceEngineCoordinator

    /** Responses registered in MockWebServer (test bookkeeping, not sockets). */
    private val enqueued = AtomicInteger()
    /** WebSocket upgrades actually accepted by the server (real connections). */
    private val socketsOpened = AtomicInteger()
    private val lastServerSocket = AtomicReference<WebSocket?>()
    /** Client-side socket of the latest connection: the object a network drop is simulated on. */
    private val lastClientSocket = AtomicReference<WebSocket?>()
    private val setupReceived = AtomicReference(CountDownLatch(1))
    private val serverClosedCount = AtomicInteger()
    private val lastApiKeyHeader = AtomicReference<String?>()
    @Volatile private var holdSetupComplete = false
    private var originalKey = ""

    private var setUpCompleted = false
    private var keyOverridden = false

    /** Wraps one initialization step so the first failure names its phase (class names only, no values). */
    private fun <T> phase(name: String, block: () -> T): T {
        try {
            return block()
        } catch (t: Throwable) {
            throw IllegalStateException("setUp fase=$name", t)
        }
    }

    @Before
    fun setUp() {
        // No runtime-permission changes here: granting/revoking RECORD_AUDIO on a running app can restart the test process.
        // TLS with a synthetic certificate: Android 28+ blocks cleartext ws:// for this app (targetSdk 34),
        // so a plain MockWebServer fails on API 29/34 only. The trust anchor exists only in this test client.
        phase("tls.certificate") {
            val certificate = HeldCertificate.Builder()
                .addSubjectAlternativeName("127.0.0.1").addSubjectAlternativeName("localhost").build()
            serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
            clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        }
        phase("mockwebserver.start") {
            server = MockWebServer().apply {
                useHttps(serverCertificates.sslSocketFactory(), false)
                start(InetAddress.getByName("127.0.0.1"), 0)
            }
        }
        phase("mockwebserver.url") { liveUrl = server.url("/live") }
        phase("okhttp.build") {
            http = OkHttpClient.Builder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .build()
        }
        phase("appPreferences.construct") { prefs = AppPreferences(context) }
        phase("keyVault.construct") { vault = SecureKeyVault(context) }
        // The real key is never read here: the store is overridden with a synthetic value and restored in tearDown.
        phase("keyVault.saveOriginal") { originalKey = vault.get(SecureKeyVault.KEY_GEMINI) }
        keyOverridden = true
        phase("keyVault.writeSynthetic") { vault.set(SecureKeyVault.KEY_GEMINI, "synthetic-test-key") }
        phase("liveClient.construct") {
            live = GeminiLiveVoiceClient(http, Gson())
            live.webSocketFactory = WebSocket.Factory { request, listener ->
                // Keep headers (x-goog-api-key) and redirect only the endpoint to the local server.
                // Runs on main inside connect(): only a precomputed URL, no host resolution here.
                http.newWebSocket(request.newBuilder().url(liveUrl).build(), listener).also { lastClientSocket.set(it) }
            }
        }
        // The coordinator is main-thread confined in production (TextToSpeech, Handler, Dispatchers.Main).
        phase("coordinator.onMain") {
            coordinator = onMain {
                VoiceEngineCoordinator(
                    context, prefs, vault, http, live, CapabilityRegistry(), HBCharacterController(context, prefs)
                ).apply { microphoneEnabled = false }
            }
        }
        setUpCompleted = true
    }

    /**
     * Cleanup never masks the original setUp failure: if setUp did not complete, it only releases what exists.
     * Cleanup failures are reported by step name, not by message.
     */
    @After
    fun tearDown() {
        val failedSteps = mutableListOf<String>()
        fun step(name: String, block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                failedSteps += "$name:${t::class.java.simpleName}"
            }
        }
        if (::coordinator.isInitialized) step("coordinator.stop") { onMain { coordinator.stopGeminiLiveSession() } }
        if (::live.isInitialized) step("live.disconnect") { live.disconnect() }
        if (::vault.isInitialized && keyOverridden) step("keyVault.restore") { vault.set(SecureKeyVault.KEY_GEMINI, originalKey) }
        if (::server.isInitialized) step("server.shutdown") { server.shutdown() }
        if (setUpCompleted && failedSteps.isNotEmpty()) {
            throw IllegalStateException("tearDown fases=${failedSteps.joinToString(",")}")
        }
    }

    private fun enqueueConnection(autoSetupComplete: Boolean) {
        enqueued.incrementAndGet()
        setupReceived.set(CountDownLatch(1))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socketsOpened.incrementAndGet()
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

    /** Runs on the app main thread, as production callers do. */
    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T?>(null)
        val error = AtomicReference<Throwable?>(null)
        instrumentation.runOnMainSync {
            try {
                result.set(block())
            } catch (t: Throwable) {
                error.set(t)
            }
        }
        error.get()?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result.get() as T
    }

    private fun waitMs(ms: Long) = Thread.sleep(ms)

    @Test
    fun missingKeyBlocksBeforeAnySocketIsOpened() {
        vault.set(SecureKeyVault.KEY_GEMINI, "")
        enqueueConnection(autoSetupComplete = true)

        onMain { coordinator.startGeminiLiveSession() }

        assertTrue(awaitState(LiveSessionState.BLOCKED))
        assertNotNull(coordinator.status.value.liveConnectionError)
        waitMs(500)
        // enqueued counts registered responses; the socket check is the server-side request count.
        assertEquals("no WebSocket upgrade reached the server", 0, server.requestCount)
    }

    @Test
    fun handshakeIsConnectingUntilSetupCompleteArrives() {
        holdSetupComplete = true
        enqueueConnection(autoSetupComplete = true)

        onMain { coordinator.startGeminiLiveSession() }

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
        onMain { coordinator.startGeminiLiveSession() }
        assertTrue(setupReceived.get().await(8, TimeUnit.SECONDS))

        onMain { coordinator.stopGeminiLiveSession() }

        assertEquals(LiveSessionState.DISCONNECTED, coordinator.status.value.liveSessionState)
        assertTrue("server must observe the client close", awaitUntil(5_000) { serverClosedCount.get() >= 1 })
        assertFalse(live.connected.value)
        waitMs(1_500)
        assertEquals("no reconnect after Stop", 1, socketsOpened.get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun stopWhileActiveClosesSocketAndKeepsDisconnected() {
        enqueueConnection(autoSetupComplete = true)
        onMain { coordinator.startGeminiLiveSession() }
        assertTrue(awaitState(LiveSessionState.ACTIVE))

        onMain { coordinator.stopGeminiLiveSession() }

        assertEquals(LiveSessionState.DISCONNECTED, coordinator.status.value.liveSessionState)
        assertTrue(awaitUntil(5_000) { serverClosedCount.get() >= 1 })
        assertFalse(live.connected.value)
        waitMs(1_500)
        assertEquals(1, socketsOpened.get())
        assertFalse(coordinator.status.value.isListening)
        assertFalse(coordinator.status.value.isSpeaking)
    }

    /** Queues a WebSocket upgrade that the server rejects with HTTP 500 (no socket is ever opened). */
    private fun enqueueRejectedUpgrade() {
        enqueued.incrementAndGet()
        server.enqueue(MockResponse().setResponseCode(500))
    }

    /**
     * Reconnect policy as implemented: one bounded retry per outage (MAX_RECONNECT_ATTEMPTS = 1),
     * and a successful Ready restores the budget. So drop -> reconnect -> ACTIVE -> drop -> reconnect -> ACTIVE
     * is allowed, but a reconnect that fails to open must block without any further attempt.
     */
    @Test
    fun networkDropReconnectsOnceThenBlocksWithoutLooping() {
        enqueueConnection(autoSetupComplete = true)   // 1: initial session
        enqueueConnection(autoSetupComplete = true)   // 2: reconnect after drop #1 (Ready restores budget)
        enqueueRejectedUpgrade()                      // 3: reconnect after drop #2 fails to open
        enqueueConnection(autoSetupComplete = true)   // 4: must never be requested
        onMain { coordinator.startGeminiLiveSession() }
        assertTrue(awaitState(LiveSessionState.ACTIVE))

        lastClientSocket.get()!!.cancel()             // drop #1 on the client side
        assertTrue("drop #1 must enter RECONNECTING", awaitState(LiveSessionState.RECONNECTING, timeoutMs = 3_000))
        assertTrue("reconnect #1 must return to ACTIVE", awaitState(LiveSessionState.ACTIVE))
        assertEquals(2, socketsOpened.get())

        lastClientSocket.get()!!.cancel()             // drop #2: budget was restored by Ready, one retry allowed
        assertTrue("drop #2 must enter RECONNECTING", awaitState(LiveSessionState.RECONNECTING, timeoutMs = 3_000))
        assertTrue("failed reconnect must block", awaitState(LiveSessionState.BLOCKED, timeoutMs = 8_000))
        assertNotNull(coordinator.status.value.liveConnectionError)
        waitMs(2_000)
        assertEquals("no attempt after a failed reconnect", 3, server.requestCount)
        assertEquals("only the two successful sessions opened a socket", 2, socketsOpened.get())
    }

    @Test
    fun stopDuringPendingReconnectCancelsIt() {
        enqueueConnection(autoSetupComplete = true)
        enqueueConnection(autoSetupComplete = true)
        onMain { coordinator.startGeminiLiveSession() }
        assertTrue(awaitState(LiveSessionState.ACTIVE))
        // ACTIVE is only reachable after the server received setup and answered; make that precondition explicit.
        assertNotNull("ACTIVE observed before the server recorded an open socket", lastServerSocket.get())

        lastClientSocket.get()!!.cancel()
        assertTrue(awaitState(LiveSessionState.RECONNECTING, timeoutMs = 3_000))
        onMain { coordinator.stopGeminiLiveSession() }

        waitMs(1_500)
        assertEquals("stop must cancel the pending reconnect", 1, socketsOpened.get())
        assertEquals(LiveSessionState.DISCONNECTED, coordinator.status.value.liveSessionState)
    }

    @Test
    fun handshakeWithoutSetupCompleteTimesOutToBlocked() {
        holdSetupComplete = true
        enqueueConnection(autoSetupComplete = true)

        onMain { coordinator.startGeminiLiveSession() }

        // Budget: production watchdog is 15 s; 25 s leaves margin for slow emulators. Not a timing assertion beyond that.
        assertTrue("watchdog should block after ~15 s", awaitState(LiveSessionState.BLOCKED, timeoutMs = 25_000))
        assertTrue(coordinator.status.value.liveConnectionError.orEmpty().contains("Tempo limite"))
        assertFalse(live.connected.value)
    }
}
