package com.novoagentehb.app

import com.novoagentehb.app.agent.media.GeminiImageGeneration
import com.novoagentehb.app.agent.orchestrator.ToolExecutionRequest
import com.novoagentehb.app.agent.policy.PolicyDecision
import com.novoagentehb.app.agent.policy.PolicyGate
import com.novoagentehb.app.agent.tools.CapabilityRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class GeminiImageGenerationUnitTest {
    private val png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6XsAAAAASUVORK5CYII="
    @Test fun explicitApprovalRequiredEvenWhenGenericPaidOptInEnabled() {
        val descriptor = requireNotNull(CapabilityRegistry().find("generate_image"))
        val req = ToolExecutionRequest("id", "key", "generate_image", mapOf("prompt" to "maçã", "model" to "example-image"))
        assertTrue(PolicyGate().evaluate(descriptor, req, true) is PolicyDecision.RequireConfirmation)
        assertTrue(runCatching { GeminiImageGeneration.buildRequest("fake", "example-image", "maçã", false) }.isFailure)
        assertTrue(runCatching { GeminiImageGeneration.buildRequest("fake", "model/escape", "maçã", true) }.isFailure)
        assertTrue(PolicyGate().evaluate(descriptor, req.copy(userConfirmed = true, paidConsentGranted = true), false) is PolicyDecision.Allowed)
    }
    @Test fun mockedProviderReturnsImageAndKeepsKeyOutOfUrl() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"image/png","data":"$png"}}]}}]}"""))
            server.start()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/fake")).build())
            }.build()
            val req = GeminiImageGeneration.buildRequest("synthetic-key", "example-image", "uma maçã", true)
            assertFalse(req.url.toString().contains("synthetic-key"))
            val image = GeminiImageGeneration.request(client, req)
            assertEquals("image/png", image.mime)
            assertArrayEquals(java.util.Base64.getDecoder().decode(png), image.bytes)
            val received = server.takeRequest()
            assertEquals("synthetic-key", received.getHeader("x-goog-api-key"))
            assertTrue(received.body.readUtf8().contains("uma maçã"))
            client.dispatcher.executorService.shutdownNow()
        }
    }
    @Test fun quotaAndInvalidResponseAreReportedWithoutEchoingProviderSecrets() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setBody("private secret"))
            server.start()
            val client = OkHttpClient.Builder().addInterceptor { c -> c.proceed(c.request().newBuilder().url(server.url("/")).build()) }.build()
            val req = GeminiImageGeneration.buildRequest("synthetic-key", "model", "prompt", true)
            val error = try { GeminiImageGeneration.request(client, req); null } catch (e: Exception) { e }
            assertNotNull(error); assertTrue(error!!.message!!.contains("429")); assertFalse(error.message!!.contains("private"))
            assertTrue(runCatching { GeminiImageGeneration.decodeResponse("{invalid") }.isFailure)
            client.dispatcher.executorService.shutdownNow()
        }
    }
}
