package com.novoagentehb.app.computer.helper

import com.novoagentehb.computerhelper.contract.HelperContract

/** Phases shown to the user for the experimental computer helper. Labels are user-facing (pt-BR). */
enum class HelperPhase(val label: String) {
    NOT_INSTALLED("Auxiliar não instalado"),
    UNAVAILABLE("Auxiliar indisponível"),
    ROOTFS_ABSENT("Rootfs ausente"),
    ROOTFS_INSTALLED("Rootfs instalado"),
    PROOT_AVAILABLE("PRoot disponível"),
    DIAGNOSTIC_RUNNING("Diagnóstico em execução"),
    DIAGNOSTIC_DONE("Diagnóstico concluído"),
    DIAGNOSTIC_ERROR("Erro de execução"),
}

/** Primary phase plus the lines shown in the existing notice card. */
data class HelperSnapshot(val phase: HelperPhase, val lines: List<String>)

/**
 * Maps raw helper replies (text as returned by the helper) to user-visible states.
 * Pure: no Android and no I/O, so it is unit-tested on the JVM.
 * Inputs are the reply texts, or null when that reply was not requested.
 */
object HelperStatusMapper {
    const val DIAGNOSTIC_OP = HelperContract.PROOT_OP_ALPINE_X86_PROBE
    private const val ALPINE_MARK = "Alpine Linux"

    fun map(
        installed: Boolean,
        helperResponding: Boolean,
        productRootfs: String?,
        testRootfs: String?,
        proot: String?,
    ): HelperSnapshot {
        val interactive = "Computador ainda não habilitado para uso interativo: nenhum terminal ou comando livre."
        if (!installed) {
            return HelperSnapshot(HelperPhase.NOT_INSTALLED, listOf("Auxiliar experimental não instalado.", interactive))
        }
        if (!helperResponding) {
            return HelperSnapshot(HelperPhase.UNAVAILABLE, listOf("Auxiliar instalado, mas não respondeu.", interactive))
        }
        val productState = field(productRootfs, "state")
        val productInstalled = productState == "INSTALLED"
        val lines = mutableListOf(
            "Auxiliar: instalado e respondendo.",
            "Rootfs Alpine ${HelperContract.ALPINE_VERSION} aarch64 (produto): ${productState ?: "não consultado"}. " +
                "Não executado: sem prova Android ARM64.",
            "Rootfs Alpine x86_64 (teste de CI): ${testState(testRootfs)}.",
        )

        val state = field(proot, "state")
        val op = field(proot, "op")?.toIntOrNull()
        val exit = field(proot, "exit")?.toIntOrNull()
        val output = proot.orEmpty()
        var phase: HelperPhase? = null

        when {
            state == "RUNNING" -> {
                phase = HelperPhase.DIAGNOSTIC_RUNNING
                lines += "Diagnóstico do Alpine em execução."
            }
            op == DIAGNOSTIC_OP && state == "EXITED" && exit == 0 && output.contains(ALPINE_MARK) -> {
                phase = HelperPhase.DIAGNOSTIC_DONE
                lines += "Alpine x86_64 executou um programa fixo no PRoot original (exit=0)."
            }
            op == DIAGNOSTIC_OP && state != null && state != "IDLE" -> {
                phase = HelperPhase.DIAGNOSTIC_ERROR
                lines += "Diagnóstico do Alpine falhou: state=$state exit=${exit ?: "-"}."
            }
            state == "EXITED" && exit == 0 -> {
                phase = HelperPhase.PROOT_AVAILABLE
                lines += "PRoot executou uma operação fixa com sucesso."
            }
        }
        if (phase == null) {
            phase = if (productInstalled) HelperPhase.ROOTFS_INSTALLED else HelperPhase.ROOTFS_ABSENT
        }
        lines += interactive
        return HelperSnapshot(phase, lines)
    }

    private fun testState(text: String?): String = when (field(text, "state")) {
        null -> "não consultado"
        "INSTALLED" -> "instalado"
        "NO_ASSET" -> "ausente neste build"
        else -> "ausente (${field(text, "state")})"
    }

    /** Reads `key=value` from the helper's text; values end at the next space. */
    internal fun field(text: String?, key: String): String? {
        if (text == null) return null
        val match = Regex("(?:^|\\s)${Regex.escape(key)}=(\\S+)").find(text) ?: return null
        return match.groupValues[1]
    }
}
