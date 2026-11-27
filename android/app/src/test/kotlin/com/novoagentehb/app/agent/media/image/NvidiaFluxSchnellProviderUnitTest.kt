package com.novoagentehb.app.agent.media.image

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Testes controlados (sem rede real e sem custo). Não substituem uma geração real na NVIDIA.
 */
class NvidiaFluxSchnellProviderUnitTest {

    private val pngB64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6XsAAAAASUVORK5CYII="
    private val pngBytes: ByteArray = Base64.getDecoder().decode(pngB64)
    private val key = "nvapi-SYNTHETIC-TEST-KEY-0001"
    private val baseRequest = ImageRequest(prompt = "dragão vermelho sobre cidade medieval")

    private fun errorOf(block: () -> Unit): ImageGenerationException {
        try { block() } catch (e: ImageGenerationException) { return e }
        fail("esperava ImageGenerationException")
        throw IllegalStateException()
    }

    // ---- Corpo da requisição (documentação oficial) ----

    @Test fun bodyMatchesDocumentedParameters() {
        val body = NvidiaFluxSchnellProvider.buildBody(baseRequest)
        assertEquals("dragão vermelho sobre cidade medieval", body.get("prompt").asString)
        assertEquals(1024, body.get("height").asInt)
        assertEquals(1024, body.get("width").asInt)
        assertEquals(0, body.get("cfg_scale").asInt)
        assertEquals("base", body.get("mode").asString)
        assertTrue(body.get("image").isJsonNull)
        assertEquals(1, body.get("samples").asInt)
        assertEquals(0L, body.get("seed").asLong)
        assertEquals(4, body.get("steps").asInt)
        assertEquals(9, body.size())
    }

    @Test fun bodyRejectsValuesTheModelDoesNotAccept() {
        assertTrue(runCatching { NvidiaFluxSchnellProvider.buildBody(baseRequest.copy(prompt = "   ")) }.isFailure)
        assertTrue(runCatching { NvidiaFluxSchnellProvider.buildBody(baseRequest.copy(prompt = "x".repeat(10001))) }.isFailure)
        assertTrue(runCatching { NvidiaFluxSchnellProvider.buildBody(baseRequest.copy(width = 512)) }.isFailure)
        assertTrue(runCatching { NvidiaFluxSchnellProvider.buildBody(baseRequest.copy(height = 768)) }.isFailure)
        assertTrue(runCatching { NvidiaFluxSchnellProvider.buildBody(baseRequest.copy(steps = 0)) }.isFailure)
        assertTrue(runCatching { NvidiaFluxSchnellProvider.buildBody(baseRequest.copy(steps = 5)) }.isFailure)
        assertTrue(runCatching { NvidiaFluxSchnellProvider.buildBody(baseRequest.copy(cfgScale = 0.5)) }.isFailure)
        assertTrue(runCatching { NvidiaFluxSchnellProvider.buildBody(baseRequest.copy(seed = -1)) }.isFailure)
        assertTrue(NvidiaFluxSchnellProvider.buildBody(baseRequest.copy(prompt = "x".repeat(10000))).get("prompt").asString.length == 10000)
    }

    // ---- Resposta: formatos, decodificação e verificação ----

    @Test fun parsesArtifactsBase64Response() {
        val image = NvidiaFluxSchnellProvider.parseResponse("""{"artifacts":[{"base64":"$pngB64","finishReason":"SUCCESS","seed":0}]}""")
        assertEquals("image/png", image.mime)
        assertEquals(1, image.width)
        assertEquals(1, image.height)
        assertArrayEquals(pngBytes, image.bytes)
    }

    @Test fun parsesImageFieldWithDataUriPrefix() {
        val image = NvidiaFluxSchnellProvider.parseResponse("""{"image":"data:image/png;base64,$pngB64"}""")
        assertArrayEquals(pngBytes, image.bytes)
    }

    @Test fun rejectsIncompleteGeneration() {
        val e = errorOf { NvidiaFluxSchnellProvider.parseResponse("""{"artifacts":[{"base64":"$pngB64","finishReason":"CONTENT_FILTERED"}]}""") }
        assertEquals(ImageErrorCategory.INVALID_RESPONSE, e.category)
    }

    @Test fun rejectsResponsesWithoutImage() {
        assertEquals(ImageErrorCategory.INVALID_RESPONSE, errorOf { NvidiaFluxSchnellProvider.parseResponse("""{"artifacts":[]}""") }.category)
        assertEquals(ImageErrorCategory.INVALID_RESPONSE, errorOf { NvidiaFluxSchnellProvider.parseResponse("""{"foo":1}""") }.category)
        assertEquals(ImageErrorCategory.INVALID_RESPONSE, errorOf { NvidiaFluxSchnellProvider.parseResponse("não é json") }.category)
        assertEquals(ImageErrorCategory.INVALID_RESPONSE, errorOf { NvidiaFluxSchnellProvider.parseResponse("""{"image":"%%%não-base64"}""") }.category)
    }

