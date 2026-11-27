package com.novoagentehb.app.agent.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Modelo de ação do navegador: puro, sem Android. Implementação própria do GabiTama (MIT). */
class BrowserActionUnitTest {

    @Test
    fun everyActionRoundTripsThroughWireName() {
        for (action in BrowserAction.entries) {
            assertEquals(action, BrowserAction.fromWireName(action.wireName))
        }
        assertNull(BrowserAction.fromWireName("acao_inexistente"))
        assertEquals(BrowserAction.entries.size, BrowserAction.wireNames.size)
    }

    @Test
    fun wireNameIsCaseAndSpaceInsensitive() {
        assertEquals(BrowserAction.RELOAD, BrowserAction.fromWireName("  Reload "))
    }

    @Test
    fun readOnlyActionsDoNotChangePage() {
        assertFalse(BrowserAction.GET_TEXT.changesPage)
        assertFalse(BrowserAction.GET_PAGE_INFO.changesPage)
        assertTrue(BrowserAction.BACK.changesPage)
        assertTrue(BrowserAction.RELOAD.changesPage)
    }

    @Test
    fun scrollDirectionParses() {
        assertEquals(ScrollDirection.DOWN, ScrollDirection.fromWireName("down"))
        assertNull(ScrollDirection.fromWireName("left"))
    }

    @Test
    fun resultFactoriesSetSuccessAndErrorCode() {
        val ok = BrowserActionResult.ok("feito", "https://example.com/")
        assertTrue(ok.success)
        assertNull(ok.errorCode)
        val err = BrowserActionResult.error("LOAD_TIMEOUT", "demorou")
        assertFalse(err.success)
        assertEquals("LOAD_TIMEOUT", err.errorCode)
    }
}
