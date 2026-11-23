package com.novoagentehb.app.ui.computer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novoagentehb.app.agent.orchestrator.OrchestratorTurnState
import com.novoagentehb.app.agent.orchestrator.RuntimeSupportTier
import com.novoagentehb.app.agent.orchestrator.TaskOrchestrator
import com.novoagentehb.app.agent.orchestrator.ToolExecutionSemantics
import com.novoagentehb.app.agent.skills.LocalSkillExecutor
import com.novoagentehb.app.agent.skills.McpRemoteTool
import com.novoagentehb.app.agent.skills.SkillAndMcpRegistry
import com.novoagentehb.app.agent.tools.CapabilityRegistry
import com.novoagentehb.app.data.local.SkillRecordEntity
import com.novoagentehb.app.computer.runtime.IsolatedRuntimeHealth
import com.novoagentehb.app.gabi.HBCharacterController
import com.novoagentehb.app.gabi.HBState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComputerViewModel @Inject constructor(
    private val skillRegistry: SkillAndMcpRegistry,
    private val capabilityRegistry: CapabilityRegistry,
    private val orchestrator: TaskOrchestrator,
    private val hbController: HBCharacterController,
    private val isolatedRuntimeHealth: IsolatedRuntimeHealth
) : ViewModel() {

    val skills: StateFlow<List<SkillRecordEntity>> = skillRegistry.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val turnState: StateFlow<OrchestratorTurnState> = orchestrator.turnState
    val hbState: StateFlow<HBState> = hbController.state

    private val _mcpToolsByServer = MutableStateFlow<Map<String, List<McpRemoteTool>>>(emptyMap())
    val mcpToolsByServer: StateFlow<Map<String, List<McpRemoteTool>>> = _mcpToolsByServer.asStateFlow()

    private val _mcpSessionIds = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _mcpAllowLanByServer = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val mcpAllowLanByServer: StateFlow<Map<String, Boolean>> = _mcpAllowLanByServer.asStateFlow()

    private val _mcpLastLog = MutableStateFlow<List<String>>(emptyList())
    val mcpLastLog: StateFlow<List<String>> = _mcpLastLog.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()
    private val _runtimeNotice = MutableStateFlow<String?>(null)
    val runtimeNotice: StateFlow<String?> = _runtimeNotice.asStateFlow()

    val capabilityCount: Int get() = capabilityRegistry.capabilities.size
    val capabilitiesByTier: Map<RuntimeSupportTier, Int> get() = capabilityRegistry.countByTier()
    val capabilitiesBySemantics: Map<ToolExecutionSemantics, Int> get() = capabilityRegistry.countBySemantics()
    val unimplementedItems: List<Pair<String, String>> get() = capabilityRegistry.unimplementedCapabilities

    fun registerSkill(name: String, instruction: String, verifiedWorkspaceAction: Boolean = false) {
        if (name.isBlank() || instruction.isBlank()) return
        viewModelScope.launch {
            val scope = if (verifiedWorkspaceAction) {
                LocalSkillExecutor.SCOPE_WORKSPACE_WRITE_VERIFY
            } else {
                LocalSkillExecutor.SCOPE_WORKSPACE_ONLY
            }
            skillRegistry.registerDescriptor(
                skillId = "skill.$name",
                name = name,
                sourceType = "LOCAL_SKILL",
                version = "1.0.0",
                summary = instruction,
                scope = scope,
                manifestOrigin = "manual_ui"
            )
            _statusNotice.value = if (verifiedWorkspaceAction) {
                "Skill com adaptador workspace_write_verify registrada (opt-in desligado por padrão)."
            } else {
                "Skill de instrução livre registrada (apenas gera manifesto; não interpreta ações livres)."
            }
        }
    }

    fun registerMcp(name: String, endpoint: String, allowLocalLan: Boolean = false) {
        if (name.isBlank() || endpoint.isBlank()) return
        viewModelScope.launch {
            val entity = skillRegistry.registerDescriptor(
                skillId = "mcp.$name",
                name = name,
                sourceType = "MCP_SERVER",
                version = "1.0.0",
                summary = "Aguardando handshake MCP Streamable HTTP (não verificado)",
                scope = if (allowLocalLan) "opt_in_lan_or_external" else "opt_in_external",
                manifestOrigin = endpoint
            )
            _mcpAllowLanByServer.value = _mcpAllowLanByServer.value + (entity.skillId to allowLocalLan)
            _statusNotice.value = "Servidor MCP \"$name\" cadastrado. Ative e teste o handshake."
        }
    }

    fun setMcpAllowLan(skillId: String, allow: Boolean) {
        _mcpAllowLanByServer.value = _mcpAllowLanByServer.value + (skillId to allow)
    }

    fun runLocalSkill(skillId: String, contextInput: String = "") {
        viewModelScope.launch {
            val (outcome, _) = orchestrator.runLocalSkill(skillId, contextInput)
            _statusNotice.value = outcome.summary
        }
    }

    fun verifyMcpServer(skillId: String) {
        viewModelScope.launch {
            val allowLan = _mcpAllowLanByServer.value[skillId] == true
            val res = orchestrator.verifyMcpServer(skillId, allowLocalLan = allowLan)
            _mcpLastLog.value = res.negotiationLog
            if (res.reachable) {
                _mcpToolsByServer.value = _mcpToolsByServer.value + (skillId to res.tools)
                res.sessionId?.let { sid ->
                    _mcpSessionIds.value = _mcpSessionIds.value + (skillId to sid)
                }
                _statusNotice.value = "MCP ativo (${res.serverName.ifBlank { skillId }}, proto=${res.protocolVersion}): ${res.tools.size} ferramenta(s)."
            } else {
                _statusNotice.value = res.error ?: "Falha no handshake MCP."
            }
        }
    }

    fun requestMcpTool(skillId: String, toolName: String) {
        val allowLan = _mcpAllowLanByServer.value[skillId] == true
        val sid = _mcpSessionIds.value[skillId]
        orchestrator.requestMcpToolExecution(
            skillId = skillId,
            toolName = toolName,
            allowLocalLan = allowLan,
            sessionId = sid
        )
    }

    fun resolveConfirmation(approved: Boolean) {
        orchestrator.resolveConfirmation(approved) { reply ->
            _statusNotice.value = reply
        }
    }

    fun stopAll() {
        orchestrator.stopCurrentExecution()
    }

    fun toggleSkill(skillId: String, enabled: Boolean) {
        viewModelScope.launch { skillRegistry.setEnabled(skillId, enabled) }
    }

    fun deleteSkill(skillId: String) {
        viewModelScope.launch {
            skillRegistry.remove(skillId)
            _mcpToolsByServer.value = _mcpToolsByServer.value - skillId
            _mcpSessionIds.value = _mcpSessionIds.value - skillId
            _mcpAllowLanByServer.value = _mcpAllowLanByServer.value - skillId
        }
    }

    fun requestWorkspaceProgram(expression: String, inputs: String) {
        orchestrator.requestWorkspaceProgram(expression, inputs) { _statusNotice.value = it }
    }

    fun runDiagnosticsCommand() {
        orchestrator.submitUserMessage("/diagnostico")
        _statusNotice.value = "Comando /diagnostico enviado para a fila do orquestrador."
        viewModelScope.launch {
            _runtimeNotice.value = if (isolatedRuntimeHealth.isStartedAndIsolated()) {
                "Serviço isolado iniciado. O shell original ainda não está disponível."
            } else {
                "Serviço isolado indisponível. O shell original ainda não está disponível."
            }
        }
    }

    fun clearNotice() {
        _runtimeNotice.value = null
        _statusNotice.value = null
    }
}
