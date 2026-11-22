package com.novoagentehb.app.agent.media

import android.graphics.BitmapFactory
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.novoagentehb.app.bridges.SafLibraryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Gemini text-to-image only. One explicit approval per request; no retry, no reference/edit. */
internal object GeminiImageGeneration {
    private const val MAX_BODY_BYTES = 18 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
    class ImageFailure(message: String) : Exception(message)
    data class Image(val bytes: ByteArray, val mime: String)
    data class Artifact(val path: String, val sha256: String)

    fun buildRequest(key: String, model: String, prompt: String, confirmed: Boolean): Request {
        require(confirmed) { "Geração de imagem requer autorização explícita de custos." }
        require(key.isNotBlank()) { "Configure a chave Gemini no cofre." }
        require(prompt.isNotBlank() && prompt.length <= 4000) { "Prompt deve conter de 1 a 4000 caracteres." }
        require(Regex("[A-Za-z0-9._-]{1,100}").matches(model)) { "Informe um identificador de modelo de imagem válido." }
        val content = JsonObject().apply { add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", prompt) }) }) }
        val body = JsonObject().apply {
            add("contents", JsonArray().apply { add(content) })
            add("generationConfig", JsonObject().apply { add("responseModalities", JsonArray().apply { add("TEXT"); add("IMAGE") }) })
        }
        return Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key.trim()).post(body.toString().toRequestBody("application/json".toMediaType())).build()
    }

    fun decodeResponse(raw: String): Image {
        try {
            val root = JsonParser.parseString(raw).asJsonObject
            val candidates = root.getAsJsonArray("candidates") ?: throw ImageFailure("O provedor não retornou imagem.")
            for (candidate in candidates) {
                val parts = candidate.asJsonObject.getAsJsonObject("content")?.getAsJsonArray("parts") ?: continue
                for (part in parts) {
                    val inline = part.asJsonObject.getAsJsonObject("inlineData") ?: continue
                    val mime = inline.get("mimeType")?.asString.orEmpty()
                    if (mime != "image/png" && mime != "image/jpeg") continue
                    val data = inline.get("data")?.asString.orEmpty()
                    require(data.length <= (MAX_IMAGE_BYTES * 4 / 3 + 8)) { "Imagem excede o limite." }
                    val bytes = Base64.getDecoder().decode(data)
                    require(bytes.isNotEmpty() && bytes.size <= MAX_IMAGE_BYTES) { "Imagem inválida ou excede o limite." }
                    return Image(bytes, mime)
                }
            }
        } catch (e: ImageFailure) { throw e }
        catch (_: Exception) { throw ImageFailure("Resposta de imagem Gemini inválida.") }
        throw ImageFailure("O provedor não retornou uma imagem PNG ou JPEG.")
    }

    suspend fun request(client: OkHttpClient, req: Request): Image = suspendCancellableCoroutine { continuation ->
        val call = client.newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build().newCall(req)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(ImageFailure("Erro de rede ou tempo limite na geração de imagem."))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { r ->
                        if (!r.isSuccessful) {
                            val msg = when (r.code) {
                                401, 403 -> "Autenticação ou acesso Gemini recusado (HTTP ${r.code})."
                                429 -> "Cota ou limite Gemini atingido (HTTP 429)."
                                else -> "Falha na geração de imagem Gemini (HTTP ${r.code})."
                            }
                            throw ImageFailure(msg)
                        }
                        val body = r.body ?: throw ImageFailure("Resposta de imagem vazia.")
                        val stream = body.byteStream()
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            if (out.size() + read > MAX_BODY_BYTES) throw ImageFailure("Resposta de imagem excede o limite.")
                            out.write(buffer, 0, read)
                        }
                        val image = decodeResponse(out.toString("UTF-8"))
                        if (continuation.isActive) continuation.resume(image)
                    }
                } catch (e: ImageFailure) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(ImageFailure("Falha na resposta de geração de imagem; verifique acesso, cota e formato."))
                }
            }
        })
    }

    suspend fun generate(client: OkHttpClient, key: String, model: String, prompt: String, confirmed: Boolean, storage: SafLibraryManager): Artifact {
        val req = buildRequest(key, model.trim(), prompt.trim(), confirmed)
        val image = request(client, req)
        return withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, options)
            check(options.outWidth in 1..8192 && options.outHeight in 1..8192 && options.outMimeType == image.mime) { "Imagem recebida não é válida ou tem dimensões não suportadas." }
            val ext = if (image.mime == "image/png") "png" else "jpg"
            val result = storage.createWorkspaceBytes("images/${UUID.randomUUID()}.$ext", image.bytes)
            check(result.success) { "Falha ao salvar e verificar imagem no workspace." }
            Artifact(result.relativePath, result.sha256)
        }
    }
}
