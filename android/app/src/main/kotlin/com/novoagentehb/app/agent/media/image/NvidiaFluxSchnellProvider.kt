package com.novoagentehb.app.agent.media.image

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Adaptador NVIDIA NIM para black-forest-labs/flux.1-schnell.
 * Documentação: https://docs.api.nvidia.com/nim/reference/black-forest-labs-flux_1-schnell-infer
 * Endpoint: https://ai.api.nvidia.com/v1/genai/black-forest-labs/flux.1-schnell
 *
 * Parâmetros aceitos pelo modelo (documentação): height=1024, width=1024, cfg_scale=0,
 * mode=base, image=null, samples=1, seed>=0, steps 1..4, prompt até 10000 caracteres.
 */
class NvidiaFluxSchnellProvider(
    client: OkHttpClient,
    private val apiKeyProvider: () -> String,
    private val endpoint: String = ENDPOINT,
    callTimeoutMs: Long = 90_000L,
    readTimeoutMs: Long = 60_000L
) : ImageGenerationProvider {

    override val id: String = ID
    override val displayName: String = "NVIDIA FLUX.1-schnell"
    override val protocol: String = "NVIDIA genai JSON (POST, Bearer)"
    override val costPolicy: ImageCostPolicy = ImageCostPolicy.FREE_TRIAL_NO_PAID_CALL

    private val http: OkHttpClient = client.newBuilder()
        .connectTimeout(15_000L, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    override fun credentialPresent(): Boolean = apiKeyProvider().isNotBlank()

    override suspend fun generate(request: ImageRequest): GeneratedImage {
        val key = apiKeyProvider().trim()
        if (key.isEmpty()) {
            throw ImageGenerationException(ImageErrorCategory.CREDENTIAL_MISSING,
                "Chave NVIDIA ausente. Configure em Ajustes > Chaves de API; nenhuma imagem foi solicitada.")
        }
        if (!VALID_KEY_CHARS.matches(key)) {
            throw ImageGenerationException(ImageErrorCategory.CREDENTIAL_MISSING,
                "Chave NVIDIA com formato inválido. Cole novamente a chave no Ajustes.")
        }
        val body = buildBody(request).toString().toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .post(body)
            .build()
        val (code, raw) = execute(httpRequest)
        return when (code) {
            in 200..299 -> parseResponse(raw)
            400, 422 -> throw ImageGenerationException(ImageErrorCategory.INVALID_REQUEST,
                "A NVIDIA recusou os parâmetros do pedido (HTTP $code).")
            401, 403 -> throw ImageGenerationException(ImageErrorCategory.ACCESS_DENIED,
                "A NVIDIA recusou o acesso (HTTP $code): chave inválida ou sem permissão para este modelo.")
            402, 429 -> throw ImageGenerationException(ImageErrorCategory.QUOTA_EXCEEDED,
                "Limite ou cota da conta NVIDIA atingido (HTTP $code). Nenhuma cobrança foi autorizada.")
            404 -> throw ImageGenerationException(ImageErrorCategory.PROVIDER_UNAVAILABLE,
                "Endpoint de geração não disponível para esta conta (HTTP 404).")
            else -> throw ImageGenerationException(ImageErrorCategory.PROVIDER_UNAVAILABLE,
                "Serviço de geração da NVIDIA indisponível (HTTP $code).")
        }
    }

    private suspend fun execute(req: Request): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(req)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isActive) return
                val timeout = e is SocketTimeoutException || e is InterruptedIOException
                continuation.resumeWithException(
                    if (timeout) ImageGenerationException(ImageErrorCategory.TIMEOUT,
                        "Tempo esgotado aguardando a NVIDIA; tente novamente mais tarde.")
                    else ImageGenerationException(ImageErrorCategory.PROVIDER_UNAVAILABLE,
                        "Falha de rede ao contatar a NVIDIA.")
                )
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use { r -> r.code to readLimited(r.body?.byteStream()) }
                }
                if (!continuation.isActive) return
                result.fold(
                    onSuccess = { continuation.resume(it) },
                    onFailure = { e ->
                        continuation.resumeWithException(
                            if (e is ImageGenerationException) e
                            else ImageGenerationException(ImageErrorCategory.INVALID_RESPONSE,
                                "Resposta da NVIDIA não pôde ser lida.")
                        )
                    }
                )
            }
        })
    }

    companion object {
        const val ID = "nvidia-flux-1-schnell"
        const val ENDPOINT = "https://ai.api.nvidia.com/v1/genai/black-forest-labs/flux.1-schnell"
        const val MAX_PROMPT_CHARS = 10000
        const val MAX_BODY_BYTES = 18 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
        const val MAX_DIMENSION = 4096
        private val VALID_KEY_CHARS = Regex("[\\x21-\\x7E]{8,512}")

        /** Corpo conforme a documentação; rejeita valores que o modelo não aceita. */
        fun buildBody(request: ImageRequest): JsonObject {
            val prompt = request.prompt.trim()
            require(prompt.isNotEmpty()) { "Descreva a imagem que deseja gerar." }
            require(prompt.length <= MAX_PROMPT_CHARS) { "Descrição muito longa (máximo $MAX_PROMPT_CHARS caracteres)." }
            require(request.width == 1024 && request.height == 1024) { "FLUX.1-schnell aceita apenas 1024x1024." }
            require(request.steps in 1..4) { "FLUX.1-schnell aceita entre 1 e 4 passos." }
            require(request.cfgScale == 0.0) { "FLUX.1-schnell aceita cfg_scale 0." }
            require(request.seed in 0L..Int.MAX_VALUE.toLong()) { "Seed deve estar entre 0 e ${Int.MAX_VALUE}." }
            return JsonObject().apply {
                addProperty("prompt", prompt)
                addProperty("height", request.height)
                addProperty("width", request.width)
                addProperty("cfg_scale", 0)
                addProperty("mode", "base")
                add("image", JsonNull.INSTANCE)
                addProperty("samples", 1)
                addProperty("seed", request.seed)
                addProperty("steps", request.steps)
            }
        }

        /**
         * Lê a resposta. Aceita os dois formatos documentados pela NVIDIA para geração:
         * `artifacts[0].base64` (genai) e `image` (string base64). Demais formatos são recusados.
         */
        fun parseResponse(raw: String): GeneratedImage {
            val root = try {
                JsonParser.parseString(raw).asJsonObject
            } catch (_: Exception) {
                throw ImageGenerationException(ImageErrorCategory.INVALID_RESPONSE, "Resposta da NVIDIA não é JSON válido.")
            }
            val data: String = when {
                root.has("artifacts") && root.get("artifacts").isJsonArray -> {
                    val artifacts: JsonArray = root.getAsJsonArray("artifacts")
                    val first = artifacts.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                        ?: throw ImageGenerationException(ImageErrorCategory.INVALID_RESPONSE, "A NVIDIA não retornou nenhuma imagem.")
                    val finish = first.get("finishReason")?.takeIf { it.isJsonPrimitive }?.asString
                    if (finish != null && finish != "SUCCESS") {
                        throw ImageGenerationException(ImageErrorCategory.INVALID_RESPONSE,
                            "A NVIDIA não concluiu a geração (finishReason=$finish).")
                    }
                    first.get("base64")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: throw ImageGenerationException(ImageErrorCategory.INVALID_RESPONSE, "Resposta sem campo base64 da imagem.")
                }
                root.has("image") && root.get("image").isJsonPrimitive -> root.get("image").asString
                else -> throw ImageGenerationException(ImageErrorCategory.INVALID_RESPONSE,
                    "Resposta da NVIDIA sem imagem reconhecível.")
            }
            val payload = data.substringAfter("base64,", data).filterNot { it.isWhitespace() }
            if (payload.isEmpty() || payload.length > (MAX_IMAGE_BYTES * 4 / 3) + 8) {
                throw ImageGenerationException(ImageErrorCategory.INVALID_RESPONSE, "Imagem recebida vazia ou acima do limite.")
            }
            val bytes = try {
                Base64.getDecoder().decode(payload)
            } catch (_: IllegalArgumentException) {
                throw ImageGenerationException(ImageErrorCategory.INVALID_RESPONSE, "Imagem recebida não é base64 válido.")
            }
            return verifyImage(bytes)
        }

        /** Verifica a assinatura e as dimensões reais dos bytes (PNG ou JPEG). */
        fun verifyImage(bytes: ByteArray): GeneratedImage {
            if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) {
                throw ImageGenerationException(ImageErrorCategory.INVALID_RESPONSE, "Imagem vazia ou acima de ${MAX_IMAGE_BYTES / (1024 * 1024)} MB.")
            }
            val info = inspectPng(bytes)?.let { Triple("image/png", it.first, it.second) }
                ?: inspectJpeg(bytes)?.let { Triple("image/jpeg", it.first, it.second) }
                ?: throw ImageGenerationException(ImageErrorCategory.INVALID_RESPONSE, "Conteúdo recebido não é PNG nem JPEG.")
            val (mime, w, h) = info
            if (w !in 1..MAX_DIMENSION || h !in 1..MAX_DIMENSION) {
                throw ImageGenerationException(ImageErrorCategory.INVALID_RESPONSE, "Dimensões da imagem fora do limite (${w}x$h).")
            }
            return GeneratedImage(bytes, mime, w, h)
        }

        internal fun inspectPng(b: ByteArray): Pair<Int, Int>? {
            val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            if (b.size < 24 || !b.copyOfRange(0, 8).contentEquals(sig)) return null
            if (String(b, 12, 4, Charsets.US_ASCII) != "IHDR") return null
            return u32(b, 16) to u32(b, 20)
        }

        internal fun inspectJpeg(b: ByteArray): Pair<Int, Int>? {
            if (b.size < 4 || (b[0].toInt() and 0xFF) != 0xFF || (b[1].toInt() and 0xFF) != 0xD8) return null
            var i = 2
            while (i + 4 <= b.size) {
                if ((b[i].toInt() and 0xFF) != 0xFF) return null
                val marker = b[i + 1].toInt() and 0xFF
                if (marker == 0xFF) { i++; continue }
                if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) { i += 2; continue }
                if (marker == 0xD9) return null
                val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
                if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                    if (i + 9 > b.size) return null
                    val h = ((b[i + 5].toInt() and 0xFF) shl 8) or (b[i + 6].toInt() and 0xFF)
                    val w = ((b[i + 7].toInt() and 0xFF) shl 8) or (b[i + 8].toInt() and 0xFF)
                    return w to h
                }
                if (len < 2) return null
                i += 2 + len
            }
            return null
        }

        private fun u32(b: ByteArray, o: Int): Int =
            ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
                ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

        private fun readLimited(stream: java.io.InputStream?): String {
            requireNotNull(stream) { "Resposta vazia" }
            stream.use { s ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = s.read(buffer)
                    if (read < 0) break
                    if (out.size() + read > MAX_BODY_BYTES) {
                        throw ImageGenerationException(ImageErrorCategory.INVALID_RESPONSE, "Resposta da NVIDIA excede o limite.")
                    }
                    out.write(buffer, 0, read)
                }
                return out.toString(Charsets.UTF_8.name())
            }
        }
    }
}
