package android.media

import java.io.File

/**
 * 视频元数据探测：桌面端没有 MediaMetadataRetriever，也没有随包的解码器。
 *
 * 上游所有取值路径都允许 null（拿不到宽高就不生成缩略图），所以这里返回 null，
 * 让视频预览自然降级而不是报错。
 */
class MediaMetadataRetriever {

    fun setDataSource(path: String) {
        source = File(path)
    }

    fun setDataSource(fd: Any?) = Unit

    fun extractMetadata(featureId: Int): String? = null

    fun getFrameAtTime(timeUs: Long = 0, option: Int = OPTION_CLOSEST_SYNC): Bitmap? = null

    fun getFrameAtIndex(index: Int, option: Int = OPTION_CLOSEST_SYNC): Bitmap? = null

    fun getScaledFrameAtTime(
        timeUs: Long,
        option: Int,
        width: Int,
        height: Int,
    ): Bitmap? = null

    fun release() {}

    private var source: File? = null

    companion object {
        const val METADATA_KEY_DURATION = 9
        const val METADATA_KEY_VIDEO_WIDTH = 18
        const val METADATA_KEY_VIDEO_HEIGHT = 19
        const val METADATA_KEY_VIDEO_ROTATION = 23
        const val METADATA_KEY_VIDEO_FRAME_COUNT = 27
        const val OPTION_CLOSEST = 3
        const val OPTION_CLOSEST_SYNC = 2
        const val OPTION_PREVIOUS_SYNC = 0
        const val OPTION_NEXT_SYNC = 1
    }
}

private typealias Bitmap = android.graphics.Bitmap
