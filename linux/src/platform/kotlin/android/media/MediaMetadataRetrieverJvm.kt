package android.media

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.mp4.Mp4Directory
import com.drew.metadata.mp4.media.Mp4VideoDirectory
import java.io.File
import kotlin.math.roundToInt

/**
 * 视频元数据探测：桌面端没有 Android 的解码栈，用 metadata-extractor（纯 Java 的 MP4 box 解析）
 * 读 mvhd/tkhd 拿时长与旋转、读视频轨道拿宽高；抽帧没有对应能力，仍然返回 null（保持原行为）。
 *
 * 不支持的容器（webm/mkv 等）读不出 Mp4Directory，extractMetadata 返回 null，调用方自然降级。
 */
class MediaMetadataRetriever {

    private var source: File? = null
    private var parsedMetadata: Metadata? = null
    private var metadataRead = false

    fun setDataSource(path: String) {
        source = File(path)
        parsedMetadata = null
        metadataRead = false
    }

    fun setDataSource(fd: Any?) = Unit

    fun extractMetadata(featureId: Int): String? {
        val metadata = parsedMetadata ?: readMetadata() ?: return null
        return when (featureId) {
            METADATA_KEY_DURATION -> durationMillis(metadata)?.toString()
            METADATA_KEY_VIDEO_WIDTH -> videoDirectory(metadata)?.getInteger(Mp4VideoDirectory.TAG_WIDTH)?.toString()
            METADATA_KEY_VIDEO_HEIGHT -> videoDirectory(metadata)?.getInteger(Mp4VideoDirectory.TAG_HEIGHT)?.toString()
            METADATA_KEY_VIDEO_ROTATION -> rotationDegrees(metadata)?.toString()
            else -> null
        }
    }

    fun getFrameAtTime(timeUs: Long = 0, option: Int = OPTION_CLOSEST_SYNC): Bitmap? = null

    fun getFrameAtIndex(index: Int, option: Int = OPTION_CLOSEST_SYNC): Bitmap? = null

    fun getScaledFrameAtTime(
        timeUs: Long,
        option: Int,
        width: Int,
        height: Int,
    ): Bitmap? = null

    fun release() {}

    /** 只解析一次，失败也记住（metadataRead 标记），避免每个 key 重复解一遍 box */
    private fun readMetadata(): Metadata? {
        if (metadataRead) return parsedMetadata
        metadataRead = true
        val file = source?.takeIf { it.isFile } ?: return null
        parsedMetadata = runCatching { ImageMetadataReader.readMetadata(file) }.getOrNull()
        return parsedMetadata
    }

    private fun videoDirectory(metadata: Metadata): Mp4VideoDirectory? =
        metadata.getDirectoriesOfType(Mp4VideoDirectory::class.java).firstOrNull()

    private fun durationMillis(metadata: Metadata): Int? {
        // TAG_DURATION_SECONDS 是 duration/timescale 的 Rational，直接换算成 Android 语义的毫秒字符串
        val seconds = metadata.getFirstDirectoryOfType(Mp4Directory::class.java)
            ?.getRational(Mp4Directory.TAG_DURATION_SECONDS)
            ?.toDouble()
            ?: return null
        return (seconds * 1000).roundToInt()
    }

    private fun rotationDegrees(metadata: Metadata): Int? {
        // tkhd 的变换矩阵算出的角度可能是 -90（=270）这类负值，归一到 0/90/180/270 再给调用方
        val degree = metadata.getFirstDirectoryOfType(Mp4Directory::class.java)
            ?.getDoubleObject(Mp4Directory.TAG_ROTATION)
            ?: return null
        return (((degree + 360.0) % 360.0) / 90.0).roundToInt() * 90 % 360
    }

    companion object {
        const val METADATA_KEY_DURATION = 9
        const val METADATA_KEY_VIDEO_WIDTH = 18
        const val METADATA_KEY_VIDEO_HEIGHT = 19
        const val METADATA_KEY_VIDEO_ROTATION = 24
        const val METADATA_KEY_VIDEO_FRAME_COUNT = 27
        const val OPTION_CLOSEST = 3
        const val OPTION_CLOSEST_SYNC = 2
        const val OPTION_PREVIOUS_SYNC = 0
        const val OPTION_NEXT_SYNC = 1
    }
}

private typealias Bitmap = android.graphics.Bitmap
