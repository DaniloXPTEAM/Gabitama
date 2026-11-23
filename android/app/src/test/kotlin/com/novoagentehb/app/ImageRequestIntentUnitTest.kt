package com.novoagentehb.app

import com.novoagentehb.app.agent.media.ImageRequestIntent
import org.junit.Assert.*
import org.junit.Test

class ImageRequestIntentUnitTest {
    @Test fun explicitRequestsAreRecognizedWithoutGuessingARequest() {
        assertEquals("uma maçã.", ImageRequestIntent.parse("Gabi, crie uma imagem de uma maçã."))
        assertEquals("paisagem", ImageRequestIntent.parse("gere uma ilustração de paisagem"))
        assertNull(ImageRequestIntent.parse("não crie uma imagem de uma maçã"))
        assertNull(ImageRequestIntent.parse("o que você acha desta imagem de uma maçã?"))
        assertNull(ImageRequestIntent.parse("crie uma imagem de " + "x".repeat(4001)))
    }
}
