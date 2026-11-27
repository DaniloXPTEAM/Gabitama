package com.novoagentehb.app.agent.orchestrator

import com.novoagentehb.app.agent.diagnostics.DiagnosticsEngine
import com.novoagentehb.app.agent.diagnostics.DiagnosticsInput
import com.novoagentehb.app.agent.executor.ToolExecutorAndVerifier
import com.novoagentehb.app.agent.memory.MemoryStore
import com.novoagentehb.app.agent.models.ModelChatMessage
import com.novoagentehb.app.agent.models.ModelRole
import com.novoagentehb.app.agent.models.ModelRouter
import com.novoagentehb.app.agent.models.ModelTurnResult
import com.novoagentehb.app.agent.models.ProviderFailure
import com.novoagentehb.app.agent.policy.PolicyDecision
import com.novoagentehb.app.agent.policy.PolicyGate
import com.novoagentehb.app.agent.skills.LocalSkillExecutor
import com.novoagentehb.app.agent.skills.McpHandshakeResult
import com.novoagentehb.app.agent.skills.McpJsonRpcClient
import com.novoagentehb.app.agent.skills.SkillAndMcpRegistry
import com.novoagentehb.app.agent.skills.SkillExecutionOutcome
import com.novoagentehb.app.agent.skills.SkillExecutionStatus
import com.novoagentehb.app.agent.tools.CapabilityRegistry
import com.novoagentehb.app.bridges.AndroidDeviceBridge
import com.novoagentehb.app.data.local.ChatMessageDao
import com.novoagentehb.app.data.local.ChatMessageEntity
import com.novoagentehb.app.data.local.ChatRole
import com.novoagentehb.app.data.local.GoalDao
import com.novoagentehb.app.data.local.TaskReceiptDao
import com.novoagentehb.app.data.local.TaskReceiptEntity
import com.novoagentehb.app.data.prefs.AppPreferences
import com.novoagentehb.app.gabi.GabiPersona
import com.novoagentehb.app.gabi.HBCharacterController
import com.novoagentehb.app.gabi.VoiceEngineCoordinator
import com.novoagentehb.app.gabi.VoiceTranscriptEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class OrchestratorTurnState(
    val status: TaskStatus = TaskStatus.IDLE,
    val activeTaskDescription: String = "",
    val pendingConfirmation: PendingConfirmation? = null,
    val lastProviderFailure: ProviderFailure? = null,
    val lastReceipt: ExecutionReceipt? = null
)

/**
 * Orquestrador sequencial original do NOVO_AGENTE_HB:
 * - Executa uma tarefa por vez em fila segura (Mutex), com botão Parar/cancelamento real.
 * - Atualiza o [HBCharacterController] em tempo real (Home, Chat e Quarto sincronizados).
 * - Consolida turnos do Gemini Live (`LiveUserTurnCommitted` e `LiveAssistantTurnCommitted`)
 *   gravando no máximo 1 mensagem do usuário e 1 do agente por turno.
 * - Despacha tool calls do Gemini Live por `PolicyGate` com chave de idempotência determinística
 *   por `callId`, sem jamais afirmar conclusão enquanto aguarda confirmação.
 */
