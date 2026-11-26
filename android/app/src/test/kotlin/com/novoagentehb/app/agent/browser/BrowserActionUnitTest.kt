package com.novoagentehb.app.agent.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fatia 1 do navegador (portada do Tama V2, GPL-3.0): modelo de ação puro, sem Android. */
class BrowserActionUnitTest {

    @Test
    fun everyActionValueRoundTripsThroughFromString() {
        for (action in BrowserAction.entries) {
            assertEquals(action, BrowserAction.fromString(action.value))
        }
        assertNull(BrowserAction.fromString("acao_inexistente"))
        assertEquals(BrowserAction.entries.size, BrowserAction.allValues.size)
    }

    @Test
    fun onlyNavigateOpensANewPage() {
        assertTrue(BrowserAction.NAVIGATE.opensNewPage)
        // Ações que operam na página atual não podem abrir outra aba (regressão #612/#614 do Tama).
        for (action in listOf(BrowserAction.EXECUTE_JS, BrowserAction.GET_TEXT, BrowserAction.CLICK, BrowserAction.SCREENSHOT)) {
            assertFalse(action.name, action.opensNewPage)
        }
    }

    @Test
    fun handOverIsDistinctSoThePersonCanTakeOverLoginOrCaptcha() {
        assertEquals("hand_over", BrowserAction.HAND_OVER.value)
        assertFalse(BrowserAction.HAND_OVER.opensNewPage)
    }

    @Test
    fun userAgentProfilesHaveExpectedViewportsAndCustomHasNoDefaultString() {
        assertEquals(412 to 915, UserAgentProfile.MOBILE_CHROME.viewportSize)
        assertEquals(1280 to 800, UserAgentProfile.DESKTOP_CHROME.viewportSize)
        assertNull(UserAgentProfile.CUSTOM.userAgentString)
        assertTrue(UserAgentProfile.MOBILE_CHROME.userAgentString!!.contains("Android"))
    }

    @Test
    fun scrollDirectionParsesOnlyKnownValues() {
        assertEquals(ScrollDirection.UP, ScrollDirection.fromString("up"))
        assertEquals(ScrollDirection.DOWN, ScrollDirection.fromString("down"))
        assertNull(ScrollDirection.fromString("left"))
    }

    @Test
    fun errorResultIsMarkedUnsuccessfulWithPrefix() {
        val r = BrowserActionResult.error("tempo esgotado")
        assertFalse(r.success)
        assertEquals("Error: tempo esgotado", r.text)
        assertNull(r.tabId)
    }
}
