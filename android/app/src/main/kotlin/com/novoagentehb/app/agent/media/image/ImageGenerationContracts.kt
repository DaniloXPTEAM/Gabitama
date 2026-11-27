package com.novoagentehb.app.agent.media.image

/**
 * Contratos da geração de imagens. Cada provedor usa seu próprio protocolo;
 * nunca misturar com endpoints de chat OpenAI-compatible.
 */

enum class ImageErrorCategory {
    NOT_AUTHORIZED,
    CREDENTIAL_MISSING,
    ACCESS_DENIED,
    QUOTA_EXCEEDED,
    TIMEOUT,
    PROVIDER_UNAVAILABLE,
    INVALID_REQUEST,
    INVALID_RESPONSE,
    SAVE_FAILED
}

class ImageGenerationException(val category: ImageErrorCategory, message: String) : Exception(message)

/**
 * FREE_TRIAL_NO_PAID_CALL: rota de teste/gratuita sem cobrança autorizada (custo autorizado: zero).
 * PAID: exige consentimento de custo explícito a cada geração.
 */
enum class ImageCostPolicy { FREE_TRIAL_NO_PAID_CALL, PAID }

data class ImageRequest(
    val prompt: String,
    val width: Int = 1024,
    val height: Int = 1024,
    val steps: Int = 4,
    val seed: Long = 0L,
    val cfgScale: Double = 0.0
)

data class GeneratedImage(val bytes: ByteArray, val mime: String, val width: Int, val height: Int)

/** Consentimento vindo do PolicyGate: confirmação explícita do pedido e, se pago, consentimento de custo. */
data class ImageConsent(val userConfirmed: Boolean, val paidConsentGranted: Boolean)

interface ImageGenerationProvider {
    val id: String
    val displayName: String
    val protocol: String
    val costPolicy: ImageCostPolicy

    /** Indica se há credencial configurada, sem revelar o valor. */
    fun credentialPresent(): Boolean

    /** Gera uma imagem; lança [ImageGenerationException] com categoria clara em caso de falha. */
    suspend fun generate(request: ImageRequest): GeneratedImage
}

data class StoredImage(val relativePath: String, val sha256: String, val sizeBytes: Long)

/** Armazenamento privado do app (workspace). Deve gravar sem sobrescrever e reler o arquivo. */
fun interface ImageArtifactStore {
    fun save(extension: String, bytes: ByteArray): StoredImage
}

data class GeneratedArtifact(
    val providerId: String,
    val relativePath: String,
    val sha256: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mime: String
)

/** Catálogo de provedores para a interface do usuário e para decisões de roteamento. */
object ImageProviderCatalog {
    data class Entry(val id: String, val protocol: String, val costPolicy: ImageCostPolicy, val routable: Boolean, val note: String)

    val entries: List<Entry> = listOf(
        Entry(
            id = NvidiaFluxSchnellProvider.ID,
            protocol = "NVIDIA genai JSON (POST, Bearer)",
            costPolicy = ImageCostPolicy.FREE_TRIAL_NO_PAID_CALL,
            routable = true,
            note = "Provedor inicial. Teste NVIDIA: sem cobrança autorizada; disponibilidade e cota não confirmadas."
        ),
        Entry(
            id = "gemini-image",
            protocol = "Gemini generateContent (x-goog-api-key)",
            costPolicy = ImageCostPolicy.PAID,
            routable = false,
            note = "Desativado nesta etapa: custo pago não autorizado. Código preservado em GeminiImageGeneration."
        )
    )
}
