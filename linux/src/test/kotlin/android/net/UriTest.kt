package android.net

import org.junit.Assert.assertEquals
import org.junit.Test

/** 桌面端 Uri 兜底解析的契约：带非法字符的字符串不能把 scheme 编没，空格要按 %20 编码 */
class UriTest {

    @Test
    fun `parse keeps scheme when path has unencoded characters`() {
        val uri = Uri.parse("https://example.com/中文 x")
        assertEquals("https", uri.scheme)
        assertEquals("example.com", uri.host)
        assertEquals("/中文 x", uri.path)
    }

    @Test
    fun `encode encodes space as percent twenty`() {
        assertEquals("a%20b", Uri.encode("a b"))
    }
}
