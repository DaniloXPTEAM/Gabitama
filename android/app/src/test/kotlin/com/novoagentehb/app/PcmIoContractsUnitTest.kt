package com.novoagentehb.app

import com.novoagentehb.app.gabi.PcmIoContracts
import org.junit.Assert.*
import org.junit.Test

class PcmIoContractsUnitTest {
    @Test fun partialWritesRemainOrdered() {
        val offsets = mutableListOf<Int>()
        PcmIoContracts.writeFully(7) { offset, remaining -> offsets.add(offset); minOf(2, remaining) }
        assertEquals(listOf(0, 2, 4, 6), offsets)
    }
    @Test fun platformErrorsAndZeroProgressAreNotSilenced() {
        assertTrue(runCatching { PcmIoContracts.requireBufferSize(-2) }.isFailure)
        assertTrue(runCatching { PcmIoContracts.requireReadResult(-3) }.isFailure)
        assertTrue(runCatching { PcmIoContracts.writeFully(10) { _, _ -> 0 } }.isFailure)
        assertTrue(runCatching { PcmIoContracts.writeFully(10) { _, _ -> -6 } }.isFailure)
        assertEquals(3, PcmIoContracts.requireReadResult(3))
    }
}
