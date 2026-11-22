package com.novoagentehb.app

import com.novoagentehb.app.agent.computer.WorkspaceProgram
import com.novoagentehb.app.agent.orchestrator.ToolExecutionRequest
import com.novoagentehb.app.agent.policy.PolicyDecision
import com.novoagentehb.app.agent.policy.PolicyGate
import com.novoagentehb.app.agent.tools.CapabilityRegistry
import com.novoagentehb.app.bridges.SafLibraryManager
import com.novoagentehb.app.bridges.WorkspaceWriteOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkspaceProgramUnitTest {
    @Test fun computesRealResultsAndVerifiesArtifact() = runBlocking {
        val files = mutableMapOf<String, String>()
        val result = WorkspaceProgram.execute("(x+1)*2", "1,2\n3", true,
            { path, value -> files[path] = value; WorkspaceWriteOutcome(true, path, value.length.toLong(), SafLibraryManager.sha256Hex(value.toByteArray())) },
            { files[it] })
        assertEquals(3, result.rowCount)
        assertEquals("entrada,resultado\n1,4.0\n2,6.0\n3,8.0\n", files[result.path])
        assertEquals(64, result.sha256.length)
    }
    @Test fun rejectsInvalidAndUnauthorizedProgramsWithoutWriting() = runBlocking {
        var writes = 0
        val writer: (String, String) -> WorkspaceWriteOutcome = { p, _ -> writes++; WorkspaceWriteOutcome(true, p, 0, "") }
        for ((formula, numbers, confirmed) in listOf(Triple("x*2", "1", false), Triple("x; shell", "1", true), Triple("1/(x-x)", "1", true), Triple("x", "NaN", true))) {
            try { WorkspaceProgram.execute(formula, numbers, confirmed, writer, read = { null }); fail("invalid program accepted") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, writes)
    }
    @Test fun cancellationDuringComputationStopsBeforeCreatingArtifact() = runBlocking {
        var writes = 0
        val started = CompletableDeferred<Unit>()
        val pause = CompletableDeferred<Unit>()
        val job = launch {
            WorkspaceProgram.execute("x*2", "2,3", true,
                { path, _ -> writes++; WorkspaceWriteOutcome(true, path, 0, "") }, { null },
                onRow = { if (it == 0) { started.complete(Unit); pause.await() } })
        }
        withTimeout(5000) { started.await() }
        job.cancel(); job.join()
        assertTrue(job.isCancelled)
        assertEquals(0, writes)
    }
    @Test fun policyRequiresExplicitConfirmation() {
        val descriptor = requireNotNull(CapabilityRegistry().find("execute_workspace_program"))
        val req = ToolExecutionRequest("task", "id", descriptor.name, mapOf("expression" to "x*2", "inputs" to "3"))
        assertTrue(PolicyGate().evaluate(descriptor, req, false) is PolicyDecision.RequireConfirmation)
        assertTrue(PolicyGate().evaluate(descriptor, req.copy(userConfirmed = true), false) is PolicyDecision.Allowed)
    }
}
