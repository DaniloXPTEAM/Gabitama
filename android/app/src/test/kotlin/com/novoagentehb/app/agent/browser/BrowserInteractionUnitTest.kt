package com.novoagentehb.app.agent.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserInteractionUnitTest {

    @Test
    fun wireNamesResolveAndUnknownIsNull() {
        assertEquals(BrowserInteractionKind.CLICK, BrowserInteractionKind.fromWireName(" Click "))
        assertEquals(BrowserInteractionKind.FILL, BrowserInteractionKind.fromWireName("fill"))
        assertNull(BrowserInteractionKind.fromWireName("submit"))
    }

    @Test
    fun validateRejectsEmptyLongOrControlSelectors() {
        assertTrue(BrowserInteractionRequest.validate(BrowserInteractionKind.CLICK, "   ", null).isFailure)
        val longSelector = "a".repeat(BrowserInteractionRequest.MAX_SELECTOR_CHARS + 1)
        assertTrue(BrowserInteractionRequest.validate(BrowserInteractionKind.CLICK, longSelector, null).isFailure)
        assertTrue(BrowserInteractionRequest.validate(BrowserInteractionKind.CLICK, "button\n.x", null).isFailure)
    }

    @Test
    fun fillRequiresValueWithinLimitsAndClickIgnoresValue() {
        assertTrue(BrowserInteractionRequest.validate(BrowserInteractionKind.FILL, "input[name=q]", null).isFailure)
        val tooLong = "x".repeat(BrowserInteractionRequest.MAX_VALUE_CHARS + 1)
        assertTrue(BrowserInteractionRequest.validate(BrowserInteractionKind.FILL, "input", tooLong).isFailure)
        assertTrue(BrowserInteractionRequest.validate(BrowserInteractionKind.FILL, "input", "a\u0007b").isFailure)
        val ok = BrowserInteractionRequest.validate(BrowserInteractionKind.FILL, " input[name=q] ", "gatos\nazuis").getOrThrow()
        assertEquals("input[name=q]", ok.selector)
        assertEquals("gatos\nazuis", ok.value)
        val click = BrowserInteractionRequest.validate(BrowserInteractionKind.CLICK, "button", "ignorado").getOrThrow()
        assertNull(click.value)
    }

    @Test
    fun stringLiteralEscapesQuotesScriptTagsAndLineSeparators() {
        assertEquals("\"a\\\"b\\\\c\"", BrowserJs.stringLiteral("a\"b\\c"))
        assertEquals("\"\\u003c/script\\u003e\"", BrowserJs.stringLiteral("</script>"))
        assertEquals("\"x\\ny\\u2028\\u0001\"", BrowserJs.stringLiteral("x\ny\u2028\u0001"))
    }

    @Test
    fun injectedValueCannotBreakOutOfTheStringLiteral() {
        val hostile = "\"); document.title='pwned'; (\""
        val script = BrowserInteractionScripts.build(
            BrowserInteractionRequest(BrowserInteractionKind.FILL, "input", hostile)
        )
        // O valor aparece apenas como literal escapado, nunca como código bruto.
        assertTrue(script.contains("var v=\"\\\"); document.title='pwned'; (\\\"\";"))
    }

    @Test
    fun clickScriptNeverSubmitsForms() {
        val script = BrowserInteractionScripts.build(BrowserInteractionRequest(BrowserInteractionKind.CLICK, "button.enviar"))
        assertTrue(script.contains("el.click()"))
        assertFalse(script.contains("submit"))
        assertTrue(script.contains("'password'") && script.contains("'file'") && script.contains("'hidden'"))
    }

    @Test
    fun fillScriptVerifiesValueBeforeReportingOk() {
        val script = BrowserInteractionScripts.build(BrowserInteractionRequest(BrowserInteractionKind.FILL, "input", "oi"))
        assertTrue(script.contains("return el.value===v?'ok':'not_verified';"))
        assertFalse(script.contains("submit"))
    }

    @Test
    fun parseCodeHandlesJsonStringNullAndEmpty() {
        assertEquals("ok", BrowserInteractionCodec.parseCode("\"ok\""))
        assertEquals("no_response", BrowserInteractionCodec.parseCode("null"))
        assertEquals("no_response", BrowserInteractionCodec.parseCode(null))
    }

    @Test
    fun okOnlyWhenWebViewConfirmed() {
        val fill = BrowserInteractionCodec.toResult(BrowserInteractionKind.FILL, "ok", "https://e.com")
        assertTrue(fill.success)
        val click = BrowserInteractionCodec.toResult(BrowserInteractionKind.CLICK, "ok", "https://e.com")
        assertTrue(click.success)
        assertTrue(click.text.contains("confira"))
        val notVerified = BrowserInteractionCodec.toResult(BrowserInteractionKind.FILL, "not_verified", null)
        assertFalse(notVerified.success)
        assertEquals("FILL_NOT_VERIFIED", notVerified.errorCode)
        assertEquals("INTERACTION_NO_RESPONSE", BrowserInteractionCodec.toResult(BrowserInteractionKind.CLICK, "no_response", null).errorCode)
        assertEquals("ELEMENT_NOT_FOUND", BrowserInteractionCodec.toResult(BrowserInteractionKind.CLICK, "not_found", null).errorCode)
        assertEquals("ELEMENT_FORBIDDEN", BrowserInteractionCodec.toResult(BrowserInteractionKind.FILL, "forbidden", null).errorCode)
    }
}
