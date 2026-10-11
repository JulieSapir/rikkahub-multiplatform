package com.yalantis.ucrop

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/**
 * UCrop 是 Android 的图片裁剪 Activity。桌面端没有它的等价控件，这里保留同一套 API：
 * `getIntent` 直接把源图按最大边长缩放后按设定格式写到目标文件，相当于“跳过裁剪、原样确认”，
 * 聊天附件与媒体创作因此仍能正常拿到一张 jpg，只是不能框选裁剪区域。
 *
 * MPTODO
 */
class UCrop private constructor(
    private val sourceUri: Uri,
    private val outputUri: Uri,
) {
    private var options: Options? = null
    private var maxWidth = 0
    private var maxHeight = 0
    private var aspectX: Float? = null
    private var aspectY: Float? = null

    fun withOptions(value: Options): UCrop = apply { options = value }

    fun withMaxResultSize(width: Int, height: Int): UCrop = apply {
        maxWidth = width
        maxHeight = height
    }

    fun withAspectRatio(x: Float, y: Float): UCrop = apply {
        aspectX = x
        aspectY = y
    }

    fun getIntent(context: Context): Intent = Intent(ACTION_CROP).apply {
        putExtra(EXTRA_SOURCE, sourceUri.toString())
        putExtra(EXTRA_OUTPUT, outputUri.toString())
        putExtra(EXTRA_MAX_WIDTH, maxWidth)
        putExtra(EXTRA_MAX_HEIGHT, maxHeight)
        val quality = options?.compressionQuality ?: 90
        val asJpeg = options?.compressionFormat != Bitmap.CompressFormat.PNG
        runCatching { writeFlattened(context, quality, asJpeg) }
            .onFailure { putExtra(EXTRA_ERROR, it.message ?: "crop failed") }
    }

    private fun writeFlattened(context: Context, quality: Int, asJpeg: Boolean) {
        val target = File(outputUri.path ?: error("没有输出路径"))
        target.parentFile?.mkdirs()
        val decoded = me.rerere.rikkahub.utils.ImageUtils.loadOptimizedBitmap(
            context = context,
            uri = sourceUri,
            maxSize = if (maxWidth > 0) maxOf(maxWidth, maxHeight) else 4096,
        ) ?: error("无法解码图片")
        try {
            target.outputStream().use { out ->
                decoded.compress(
                    if (asJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
                    quality,
                    out,
                )
            }
        } finally {
            decoded.recycle()
        }
    }

    class Options {
        var freeStyleCropEnabled: Boolean = false
            private set
        var compressionFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
            private set
        var compressionQuality: Int = 90
            private set
        var allowedGestures: IntArray = IntArray(0)
            private set

        fun setFreeStyleCropEnabled(enabled: Boolean) {
            freeStyleCropEnabled = enabled
        }

        fun setAllowedGestures(scale: Int, rotate: Int, other: Int) {
            allowedGestures = intArrayOf(scale, rotate, other)
        }

        fun setCompressionFormat(format: Bitmap.CompressFormat) {
            compressionFormat = format
        }

        fun setCompressionQuality(quality: Int) {
            compressionQuality = quality
        }

        fun setAspectRatioShowTitle(show: Boolean) = Unit

        fun setToolbarColor(color: Int) = Unit
    }

    companion object {
        const val RESULT_ERROR = 1001
        internal const val ACTION_CROP = "com.yalantis.ucrop.CROP"
        internal const val EXTRA_SOURCE = "extra_source"
        internal const val EXTRA_OUTPUT = "extra_output"
        internal const val EXTRA_MAX_WIDTH = "extra_max_width"
        internal const val EXTRA_MAX_HEIGHT = "extra_max_height"
        internal const val EXTRA_ERROR = "extra_crop_error"

        fun of(sourceUri: Uri, destinationUri: Uri): UCrop = UCrop(sourceUri, destinationUri)

        fun getResultUri(intent: Intent?): Uri? =
            intent?.getStringExtra(EXTRA_OUTPUT)?.let { Uri.parse(it) }

        fun getError(intent: Intent?): Throwable? =
            intent?.getStringExtra(EXTRA_ERROR)?.let { Throwable(it) }
    }
}

object UCropActivity {
    const val SCALE = 0
    const val ROTATE = 1
    const val MOVE = 2
    const val NONE = 3
}
