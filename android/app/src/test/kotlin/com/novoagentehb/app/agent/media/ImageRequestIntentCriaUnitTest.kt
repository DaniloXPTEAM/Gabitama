package com.novoagentehb.app.agent.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageRequestIntentCriaUnitTest {
    @Test fun ownerExampleIsRecognizedAsExplicitImageCommand() {
        assertEquals(
            "um dragão vermelho voando sobre uma cidade medieval",
            ImageRequestIntent.parse("Gabi, cria uma imagem de um dragão vermelho voando sobre uma cidade medieval.")
                ?.removeSuffix(".")
        )
    }

    @Test fun generalMentionIsNotAnImageCommand() {
        assertNull(ImageRequestIntent.parse("Você viu aquela imagem de um dragão ontem?"))
    }
}
