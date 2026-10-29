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
            return Bitmap(applyMatrix(sub, matrix, filter))
        }
    }
}

/**
 * android.graphics.Matrix 的桌面端实现，内部就是 AffineTransform：旋转/缩放/镜像统一走它，
 * pre/post 语义与 Android 一致（pre 先应用、post 后应用），createBitmap 绘制时整体应用。
 */
class Matrix {
    internal val transform = java.awt.geom.AffineTransform()

    private fun rotation(degrees: Float) =
        java.awt.geom.AffineTransform.getRotateInstance(Math.toRadians(degrees.toDouble()))

    private fun scaling(scaleX: Float, scaleY: Float) =
        java.awt.geom.AffineTransform.getScaleInstance(scaleX.toDouble(), scaleY.toDouble())

    fun preRotate(degrees: Float): Matrix = apply { transform.concatenate(rotation(degrees)) }

    fun postRotate(degrees: Float): Matrix = apply { transform.preConcatenate(rotation(degrees)) }

    fun preScale(scaleX: Float, scaleY: Float): Matrix = apply { transform.concatenate(scaling(scaleX, scaleY)) }

    fun postScale(scaleX: Float, scaleY: Float): Matrix = apply { transform.preConcatenate(scaling(scaleX, scaleY)) }

    fun setRotate(degrees: Float): Matrix = apply { transform.setToRotation(Math.toRadians(degrees.toDouble())) }

    fun setScale(scaleX: Float, scaleY: Float): Matrix = apply { transform.setToScale(scaleX.toDouble(), scaleY.toDouble()) }
}

/** 按 Matrix 变换绘制源图：画布取变换后包围盒，负半轴部分平移回原点 */
private fun applyMatrix(
    src: java.awt.image.BufferedImage,
    matrix: Matrix?,
    filter: Boolean,
): java.awt.image.BufferedImage {
    val tx = matrix?.transform
    if (tx == null || tx.isIdentity) return toArgb(src)
    val rect = java.awt.geom.Rectangle2D.Double(0.0, 0.0, src.width.toDouble(), src.height.toDouble())
    val bounds = tx.createTransformedShape(rect).bounds
    val out = java.awt.image.BufferedImage(
        bounds.width.coerceAtLeast(1),
        bounds.height.coerceAtLeast(1),
        java.awt.image.BufferedImage.TYPE_INT_ARGB,
    )
    val g = out.createGraphics()
    g.setRenderingHint(
        java.awt.RenderingHints.KEY_INTERPOLATION,
        if (filter) java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
        else java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
    )
    g.translate(-bounds.x.toDouble(), -bounds.y.toDouble())
    g.transform(tx)
    g.drawImage(src, 0, 0, null)
    g.dispose()
    return out
}

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

    fun decodeFile(pathName: String): Bitmap =
        runCatching { java.io.FileInputStream(pathName).use { decodeStrict(it) } }.getOrElse { blank() }

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
