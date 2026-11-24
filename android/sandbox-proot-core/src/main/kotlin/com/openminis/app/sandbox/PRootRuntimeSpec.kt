package com.openminis.app.sandbox

/**
 * Contract between a computer host and the shared PRoot pieces (argv and environment).
 *
 * The host fills it from its real state; this class does not look anything up. It has no Android types,
 * so the JVM tests cover it. It never names GabiTama data: no binds, no Gabi directories.
 *
 * Execution is controlled here: a command is produced only when the rootfs is installed and the
 * execution mode is [Execution.FIXED_OPERATIONS_ONLY]. Callers pass fixed program text, never user input.
 */
data class PRootRuntimeSpec(
    /** Canonical guest root, or null when the host has none. */
    val rootfsDir: String?,
    /** True only when the host verified the installation (not merely the presence of files). */
    val rootfsInstalled: Boolean,
    /** ELF arch the verified rootfs was checked against, e.g. "x86_64" or "aarch64". */
    val rootfsArch: String,
    /** Absolute path of the PRoot executable (`libproot.so`). */
    val prootBinary: String,
    /** Directory with the host's native libraries (LD_LIBRARY_PATH). Empty when unknown. */
    val nativeLibDir: String,
    /** PROOT_LOADER path, or "" when the host has none. */
    val loaderPath: String,
    /** PROOT_LOADER_32 path, or "" when the host has none. */
    val loader32Path: String,
    /** PROOT_TMP_DIR. */
    val tmpDir: String,
    val execution: Execution,
) {
    enum class Execution { DISABLED, FIXED_OPERATIONS_ONLY }

    /** Environment for the PRoot process. [path] is the PATH to set, or null to leave it unset. */
    fun prootEnvironment(path: String?): Map<String, String> =
        PRootEnvironment.processEnv(path, tmpDir, nativeLibDir, loaderPath, loader32Path)

    /**
     * argv for one fixed guest command, or null when execution is not allowed for this spec.
     * [leadingOptions] are PRoot lifecycle flags placed right after the binary.
     */
    fun commandFor(shellCommand: String, leadingOptions: List<String> = emptyList()): List<String>? {
        val root = rootfsDir ?: return null
        if (execution != Execution.FIXED_OPERATIONS_ONLY || !rootfsInstalled) return null
        return ProotCommandLine.build(
            prootBinary = prootBinary,
            rootfsDir = root,
            bindMounts = emptyMap(),
            offloadSpec = null,
            shellCommand = shellCommand,
            leadingOptions = leadingOptions,
        )
    }

    /** Short description of the components that built the command, for the reply to the caller. */
    fun components(): String =
        "components=PRootRuntimeSpec>ProotCommandLine>PRootEnvironment " +
            "rootfs=${if (rootfsInstalled) "INSTALLED" else "ABSENT"} arch=$rootfsArch execution=${execution.name}"
}
