package com.novoagentehb.app

import com.novoagentehb.app.gabi.SpeechText
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextUnitTest {
    @Test fun removesFormattingForSpeechButPreservesContent() {
        val markdown = "# Olá\n- **Gabi** e *Tama*\n- [site](https://example.com)\n```kotlin\nprintln(1)\n```\nUse `código`."
        assertEquals("Olá\nGabi e Tama\nsite\n\nprintln(1)\n\nUse código.", SpeechText.fromMarkdown(markdown))
    }
    @Test fun preservesOrdinaryUnderscoresAndMultiplication() {
        assertEquals("gabi_24l e 2*3", SpeechText.fromMarkdown("gabi_24l e 2*3"))
    }
}
