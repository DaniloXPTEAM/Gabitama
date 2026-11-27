package com.novoagentehb.app.agent.media.image

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64
import java.util.UUID

class ImageGenerationExecutorUnitTest {

    @get:Rule val tmp = TemporaryFolder()

    private val pngB64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6XsAAAAASUVORK5CYII="
    private val pngBytes: ByteArray = Base64.getDecoder().decode(pngB64)
    private val confirmed = ImageConsent(userConfirmed = true, paidConsentGranted = false)

    /** Armazenamento real em disco (mesma regra do SafImageArtifactStore: novo arquivo, releitura). */
    private inner class TempDirStore(private val root: File) : ImageArtifactStore {
        override fun save(extension: String, bytes: ByteArray): StoredImage {
            val rel = "images/${UUID.randomUUID()}.$extension"
            val target = File(root, rel)
            target.parentFile!!.mkdirs()
            check(target.createNewFile())
            target.writeBytes(bytes)
            val back = target.readBytes()
            return StoredImage(rel, ImageGenerationExecutor.sha256Hex(back), back.size.toLong())
        }
    }

    /** Provedor controlado: registra chamadas e devolve bytes preparados. */
    private class FakeProvider(
        private val hasKey: Boolean = true,
        private val cost: ImageCostPolicy = ImageCostPolicy.FREE_TRIAL_NO_PAID_CALL,
        private val result: () -> GeneratedImage
    ) : ImageGenerationProvider {
        var calls = 0
        override val id = "fake"
        override val displayName = "Fake"
        override val protocol = "fake"
        override val costPolicy = cost
        override fun credentialPresent() = hasKey
        override suspend fun generate(request: ImageRequest): GeneratedImage { calls++; return result() }
    }

    private fun errorOf(block: suspend () -> Any?): ImageGenerationException = runBlocking {
        try { block(); fail("esperava falha") ; throw IllegalStateException() }
        catch (e: ImageGenerationException) { e }
    }

    private fun okImage() = GeneratedImage(pngBytes, "image/png", 1, 1)

    @Test fun withoutUserConfirmationNothingIsCalled() {
        val provider = FakeProvider { okImage() }
        val executor = ImageGenerationExecutor(listOf(provider), TempDirStore(tmp.root))
        val e = errorOf { executor.generate("maçã", ImageConsent(userConfirmed = false, paidConsentGranted = true)) }
        assertEquals(ImageErrorCategory.NOT_AUTHORIZED, e.category)
        assertEquals(0, provider.calls)
    }

    @Test fun paidProviderRequiresPaidConsentAndDoesNotCall() {
        val provider = FakeProvider(cost = ImageCostPolicy.PAID) { okImage() }
        val executor = ImageGenerationExecutor(listOf(provider), TempDirStore(tmp.root))
        val e = errorOf { executor.generate("maçã", confirmed) }
        assertEquals(ImageErrorCategory.NOT_AUTHORIZED, e.category)
        assertEquals(0, provider.calls)
    }

    @Test fun missingCredentialIsReportedBeforeCalling() {
        val provider = FakeProvider(hasKey = false) { okImage() }
        val executor = ImageGenerationExecutor(listOf(provider), TempDirStore(tmp.root))
        val e = errorOf { executor.generate("maçã", confirmed) }
        assertEquals(ImageErrorCategory.CREDENTIAL_MISSING, e.category)
        assertEquals(0, provider.calls)
    }

    @Test fun savedImageIsRereadAndMatchesHash() = runBlocking {
        val provider = FakeProvider { okImage() }
        val executor = ImageGenerationExecutor(listOf(provider), TempDirStore(tmp.root))
        val artifact = executor.generate("maçã", confirmed)
        assertEquals(1, provider.calls)
        assertTrue(artifact.relativePath.matches(Regex("images/[a-f0-9-]{36}\\.png")))
        val file = File(tmp.root, artifact.relativePath)
        assertTrue(file.isFile)
        assertArrayEquals(pngBytes, file.readBytes())
        assertEquals(ImageGenerationExecutor.sha256Hex(pngBytes), artifact.sha256)
        assertEquals(pngBytes.size.toLong(), artifact.sizeBytes)
        assertEquals("image/png", artifact.mime)
    }

    @Test fun storeReportingDifferentHashIsRejected() {
        val provider = FakeProvider { okImage() }
        val lying = ImageArtifactStore { _, _ -> StoredImage("images/x.png", "0".repeat(64), pngBytes.size.toLong()) }
        val executor = ImageGenerationExecutor(listOf(provider), lying)
        val e = errorOf { executor.generate("maçã", confirmed) }
        assertEquals(ImageErrorCategory.SAVE_FAILED, e.category)
    }

    @Test fun storeOutsideImagesFolderIsRejected() {
        val provider = FakeProvider { okImage() }
        val outside = ImageArtifactStore { _, bytes -> StoredImage("../escape.png", ImageGenerationExecutor.sha256Hex(bytes), bytes.size.toLong()) }
        val executor = ImageGenerationExecutor(listOf(provider), outside)
        val e = errorOf { executor.generate("maçã", confirmed) }
        assertEquals(ImageErrorCategory.SAVE_FAILED, e.category)
    }

    /** Fim a fim: Chat -> executor -> adaptador NVIDIA (servidor simulado) -> arquivo no disco. */
    @Test fun endToEndWithControlledNvidiaServerWritesRealFile() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"artifacts":[{"base64":"$pngB64","finishReason":"SUCCESS","seed":0}]}"""))
            server.start()
            val provider = NvidiaFluxSchnellProvider(
                client = OkHttpClient(),
                apiKeyProvider = { "nvapi-SYNTHETIC-TEST-KEY-0002" },
                endpoint = server.url("/v1/genai/black-forest-labs/flux.1-schnell").toString()
            )
            val executor = ImageGenerationExecutor(listOf(provider), TempDirStore(tmp.root))
            val artifact = executor.generate("dragão vermelho voando sobre uma cidade medieval", confirmed)
            assertEquals(NvidiaFluxSchnellProvider.ID, artifact.providerId)
            assertArrayEquals(pngBytes, File(tmp.root, artifact.relativePath).readBytes())
            val sent = server.takeRequest().body.readUtf8()
            assertTrue(sent.contains("dragão vermelho voando sobre uma cidade medieval"))
            assertFalse(sent.contains("nvapi-SYNTHETIC"))
        }
    }

    @Test fun endToEndProviderErrorDoesNotCreateAnyFile() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
            server.start()
            val provider = NvidiaFluxSchnellProvider(
                client = OkHttpClient(),
                apiKeyProvider = { "nvapi-SYNTHETIC-TEST-KEY-0003" },
                endpoint = server.url("/v1/genai/black-forest-labs/flux.1-schnell").toString()
            )
            val executor = ImageGenerationExecutor(listOf(provider), TempDirStore(tmp.root))
            val e = errorOf { executor.generate("maçã", confirmed) }
            assertEquals(ImageErrorCategory.QUOTA_EXCEEDED, e.category)
            assertFalse(File(tmp.root, "images").exists() && File(tmp.root, "images").listFiles().orEmpty().isNotEmpty())
        }
    }
}
