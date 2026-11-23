package com.novoagentehb.app

import com.novoagentehb.app.data.providers.CustomProviderFormat
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class CustomProviderFormatUnitTest {
    private val good = """{"format":"gabitama-openai-provider-v1","name":"Teste","base_url":"https://api.example.com/v1","chat_model":"chat-1","screen_model":"chat-1","vision_model":"vision-1"}"""
    @Test fun importValidatesStrictFormatAndRejectsCredentialsAndLocalEndpoints() {
        val p = CustomProviderFormat.importJson(good)
        assertEquals("chat-1", p.chatModel)
        assertEquals("https://api.example.com/v1", p.baseUrl)
        assertTrue(runCatching { CustomProviderFormat.importJson(good.dropLast(1) + ",\"api_key\":\"secret\"}") }.isFailure)
        assertTrue(runCatching { CustomProviderFormat.importJson(good.replace("https://api.example.com", "http://api.example.com")) }.isFailure)
        assertTrue(runCatching { CustomProviderFormat.importJson(good.replace("api.example.com", "127.0.0.1")) }.isFailure)
        assertTrue(runCatching { CustomProviderFormat.importJson(good.replace("api.example.com", "api.example.com?secret=x")) }.isFailure)
    }
    @Test fun networkGuardRejectsPrivateAndLoopbackAddresses() {
        listOf("127.0.0.1", "10.0.0.2", "100.64.0.1", "192.168.1.1", "::1", "fc00::1").forEach {
            assertFalse(it, CustomProviderFormat.isPublicAddress(InetAddress.getByName(it)))
        }
        assertTrue(CustomProviderFormat.isPublicAddress(InetAddress.getByName("8.8.8.8")))
    }
}
