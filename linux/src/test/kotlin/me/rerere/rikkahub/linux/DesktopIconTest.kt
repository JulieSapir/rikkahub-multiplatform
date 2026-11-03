package me.rerere.rikkahub.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.imageio.ImageIO

class DesktopIconTest {
    @Test
    fun `desktop icon is available on runtime classpath`() {
        val image = javaClass.getResourceAsStream("/icons/rikkahub.png")
            ?.use { ImageIO.read(it) }
            ?: error("窗口图标未打包到运行时资源中")
        assertEquals(512, image.width)
        assertEquals(512, image.height)
        assertTrue(image.colorModel.hasAlpha())
        assertEquals(0, image.getRGB(0, 0) ushr 24)
        assertEquals(255, image.getRGB(256, 256) ushr 24)
    }
}
