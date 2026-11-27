package com.novoagentehb.app.agent.media.image

import com.novoagentehb.app.bridges.SafLibraryManager
import com.novoagentehb.app.data.security.SecureKeyVault
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executa geração de imagem: consentimento -> provedor -> bytes verificados -> arquivo salvo e relido.
 * Só declara sucesso quando o arquivo foi gravado e o SHA-256 relido confere.
 */
@Singleton
class ImageGenerationExecutor(
    private val providers: List<ImageGenerationProvider>,
    private val store: ImageArtifactStore
) {
    @Inject
    constructor(okHttpClient: OkHttpClient, keyVault: SecureKeyVault, safLibraryManager: SafLibraryManager) : this(
        listOf(NvidiaFluxSchnellProvider(okHttpClient, { keyVault.get(SecureKeyVault.KEY_NVIDIA_API_KEY) })),
        SafImageArtifactStore(safLibraryManager)
    )

    suspend fun generate(prompt: String, consent: ImageConsent): GeneratedArtifact {
        if (!consent.userConfirmed) {
            throw ImageGenerationException(ImageErrorCategory.NOT_AUTHORIZED,
                "Geração não autorizada: confirme o pedido de imagem.")
        }
        // Roteamento atual: primeiro provedor roteável (apenas NVIDIA FLUX.1-schnell). Provedores pagos exigem consentimento.
        val provider = providers.firstOrNull()
            ?: throw ImageGenerationException(ImageErrorCategory.PROVIDER_UNAVAILABLE, "Nenhum provedor de imagem disponível.")
        if (provider.costPolicy == ImageCostPolicy.PAID && !consent.paidConsentGranted) {
            throw ImageGenerationException(ImageErrorCategory.NOT_AUTHORIZED,
                "${provider.displayName} é pago e o custo não foi autorizado; nenhuma chamada foi feita.")
        }
        if (!provider.credentialPresent()) {
            throw ImageGenerationException(ImageErrorCategory.CREDENTIAL_MISSING,
                "Chave do ${provider.displayName} ausente. Configure em Ajustes > Chaves de API; nenhuma imagem foi solicitada.")
        }
        val image = provider.generate(ImageRequest(prompt = prompt))
        val expectedSha = sha256Hex(image.bytes)
        val extension = if (image.mime == "image/png") "png" else "jpg"
        val stored = store.save(extension, image.bytes)
        if (stored.sha256 != expectedSha || stored.sizeBytes != image.bytes.size.toLong() || !stored.relativePath.startsWith("images/")) {
            throw ImageGenerationException(ImageErrorCategory.SAVE_FAILED,
                "Imagem não pôde ser verificada no armazenamento privado.")
        }
        return GeneratedArtifact(
            providerId = provider.id,
            relativePath = stored.relativePath,
            sha256 = stored.sha256,
            sizeBytes = stored.sizeBytes,
            width = image.width,
            height = image.height,
            mime = image.mime
        )
    }

    companion object {
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/** Grava em images/<uuid>.<ext> no workspace privado, sem sobrescrever, com releitura. */
class SafImageArtifactStore(private val safLibraryManager: SafLibraryManager) : ImageArtifactStore {
    override fun save(extension: String, bytes: ByteArray): StoredImage {
        val result = safLibraryManager.createWorkspaceBytes("images/${UUID.randomUUID()}.$extension", bytes)
        if (!result.success) {
            throw ImageGenerationException(ImageErrorCategory.SAVE_FAILED, "Falha ao salvar a imagem no workspace privado.")
        }
        return StoredImage(result.relativePath, result.sha256, result.sizeBytes)
    }
}
