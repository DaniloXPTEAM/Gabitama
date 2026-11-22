package com.novoagentehb.app

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novoagentehb.app.agent.diagnostics.DiagnosticsEngine
import com.novoagentehb.app.agent.diagnostics.DiagnosticsInput
import com.novoagentehb.app.agent.orchestrator.ToolExecutionRequest
import com.novoagentehb.app.agent.policy.PolicyDecision
import com.novoagentehb.app.agent.policy.PolicyGate
import com.novoagentehb.app.agent.skills.LocalSkillExecutor
import com.novoagentehb.app.agent.skills.McpJsonRpcClient
import com.novoagentehb.app.agent.skills.SkillAndMcpRegistry
import com.novoagentehb.app.agent.skills.SkillExecutionStatus
import com.novoagentehb.app.agent.tools.CapabilityRegistry
import com.novoagentehb.app.bridges.SafLibraryManager
import com.novoagentehb.app.data.local.AppDatabase
import com.novoagentehb.app.data.prefs.ProviderId
import com.novoagentehb.app.data.prefs.VoiceModeId
import com.novoagentehb.app.data.security.SecureKeyVault
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress

@RunWith(AndroidJUnit4::class)
class SafetyPolicyAndMockIsolationAndroidTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun policyGateBlocksOtpAndRequiresConsentBeforeWhatsappCallOrPaidApi() {
        val registry = CapabilityRegistry()
        val gate = PolicyGate()

        val whatsapp = registry.find("send_whatsapp_message")!!
        val unconfirmed = ToolExecutionRequest(
            taskId = "it-1",
            idempotencyKey = "it-k1",
            capabilityName = whatsapp.name,
            args = mapOf("contact_name" to "João", "message" to "Oi"),
            userConfirmed = false
        )
        assertTrue(gate.evaluate(whatsapp, unconfirmed, allowPaidWithoutPrompt = false) is PolicyDecision.RequireConfirmation)

        val otpAttempt = unconfirmed.copy(
            args = mapOf("contact_name" to "João", "message" to "Código OTP 994411"),
            userConfirmed = true
        )
        assertTrue(gate.evaluate(whatsapp, otpAttempt, allowPaidWithoutPrompt = true) is PolicyDecision.Denied)

