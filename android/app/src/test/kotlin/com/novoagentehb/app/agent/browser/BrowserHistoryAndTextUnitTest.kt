package com.novoagentehb.app.agent.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserHistoryAndTextUnitTest {

    @Test
    fun repeatedUrlIsNotRecordedTwice() {
        assertFalse(BrowserHistoryPolicy.shouldRecord("https://a.com", "https://a.com"))
        assertTrue(BrowserHistoryPolicy.shouldRecord("https://a.com", "https://b.com"))
        assertTrue(BrowserHistoryPolicy.shouldRecord(null, "https://a.com"))
    }

    @Test
    fun titleIsCollapsedAndFallsBackToUrl() {
        assertEquals("Olá mundo", BrowserHistoryPolicy.sanitizeTitle("  Olá \n  mundo ", "https://x"))
        assertEquals("https://x", BrowserHistoryPolicy.sanitizeTitle("   ", "https://x"))
        assertEquals(BrowserHistoryPolicy.MAX_TITLE_LENGTH, BrowserHistoryPolicy.sanitizeTitle("t".repeat(999), "u").length)
    }

    @Test
    fun excessCountRespectsLimit() {
        assertEquals(0, BrowserHistoryPolicy.excessCount(BrowserHistoryPolicy.MAX_ENTRIES))
        assertEquals(1, BrowserHistoryPolicy.excessCount(BrowserHistoryPolicy.MAX_ENTRIES + 1))
    }

    @Test
    fun decodeReturnsEmptyForNullAndNullLiteral() {
        assertEquals("", BrowserTextCodec.decode(null))
        assertEquals("", BrowserTextCodec.decode("null"))
    }

    @Test
    fun decodeUnquotesAndDecodesPercentEncoding() {
        assertEquals("olá mundo", BrowserTextCodec.decode("\"ol%C3%A1%20mundo\""))
        assertEquals("a b", BrowserTextCodec.decode("a%20%20%20b"))
    }

    @Test
    fun decodeCapsLength() {
        val raw = "%41".repeat(BrowserTextCodec.MAX_TEXT_CHARS + 500)
        assertEquals(BrowserTextCodec.MAX_TEXT_CHARS, BrowserTextCodec.decode(raw).length)
    }
}
