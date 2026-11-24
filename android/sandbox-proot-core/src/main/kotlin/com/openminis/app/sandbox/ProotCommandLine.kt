package com.openminis.app.sandbox

/**
 * Android-free construction of the PRoot argv, shared by GabiTama and the computer helper.
 *
 * Origin: the body of `PRootKernel.buildProotCommand` in the original Tama V2 computer
 * (`com.openminis.app.sandbox`). The argument order and flags are kept as they were there.
 * Only the inputs changed: the caller now supplies the PRoot binary, the rootfs directory,
 * the bind map and the offload spec, instead of the Gabi-owned `RootfsManager` and
 * `NativeOffloadServer` singletons. The caller decides what to bind; this function never
 * adds a host path by itself.
 *
 * Intentionally NOT here: the `Log` lines, the `check(isBooted)` guard and the offload server
 * lifecycle. Those stay with the caller, which owns that state.
 */
object ProotCommandLine {

    /**
     * @param prootBinary absolute path of the PRoot executable (`libproot.so`).
     * @param rootfsDir absolute path of the guest root (`-r`).
     * @param bindMounts Linux path -> host path. Emitted as `-b host:linux`, in insertion order.
     * @param offloadSpec `socket:handler,handler` for `--native-offload`, or null to omit it.
     * @param shellCommand the command run by `/bin/sh -c` inside the guest.
     * @param leadingOptions PRoot options placed right after the binary (e.g. lifecycle flags
     *   that the caller owns, such as `--kill-on-exit`).
     */
    fun build(
        prootBinary: String,
        rootfsDir: String,
        bindMounts: Map<String, String>,
        offloadSpec: String?,
        shellCommand: String,
        leadingOptions: List<String> = emptyList(),
    ): List<String> {
        val cmd = mutableListOf<String>()

        cmd.add(prootBinary)
        cmd.addAll(leadingOptions)

        // Run as fake root (uid 0)
        cmd.add("-0")

        // T141: Translate hardlink() syscalls to symlink(). Android's /data
        // (ext4/F2FS as mounted by zygote) refuses cross-directory hardlinks
        // for app uids, so PRoot would otherwise pass link() straight through
        // to the host kernel and the host kernel rejects with EPERM. Alpine's
        // apk installs busybox-applet packages (binutils, gcc deps, etc.) by
        // hardlinking ar/ld/nm/strip → busybox; without this flag every such
        // install fails with "Permission denied". Symlinks are functionally
        // equivalent for the apk consumer.
        cmd.add("--link2symlink")

        // Set rootfs
        cmd.add("-r")
        cmd.add(rootfsDir)

        // Bind essential pseudo-filesystems
        cmd.add("-b")
        cmd.add("/dev")
        cmd.add("-b")
        cmd.add("/proc")
        cmd.add("-b")
        cmd.add("/sys")

        // Working directory
        cmd.add("-w")
        cmd.add("/root")

        // Caller-granted binds only
        for ((linuxPath, hostPath) in bindMounts) {
            cmd.add("-b")
            cmd.add("$hostPath:$linuxPath")
        }

        // Native offload: route registered handler names to the host-side server.
        if (offloadSpec != null) {
            cmd.add("--native-offload=$offloadSpec")
        }

        // Shell command
        cmd.add("/bin/sh")
        cmd.add("-c")
        cmd.add(shellCommand)
        return cmd
    }
}