@Singleton
class TaskOrchestrator @Inject constructor(
    private val registry: CapabilityRegistry,
    private val policyGate: PolicyGate,
    private val modelRouter: ModelRouter,
    private val memoryStore: MemoryStore,
    private val skillRegistry: SkillAndMcpRegistry,
    private val localSkillExecutor: LocalSkillExecutor,
    private val mcpClient: McpJsonRpcClient,
    private val executor: ToolExecutorAndVerifier,
    private val diagnosticsEngine: DiagnosticsEngine,
    private val chatDao: ChatMessageDao,
    private val goalDao: GoalDao,
    private val receiptDao: TaskReceiptDao,
    private val prefs: AppPreferences,
    private val hbController: HBCharacterController,
    private val voiceCoordinator: VoiceEngineCoordinator
) {
    private val sessionId = UUID.randomUUID().toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()
    private var currentJob: Job? = null

    private val _turnState = MutableStateFlow(OrchestratorTurnState())
    val turnState: StateFlow<OrchestratorTurnState> = _turnState.asStateFlow()

    init {
        voiceCoordinator.registerLiveToolCallHandler { callId, name, args ->
            executeLiveToolCall(callId, name, args)
        }
        observeVoiceTranscripts()
    }

    private fun observeVoiceTranscripts() {
        scope.launch {
            voiceCoordinator.events.collect { event ->
                when (event) {
                    is VoiceTranscriptEvent.FinalResult -> {
                        submitUserMessage(event.text) { reply ->
                            voiceCoordinator.speak(reply)
                        }
                    }
                    is VoiceTranscriptEvent.LiveUserTurnCommitted -> {
                        chatDao.insert(
                            ChatMessageEntity(
                                sessionId = sessionId,
                                role = ChatRole.USER.name,
                                content = event.text,
                                taskId = "live_turn_${event.turnId}"
                            )
                        )
                    }
                    is VoiceTranscriptEvent.LiveAssistantTurnCommitted -> {
                        saveAssistantMessage(
                            content = event.text,
                            taskId = "live_turn_${event.turnId}",
                            providerUsed = "gemini_live",
                            modelUsed = event.modelUsed
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun publishTurnState(transform: (OrchestratorTurnState) -> OrchestratorTurnState) {
        val next = transform(_turnState.value)
        _turnState.value = next
        hbController.updateTurnState(next)
    }

    fun submitUserMessage(
        rawText: String,
        goalId: Long? = null,
        onAssistantReply: ((String) -> Unit)? = null
    ) {
        val text = rawText.trim()
        if (text.isBlank()) return

        currentJob = scope.launch {
            queueMutex.withLock {
                try {
                    processMessageLocked(text, goalId, onAssistantReply)
                } catch (_: CancellationException) {
                    publishTurnState {
                        it.copy(
                            status = TaskStatus.CANCELLED,
                            activeTaskDescription = "Execução interrompida pela pessoa."
                        )
                    }
                    hbController.notifyTaskOutcome(
                        success = false,
                        cancelled = true,
                        taskId = "cancelled",
                        summary = "Execução interrompida pela pessoa."
                    )
                }
            }
        }
    }

    suspend fun executeLiveToolCall(
        callId: String,
        capabilityName: String,
        args: Map<String, String>
    ): String {
        return queueMutex.withLock {
            val safeCallId = callId.ifBlank { UUID.randomUUID().toString() }
            val taskId = "live_$safeCallId"
            val receipt = executeCapabilityWithPolicies(
                ToolExecutionRequest(
                    taskId = taskId,
                    idempotencyKey = "live:$safeCallId:$capabilityName",
                    capabilityName = capabilityName,
                    args = args
                ),
                providerUsed = "gemini_live",
                liveCallId = safeCallId
            )
            when (receipt.status) {
                TaskStatus.AWAITING_CONFIRMATION -> {
                    "CONFIRMACAO_PENDENTE: a ação $capabilityName NÃO foi executada ainda e aguarda aprovação explícita da pessoa na tela. Não diga que já foi concluída."
                }
                TaskStatus.COMPLETED -> {
                    hbController.notifyTaskOutcome(true, false, receipt.taskId, receipt.summary)
                    publishTurnState { it.copy(status = TaskStatus.IDLE, activeTaskDescription = "", lastReceipt = receipt) }
                    receipt.summary
                }
                else -> {
                    hbController.notifyTaskOutcome(false, false, receipt.taskId, receipt.summary, receipt.errorCode)
                    publishTurnState { it.copy(status = TaskStatus.IDLE, activeTaskDescription = "", lastReceipt = receipt) }
                    receipt.summary
                }
            }
        }
    }

    suspend fun runLocalSkill(skillId: String, inputContext: String = ""): Pair<SkillExecutionOutcome, ExecutionReceipt> {
        return queueMutex.withLock {
            val taskId = UUID.randomUUID().toString()
            val skill = skillRegistry.getById(skillId)
            if (skill == null) {
                val out = SkillExecutionOutcome(
                    status = SkillExecutionStatus.FAILED,
                    artifactCreated = false,
                    artifactPath = null,
                    taskExecuted = false,
                    verifiedPostCondition = false,
                    summary = "Skill não encontrada ($skillId).",
                    errorCode = "SKILL_NOT_FOUND",
                    semantics = ToolExecutionSemantics.NOT_IMPLEMENTED
                )
                val fail = ExecutionReceipt(
                    taskId = taskId,
                    idempotencyKey = "$taskId:skill:$skillId",
                    capabilityName = "local_skill:$skillId",
                    riskLevel = RiskLevel.LOCAL_MUTATION,
                    status = TaskStatus.FAILED,
                    summary = out.summary,
                    verifiedOutcome = false,
                    errorCode = out.errorCode,
                    semantics = ToolExecutionSemantics.NOT_IMPLEMENTED
                )
                hbController.notifyTaskOutcome(false, false, taskId, fail.summary, fail.errorCode)
                return@withLock out to fail
            }

            publishTurnState {
                it.copy(
                    status = TaskStatus.EXECUTING,
                    activeTaskDescription = "Processando skill ${skill.name}…"
                )
            }
            val started = System.currentTimeMillis()
            val out = localSkillExecutor.execute(skill, inputContext)
            val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)

            val taskStatus = when (out.status) {
                SkillExecutionStatus.TASK_EXECUTED -> TaskStatus.COMPLETED
                SkillExecutionStatus.ARTIFACT_PREPARED -> TaskStatus.COMPLETED
                SkillExecutionStatus.BLOCKED_OPT_IN,
                SkillExecutionStatus.BLOCKED_QUARANTINE -> TaskStatus.BLOCKED_POLICY
                else -> TaskStatus.FAILED
            }
            val capPrefix = if (out.taskExecuted) "local_skill_executed" else "local_skill_manifest"
            val receipt = ExecutionReceipt(
                taskId = taskId,
                idempotencyKey = "$taskId:$capPrefix:${skill.skillId}",
                capabilityName = "$capPrefix:${skill.skillId}",
                riskLevel = RiskLevel.LOCAL_MUTATION,
                status = taskStatus,
                summary = out.summary,
                verifiedOutcome = out.verifiedPostCondition,
                errorCode = out.errorCode,
                durationMs = elapsed,
                semantics = out.semantics
            )
            persistReceipt(receipt, null)
            publishTurnState {
                it.copy(
                    status = TaskStatus.IDLE,
                    activeTaskDescription = "",
                    lastReceipt = receipt
                )
            }
            hbController.notifyTaskOutcome(
                success = out.taskExecuted && out.verifiedPostCondition,
                cancelled = false,
                taskId = taskId,
                summary = out.summary,
                errorCode = out.errorCode
            )
            out to receipt
        }
    }

    suspend fun verifyMcpServer(skillId: String, allowLocalLan: Boolean = false): McpHandshakeResult {
        val rec = skillRegistry.getById(skillId)
            ?: return McpHandshakeResult(false, error = "Servidor MCP não cadastrado.")
        if (!rec.enabledByUser) {
            return McpHandshakeResult(false, error = "Ative o servidor MCP (opt-in) antes de conectar.")
        }
        publishTurnState {
            it.copy(
                status = TaskStatus.EXECUTING,
                activeTaskDescription = "Testando handshake MCP (${rec.name})…"
            )
        }
        val res = mcpClient.handshakeAndListTools(rec.manifestOrigin, allowLocalLan = allowLocalLan)
        val statusSummary = if (res.reachable) {
            val toolNames = res.tools.take(4).joinToString(", ") { it.name }.ifBlank { "nenhuma ferramenta listada" }
            "Handshake MCP OK (${res.serverName.ifBlank { rec.name }}, proto=${res.protocolVersion}, notif=${res.initializedNotificationSent}): ${res.tools.size} ferramenta(s) [$toolNames]"
        } else {
            "Falha handshake MCP: ${res.error ?: "indisponível"}"
        }
        skillRegistry.updateVerifiedSummary(skillId, statusSummary)
        val receipt = ExecutionReceipt(
            taskId = UUID.randomUUID().toString(),
            idempotencyKey = "mcp_handshake:$skillId:${System.currentTimeMillis()}",
            capabilityName = "mcp_handshake:${rec.name}",
            riskLevel = RiskLevel.READ_ONLY,
            status = if (res.reachable) TaskStatus.COMPLETED else TaskStatus.FAILED,
            summary = statusSummary,
            verifiedOutcome = res.reachable,
            errorCode = if (res.reachable) null else "MCP_UNREACHABLE",
            semantics = ToolExecutionSemantics.READ_VERIFIED
        )
        persistReceipt(receipt, null)
        publishTurnState {
            it.copy(status = TaskStatus.IDLE, activeTaskDescription = "", lastReceipt = receipt)
        }
        hbController.notifyTaskOutcome(res.reachable, false, receipt.taskId, statusSummary, receipt.errorCode)
        return res
    }

    /** Request a bounded local computation through the same confirmation and receipt path as agent tools. */
    fun requestWorkspaceProgram(expression: String, inputs: String, onResult: (String) -> Unit) {
        if (_turnState.value.status != TaskStatus.IDLE || _turnState.value.pendingConfirmation != null) {
            onResult("Outra ação está em andamento ou aguardando confirmação.")
            return
        }
        val taskId = UUID.randomUUID().toString()
        publishTurnState { it.copy(status = TaskStatus.PLANNING, activeTaskDescription = "Preparando cálculo local…") }
        currentJob = scope.launch {
            queueMutex.withLock {
                val receipt = executeCapabilityWithPolicies(ToolExecutionRequest(
                    taskId = taskId, idempotencyKey = "$taskId:workspace_program",
                    capabilityName = "execute_workspace_program", args = mapOf("expression" to expression, "inputs" to inputs)
                ))
                if (receipt.status != TaskStatus.AWAITING_CONFIRMATION) {
                    publishTurnState { it.copy(status = TaskStatus.IDLE, activeTaskDescription = "", lastReceipt = receipt) }
                    onResult(receipt.summary)
                }
            }
        }
    }

    fun requestMcpToolExecution(
        skillId: String,
        toolName: String,
        args: Map<String, String> = emptyMap(),
        allowLocalLan: Boolean = false,
        sessionId: String? = null
    ) {
        val taskId = UUID.randomUUID().toString()
        val encodedArgs = args.toMutableMap().apply {
            put("__mcp_allow_lan", allowLocalLan.toString())
            if (!sessionId.isNullOrBlank()) put("__mcp_session_id", sessionId)
        }
        publishTurnState {
            it.copy(
                status = TaskStatus.AWAITING_CONFIRMATION,
                activeTaskDescription = "Aguardando autorização para ferramenta MCP $toolName…",
                pendingConfirmation = PendingConfirmation(
                    taskId = taskId,
                    capabilityName = "mcp::$skillId::$toolName",
                    args = encodedArgs,
                    promptToUser = "Autorizar chamada externa MCP \"$toolName\" no servidor $skillId?",
                    riskLevel = RiskLevel.EXTERNAL_SIDE_EFFECT,
                    idempotencyKey = "$taskId:mcp:$skillId:$toolName"
                )
            )
        }
    }

    fun resolveConfirmation(approved: Boolean, onAssistantReply: ((String) -> Unit)? = null) {
        val pending = _turnState.value.pendingConfirmation ?: return
        publishTurnState { it.copy(pendingConfirmation = null) }

        if (!approved) {
            scope.launch {
                val cancelMsg = "Tudo bem, cancelei a ação ${pending.capabilityName}."
                saveAssistantMessage(cancelMsg, taskId = pending.taskId)
                if (pending.liveCallId != null) {
                    voiceCoordinator.sendLiveSystemNote("A pessoa cancelou a ação ${pending.capabilityName}.")
                }
                publishTurnState { it.copy(status = TaskStatus.IDLE, activeTaskDescription = "") }
                hbController.notifyTaskOutcome(false, true, pending.taskId, cancelMsg)
                onAssistantReply?.invoke(cancelMsg)
            }
            return
        }

        currentJob = scope.launch {
            queueMutex.withLock {
                if (pending.capabilityName.startsWith("mcp::")) {
                    val parts = pending.capabilityName.removePrefix("mcp::").split("::", limit = 2)
                    val skillId = parts.getOrNull(0).orEmpty()
                    val toolName = parts.getOrNull(1).orEmpty()
                    val server = skillRegistry.getById(skillId)
                    val allowLan = pending.args["__mcp_allow_lan"] == "true"
                    val mcpSid = pending.args["__mcp_session_id"]
                    val cleanArgs = pending.args.filterKeys { !it.startsWith("__mcp_") }
                    publishTurnState {
                        it.copy(status = TaskStatus.EXECUTING, activeTaskDescription = "Executando MCP $toolName…")
                    }
                    val res = if (server != null && server.enabledByUser) {
                        mcpClient.callRemoteTool(
                            endpointUrl = server.manifestOrigin,
                            toolName = toolName,
                            arguments = cleanArgs,
                            sessionId = mcpSid,
                            allowLocalLan = allowLan
                        )
                    } else {
                        com.novoagentehb.app.agent.skills.McpToolCallResult(false, "", error = "Servidor MCP desativado ou ausente.")
                    }
                    val summary = if (res.success) "MCP $toolName: ${res.outputText}" else "Erro MCP $toolName: ${res.error}"
                    val receipt = ExecutionReceipt(
                        taskId = pending.taskId,
                        idempotencyKey = pending.idempotencyKey,
                        capabilityName = pending.capabilityName,
                        riskLevel = RiskLevel.EXTERNAL_SIDE_EFFECT,
                        status = if (res.success) TaskStatus.COMPLETED else TaskStatus.FAILED,
                        summary = summary,
                        verifiedOutcome = res.success,
                        errorCode = if (res.success) null else "MCP_CALL_FAILED",
                        semantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED
                    )
                    persistReceipt(receipt, pending.goalId)
                    saveAssistantMessage(summary, taskId = pending.taskId)
                    publishTurnState { it.copy(status = TaskStatus.IDLE, activeTaskDescription = "", lastReceipt = receipt) }
                    hbController.notifyTaskOutcome(res.success, false, pending.taskId, summary, receipt.errorCode)
                    onAssistantReply?.invoke(summary)
                    return@withLock
                }

                val receipt = executeCapabilityWithPolicies(
                    rawRequest = ToolExecutionRequest(
                        taskId = pending.taskId,
                        idempotencyKey = pending.idempotencyKey,
                        capabilityName = pending.capabilityName,
                        args = pending.args,
                        goalId = pending.goalId,
                        userConfirmed = true,
                        paidConsentGranted = true
                    ),
                    liveCallId = pending.liveCallId
                )
                saveAssistantMessage(receipt.summary, taskId = receipt.taskId)
                if (pending.liveCallId != null) {
                    voiceCoordinator.sendLiveSystemNote("Resultado da ação confirmada (${pending.capabilityName}): ${receipt.summary}")
                }
                publishTurnState {
                    it.copy(
                        status = TaskStatus.IDLE,
                        activeTaskDescription = "",
                        lastReceipt = receipt
                    )
                }
                hbController.notifyTaskOutcome(
                    success = receipt.status == TaskStatus.COMPLETED,
                    cancelled = false,
                    taskId = receipt.taskId,
                    summary = receipt.summary,
                    errorCode = receipt.errorCode
                )
                onAssistantReply?.invoke(receipt.summary)
            }
        }
    }

    fun stopCurrentExecution() {
        currentJob?.cancel()
        currentJob = null
        publishTurnState {
            it.copy(
                status = TaskStatus.IDLE,
                activeTaskDescription = ""
            )
        }
        hbController.notifyTaskOutcome(
            success = false,
            cancelled = true,
            taskId = "stopped",
            summary = "Parado pela pessoa."
        )
    }

    fun clearFailureCard() {
        publishTurnState { it.copy(lastProviderFailure = null) }
    }

    private suspend fun processMessageLocked(
        text: String,
        goalId: Long?,
        onAssistantReply: ((String) -> Unit)?
    ) {
        val taskId = UUID.randomUUID().toString()
        chatDao.insert(ChatMessageEntity(sessionId = sessionId, role = ChatRole.USER.name, content = text, taskId = taskId))
        publishTurnState {
            it.copy(
                status = TaskStatus.PLANNING,
                activeTaskDescription = "Planejando resposta…",
                lastProviderFailure = null
            )
        }

        if (diagnosticsEngine.isExactCommand(text)) {
            val report = diagnosticsEngine.buildReport(
                DiagnosticsInput(
                    primaryProvider = prefs.primaryProvider.first(),
                    chatModel = prefs.chatModel.first(),
                    voiceMode = prefs.voiceMode.first(),
                    prosodyProfile = prefs.prosodyProfile.first(),
                    lastFailure = _turnState.value.lastProviderFailure,
                    petComponentsPresent = false
                )
            )
            saveAssistantMessage(report, taskId = taskId)
            publishTurnState { it.copy(status = TaskStatus.IDLE, activeTaskDescription = "") }
            hbController.notifyTaskOutcome(true, false, taskId, "Diagnóstico gerado")
            onAssistantReply?.invoke("Relatório de diagnóstico local gerado na tela.")
            return
        }

        val imagePrompt = com.novoagentehb.app.agent.media.ImageRequestIntent.parse(text)
        if (imagePrompt != null) {
            val receipt = executeCapabilityWithPolicies(ToolExecutionRequest(taskId, "$taskId:generate_image", "generate_image", mapOf("prompt" to imagePrompt), goalId))
            if (receipt.status != TaskStatus.AWAITING_CONFIRMATION) {
                saveAssistantMessage(receipt.summary, taskId)
                publishTurnState { it.copy(status = TaskStatus.IDLE, activeTaskDescription = "", lastReceipt = receipt) }
                onAssistantReply?.invoke(receipt.summary)
            }
            return
        }

        val systemPrompt = buildFullSystemPrompt()
        val recentHistory = chatDao.getRecentDesc(12).reversed().map {
            ModelChatMessage(if (it.role == ChatRole.ASSISTANT.name) "assistant" else "user", it.content)
        }

        when (val turn = modelRouter.generateTurn(
            role = ModelRole.CHAT,
            systemPrompt = systemPrompt,
            history = recentHistory,
            tools = registry.capabilities
        )) {
            is ModelTurnResult.TextReply -> {
                saveAssistantMessage(turn.text, taskId, turn.providerId, turn.modelId)
                publishTurnState { it.copy(status = TaskStatus.IDLE, activeTaskDescription = "") }
                hbController.notifyTaskOutcome(true, false, taskId, "Respondido")
                onAssistantReply?.invoke(turn.text)
            }
            is ModelTurnResult.ToolCallReply -> {
                val firstCall = turn.calls.first()
                val req = ToolExecutionRequest(
                    taskId = taskId,
                    idempotencyKey = "$taskId:${firstCall.name}",
                    capabilityName = firstCall.name,
                    args = firstCall.args,
                    goalId = goalId
                )
                val receipt = executeCapabilityWithPolicies(req, turn.providerId)
                if (receipt.status == TaskStatus.AWAITING_CONFIRMATION) {
                    onAssistantReply?.invoke(receipt.summary)
                } else {
                    saveAssistantMessage(receipt.summary, taskId, turn.providerId, turn.modelId)
                    publishTurnState {
                        it.copy(
                            status = TaskStatus.IDLE,
                            activeTaskDescription = "",
                            lastReceipt = receipt
                        )
                    }
                    hbController.notifyTaskOutcome(
                        success = receipt.status == TaskStatus.COMPLETED,
                        cancelled = false,
                        taskId = receipt.taskId,
                        summary = receipt.summary,
                        errorCode = receipt.errorCode
                    )
                    onAssistantReply?.invoke(receipt.summary)
                }
            }
            is ModelTurnResult.Failure -> {
                val offlineTool = AndroidDeviceBridge.parseOfflineIntent(text)
                if (offlineTool != null) {
                    val receipt = executeCapabilityWithPolicies(
                        ToolExecutionRequest(
                            taskId = taskId,
                            idempotencyKey = "$taskId:offline:${offlineTool.first}",
                            capabilityName = offlineTool.first,
                            args = offlineTool.second,
                            goalId = goalId
                        )
                    )
                    saveAssistantMessage(receipt.summary, taskId)
                    publishTurnState {
                        it.copy(
                            status = TaskStatus.IDLE,
                            activeTaskDescription = "",
                            lastProviderFailure = turn.failure,
                            lastReceipt = receipt
                        )
                    }
                    hbController.notifyTaskOutcome(
                        success = receipt.status == TaskStatus.COMPLETED,
                        cancelled = false,
                        taskId = receipt.taskId,
                        summary = receipt.summary,
                        errorCode = receipt.errorCode
                    )
                    onAssistantReply?.invoke(receipt.summary)
                } else {
                    executor.lastFailureSnapshot = turn.failure
                    saveAssistantMessage(turn.failure.userSummary, taskId)
                    publishTurnState {
                        it.copy(
                            status = TaskStatus.IDLE,
                            activeTaskDescription = "",
                            lastProviderFailure = turn.failure
                        )
                    }
                    hbController.notifyTaskOutcome(
                        success = false,
                        cancelled = false,
                        taskId = taskId,
                        summary = turn.failure.userSummary,
                        errorCode = turn.failure.category.name
                    )
                    onAssistantReply?.invoke(turn.failure.userSummary)
                }
            }
        }
    }

    suspend fun executeCapabilityWithPolicies(
        rawRequest: ToolExecutionRequest,
        providerUsed: String? = null,
        liveCallId: String? = null
    ): ExecutionReceipt {
        val request = rawRequest
        val descriptor = registry.find(request.capabilityName)
            ?: return ExecutionReceipt(
                taskId = request.taskId,
                idempotencyKey = request.idempotencyKey,
                capabilityName = request.capabilityName,
                riskLevel = RiskLevel.READ_ONLY,
                status = TaskStatus.FAILED,
                summary = "Ferramenta desconhecida: ${request.capabilityName}.",
                verifiedOutcome = false,
                errorCode = "UNKNOWN_CAPABILITY",
                semantics = ToolExecutionSemantics.NOT_IMPLEMENTED
            )

        val existing = receiptDao.findByIdempotencyKey(request.idempotencyKey)
        if (existing != null && existing.status == TaskStatus.COMPLETED.name && !descriptor.idempotent) {
            return ExecutionReceipt(
                taskId = existing.taskId,
                idempotencyKey = existing.idempotencyKey,
                capabilityName = existing.capabilityName,
                riskLevel = descriptor.riskLevel,
                status = TaskStatus.COMPLETED,
                summary = "Ação já executada anteriormente (${existing.summary}). Reenvio bloqueado por idempotência.",
                verifiedOutcome = existing.verifiedOutcome,
                semantics = descriptor.expectedSemantics
            )
        }

        val allowPaid = prefs.allowPaidCallsWithoutConfirm.first()
        when (val decision = policyGate.evaluate(descriptor, request, allowPaid)) {
            is PolicyDecision.Denied -> {
                val rec = ExecutionReceipt(
                    taskId = request.taskId,
                    idempotencyKey = request.idempotencyKey,
                    capabilityName = descriptor.name,
                    riskLevel = descriptor.riskLevel,
                    status = TaskStatus.BLOCKED_POLICY,
                    summary = decision.reason,
                    verifiedOutcome = false,
                    errorCode = "POLICY_DENIED",
                    semantics = descriptor.expectedSemantics
                )
                persistReceipt(rec, request.goalId)
                return rec
            }
            is PolicyDecision.RequireConfirmation -> {
                publishTurnState {
                    it.copy(
                        status = TaskStatus.AWAITING_CONFIRMATION,
                        activeTaskDescription = "Aguardando sua autorização…",
                        pendingConfirmation = PendingConfirmation(
                            taskId = request.taskId,
                            capabilityName = descriptor.name,
                            args = request.args,
                            promptToUser = decision.reasonPrompt,
                            riskLevel = descriptor.riskLevel,
                            goalId = request.goalId,
                            idempotencyKey = request.idempotencyKey,
                            liveCallId = liveCallId
                        )
                    )
                }
                return ExecutionReceipt(
                    taskId = request.taskId,
                    idempotencyKey = request.idempotencyKey,
                    capabilityName = descriptor.name,
                    riskLevel = descriptor.riskLevel,
                    status = TaskStatus.AWAITING_CONFIRMATION,
                    summary = decision.reasonPrompt,
                    verifiedOutcome = false,
                    semantics = descriptor.expectedSemantics
                )
            }
            is PolicyDecision.Allowed -> Unit
        }

        publishTurnState {
            it.copy(
                status = TaskStatus.EXECUTING,
                activeTaskDescription = "Executando ${descriptor.name}…"
            )
        }
        val started = System.currentTimeMillis()
        val outcome = executor.executeAndVerify(request)
        val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)

        val receipt = ExecutionReceipt(
            taskId = request.taskId,
            idempotencyKey = request.idempotencyKey,
            capabilityName = descriptor.name,
            riskLevel = descriptor.riskLevel,
            status = if (outcome.success) TaskStatus.COMPLETED else TaskStatus.FAILED,
            summary = outcome.summary,
            verifiedOutcome = outcome.verified,
            errorCode = outcome.errorCode,
            providerUsed = providerUsed,
            durationMs = elapsed,
            semantics = outcome.semantics
        )
        persistReceipt(receipt, request.goalId)
        return receipt
    }

    private suspend fun persistReceipt(receipt: ExecutionReceipt, goalId: Long?) {
        receiptDao.upsert(
            TaskReceiptEntity(
                taskId = receipt.taskId,
                idempotencyKey = receipt.idempotencyKey,
                goalId = goalId,
                capabilityName = receipt.capabilityName,
                riskLevel = receipt.riskLevel.name,
                status = receipt.status.name,
                summary = receipt.summary,
                verifiedOutcome = receipt.verifiedOutcome,
                errorCode = receipt.errorCode,
                providerUsed = receipt.providerUsed,
                durationMs = receipt.durationMs,
                createdAt = receipt.timestampMillis
            )
        )
        if (goalId != null) {
            goalDao.getById(goalId)?.let { goal ->
                val linked = (goal.linkedTaskIds.split(",").filter { it.isNotBlank() } + receipt.taskId)
                    .distinct()
                    .joinToString(",")
                goalDao.update(goal.copy(linkedTaskIds = linked, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    private suspend fun buildFullSystemPrompt(): String {
        val base = GabiPersona.buildSystemPrompt(
            defaultCity = prefs.defaultCity.first(),
            memories = memoryStore.getTopContext(12),
            activeGoals = goalDao.observeAll().first()
        )
        val skillsFrag = skillRegistry.activeSkillsPromptFragment()
        return if (skillsFrag.isBlank()) base else "$base\n\n$skillsFrag"
    }

    private suspend fun saveAssistantMessage(
        content: String,
        taskId: String? = null,
        providerUsed: String? = null,
        modelUsed: String? = null
    ) {
        chatDao.insert(
            ChatMessageEntity(
                sessionId = sessionId,
                role = ChatRole.ASSISTANT.name,
                content = content,
                taskId = taskId,
                providerUsed = providerUsed,
                modelUsed = modelUsed
            )
        )
    }
}
