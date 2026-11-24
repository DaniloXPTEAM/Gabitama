package com.openminis.app.sandbox

import org.junit.Assert.assertEquals
import org.junit.Test

class ProotCommandLineTest {

    @Test fun keepsOriginalArgumentOrderWithoutGabiBinds() {
        val argv = ProotCommandLine.build(
            prootBinary = "/lib/libproot.so",
            rootfsDir = "/data/alpine",
            bindMounts = emptyMap(),
            offloadSpec = null,
            shellCommand = "echo ok",
        )
        assertEquals(
            listOf(
                "/lib/libproot.so", "-0", "--link2symlink",
                "-r", "/data/alpine",
                "-b", "/dev", "-b", "/proc", "-b", "/sys",
                "-w", "/root",
                "/bin/sh", "-c", "echo ok",
            ),
            argv,
        )
    }

    @Test fun leadingOptionsSitRightAfterBinaryAndBindsKeepInsertionOrder() {
        val argv = ProotCommandLine.build(
            prootBinary = "p",
            rootfsDir = "r",
            bindMounts = linkedMapOf("/var/minis/mounts/a" to "/host/a", "/mnt/b" to "/host/b"),
            offloadSpec = "sock:h1,h2",
            shellCommand = "true",
            leadingOptions = listOf("--kill-on-exit"),
        )
        assertEquals("--kill-on-exit", argv[1])
        assertEquals("-0", argv[2])
        val binds = argv.withIndex().filter { it.value == "-b" }.map { argv[it.index + 1] }
        assertEquals(listOf("/dev", "/proc", "/sys", "/host/a:/var/minis/mounts/a", "/host/b:/mnt/b"), binds)
        assertEquals("--native-offload=sock:h1,h2", argv[argv.size - 4])
        assertEquals(listOf("/bin/sh", "-c", "true"), argv.takeLast(3))
    }
}