        val paidTool = registry.find("ask_claude")!!
        val paidUnconsented = ToolExecutionRequest("it-2", "it-k2", paidTool.name, mapOf("query" to "teste"))
        assertTrue(gate.evaluate(paidTool, paidUnconsented, allowPaidWithoutPrompt = false) is PolicyDecision.RequireConfirmation)
    }

    @Test
    fun diagnosticsAndSecretRedactionNeverExposeSecretsOnAndroid() {
        val raw = "Falha AIzaSyFakeKeyAndroidTest12345678 e Bearer secret_tok_987654"
        val redacted = SecureKeyVault.redactSecrets(raw)
        assertFalse(redacted.contains("AIzaSyFakeKey"))
        assertFalse(redacted.contains("secret_tok"))

        val report = DiagnosticsEngine().buildReport(
            DiagnosticsInput(
                primaryProvider = ProviderId.GEMINI,
                chatModel = "gemini-2.5-flash",
                voiceMode = VoiceModeId.GEMINI_LIVE,
                prosodyProfile = "B2_base",
                lastFailure = null,
                petComponentsPresent = false
            )
        )
        assertTrue(report.contains("NOVO_AGENTE_HB"))
        assertTrue(report.contains("camada_pet_excluida: true"))
    }

    @Test
    fun localSkillWritesAndVerifiesSha256InPrivateWorkspaceAndRoomPersists(): Unit = runBlocking {
        val saf = SafLibraryManager(context)
        val executor = LocalSkillExecutor(saf)
        val registry = SkillAndMcpRegistry(db.skillRecordDao())

        val entity = registry.registerDescriptor(
            skillId = "skill.android_smoke",
            name = "Android Smoke",
            sourceType = "LOCAL_SKILL",
            version = "1.0.0",
            summary = "Verificacao real em filesDir",
            scope = LocalSkillExecutor.SCOPE_WORKSPACE_WRITE_VERIFY,
            manifestOrigin = "android_test"
        )
        // Opt-in desligado por padrão bloqueia execução automática
        val blocked = executor.execute(entity, "ctx")
        assertEquals(SkillExecutionStatus.BLOCKED_OPT_IN, blocked.status)

        registry.setEnabled(entity.skillId, true)
        val enabled = registry.getById(entity.skillId)!!
        val ok = executor.execute(enabled, "ctx-verificado")
        assertEquals(SkillExecutionStatus.TASK_EXECUTED, ok.status)
        assertTrue(ok.verifiedPostCondition)
        assertEquals(64, ok.sha256.length)
        ok.artifactPath?.let { rel ->
            val fname = rel.substringAfterLast('/')
            saf.deleteWorkspaceFile(fname)
        }
    }

    @Test
    fun safLibraryIsolatesPathTraversalAndRoundTripsNotes() {
        val saf = SafLibraryManager(context)
        val writeRes = saf.writeWorkspaceFile("../../escape_check.txt", "Conteudo seguro R1.4.2")
        assertTrue(writeRes.success)
        assertFalse(writeRes.relativePath.contains(".."))
        assertEquals(64, writeRes.sha256.length)
        val outside = File(context.filesDir, "escape_check.txt")
        assertFalse(outside.exists())
        assertEquals("Conteudo seguro R1.4.2", saf.readWorkspaceFile(writeRes.relativePath))
        assertTrue(saf.deleteWorkspaceFile(writeRes.relativePath))
    }

    @Test
    fun workspaceRejectsSymlinkEscapingItsRoot() {
        val saf = SafLibraryManager(context)
        val workspace = File(context.filesDir, "agent_workspace").apply { mkdirs() }
        val outside = File(context.cacheDir, "workspace-symlink-test").apply { mkdirs() }
        val link = File(workspace, "outside-link-test")
        val escaped = File(outside, "must-not-exist.txt")
        try {
            android.system.Os.symlink(outside.absolutePath, link.absolutePath)
            assertFalse(saf.writeWorkspaceFile("outside-link-test/must-not-exist.txt", "test").success)
            assertFalse(escaped.exists())
            assertNull(saf.readWorkspaceFile("outside-link-test/must-not-exist.txt"))
            assertFalse(saf.deleteWorkspaceFile("outside-link-test/must-not-exist.txt"))
            assertFalse(saf.listWorkspaceFiles().any { it.name.startsWith("outside-link-test/") })
        } finally {
            link.delete()
            outside.deleteRecursively()
        }
    }

    @Test
    fun arithmeticProgramExecutesAndPersistsARealArtifact() = runBlocking {
        val saf = SafLibraryManager(context)
        val result = com.novoagentehb.app.agent.computer.WorkspaceProgram.execute(
            "x*3+1", "2,4", true,
            { path, body -> saf.createWorkspaceFile(path, body) },
            { path -> saf.readWorkspaceFile(path) }
        )
        try {
            assertEquals(2, result.rowCount)
            assertEquals("entrada,resultado\n2,7.0\n4,13.0\n", saf.readWorkspaceFile(result.path))
            assertEquals(64, result.sha256.length)
            assertFalse(saf.createWorkspaceFile(result.path, "não sobrescrever").success)
            assertEquals("entrada,resultado\n2,7.0\n4,13.0\n", saf.readWorkspaceFile(result.path))
        } finally {
            assertTrue(saf.deleteWorkspaceFile(result.path))
        }
    }

    @Test
    fun taskModelsPersistAndRouterUsesSelectionForActiveProvider() = runBlocking {
        val prefs = com.novoagentehb.app.data.prefs.AppPreferences(context)
        val originalProvider = prefs.primaryProvider.first()
        val oldChat = prefs.chatModel.first()
        val oldScreen = prefs.screenOpModel.first()
        val oldVision = prefs.visionModel.first()
        val oldLive = prefs.liveVoiceModel.first()
        try {
            prefs.setPrimaryProvider(ProviderId.GROQ)
            prefs.setModels("custom-chat", "custom-screen", "custom-vision", "live-unchanged")
            val reopened = com.novoagentehb.app.data.prefs.AppPreferences(context)
            val router = com.novoagentehb.app.agent.models.ModelRouter(OkHttpClient(), reopened, SecureKeyVault(context))
            assertEquals("custom-chat", router.resolveModelForRole(ProviderId.GROQ, com.novoagentehb.app.agent.models.ModelRole.CHAT))
            assertEquals("custom-screen", router.resolveModelForRole(ProviderId.GROQ, com.novoagentehb.app.agent.models.ModelRole.SCREEN_OP))
            assertEquals("custom-vision", router.resolveModelForRole(ProviderId.GROQ, com.novoagentehb.app.agent.models.ModelRole.VISION))
            assertEquals("live-unchanged", reopened.liveVoiceModel.first())
            assertEquals("claude-sonnet-4-20250514", router.resolveModelForRole(ProviderId.ANTHROPIC, com.novoagentehb.app.agent.models.ModelRole.CHAT))
            assertEquals("UNAVAILABLE_IN_CATALOGUE", router.resolveModelForRole(ProviderId.GROQ, com.novoagentehb.app.agent.models.ModelRole.MEDIA_GEN))
        } finally {
            prefs.setModels(oldChat, oldScreen, oldVision, oldLive)
            prefs.setPrimaryProvider(originalProvider)
        }
    }

    @Test
    fun mcpClientEnforcesOptInLanGateAndExecutesAgainstLocalMockServer(): Unit = runBlocking {
        // Ephemeral self-signed TLS, trusted only by this test client; never weaken app policy.
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("127.0.0.1")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return when {
                    body.contains("\"method\":\"initialize\"") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Mcp-Session-Id", "android-sess-1")
                        .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","serverInfo":{"name":"android-mock-mcp"}}}""")
                    body.contains("\"method\":\"notifications/initialized\"") -> MockResponse().setResponseCode(202)
                    body.contains("\"method\":\"tools/list\"") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"echo_safe","description":"Eco seguro"}]}}""")
                    body.contains("\"method\":\"tools/call\"") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"eco: android_ok"}]}}""")
                    else -> MockResponse().setResponseCode(400)
                }
            }
        }
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        try {
            val client = McpJsonRpcClient(OkHttpClient.Builder().sslSocketFactory(
                clientCertificates.sslSocketFactory(), clientCertificates.trustManager
            ).build())
            val url = server.url("/mcp").toString()

            val defaultBlocked = client.handshakeAndListTools(url, allowLocalLan = false)
            assertFalse(defaultBlocked.reachable)

            val hs = client.handshakeAndListTools(url, allowLocalLan = true)
            assertTrue(hs.error ?: "Handshake failed", hs.reachable)
            assertEquals("android-mock-mcp", hs.serverName)
            assertEquals(1, hs.tools.size)

            val res = client.callRemoteTool(url, "echo_safe", mapOf("q" to "teste"), sessionId = hs.sessionId, allowLocalLan = true)
            assertTrue(res.success)
            assertEquals("eco: android_ok", res.outputText)
        } finally {
            server.shutdown()
        }
    }
}
