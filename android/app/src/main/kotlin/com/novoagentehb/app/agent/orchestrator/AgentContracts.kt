package com.novoagentehb.app.agent.orchestrator

enum class RiskLevel {
    READ_ONLY,
    LOCAL_MUTATION,
    DEVICE_BRIDGE,
    EXTERNAL_SIDE_EFFECT,
    PAID_API_CALL
}

enum class RuntimeSupportTier(val label: String) {
    NATIVE_LOCAL_VERIFIED("Local Room / Workspace"),
    ANDROID_BRIDGE_INTENT("Intent / Bridge Android (handoff)"),
    ACCESSIBILITY_OR_LISTENER("Depende de permissão/serviço"),
    HTTP_BYOK_OR_PUBLIC("HTTP / BYOK"),
    NOT_IMPLEMENTED("Não implementado")
}

enum class ToolExecutionSemantics(val label: String) {
    EXECUTED_AND_VERIFIED("Ação local executada e verificada"),
    READ_VERIFIED("Leitura local/API verificada"),
    NOTIFICATION_PREVIEW_ONLY("Somente prévias de notificação Android"),
    HANDOFF_TO_USER("App/Intent aberto — usuário conclui no app"),
    ACCESSIBILITY_AUTOMATED("Automação via Acessibilidade (exige confirmação)"),
    ARTIFACT_CREATED_ONLY("Apenas manifesto/artefato criado (não executa instruções)"),
    NOT_IMPLEMENTED("Não implementado")
}

enum class CapabilityVerificationState(val label: String) {
    NOT_IMPLEMENTED("NOT_IMPLEMENTED"),
    INTEGRATED_UNTESTED("INTEGRATED_UNTESTED"),
    DEVICE_VERIFIED("DEVICE_VERIFIED")
}

enum class TaskStatus {
    IDLE,
    PLANNING,
    AWAITING_CONFIRMATION,
    EXECUTING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
    BLOCKED_POLICY
}

data class ToolParamSpec(
    val name: String,
    val description: String,
    val required: Boolean = false
)

data class ToolDescriptor(
    val name: String,
    val description: String,
    val riskLevel: RiskLevel,
    val params: List<ToolParamSpec> = emptyList(),
    val idempotent: Boolean = false,
    val runtimeTier: RuntimeSupportTier = RuntimeSupportTier.NATIVE_LOCAL_VERIFIED,
    val expectedSemantics: ToolExecutionSemantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED,
    val requiredPermissionNote: String = "Nenhuma permissão Android extra",
    val postConditionNote: String = "Verificável via banco local / workspace",
    val verificationState: CapabilityVerificationState = CapabilityVerificationState.INTEGRATED_UNTESTED
)

data class ToolExecutionRequest(
    val taskId: String,
    val idempotencyKey: String,
    val capabilityName: String,
    val args: Map<String, String>,
    val goalId: Long? = null,
    val userConfirmed: Boolean = false,
    val paidConsentGranted: Boolean = false
)

data class ToolExecutionResult(
    val success: Boolean,
    val verified: Boolean,
    val summary: String,
    val payload: String = "",
    val errorCode: String? = null,
    val semantics: ToolExecutionSemantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED
)

data class ExecutionReceipt(
    val taskId: String,
    val idempotencyKey: String,
    val capabilityName: String,
    val riskLevel: RiskLevel,
    val status: TaskStatus,
    val summary: String,
    val verifiedOutcome: Boolean,
    val errorCode: String? = null,
    val providerUsed: String? = null,
    val durationMs: Long = 0L,
    val timestampMillis: Long = System.currentTimeMillis(),
    val semantics: ToolExecutionSemantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED
)

data class PendingConfirmation(
    val taskId: String,
    val capabilityName: String,
    val args: Map<String, String>,
    val promptToUser: String,
    val riskLevel: RiskLevel,
    val goalId: Long? = null,
    val idempotencyKey: String,
    val liveCallId: String? = null
)
