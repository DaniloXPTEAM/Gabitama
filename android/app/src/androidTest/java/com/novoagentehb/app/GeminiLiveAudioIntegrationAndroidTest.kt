package com.novoagentehb.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.novoagentehb.app.agent.tools.CapabilityRegistry
import com.novoagentehb.app.data.prefs.AppPreferences
import com.novoagentehb.app.data.security.SecureKeyVault
import com.novoagentehb.app.gabi.GeminiLiveVoiceClient
import com.novoagentehb.app.gabi.HBCharacterController
import com.novoagentehb.app.gabi.LiveSessionState
import com.novoagentehb.app.gabi.SyntheticAudioFactory
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Gemini Live audio integration: real VoiceEngineCoordinator + GeminiLiveVoiceClient + OkHttp, with
 * MockWebServer as the Gemini endpoint (synthetic events, no network, no key), and synthetic AudioRecord /
 * AudioTrack ports. Proves the coordinator's commands reach the ports and the server, in order, and that
 * nothing continues after Stop. NOT a device test: no microphone, no audible output, no permission change.
 */
@RunWith(AndroidJUnit4::class)
class GeminiLiveAudioIntegrationAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private lateinit var server: MockWebServer
    private lateinit var liveUrl: okhttp3.HttpUrl
    private lateinit var serverCertificates: HandshakeCertificates
    private lateinit var clientCertificates: HandshakeCertificates
    private lateinit var http: OkHttpClient
    private lateinit var prefs: AppPreferences
    private lateinit var vault: SecureKeyVault
    private lateinit var live: com.novoagentehb.app.gabi.GeminiLiveVoiceClient
    private lateinit var coordinator: VoiceEngineCoordinator
    private lateinit var factory: SyntheticAudioFactory

    private val socketsOpened = AtomicInteger()
    private val enqueued = AtomicInteger()
    private val lastServerSocket = AtomicReference<WebSocket?>()
    private val lastClientSocket = AtomicReference<WebSocket?>()
    private val setupReceived = AtomicReference(CountDownLatch(1))
    /** Every text frame the client sent (setup, realtimeInput). */
    private val clientMessages = CopyOnWriteArrayList<String>()
    private val permissionGranted = AtomicBoolean(true)
    @Volatile private var holdSetupComplete = false
    private var originalKey = ""
    private var keyOverridden = false

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("127.0.0.1").addSubjectAlternativeName("localhost").build()
        serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            start(InetAddress.getByName("127.0.0.1"), 0)
        }
        liveUrl = server.url("/live")
        http = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build()
        prefs = AppPreferences(context)
        vault = SecureKeyVault(context)
        originalKey = vault.get(SecureKeyVault.KEY_GEMINI)
        keyOverridden = true
        vault.set(SecureKeyVault.KEY_GEMINI, "synthetic-test-key")
        live = GeminiLiveVoiceClient(http, Gson())
        live.webSocketFactory = WebSocket.Factory { request, listener ->
            http.newWebSocket(request.newBuilder().url(liveUrl).build(), listener).also { lastClientSocket.set(it) }
        }
        factory = SyntheticAudioFactory()
        coordinator = onMain {
            VoiceEngineCoordinator(context, prefs, vault, http, live, CapabilityRegistry(), HBCharacterController(context, prefs))
                .apply {
                    microphoneEnabled = true
                    pcmAudioFactory = factory
                    micPermissionCheck = { permissionGranted.get() }
                }
        }
    }

    @After
    fun tearDown() {
        // Cleanup is best-effort per resource: an uninitialized or already-closed resource never throws here.
        if (::coordinator.isInitialized) runCatching { onMain { coordinator.stopGeminiLiveSession() } }
        if (::live.isInitialized) runCatching { live.disconnect() }
        if (::vault.isInitialized && keyOverridden) runCatching { vault.set(SecureKeyVault.KEY_GEMINI, originalKey) }
        if (::server.isInitialized) runCatching { server.shutdown() }
    }

    private fun enqueueConnection() {
        enqueued.incrementAndGet()
        setupReceived.set(CountDownLatch(1))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socketsOpened.incrementAndGet()
                lastServerSocket.set(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"setup\"")) {
                    clientMessages += text
                    setupReceived.get().countDown()
                    if (!holdSetupComplete) webSocket.send(SETUP_COMPLETE)
                } else if (text.contains("realtimeInput")) {
                    clientMessages += text
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
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

    /** Decoded PCM16 the client sent, in the order sent. */
    private fun microphoneBytesSent(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (text in clientMessages) {
            if (!text.contains("realtimeInput") || !text.contains("\"audio\"")) continue
            val data = JsonParser.parseString(text).asJsonObject
                .getAsJsonObject("realtimeInput").getAsJsonObject("audio").get("data").asString
            out.write(Base64.getDecoder().decode(data))
        }
        return out.toByteArray()
    }

    private fun sendModelAudio(bytes: ByteArray) {
        val b64 = Base64.getEncoder().encodeToString(bytes)
        lastServerSocket.get()!!.send(
            "{\"serverContent\":{\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"$b64\"}}]}}}"
        )
    }

    private fun pattern(size: Int, seed: Int) = ByteArray(size) { ((it + seed) % 251).toByte() }

    private fun startAndWaitActive() {
        enqueueConnection()
        onMain { coordinator.startGeminiLiveSession() }
        assertTrue("session must reach ACTIVE", awaitState(LiveSessionState.ACTIVE))
    }

    @Test
    fun handshakeGatesMicrophoneUntilReadyAndListeningReflectsRecording() {
        holdSetupComplete = true
        enqueueConnection()
        onMain { coordinator.startGeminiLiveSession() }
        assertTrue(setupReceived.get().await(8, TimeUnit.SECONDS))
        Thread.sleep(300)
        assertEquals("no microphone before setupComplete", 0, factory.captures.size)
        assertFalse(coordinator.status.value.isListening)

        lastServerSocket.get()!!.send(SETUP_COMPLETE)

        assertTrue(awaitState(LiveSessionState.ACTIVE))
        assertTrue("capture must start after ACTIVE", awaitUntil(5_000) { factory.captures.any { it.isRecording } })
        assertTrue("listening only once recording is real", awaitUntil(3_000) { coordinator.status.value.isListening })
        assertEquals(1, factory.captures[0].starts.get())
    }

    @Test
    fun capturedPcm16ReachesTheServerInOrder() {
        startAndWaitActive()
        assertTrue("audio must reach the server", awaitUntil(8_000) { microphoneBytesSent().size >= 3_200 })

        val sent = microphoneBytesSent()
        val expected = ByteArray(sent.size) { (it % 256).toByte() }
        assertArrayEquals("captured PCM16 must arrive complete and in order", expected, sent)
    }

    @Test
    fun serverPcm24kIsPlayedInOrderAndSpeakingFollowsTheTurn() {
        startAndWaitActive()
        val a = pattern(4_800, 1)
        val b = pattern(9_600, 2)
        val c = pattern(2_400, 3)
        sendModelAudio(a)
        sendModelAudio(b)
        sendModelAudio(c)

        assertTrue("speaking while the turn plays", awaitUntil(3_000) { coordinator.status.value.isSpeaking })
        assertTrue("all PCM24k reaches playback", awaitUntil(10_000) {
            factory.playbacks.isNotEmpty() && factory.playbacks[0].receivedBytes().size == a.size + b.size + c.size
        })
        assertArrayEquals(a + b + c, factory.playbacks[0].receivedBytes())

        lastServerSocket.get()!!.send("{\"serverContent\":{\"turnComplete\":true}}")
        assertTrue("not speaking after turnComplete", awaitUntil(3_000) { !coordinator.status.value.isSpeaking })
    }

    /** Barge-in: the pending chunk is discarded; only audio of the new turn reaches the track. */
    @Test
    fun bargeInDiscardsPendingAudioAndPlaysOnlyTheNewTurn() {
        factory.playbackStuck = true
        startAndWaitActive()
        sendModelAudio(pattern(4_800, 4))
        assertTrue("old chunk is pending on a stuck track", awaitUntil(5_000) { factory.playbacks.isNotEmpty() })

        lastServerSocket.get()!!.send("{\"serverContent\":{\"interrupted\":true}}")
        assertTrue("interruption clears speaking", awaitUntil(3_000) { !coordinator.status.value.isSpeaking })
        assertTrue("interruption flushes the track", awaitUntil(3_000) { factory.playbacks[0].flushes.get() >= 1 })
        factory.playbacks[0].stuck = false

        val fresh = pattern(4_800, 5)
        sendModelAudio(fresh)
        assertTrue("new turn plays", awaitUntil(8_000) { factory.playbacks[0].receivedBytes().size >= fresh.size })
        assertArrayEquals("no audio from the interrupted turn", fresh, factory.playbacks[0].receivedBytes())
        assertEquals(LiveSessionState.ACTIVE, coordinator.status.value.liveSessionState)
    }

    @Test
    fun stopDuringCaptureReleasesMicrophoneAndSendsNothingAfter() {
        startAndWaitActive()
        assertTrue(awaitUntil(5_000) { microphoneBytesSent().size >= 640 })

        onMain { coordinator.stopGeminiLiveSession() }

        assertEquals(LiveSessionState.DISCONNECTED, coordinator.status.value.liveSessionState)
        assertFalse(coordinator.status.value.isListening)
        assertTrue("capture is released exactly once", awaitUntil(3_000) { factory.captures[0].releases.get() == 1 })
        assertTrue(factory.captures[0].stops.get() >= 1)
        Thread.sleep(300) // let in-flight frames drain
        val settled = microphoneBytesSent().size
        Thread.sleep(800)
        assertEquals("no microphone audio after Stop", settled, microphoneBytesSent().size)
    }

    @Test
    fun stopDuringPlaybackReleasesTheTrackAndWritesNothingAfter() {
        factory.playbackStuck = true
        startAndWaitActive()
        sendModelAudio(pattern(4_800, 6))
        assertTrue(awaitUntil(5_000) { factory.playbacks.isNotEmpty() })

        onMain { coordinator.stopGeminiLiveSession() }

        assertTrue("track released", awaitUntil(3_000) { factory.playbacks[0].releases.get() == 1 })
        assertEquals("nothing written to the released track", 0, factory.playbacks[0].writesAfterRelease.get())
        assertEquals(0, factory.playbacks[0].receivedBytes().size)
        assertFalse(coordinator.status.value.isSpeaking)
    }

    @Test
    fun stopDuringReconnectReleasesCaptureAndDoesNotRestartIt() {
        enqueueConnection()
        enqueueConnection()
        onMain { coordinator.startGeminiLiveSession() }
        assertTrue(awaitState(LiveSessionState.ACTIVE))
        assertTrue(awaitUntil(5_000) { factory.captures.any { it.isRecording } })

        lastClientSocket.get()!!.cancel()
        assertTrue(awaitState(LiveSessionState.RECONNECTING, timeoutMs = 3_000))
        assertTrue("drop stops the microphone", awaitUntil(3_000) { factory.captures[0].releases.get() == 1 })
        assertFalse(coordinator.status.value.isListening)

        onMain { coordinator.stopGeminiLiveSession() }

        Thread.sleep(1_500)
        assertEquals("Stop cancels the pending reconnect", 1, socketsOpened.get())
        assertEquals("no new microphone after Stop", 1, factory.captures.size)
        assertEquals(LiveSessionState.DISCONNECTED, coordinator.status.value.liveSessionState)
    }

    /**
     * Audio from the old socket after GoingAway, before the reconnect call (600 ms), must neither play nor
     * revive ACTIVE. The second connection is queued so the reconnect itself can complete afterwards.
     */
    @Test
    fun oldSocketAudioAfterGoingAwayIsNotPlayedAndDoesNotReviveActive() {
        enqueueConnection()
        enqueueConnection()
        onMain { coordinator.startGeminiLiveSession() }
        assertTrue(awaitState(LiveSessionState.ACTIVE))
        assertTrue(awaitUntil(5_000) { factory.captures.any { it.isRecording } })

        lastServerSocket.get()!!.send("{\"goAway\":{}}")
        assertTrue("GoingAway starts the reconnect", awaitState(LiveSessionState.RECONNECTING, timeoutMs = 3_000))
        sendModelAudio(pattern(4_800, 9))
        Thread.sleep(300) // still inside the reconnect window

        assertTrue("old audio must not revive ACTIVE", coordinator.status.value.liveSessionState != LiveSessionState.ACTIVE)
        assertFalse("old audio must not mark speaking", coordinator.status.value.isSpeaking)
        assertEquals("old audio must not reach playback", 0, factory.playbacks.sumOf { it.receivedBytes().size })
    }

    /**
     * Reproduces the AudioTrack retained by a terminal block: after a server error the session is BLOCKED, and
     * the paused track must be released once, with nothing written after the release. Stop must be idempotent,
     * and a new session must play only its own audio on a fresh track.
     */
    @Test
    fun terminalBlockReleasesAudioTrackWritesNothingAfterAndNewSessionGetsFreshTrack() {
        startAndWaitActive()
        assertTrue(awaitUntil(5_000) { factory.captures.any { it.isRecording } })
        val old = pattern(4_800, 10)
        sendModelAudio(old)
        assertTrue("old turn reaches the first track", awaitUntil(5_000) {
            factory.playbacks.isNotEmpty() && factory.playbacks[0].receivedBytes().size == old.size
        })

        lastServerSocket.get()!!.send("{\"error\":{\"code\":403,\"status\":\"PERMISSION_DENIED\"}}")
        assertTrue("server error must block the session", awaitState(LiveSessionState.BLOCKED))
        assertTrue(
            "terminal block must release the AudioTrack",
            awaitUntil(3_000) { factory.playbacks[0].releases.get() == 1 }
        )

        onMain { coordinator.stopGeminiLiveSession() }
        onMain { coordinator.stopGeminiLiveSession() }
        Thread.sleep(300)
        assertEquals("Stop after a block must not release again", 1, factory.playbacks[0].releases.get())
        assertEquals("nothing written to the released track", 0, factory.playbacks[0].writesAfterRelease.get())

        startAndWaitActive()
        val fresh = pattern(2_400, 11)
        sendModelAudio(fresh)
        assertTrue("new session plays on a fresh track", awaitUntil(8_000) {
            factory.playbacks.size == 2 && factory.playbacks[1].receivedBytes().size == fresh.size
        })
        assertArrayEquals("no audio from the blocked session", fresh, factory.playbacks[1].receivedBytes())
        assertEquals("old track stays released once", 1, factory.playbacks[0].releases.get())
        assertEquals("old track receives nothing after the block", 0, factory.playbacks[0].writesAfterRelease.get())
    }

    /** RECONNECTING keeps the same track: the new socket's audio plays, the track is not released. */
    @Test
    fun goingAwayReconnectKeepsTrackAndPlaysAudioOfTheNewSocket() {
        enqueueConnection()
        enqueueConnection()
        onMain { coordinator.startGeminiLiveSession() }
        assertTrue(awaitState(LiveSessionState.ACTIVE))
        assertTrue(awaitUntil(5_000) { factory.captures.any { it.isRecording } })
        val before = pattern(2_400, 12)
        sendModelAudio(before)
        assertTrue("first turn plays", awaitUntil(5_000) {
            factory.playbacks.isNotEmpty() && factory.playbacks[0].receivedBytes().size == before.size
        })

        lastServerSocket.get()!!.send("{\"goAway\":{}}")
        assertTrue("GoingAway starts the reconnect", awaitState(LiveSessionState.RECONNECTING, timeoutMs = 3_000))
        assertTrue("reconnect reaches ACTIVE again", awaitState(LiveSessionState.ACTIVE, timeoutMs = 8_000))
        val after = pattern(1_200, 13)
        sendModelAudio(after)

        assertTrue("audio after reconnect plays", awaitUntil(5_000) {
            factory.playbacks[0].receivedBytes().size == before.size + after.size
        })
        assertEquals("same track across the reconnect", 1, factory.playbacks.size)
        assertEquals("reconnect must not release the track", 0, factory.playbacks[0].releases.get())
        assertArrayEquals(before + after, factory.playbacks[0].receivedBytes())
    }

    @Test
    fun captureInitializationFailureBlocksWithoutListening() {
        factory.failCaptureInit = true
        enqueueConnection()
        onMain { coordinator.startGeminiLiveSession() }

        assertTrue("capture failure must block the session", awaitState(LiveSessionState.BLOCKED))
        assertNotNull(coordinator.status.value.liveConnectionError)
        assertFalse(coordinator.status.value.isListening)
        assertEquals(0, microphoneBytesSent().size)
    }

    @Test
    fun playbackFailureBlocksTheSessionAndReleasesTheMicrophone() {
        factory.playbackErrorCode = -6
        startAndWaitActive()
        assertTrue(awaitUntil(5_000) { factory.captures.any { it.isRecording } })

        sendModelAudio(pattern(4_800, 7))

        assertTrue("playback failure must block", awaitState(LiveSessionState.BLOCKED))
        assertFalse(coordinator.status.value.isSpeaking)
        assertTrue("microphone released after the failure", awaitUntil(3_000) { factory.captures[0].releases.get() == 1 })
    }

    @Test
    fun deniedMicrophonePermissionBlocksBeforeAnySocketAndNeverOpensCapture() {
        permissionGranted.set(false)
        enqueueConnection()
        onMain { coordinator.startGeminiLiveSession() }

        assertTrue(awaitState(LiveSessionState.BLOCKED))
        assertTrue(coordinator.status.value.liveConnectionError.orEmpty().contains("Permissão de microfone"))
        Thread.sleep(300)
        assertEquals("no WebSocket without permission", 0, server.requestCount)
        assertEquals("no recorder without permission", 0, factory.captures.size)
    }

    @Test
    fun restartAfterStopUsesFreshResourcesAndNeverTouchesTheOldOnes() {
        startAndWaitActive()
        assertTrue(awaitUntil(5_000) { factory.captures.any { it.isRecording } })
        onMain { coordinator.stopGeminiLiveSession() }
        assertTrue(awaitUntil(3_000) { factory.captures[0].releases.get() == 1 })
        val stopsBefore = factory.captures[0].stops.get()

        startAndWaitActive()
        assertTrue(awaitUntil(5_000) { factory.captures.size == 2 && factory.captures[1].isRecording })
        assertTrue(awaitUntil(8_000) { microphoneBytesSent().size >= 640 })
        sendModelAudio(pattern(2_400, 8))
        assertTrue(awaitUntil(5_000) { factory.playbacks.isNotEmpty() && factory.playbacks.last().receivedBytes().size == 2_400 })

        assertEquals("the old recorder is not touched again", stopsBefore, factory.captures[0].stops.get())
        assertEquals("the old recorder is released once", 1, factory.captures[0].releases.get())
        assertEquals("the new recorder is not released", 0, factory.captures[1].releases.get())
        assertEquals("the new session starts its own track", 1, factory.playbacks.size)
    }

    /**
     * Server error frame that echoes the configured key: the coordinator state must not show it, the session
     * must block, the microphone must be released, and a new session must work afterwards.
     */
    @Test
    fun serverErrorEchoingTheKeyIsSanitizedInCoordinatorStateAndSessionRecovers() {
        startAndWaitActive()
        assertTrue(awaitUntil(5_000) { factory.captures.any { it.isRecording } })
        lastServerSocket.get()!!.send(
            "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"quota for key synthetic-test-key\"}}"
        )

        assertTrue("server error must block the session", awaitState(LiveSessionState.BLOCKED))
        val message = coordinator.status.value.liveConnectionError.orEmpty()
        assertTrue("category and code are kept", message.contains("429") && message.contains("Cota"))
        assertFalse("configured key must never reach the UI state", message.contains("synthetic-test-key"))
        assertTrue("microphone released after the error", awaitUntil(3_000) { factory.captures[0].releases.get() == 1 })
        assertFalse(coordinator.status.value.isListening)

        onMain { coordinator.stopGeminiLiveSession() }
        startAndWaitActive()
        assertEquals("recovery opens a fresh session", 2, socketsOpened.get())
    }

    private companion object {
        const val SETUP_COMPLETE = "{\"setupComplete\":{}}"
    }
}
