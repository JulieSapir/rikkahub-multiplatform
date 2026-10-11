package me.rerere.rikkahub.linux

import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import me.rerere.rikkahub.linux.res.VectorDrawableParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * res/drawable 下的 VectorDrawable 要能翻成 ImageVector，Icon 才会画出矢量图形并按内容着色，
 * 而不是退化成一块纯色方块。
 */
class VectorDrawableParserTest {

    @Test
    fun deepthinkBecomesTintableImageVector() {
        val xml = readResource("/rikka-res/drawable/app/deepthink.xml")
        val image = VectorDrawableParser.parseText(xml) ?: error("deepthink.xml 解析失败")
        assertEquals(1024f, image.viewportWidth, 0f)
        assertEquals(1024f, image.viewportHeight, 0f)

        val path = image.root.toList().single() as VectorPath
        assertTrue("pathData 为空", path.pathData.isNotEmpty())
        assertNotNull("fillColor 没有解析出来", path.fill)
    }

    @Test
    fun groupedVectorsKeepTheirChildren() {
        val image = VectorDrawableParser.parseText(
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
              android:width="24dp" android:height="24dp"
              android:viewportWidth="24" android:viewportHeight="24">
              <group android:name="g" android:rotation="90" android:pivotX="12" android:scaleX="2">
                <path android:fillColor="#FFF" android:pathData="M0,0L12,0L12,12Z" />
              </group>
            </vector>
            """.trimIndent()
        )
        assertNotNull(image)
        val group = image!!.root.toList().single() as VectorGroup
        assertEquals(90f, group.rotation, 0f)
        assertEquals(2f, group.scaleX, 0f)
        assertEquals(1, group.toList().size)
    }

    private fun readResource(path: String): String =
        javaClass.getResourceAsStream(path)!!.reader().readText()
}
