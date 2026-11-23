package com.novoagentehb.app.data.providers

import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.util.UUID

/** Independent, deliberately narrow import format; no upstream code or credentials embedded in JSON. */
data class CustomProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val chatModel: String,
    val screenModel: String,
    val visionModel: String
)

object CustomProviderFormat {
    private val modelPattern = Regex("[A-Za-z0-9._:/-]{1,160}")
    private val allowedFields = setOf("format", "name", "base_url", "chat_model", "screen_model", "vision_model")

    fun validate(name: String, url: String, chat: String, screen: String, vision: String, id: String = UUID.randomUUID().toString()): CustomProvider {
        val n = name.trim()
        require(n.length in 1..80 && n.none { it.isISOControl() }) { "Nome inválido." }
        val parsed = url.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("URL inválida.")
        require(parsed.isHttps && parsed.port == 443 && parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.query == null && parsed.fragment == null) { "Use uma URL HTTPS na porta 443, sem credenciais, consulta ou fragmento." }
        require(parsed.host.contains('.') && parsed.host.any { it.isLetter() } && !parsed.host.endsWith(".local", true) && ':' !in parsed.host) { "Use um domínio público; endereços locais não são aceitos." }
        val models = listOf(chat, screen, vision).map { it.trim() }
        require(models.all { modelPattern.matches(it) }) { "Informe identificadores válidos para conversa, tela e visão." }
        require(Regex("[a-f0-9-]{36}").matches(id)) { "Identificador de provedor inválido." }
        return CustomProvider(id, n, parsed.toString().trimEnd('/'), models[0], models[1], models[2])
    }

    fun importJson(raw: String): CustomProvider {
        require(raw.toByteArray(Charsets.UTF_8).size <= 65536) { "Arquivo excede 64 KB." }
        val obj = try { JsonParser.parseString(raw).asJsonObject } catch (_: Exception) { throw IllegalArgumentException("JSON de provedor inválido.") }
        require(obj.keySet().all { it in allowedFields }) { "Campos não suportados; credenciais devem ser informadas no cofre, nunca importadas neste formato." }
        fun field(key: String): String {
            val e = obj.get(key)
            require(e != null && e.isJsonPrimitive && e.asJsonPrimitive.isString) { "Campo ausente ou inválido: $key." }
            return e.asString
        }
        require(field("format") == "gabitama-openai-provider-v1") { "Formato de provedor não suportado." }
        val chat = field("chat_model")
        return validate(field("name"), field("base_url"), chat, field("screen_model"), field("vision_model"))
    }

    fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address.map { it.toInt() and 255 }
        if (b.size == 16) return (b[0] and 0xfe) != 0xfc && !(b.take(12) == listOf(0,0,0,0,0,0,0,0,0,0,255,255))
        if (b.size != 4) return false
        return !(b[0] == 0 || b[0] == 10 || b[0] == 127 || b[0] >= 224 ||
            (b[0] == 100 && b[1] in 64..127) || (b[0] == 169 && b[1] == 254) ||
            (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) ||
            (b[0] == 198 && b[1] in 18..19))
    }
}
