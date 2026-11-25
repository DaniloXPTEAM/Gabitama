package com.novoagentehb.app

import com.google.gson.Gson
import com.novoagentehb.app.gabi.GeminiLiveEvent
import com.novoagentehb.app.gabi.GeminiLiveVoiceClient
import com.novoagentehb.app.gabi.LiveErrorText
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Public error texts of the Gemini Live client. Synthetic keys and frames only; no network, no real key.
 * Covers: exact masking of a configured key, known patterns, unknown-format keys inside server text,
 * unexpected JSON shapes, HTTP and transport failures, and close frames. Behaviour of retry/reconnect is
 * not touched here (see GeminiLiveSocketLifecycleUnitTest and ModelRouterAndVoiceUnitTest).
 */
class GeminiLiveErrorSanitizationUnitTest {

    /** Unknown-format key: not matched by any known pattern, so only exact masking can hide it. */
    private val customKey = "custom-secret-XYZ-999999"

    private class Socket(private val req: Request) : WebSocket {
        override fun request() = req
        override fun queueSize() = 0L
        override fun send(text: String) = true
        override fun send(bytes: ByteString) = true
        override fun close(code: Int, reason: String?) = true
        override fun cancel() {}
    }

    private class Factory : WebSocket.Factory {
        val sockets = mutableListOf<WebSocket>()
        val listeners = mutableListOf<WebSocketListener>()
        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
            val s = Socket(request); sockets += s; listeners += listener; return s
        }
        fun open(i: Int) = listeners[i].onOpen(sockets[i], Response.Builder()
            .request(sockets[i].request()).protocol(Protocol.HTTP_1_1).code(101).message("Switching Protocols").build())
    }

    private fun reasonOf(json: String): String {
        val events = GeminiLiveVoiceClient.parseServerMessage(json)
        return (events.single() as GeminiLiveEvent.ConnectionFailed).reason
    }

    // --- exact-key masking -------------------------------------------------------------------------

    @Test fun exactConfiguredKeyIsMaskedInAnyText() {
        val text = "rejected $customKey and again $customKey"
        val masked = LiveErrorText.mask(text, customKey)
        assertFalse(masked.contains(customKey))
        assertTrue(masked.contains(LiveErrorText.MASK))
    }

    @Test fun shortOrAbsentKeyIsNotUsedForExactMasking() {
        assertEquals("abc is not masked", "abc x", LiveErrorText.mask("abc x", "abc"))
        assertEquals("no key, nothing masked", "plain text", LiveErrorText.mask("plain text", null))
        assertEquals("blank key, nothing masked", "plain text", LiveErrorText.mask("plain text", "   "))
    }

    @Test fun knownKeyFormatsStillAreMaskedWithoutAnyConfiguredKey() {
        val aiza = "AIza" + "SyFakeFakeFakeFakeFakeFakeFake1234"
        val masked = LiveErrorText.mask("key $aiza bearer token Bearer abcdefghijklmnop", null)
        assertFalse(masked.contains(aiza))
        assertFalse(masked.contains("abcdefghijklmnop"))
    }

    // --- server error frames -----------------------------------------------------------------------

    @Test fun unknownFormatKeyInServerErrorMessageIsNeverShown() {
        val reason = reasonOf(
            """{"error":{"code":403,"status":"PERMISSION_DENIED","message":"denied for key $customKey"}}"""
        )
        assertFalse(reason.contains(customKey))
        assertTrue(reason.contains("Autenticação"))
        assertTrue(reason.contains("403"))
    }

    @Test fun serverErrorCategoryFollowsStatusThenCode() {
        assertTrue(reasonOf("""{"error":{"code":500,"status":"RESOURCE_EXHAUSTED"}}""").contains("Cota"))
        assertTrue(reasonOf("""{"error":{"code":429}}""").contains("Cota"))
        assertTrue(reasonOf("""{"error":{"code":503}}""").contains("indisponível"))
        assertTrue(reasonOf("""{"error":{"code":504}}""").contains("Tempo esgotado"))
        assertTrue(reasonOf("""{"error":{"code":404}}""").contains("não encontrado"))
        assertTrue(reasonOf("""{"error":{"code":418}}""").startsWith("Erro do servidor Gemini Live."))
    }

    @Test fun serverErrorWithoutAnyCodeOrStatusStillFailsWithGenericCategory() {
        assertEquals("Erro do servidor Gemini Live.", reasonOf("""{"error":{}}"""))
        assertEquals("Erro do servidor Gemini Live.", reasonOf("""{"error":{"message":"raw text only"}}"""))
    }

    @Test fun rawServerTextIsNeverEchoedEvenWhenShapeIsUnexpected() {
        val shapes = listOf(
            """{"error":"raw free text $customKey"}""",
            """{"error":{"code":{"nested":"$customKey"},"status":"$customKey"}}""",
            """{"error":{"code":"429 $customKey","status":"<script>$customKey</script>"}}""",
            """{"error":{"status":"lower_case_$customKey"}}""",
            """{"error":null}""",
        )
        for (json in shapes) {
            val reason = reasonOf(json)
            assertFalse("shape leaked: $json -> $reason", reason.contains(customKey))
            assertFalse(reason.contains("<script>"))
            assertFalse(reason.contains("raw free text"))
        }
    }

    @Test fun statusTokenWithUnexpectedCharactersIsDropped() {
        val reason = reasonOf("""{"error":{"status":"RESOURCE EXHAUSTED!"}}""")
        assertFalse(reason.contains("status RESOURCE"))
        assertEquals("Erro do servidor Gemini Live.", reason)
    }

    @Test fun malformedOptionalSectionsDoNotDropSetupCompleteOrCrash() {
        val events = GeminiLiveVoiceClient.parseServerMessage(
            """{"setupComplete":{},"serverContent":"oops","toolCall":5,"usageMetadata":"x","goAway":{}}"""
        )
        assertTrue(events.any { it is GeminiLiveEvent.Ready })
        assertTrue(events.any { it is GeminiLiveEvent.GoingAway })
    }

    // --- HTTP and transport failures ---------------------------------------------------------------

    @Test fun httpFailureKeepsCodeAndCategoryWithoutTransportText() {
        val text = LiveErrorText.failure(IOException("boom $customKey"), 401)
        assertEquals("HTTP 401 · " + "Autenticação recusada pelo Gemini Live. Verifique a chave configurada.", text)
        assertFalse(text.contains(customKey))
        assertTrue(LiveErrorText.failure(null, 429).contains("Cota"))
        assertTrue(LiveErrorText.failure(null, 503).contains("indisponível"))
        assertTrue(LiveErrorText.failure(null, 302).endsWith("Erro do servidor Gemini Live."))
    }

    @Test fun transportFailuresUseFixedCategoriesAndClassName() {
        assertTrue(LiveErrorText.failure(SocketTimeoutException("x"), null).startsWith("Tempo esgotado"))
        assertTrue(LiveErrorText.failure(InterruptedIOException("x"), null).contains("(InterruptedIOException)"))
        assertTrue(LiveErrorText.failure(SSLHandshakeException("tls $customKey"), null).startsWith("Falha de segurança (TLS)"))
        assertTrue(LiveErrorText.failure(UnknownHostException("host $customKey"), null).startsWith("Falha de rede"))
        assertTrue(LiveErrorText.failure(RuntimeException("x"), null).startsWith("Falha ao conectar"))
        assertFalse(LiveErrorText.failure(UnknownHostException("host $customKey"), null).contains(customKey))
    }

    @Test fun closeReasonFromServerIsNotShown() {
        val text = LiveErrorText.closedByServer(1008)
        assertEquals("Conexão encerrada pelo serviço (código 1008).", text)
    }

    @Test fun handshakeRejectionBodyWithKeyIsNotShownAndKeepsCode() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden for key $customKey"))
            server.start()
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/")).build())
            }.build()
            val client = GeminiLiveVoiceClient(http, Gson())
            val failure = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(10_000) { client.events.first { it is GeminiLiveEvent.ConnectionFailed } }
            }
            assertTrue(client.connect(customKey, systemInstruction = "test"))
            val reason = (failure.await() as GeminiLiveEvent.ConnectionFailed).reason
            assertTrue(reason.contains("403"))
            assertFalse(reason.contains(customKey))
        }
    }

    @Test fun socketFailureAfterSetupKeepsRetryabilityAndMasksExactKey() {
        val factory = Factory()
        val client = GeminiLiveVoiceClient(OkHttpClient(), Gson()).apply { webSocketFactory = factory }
        assertTrue(client.connect(customKey, systemInstruction = "test"))
        factory.open(0)
        factory.listeners[0].onMessage(factory.sockets[0], "{\"setupComplete\":{}}")
        assertTrue(client.connected.value)
        // retryable is unchanged: a network loss after Ready is still retryable
        val failure = firstFailureAfter(client) {
            factory.listeners[0].onFailure(factory.sockets[0], IOException("lost $customKey"), null)
        }
        assertTrue(failure.retryable)
        assertFalse(failure.reason.contains(customKey))
    }

    @Test fun serverCloseWithKeyInReasonIsNotShown() {
        val factory = Factory()
        val client = GeminiLiveVoiceClient(OkHttpClient(), Gson()).apply { webSocketFactory = factory }
        assertTrue(client.connect(customKey, systemInstruction = "test"))
        factory.open(0)
        val failure = firstFailureAfter(client) {
            factory.listeners[0].onClosing(factory.sockets[0], 1008, "policy for $customKey")
        }
        assertFalse(failure.reason.contains(customKey))
        assertTrue(failure.reason.contains("1008"))
    }

    /** Subscribes before the trigger runs (SharedFlow has no replay) and returns the first failure. */
    private fun firstFailureAfter(client: GeminiLiveVoiceClient, trigger: () -> Unit): GeminiLiveEvent.ConnectionFailed =
        runBlocking {
            val pending = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) { client.events.first { it is GeminiLiveEvent.ConnectionFailed } }
            }
            trigger()
            pending.await() as GeminiLiveEvent.ConnectionFailed
        }
}
