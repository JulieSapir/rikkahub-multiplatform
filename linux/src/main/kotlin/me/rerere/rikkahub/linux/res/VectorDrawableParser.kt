package me.rerere.rikkahub.linux.res

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Android 的 VectorDrawable（res/drawable 下的 xml）在桌面端没有渲染管线：
 * androidx 的 VectorDrawable 实现绑在 Android 框架上，Coil 的 SVG 解码器又不认 Android 的命名空间。
 *
 * 这里把上游用到的那部分属性（vector 尺寸/viewport、path 的 pathData/fillColor/strokeColor/
 * strokeWidth/alpha、group 的平移缩放旋转）翻成 Compose 的 ImageVector，
 * 于是 painterResource(R.drawable.deepthink) 这类调用点和 Android 一样能画出矢量图标。
 * 上游图标全是纯色填充的单色图标，渐变 / clip-path / trim-path 没有用到，遇到就忽略。
 */
object VectorDrawableParser {

    private val cache = ConcurrentHashMap<String, ImageVector?>()

    /** 同一份 XML 只解析一次：图标会在列表里被反复组合。 */
    fun parseText(xml: String): ImageVector? = cache.getOrPut(xml) {
        runCatching { decode(xml) }.getOrNull()
    }

    private fun decode(xml: String): ImageVector? {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
        val root = document.documentElement ?: return null
        if (root.tagName != "vector") return null

        val viewportWidth = root.attrFloat("viewportWidth") ?: 24f
        val viewportHeight = root.attrFloat("viewportHeight") ?: 24f
        val width = root.attrFloat("width")?.takeIf { it > 0f } ?: viewportWidth
        val height = root.attrFloat("height")?.takeIf { it > 0f } ?: viewportHeight

        return ImageVector.Builder(
            name = "VectorDrawable",
            defaultWidth = width.dp,
            defaultHeight = height.dp,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        ).apply { addChildren(root) }.build()
    }

    /**
     * 桌面端的 ImageVector.Builder 只有命令式的 addGroup/addPath：addGroup 之后 addPath 就落进这个组，
     * clearGroup 回到父一级，所以递归下去即可，不用给 group 和 vector 两级各写一遍。
     */
    private fun ImageVector.Builder.addChildren(parent: Element) {
        parent.childElements().forEach { child ->
            when (child.tagName) {
                "path" -> {
                    val pathData = child.attr("pathData")?.let { PathParser().parsePathString(it).toNodes() }
                    if (pathData != null) {
                        addPath(
                            pathData = pathData,
                            fill = child.attrColor("fillColor")?.let { SolidColor(it) },
                            fillAlpha = child.attrFloat("fillAlpha") ?: 1f,
                            stroke = child.attrColor("strokeColor")?.let { SolidColor(it) },
                            strokeAlpha = child.attrFloat("strokeAlpha") ?: 1f,
                            strokeLineWidth = child.attrFloat("strokeWidth") ?: 0f,
                        )
                    }
                }

                "group" -> {
                    addGroup(
                        name = child.attr("name").orEmpty(),
                        rotate = child.attrFloat("rotation") ?: 0f,
                        pivotX = child.attrFloat("pivotX") ?: 0f,
                        pivotY = child.attrFloat("pivotY") ?: 0f,
                        scaleX = child.attrFloat("scaleX") ?: 1f,
                        scaleY = child.attrFloat("scaleY") ?: 1f,
                        translationX = child.attrFloat("translateX") ?: 0f,
                        translationY = child.attrFloat("translateY") ?: 0f,
                    )
                    addChildren(child)
                    clearGroup()
                }

                else -> Unit
            }
        }
    }

    private fun Element.childElements(): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    /** 属性都带 android: 前缀，按 localName 取才不用管命名空间声明的差别。 */
    private fun Element.attr(name: String): String? {
        val direct = getAttribute(name)
        if (!direct.isNullOrBlank()) return direct
        val attributes = this.attributes
        for (i in 0 until attributes.length) {
            val item = attributes.item(i)
            if (item.localName == name) return item.nodeValue
        }
        return null
    }

    private fun Element.attrFloat(name: String): Float? =
        attr(name)?.removeSuffix("dp")?.removeSuffix("sp")?.trim()?.toFloatOrNull()

    private fun Element.attrColor(name: String): Color? = attr(name)?.let { parseColor(it) }

    /** 支持 #RGB / #ARGB / #RRGGBB / #AARRGGBB，其它写法（@color/xxx）当作没有。 */
    private fun parseColor(raw: String): Color? {
        val value = raw.trim().removePrefix("#")
        val hex = when (value.length) {
            3 -> "FF${value.map { "$it$it" }.joinToString("")}"
            4 -> value.map { "$it$it" }.joinToString("")
            6 -> "FF$value"
            8 -> value
            else -> return null
        }
        val parsed = hex.toLongOrNull(16) ?: return null
        return Color(
            red = (parsed shr 16 and 0xff).toInt(),
            green = (parsed shr 8 and 0xff).toInt(),
            blue = (parsed and 0xff).toInt(),
            alpha = (parsed shr 24 and 0xff).toInt(),
        )
    }
}
