package com.novoagentehb.app.gabi

import com.novoagentehb.app.gabi.AvatarFramePlanner.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarFramePlannerUnitTest {

    @Test
    fun webpFramesAreOrderedAndNonImageFilesIgnored() {
        val files = listOf("f010.webp", "Thumbs.db", "f002.webp", "f001.webp", "notes.txt", "f003.png")
        assertEquals(
            listOf("f001.webp", "f002.webp", "f003.png", "f010.webp"),
            AvatarFramePlanner.frameNames(files)
        )
    }

    @Test
    fun emptyFolderFallsBackToStaticImage() {
        assertEquals(Plan.Fallback, AvatarFramePlanner.plan(emptyList(), reduceMotion = false))
        assertEquals(Plan.Fallback, AvatarFramePlanner.plan(listOf("readme.md"), reduceMotion = false))
    }

    @Test
    fun reduceMotionShowsOnlyFirstFrameStill() {
        val plan = AvatarFramePlanner.plan(listOf("f002.webp", "f001.webp"), reduceMotion = true)
        assertEquals(Plan.Still("f001.webp"), plan)
    }

    @Test
    fun normalMotionLoopsAllFramesInOrder() {
        val plan = AvatarFramePlanner.plan(listOf("f002.webp", "f001.webp", "f003.webp"), reduceMotion = false)
        assertEquals(Plan.Loop(listOf("f001.webp", "f002.webp", "f003.webp")), plan)
    }

    @Test
    fun nextIndexWrapsAroundAndHandlesEmpty() {
        assertEquals(1, AvatarFramePlanner.nextIndex(0, 3))
        assertEquals(0, AvatarFramePlanner.nextIndex(2, 3))
        assertEquals(0, AvatarFramePlanner.nextIndex(0, 0))
    }

    @Test
    fun frameDelayIsClampedToSixtyFpsAndNeverDividesByZero() {
        assertEquals(41L, AvatarFramePlanner.frameDelayMs(24))
        assertEquals(16L, AvatarFramePlanner.frameDelayMs(1000))
        assertEquals(1000L, AvatarFramePlanner.frameDelayMs(0))
    }

    @Test
    fun prefetchWindowIsBoundedSoMemoryDoesNotGrowWithSequenceLength() {
        // Janela fixa e pequena: no máximo 4 quadros decodificados à frente, independente do tamanho da sequência.
        assertTrue(AvatarFramePlanner.PREFETCH_FRAMES in 1..8)
        // Desiste após uma volta completa sem sucesso (evita laço ocupado), com mínimo de 1.
        assertEquals(353, AvatarFramePlanner.maxConsecutiveFailures(353))
        assertEquals(1, AvatarFramePlanner.maxConsecutiveFailures(0))
    }
}
