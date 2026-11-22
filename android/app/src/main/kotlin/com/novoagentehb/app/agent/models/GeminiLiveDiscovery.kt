package com.novoagentehb.app.agent.models

import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Listing bidi models is not proof of audio compatibility, entitlement, availability or price. */
internal object GeminiLiveDiscovery {
    fun list(client: OkHttpClient, key: String): List<String> {
        require(key.isNotBlank()) { "Chave Gemini não configurada no cofre." }
        val models = linkedSetOf<String>()
        val seenTokens = mutableSetOf<String>()
        var token: String? = null
        do {
            val url = "https://generativelanguage.googleapis.com/v1beta/models".toHttpUrl().newBuilder()
                .apply { token?.let { addQueryParameter("pageToken", it) } }.build()
            val request = Request.Builder().url(url).header("x-goog-api-key", key.trim()).build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val summary = when (response.code) {
                            401, 403 -> "Autenticação ou acesso Gemini recusado (HTTP ${response.code})."
                            429 -> "Limite ou cota Gemini atingido (HTTP 429)."
                            else -> "Falha ao listar modelos Gemini (HTTP ${response.code})."
                        }
                        throw IllegalStateException(summary)
                    }
                    val root = try { JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject }
                    catch (_: Exception) { throw IllegalStateException("Resposta inválida na descoberta Gemini.") }
                    val page = root.get("models")
                    if (page != null && !page.isJsonArray) throw IllegalStateException("Lista de modelos Gemini inválida.")
                    page?.asJsonArray?.forEach { element ->
                        if (!element.isJsonObject) throw IllegalStateException("Modelo Gemini inválido na resposta.")
                        val obj = element.asJsonObject
                        val name = runCatching { obj.get("name")?.asString }.getOrNull().orEmpty().removePrefix("models/")
                        val methods = obj.get("supportedGenerationMethods")
                        if (name.isNotBlank() && methods?.isJsonArray == true && methods.asJsonArray.any {
                            runCatching { it.asString.equals("bidiGenerateContent", true) }.getOrDefault(false)
                        }) models.add(name)
                    }
                    token = try { root.get("nextPageToken")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() } }
                        catch (_: Exception) { throw IllegalStateException("Token de paginação Gemini inválido.") }
                    if (token != null && (!seenTokens.add(token!!) || seenTokens.size > 100)) {
                        throw IllegalStateException("Paginação Gemini inválida ou excessiva; lista incompleta.")
                    }
                }
            } catch (_: IOException) {
                throw IllegalStateException("Falha de rede ou tempo limite ao listar modelos Gemini.")
            }
        } while (token != null)
        return models.toList()
    }
}