    @Test fun rejectsContentThatIsNotPngOrJpeg() {
        val text = Base64.getEncoder().encodeToString("isto é texto, não imagem".toByteArray())
        assertEquals(ImageErrorCategory.INVALID_RESPONSE, errorOf { NvidiaFluxSchnellProvider.parseResponse("""{"image":"$text"}""") }.category)
    }

    @Test fun recognizesJpegDimensionsFromSofMarker() {
        // FF D8 | SOF0 com altura 16 e largura 32
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xC0.toByte(), 0x00, 0x0B, 0x08, 0x00, 0x10, 0x00, 0x20, 0x01, 0x01, 0x11, 0x00)
        val image = NvidiaFluxSchnellProvider.verifyImage(jpeg)
        assertEquals("image/jpeg", image.mime)
        assertEquals(32, image.width)
        assertEquals(16, image.height)
    }

    @Test fun pngDimensionsComeFromIhdrNotFromClaims() {
        val info = NvidiaFluxSchnellProvider.inspectPng(pngBytes)
        assertEquals(1 to 1, info)
        val corrupted = pngBytes.copyOf().also { it[12] = 0x00 }
        assertEquals(null, NvidiaFluxSchnellProvider.inspectPng(corrupted))
    }

    // ---- HTTP: cabeçalhos, corpo, erros e timeout (MockWebServer) ----

    private fun provider(server: MockWebServer, apiKey: String = key, callTimeoutMs: Long = 90_000L) =
        NvidiaFluxSchnellProvider(
            client = OkHttpClient(),
            apiKeyProvider = { apiKey },
            endpoint = server.url("/v1/genai/black-forest-labs/flux.1-schnell").toString(),
            callTimeoutMs = callTimeoutMs
        )

    @Test fun sendsBearerKeyJsonBodyAndReturnsImage() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"artifacts":[{"base64":"$pngB64","finishReason":"SUCCESS"}]}"""))
            server.start()
            val image = provider(server).generate(baseRequest)
            assertArrayEquals(pngBytes, image.bytes)
            val received = server.takeRequest()
            assertEquals("POST", received.method)
            assertEquals("/v1/genai/black-forest-labs/flux.1-schnell", received.path)
            assertEquals("Bearer $key", received.getHeader("Authorization"))
            assertEquals("application/json", received.getHeader("Content-Type")?.substringBefore(';'))
            val sent = received.body.readUtf8()
            assertTrue(sent.contains("\"steps\":4"))
            assertTrue(sent.contains("\"mode\":\"base\""))
            assertTrue(!received.requestUrl.toString().contains(key))
        }
    }

    @Test fun missingKeyFailsBeforeAnyNetworkCall() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val e = errorOf { runBlocking { provider(server, apiKey = "   ").generate(baseRequest) } }
            assertEquals(ImageErrorCategory.CREDENTIAL_MISSING, e.category)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun malformedKeyIsRejectedWithoutNetwork() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val e = runCatching { provider(server, apiKey = "chave com espaço").generate(baseRequest) }.exceptionOrNull() as ImageGenerationException
            assertEquals(ImageErrorCategory.CREDENTIAL_MISSING, e.category)
            assertEquals(0, server.requestCount)
        }
    }

    private suspend fun statusCategory(code: Int): ImageErrorCategory? = MockWebServer().use { server ->
        server.enqueue(MockResponse().setResponseCode(code).setBody("{\"error\":\"x\"}"))
        server.start()
        try { provider(server).generate(baseRequest); null } catch (e: ImageGenerationException) { e.category }
    }

    @Test fun httpErrorsMapToClearCategories() = runBlocking {
        assertEquals(ImageErrorCategory.ACCESS_DENIED, statusCategory(401))
        assertEquals(ImageErrorCategory.ACCESS_DENIED, statusCategory(403))
        assertEquals(ImageErrorCategory.QUOTA_EXCEEDED, statusCategory(429))
        assertEquals(ImageErrorCategory.QUOTA_EXCEEDED, statusCategory(402))
        assertEquals(ImageErrorCategory.INVALID_REQUEST, statusCategory(422))
        assertEquals(ImageErrorCategory.PROVIDER_UNAVAILABLE, statusCategory(404))
        assertEquals(ImageErrorCategory.PROVIDER_UNAVAILABLE, statusCategory(503))
    }

    @Test fun slowProviderTimesOutAsTimeoutCategory() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}").setBodyDelay(5, TimeUnit.SECONDS))
            server.start()
            val e = runCatching { provider(server, callTimeoutMs = 300L).generate(baseRequest) }.exceptionOrNull()
            assertTrue(e is ImageGenerationException)
            assertEquals(ImageErrorCategory.TIMEOUT, (e as ImageGenerationException).category)
        }
    }

    @Test fun invalidBodyFromServerIsInvalidResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<html>proxy</html>"))
            server.start()
            val e = runCatching { provider(server).generate(baseRequest) }.exceptionOrNull() as ImageGenerationException
            assertEquals(ImageErrorCategory.INVALID_RESPONSE, e.category)
        }
    }
}
