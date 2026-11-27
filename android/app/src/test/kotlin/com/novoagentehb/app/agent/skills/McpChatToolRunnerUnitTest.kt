package com.novoagentehb.app.agent.skills

import com.novoagentehb.app.agent.orchestrator.ToolExecutionSemantics
import com.novoagentehb.app.data.local.SkillRecordEntity
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Ferramenta MCP no fluxo do agente, contra um servidor HTTP real (MockWebServer em 127.0.0.1).
 * Cobre o handshake JSON-RPC (initialize -> notifications/initialized -> tools/list -> tools/call),
 * o transporte de Mcp-Session-Id e as recusas que acontecem antes de qualquer conexão.
 */
class McpChatToolRunnerUnitTest {

    private lateinit var server: MockWebServer
    private val received = CopyOnWriteArrayList<String>()   // "<corpo>|sessao=<id>"
    private var toolsCallReply: String = """{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"eco: olá"}],"isError":false}}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                received.add(body + "|sessao=" + (request.getHeader("Mcp-Session-Id") ?: "-"))
                return when {
                    body.contains("\"method\":\"initialize\"") -> MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Mcp-Session-Id", "sess-1")
                        .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","serverInfo":{"name":"demo-server"},"capabilities":{}}}""")
                    body.contains("notifications/initialized") -> MockResponse().setResponseCode(202)
                    body.contains("\"tools/list\"") -> MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"echo","description":"Ecoa texto"}]}}""")
                    body.contains("\"tools/call\"") -> MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(toolsCallReply)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun server(
        enabled: Boolean = true,
        sourceType: String = McpChatToolRunner.SOURCE_TYPE,
        scope: String = McpChatToolRunner.SCOPE_LAN_ALLOWED,
        url: String = server.url("/mcp").toString(),
    ) = SkillRecordEntity(
        skillId = "mcp.demo",
        name = "demo",
        sourceType = sourceType,
        version = "1.0.0",
        summary = "ok",
        scope = scope,
        manifestOrigin = url,
        enabledByUser = enabled,
    )

    private fun runner(entity: SkillRecordEntity?) = McpChatToolRunner(McpJsonRpcClient(OkHttpClient())) { id ->
        entity?.takeIf { it.skillId == id }
    }

    private fun call(
        runner: McpChatToolRunner,
        confirmed: Boolean = true,
        tool: String = "echo",
        arguments: String = """{"texto":"olá"}""",
    ) = runBlocking {
        runner.run(
            mapOf("server_id" to "mcp.demo", "tool_name" to tool, "arguments" to arguments),
            userConfirmed = confirmed
        )
    }

    @Test
    fun confirmedCallCompletesRealHandshakeAndReturnsServerResponse() {
        val result = call(runner(server()))

        assertTrue(result.summary, result.success)
        assertTrue(result.verified)
        assertEquals(ToolExecutionSemantics.REMOTE_RESPONSE_ONLY, result.semantics)
        assertTrue(result.summary.contains("eco: olá"))
        assertTrue(result.summary.contains("conteúdo externo não confiável"))

        // Ordem real do protocolo, com a sessão devolvida pelo initialize reaproveitada no tools/call.
        assertEquals(4, received.size)
        assertTrue(received[0].contains("\"method\":\"initialize\""))
        assertTrue(received[1].contains("notifications/initialized") && received[1].endsWith("sessao=sess-1"))
        assertTrue(received[2].contains("\"tools/list\""))
        val call = received[3]
        assertTrue(call, call.contains("\"method\":\"tools/call\""))
        assertTrue(call, call.contains("\"name\":\"echo\""))
        assertTrue(call, call.contains("\"texto\":\"olá\""))
        assertTrue(call, call.endsWith("sessao=sess-1"))
    }

    @Test
    fun serverErrorIsReportedAsFailureNotAsSuccess() {
        toolsCallReply = """{"jsonrpc":"2.0","id":3,"error":{"code":-32602,"message":"argumento inválido"}}"""
        val result = call(runner(server()))

        assertFalse(result.success)
        assertFalse(result.verified)
        assertEquals("MCP_CALL_FAILED", result.errorCode)
        assertTrue(result.summary, result.summary.contains("argumento inválido"))
    }

    @Test
    fun unconfirmedCallNeverTouchesTheNetwork() {
        val result = call(runner(server()), confirmed = false)
        assertEquals("CONFIRMATION_REQUIRED", result.errorCode)
        assertEquals(0, received.size)
    }

    @Test
    fun unknownDisabledOrWrongTypeServersAreRefusedWithoutConnecting() {
        assertEquals("MCP_SERVER_NOT_FOUND", call(runner(null)).errorCode)
        assertEquals("MCP_SERVER_DISABLED", call(runner(server(enabled = false))).errorCode)
        assertEquals("MCP_SERVER_INVALID", call(runner(server(sourceType = "LOCAL_SKILL"))).errorCode)
        assertEquals(0, received.size)
    }

    @Test
    fun invalidToolNameAndNestedArgumentsAreRefusedWithoutConnecting() {
        assertEquals("TOOL_NAME_INVALID", call(runner(server()), tool = "../../etc").errorCode)
        assertEquals("ARGUMENTS_INVALID", call(runner(server()), arguments = """{"a":{"b":1}}""").errorCode)
        assertEquals("ARGUMENTS_INVALID", call(runner(server()), arguments = "[1,2]").errorCode)
        assertEquals(0, received.size)
    }

    @Test
    fun loopbackServerIsRefusedUnlessLanWasExplicitlyAllowed() {
        val result = call(runner(server(scope = "opt_in_external")))

        assertFalse(result.success)
        assertEquals("MCP_CALL_FAILED", result.errorCode)
        assertTrue(result.summary, result.summary.contains("bloqueado"))
        assertEquals(0, received.size)
    }

    @Test
    fun argumentParserAcceptsSimpleValuesOnly() {
        val ok = McpChatToolRunner.parseArguments("""{"n":3,"b":true,"s":"x"}""").getOrNull()
        assertEquals(mapOf("n" to "3", "b" to "true", "s" to "x"), ok)
        assertEquals(emptyMap<String, String>(), McpChatToolRunner.parseArguments("").getOrNull())
        assertNull(McpChatToolRunner.parseArguments("não é json").getOrNull())
        assertNull(McpChatToolRunner.parseArguments("""{"x":[1]}""").getOrNull())
    }
}
