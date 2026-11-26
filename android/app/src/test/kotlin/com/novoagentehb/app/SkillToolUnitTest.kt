package com.novoagentehb.app

import com.novoagentehb.app.agent.orchestrator.RiskLevel
import com.novoagentehb.app.agent.orchestrator.ToolExecutionRequest
import com.novoagentehb.app.agent.orchestrator.ToolExecutionSemantics
import com.novoagentehb.app.agent.policy.PolicyDecision
import com.novoagentehb.app.agent.policy.PolicyGate
import com.novoagentehb.app.agent.skills.LocalSkillExecutor
import com.novoagentehb.app.agent.skills.SkillExecutionOutcome
import com.novoagentehb.app.agent.skills.SkillExecutionStatus
import com.novoagentehb.app.agent.tools.CapabilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Caminho da skill local até o resultado: capability visível ao modelo, gate de confirmação
 * e mapeamento honesto do desfecho. Não usa Android nem rede.
 */
class SkillToolUnitTest {

    private fun outcome(
        status: SkillExecutionStatus,
        verified: Boolean = false,
        errorCode: String? = null,
        artifactPath: String? = null
    ) = SkillExecutionOutcome(
        status = status,
        artifactCreated = artifactPath != null,
        artifactPath = artifactPath,
        taskExecuted = status == SkillExecutionStatus.TASK_EXECUTED,
        verifiedPostCondition = verified,
        summary = "resumo ${status.name}",
        errorCode = errorCode
    )

    @Test
    fun runLocalSkillIsRegisteredAsConfirmedLocalMutationWithRequiredSkillId() {
        val d = CapabilityRegistry().find("run_local_skill") ?: error("run_local_skill ausente do catálogo")
        assertEquals(RiskLevel.LOCAL_MUTATION, d.riskLevel)
        assertEquals(ToolExecutionSemantics.EXECUTED_AND_VERIFIED, d.expectedSemantics)
        assertTrue(d.params.any { it.name == "skill_id" && it.required })
        assertFalse(d.params.first { it.name == "input" }.required)
    }

    @Test
    fun runLocalSkillRequiresExplicitConfirmationBeforeRunning() {
        val gate = PolicyGate()
        val d = CapabilityRegistry().find("run_local_skill")!!
        val args = mapOf("skill_id" to "doc-campanha")

        val unconfirmed = gate.evaluate(d, ToolExecutionRequest("t1", "k1", "run_local_skill", args), allowPaidWithoutPrompt = true)
        assertTrue(unconfirmed is PolicyDecision.RequireConfirmation)
        assertTrue((unconfirmed as PolicyDecision.RequireConfirmation).reasonPrompt.contains("doc-campanha"))

        val confirmed = gate.evaluate(d, ToolExecutionRequest("t1", "k1", "run_local_skill", args, userConfirmed = true), allowPaidWithoutPrompt = false)
        assertEquals(PolicyDecision.Allowed, confirmed)
    }

    @Test
    fun taskExecutedWithVerifiedDiskWriteReportsSuccessAndVerified() {
        val r = LocalSkillExecutor.toToolResult(
            outcome(SkillExecutionStatus.TASK_EXECUTED, verified = true, artifactPath = "skills/x-documento-verificado.md")
        )
        assertTrue(r.success)
        assertTrue(r.verified)
        assertEquals("skills/x-documento-verificado.md", r.payload)
        assertEquals(null, r.errorCode)
    }

    @Test
    fun manifestOnlyIsSuccessfulButNeverClaimedAsVerifiedAction() {
        val r = LocalSkillExecutor.toToolResult(outcome(SkillExecutionStatus.ARTIFACT_PREPARED, verified = false))
        assertTrue(r.success)
        assertFalse("manifesto não é ação verificada", r.verified)
    }

    @Test
    fun blockedAndFailedOutcomesReturnErrorCodes() {
        val blocked = LocalSkillExecutor.toToolResult(outcome(SkillExecutionStatus.BLOCKED_OPT_IN, errorCode = "SKILL_DISABLED"))
        assertFalse(blocked.success)
        assertEquals("SKILL_DISABLED", blocked.errorCode)

        val failed = LocalSkillExecutor.toToolResult(outcome(SkillExecutionStatus.FAILED))
        assertFalse(failed.success)
        assertEquals("SKILL_FAILED", failed.errorCode)
    }

    @Test
    fun everyExecutionStatusIsMappedWithoutClaimingSuccessForFailures() {
        for (status in SkillExecutionStatus.entries) {
            val r = LocalSkillExecutor.toToolResult(outcome(status))
            if (status == SkillExecutionStatus.TASK_EXECUTED || status == SkillExecutionStatus.ARTIFACT_PREPARED) {
                assertTrue(status.name, r.success)
            } else {
                assertFalse(status.name, r.success)
                assertNotNull(status.name, r.errorCode)
            }
        }
    }
}
