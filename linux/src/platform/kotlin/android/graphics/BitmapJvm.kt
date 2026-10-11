package android.graphics

import java.io.InputStream
import java.io.OutputStream
import javax.imageio.ImageIO

/**
 * android.graphics.Bitmap / BitmapFactory 的 JVM 实现，走 javax.imageio。
 *
 * 上游用它们做图片尺寸探测、缩放采样与 PNG/JPEG 重编码；桌面端不需要 EXIF 旋转（相机来源不存在），
 * 所以 correctImageOrientation 之类的路径保持原图。WEBP 编码 JDK 不自带，返回失败让调用方退回原图。
 */
class Bitmap internal constructor(internal val image: java.awt.image.BufferedImage) {

    val width: Int get() = image.width
    val height: Int get() = image.height

    enum class CompressFormat { JPEG, PNG, WEBP }

    enum class Config { ARGB_8888, RGB_565 }

    fun compress(format: CompressFormat, quality: Int, out: OutputStream): Boolean = runCatching {
        when (format) {
            CompressFormat.PNG -> ImageIO.write(image, "png", out)
            CompressFormat.JPEG -> ImageIO.write(toRgb(image), "jpg", out)
            CompressFormat.WEBP -> false
        }
    }.getOrDefault(false)

    fun copy(config: Config, mutable: Boolean): Bitmap = this

    fun hasAlpha(): Boolean = image.colorModel.hasAlpha()

    fun getPixel(x: Int, y: Int): Int = image.getRGB(x, y)

    /** 桌面端没有硬件位图，recycle 只是断开引用 */
    fun recycle() {}

    val isRecycled: Boolean get() = false

    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap =
            Bitmap(java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB))

        fun createBitmap(
            source: Bitmap,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            matrix: Matrix?,
            filter: Boolean,
        ): Bitmap {
            val clippedX = x.coerceIn(0, (source.width - 1).coerceAtLeast(0))
            val clippedY = y.coerceIn(0, (source.height - 1).coerceAtLeast(0))
            val w = width.coerceAtMost(source.width - clippedX).coerceAtLeast(1)
            val h = height.coerceAtMost(source.height - clippedY).coerceAtLeast(1)
            val sub = source.image.getSubimage(clippedX, clippedY, w, h)
            return Bitmap(if (matrix?.needsRotation == true) rotate(sub, matrix.degrees) else toArgb(sub))
        }
    }
}

/** 只做上游用到的旋转，缩放/错切不经过这里 */
class Matrix {
    var degrees: Float = 0f
        private set

    fun postRotate(degrees: Float): Matrix {
        this.degrees = (this.degrees + degrees) % 360f
        return this
    }

    fun preScale(scaleX: Float, scaleY: Float): Matrix {
        scaleApplied = scaleX < 0 || scaleY < 0
        return this
    }

    var scaleApplied: Boolean = false
        private set

    val needsRotation: Boolean get() = degrees.toInt() % 360 != 0
}

private fun rotate(image: java.awt.image.BufferedImage, degrees: Float): java.awt.image.BufferedImage {
    val radians = Math.toRadians(degrees.toDouble())
    val sin = abs(kotlin.math.sin(radians))
    val cos = abs(kotlin.math.cos(radians))
    val w = (image.width * sin + image.height * cos).toInt().coerceAtLeast(1)
    val h = (image.width * cos + image.height * sin).toInt().coerceAtLeast(1)
    val out = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.translate(w / 2.0, h / 2.0)
    g.rotate(radians)
    g.drawImage(image, -image.width / 2, -image.height / 2, null)
    g.dispose()
    return out
}

private fun abs(v: Double) = if (v < 0) -v else v

private fun toArgb(src: java.awt.image.BufferedImage): java.awt.image.BufferedImage {
    if (src.type == java.awt.image.BufferedImage.TYPE_INT_ARGB) return src
    val out = java.awt.image.BufferedImage(src.width, src.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.drawImage(src, 0, 0, null)
    g.dispose()
    return out
}

private fun toRgb(src: java.awt.image.BufferedImage): java.awt.image.BufferedImage {
    if (src.type == java.awt.image.BufferedImage.TYPE_INT_RGB) return src
    val out = java.awt.image.BufferedImage(src.width, src.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val g = out.createGraphics()
    g.drawImage(java.awt.Color.WHITE.let { bg ->
        val canvas = java.awt.image.BufferedImage(src.width, src.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val cg = canvas.createGraphics()
        cg.color = bg
        cg.fillRect(0, 0, src.width, src.height)
        cg.drawImage(src, 0, 0, null)
        cg.dispose()
        canvas
    }, 0, 0, null)
    g.dispose()
    return out
}

object BitmapFactory {

    class Options {
        var inJustDecodeBounds: Boolean = false
        var inSampleSize: Int = 1
        var outWidth: Int = 0
        var outHeight: Int = 0
        var outMimeType: String? = null
    }

    /** Android 版返回平台类型，上游很多地方不写 !!，所以无 Options 的重载保证非空 */
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap =
        decodeStrict(java.io.ByteArrayInputStream(data, offset, length))

    fun decodeByteArray(
        data: ByteArray,
        offset: Int,
        length: Int,
        opts: Options,
    ): Bitmap? = decodeStream(java.io.ByteArrayInputStream(data, offset, length), null, opts)

    fun decodeFile(pathName: String): Bitmap = decodeStrict(
        runCatching { java.io.FileInputStream(pathName) }.getOrElse { return blank() },
    )

    fun decodeFile(pathName: String, opts: Options?): Bitmap? =
        runCatching { java.io.FileInputStream(pathName).use { decodeStream(it, null, opts) } }.getOrNull()

    fun decodeStream(`in`: InputStream): Bitmap = decodeStrict(`in`)

    fun decodeStream(`in`: InputStream, outPadding: Any?, opts: Options?): Bitmap? {
        val bytes = runCatching { `in`.readBytes() }.getOrNull() ?: return null
        val (w, h) = readDimensions(bytes) ?: return null
        if (opts?.inJustDecodeBounds == true) {
            opts.outWidth = w
            opts.outHeight = h
            return null
        }
        val image = runCatching { ImageIO.read(java.io.ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
        val sample = (opts?.inSampleSize ?: 1).coerceAtLeast(1)
        val scaled = if (sample > 1) {
            val tw = (image.width / sample).coerceAtLeast(1)
            val th = (image.height / sample).coerceAtLeast(1)
            val out = java.awt.image.BufferedImage(tw, th, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.drawImage(image, 0, 0, tw, th, null)
            g.dispose()
            out
        } else {
            image
        }
        opts?.outWidth = scaled.width
        opts?.outHeight = scaled.height
        return Bitmap(scaled)
    }

    private fun decodeStrict(`in`: InputStream): Bitmap =
        decodeStream(`in`, null, null) ?: blank()

    private fun blank(): Bitmap = Bitmap(
        java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB),
    )

    /** 只读头部拿尺寸，避免为探测尺寸解码整张图 */
    private fun readDimensions(bytes: ByteArray): Pair<Int, Int>? = runCatching {
        val input = javax.imageio.ImageIO.createImageInputStream(java.io.ByteArrayInputStream(bytes))
        val readers = javax.imageio.ImageIO.getImageReaders(input)
        if (!readers.hasNext()) return null
        val reader = readers.next()
        try {
            reader.input = input
            reader.getWidth(0) to reader.getHeight(0)
        } finally {
            reader.dispose()
        }
    }.getOrNull()
}
