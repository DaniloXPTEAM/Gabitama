package com.novoagentehb.app.gabi

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Garante, no emulador/aparelho, que cada quadro WebP real dos assets decodifica
 * e mantém o tamanho e o canal alfa esperados. Não publica nada e não altera assets.
 */
@RunWith(AndroidJUnit4::class)
class AvatarSequenceDecodeInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyAvatarWebpFrameDecodesWithExpectedSizeAndAlpha() {
        val folders = listOf("idle", "listening", "talking", "thinking")
        for (folder in folders) {
            val names = AvatarFramePlanner.frameNames(context.assets.list("avatar/$folder")?.toList().orEmpty())
            assertTrue("sequência $folder vazia", names.isNotEmpty())
            for (name in names) {
                val opts = BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
                val bmp = context.assets.open("avatar/$folder/$name").use { BitmapFactory.decodeStream(it, null, opts) }
                assertNotNull("quadro $folder/$name não decodificou", bmp)
                assertEquals("largura de $folder/$name", 360, bmp!!.width)
                assertEquals("altura de $folder/$name", 480, bmp.height)
                assertTrue("sem alfa em $folder/$name", bmp.hasAlpha())
                bmp.recycle()
            }
        }
    }
}
