package com.novoagentehb.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openminis.app.sandbox.PtyBridge
import ai.tama.sandbox.ShellMarkerDecoder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Synthetic, fixed commands only: the PTY bridge is not exposed as an agent shell. */
@RunWith(AndroidJUnit4::class)
class TamaOriginalPtyAndroidTest {
    @Test fun originalBridgeExecutesOnRealPtyAndReturnsOutputAndExitCode() {
        val pid = IntArray(1)
        val fd = PtyBridge.forkExec("/system/bin/sh", arrayOf("sh", "-c", "printf 'PTY_PROBE_42\\n__MINIS_DONE_test_EXIT_7__\\n'; exit 7"),
            arrayOf("PATH=/system/bin", "TERM=xterm"), null, 80, 24, pid)
        assertTrue("forkpty should return a valid fd, got $fd", fd >= 0)
        assertTrue(pid[0] > 0)
        val worker = Executors.newSingleThreadExecutor()
        var reaped = false
        try {
            val result = worker.submit<Triple<String, Int?, Int>> {
                val bytes = ByteArray(32)
                val decoder = ShellMarkerDecoder("test")
                val text = StringBuilder()
                var markerExit: Int? = null
                while (markerExit == null) {
                    val n = PtyBridge.readBytes(fd, bytes, 0, bytes.size)
                    if (n <= 0) break
                    val frame = decoder.feed(String(bytes, 0, n, Charsets.UTF_8))
                    text.append(frame.text)
                    markerExit = frame.exitCode
                }
                Triple(text.toString(), markerExit, PtyBridge.waitFor(pid[0]))
            }.get(10, TimeUnit.SECONDS)
            reaped = true
            assertTrue("PTY output missing", result.first.contains("PTY_PROBE_42"))
            assertEquals(7, result.second)
            assertEquals(7, result.third)
        } finally {
            if (!reaped) PtyBridge.sendSignal(pid[0], 9)
            PtyBridge.closeFd(fd)
            worker.shutdownNow()
        }
    }

    @Test fun originalBridgeCanTerminateAndReapItsChild() {
        val pid = IntArray(1)
        val fd = PtyBridge.forkExec("/system/bin/sh", arrayOf("sh", "-c", "exec sleep 30"),
            arrayOf("PATH=/system/bin", "TERM=xterm"), null, 80, 24, pid)
        assertTrue("forkpty should return a valid fd, got $fd", fd >= 0)
        assertTrue(pid[0] > 0)
        val worker = Executors.newSingleThreadExecutor()
        var reaped = false
        try {
            assertEquals(0, PtyBridge.sendSignal(pid[0], 15))
            val status = worker.submit<Int> { PtyBridge.waitFor(pid[0]) }.get(10, TimeUnit.SECONDS)
            reaped = true
            assertEquals(-143, status)
        } finally {
            if (!reaped) PtyBridge.sendSignal(pid[0], 9)
            PtyBridge.closeFd(fd)
            worker.shutdownNow()
        }
    }
}
