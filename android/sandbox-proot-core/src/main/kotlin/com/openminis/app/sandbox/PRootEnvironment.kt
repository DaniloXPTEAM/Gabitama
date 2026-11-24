package com.openminis.app.sandbox

/**
 * Process environment for running the PRoot binary, shared by GabiTama and the computer helper.
 *
 * Origin: the variable block repeated in the original Tama V2 `ShellExecutor`, `TerminalSession`
 * and `PersistentShell` (`PROOT_TMP_DIR`, `LD_LIBRARY_PATH`, `PROOT_LOADER`, `PROOT_LOADER_32`), each
 * set only when its value is known. The rule is unchanged: a value is written only when it is not
 * empty. The caller supplies every path; this object reads no Android state.
 */
object PRootEnvironment {

    /**
     * @param path value for PATH, or null to leave PATH unset.
     * @param tmpDir PROOT_TMP_DIR (always set).
     * @param nativeLibDir directory with the extracted native libraries, for LD_LIBRARY_PATH.
     * @param loader PROOT_LOADER override, only if the caller found a side-loaded loader.
     * @param loader32 PROOT_LOADER_32 override, same rule.
     */
    fun processEnv(
        path: String?,
        tmpDir: String,
        nativeLibDir: String?,
        loader: String?,
        loader32: String?,
    ): Map<String, String> {
        val env = linkedMapOf<String, String>()
        if (!path.isNullOrEmpty()) env["PATH"] = path
        env["PROOT_TMP_DIR"] = tmpDir
        if (!nativeLibDir.isNullOrEmpty()) env["LD_LIBRARY_PATH"] = nativeLibDir
        if (!loader.isNullOrEmpty()) env["PROOT_LOADER"] = loader
        if (!loader32.isNullOrEmpty()) env["PROOT_LOADER_32"] = loader32
        return env
    }
}
