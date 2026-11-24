package com.novoagentehb.app.computer.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperStatusMapperTest {

    private val installedRootfs = "state=INSTALLED release=3.21.3 arch=aarch64 files=522"

    @Test
    fun notInstalledWhenPackageMissing() {
        val s = HelperStatusMapper.map(false, false, null, null, null)
        assertEquals(HelperPhase.NOT_INSTALLED, s.phase)
        assertTrue(s.lines.last().contains("uso interativo"))
    }

    @Test
    fun unavailableWhenInstalledButNoReply() {
        assertEquals(HelperPhase.UNAVAILABLE, HelperStatusMapper.map(true, false, null, null, null).phase)
    }

    @Test
    fun rootfsAbsentAndInstalled() {
        assertEquals(HelperPhase.ROOTFS_ABSENT, HelperStatusMapper.map(true, true, "state=NOT_STARTED", null, "state=IDLE op=0").phase)
        assertEquals(HelperPhase.ROOTFS_INSTALLED, HelperStatusMapper.map(true, true, installedRootfs, null, "state=IDLE op=0").phase)
    }

    @Test
    fun alpineProbeDoneOnlyWithAlpineMarkAndExitZero() {
        val done = "state=EXITED op=3 exit=0 elapsedMs=120 note=pid=1 leftover=0 output=echo | NAME=\"Alpine Linux\""
        assertEquals(HelperPhase.DIAGNOSTIC_DONE, HelperStatusMapper.map(true, true, installedRootfs, "state=INSTALLED", done).phase)
        val noMark = "state=EXITED op=3 exit=0 note=pid=1 leftover=0 output=hello"
        assertEquals(HelperPhase.DIAGNOSTIC_ERROR, HelperStatusMapper.map(true, true, installedRootfs, null, noMark).phase)
    }

    @Test
    fun runningAndFailedProbes() {
        assertEquals(
            HelperPhase.DIAGNOSTIC_RUNNING,
            HelperStatusMapper.map(true, true, installedRootfs, null, "state=RUNNING op=3 exit=-1").phase,
        )
        val failed = "state=EXITED op=3 exit=2 note=pid=1 leftover=0 output=can't fork"
        assertEquals(HelperPhase.DIAGNOSTIC_ERROR, HelperStatusMapper.map(true, true, installedRootfs, null, failed).phase)
    }

    @Test
    fun prootVersionExitZeroIsProotAvailable() {
        val version = "state=EXITED op=1 exit=0 note=pid=1 output=proot"
        assertEquals(HelperPhase.PROOT_AVAILABLE, HelperStatusMapper.map(true, true, installedRootfs, null, version).phase)
    }

    @Test
    fun fieldParserReadsKeysOnly() {
        assertEquals("INSTALLED", HelperStatusMapper.field("state=INSTALLED release=3.21.3", "state"))
        assertEquals(null, HelperStatusMapper.field("xstate=BAD", "state"))
    }
}
