package android.media

import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine

/**
 * speech 的云端 ASR provider 用 Android 的 AudioRecord 采 16kHz PCM 推给各家实时接口，
 * 桌面端用 JavaSound 的 TargetDataLine 顶替，采样格式对齐上游约定：16bit 小端单声道。
 */
object AudioFormat {
    const val CHANNEL_IN_MONO = 16
    const val CHANNEL_IN_STEREO = 12
    const val ENCODING_PCM_16BIT = 2
}

object MediaRecorder {
    /** 采集路由（回声消除等）是移动通话的概念，桌面端只有常量被引用到 */
    object AudioSource {
        const val VOICE_COMMUNICATION = 7
    }
}

/**
 * AudioRecord 的桌面端实现。
 *
 * read 的语义与 provider 的采集循环对齐：正常路径必须返回正数（Volcengine 把 0 当空转、
 * 负数当错误），所以 stop() 之后返回 0——所有 provider 都是先 cancel 采集协程再 stop，
 * 循环靠 isActive 退出，返回 -1 反而会被当成读错误在正常停止时也报错。
 *
 * read 持有 ioLock 阻塞在 line.read 里，stop()/release() 拿同一把锁，串行化后不依赖
 * 「line.stop() 能否打断阻塞中的 read」这一实现相关的行为。
 */
class AudioRecord(
    audioSource: Int,
    sampleRateInHz: Int,
    channelConfig: Int,
    audioFormat: Int,
    bufferSizeInBytes: Int,
) {
    private val channelCount = if (channelConfig == AudioFormat.CHANNEL_IN_STEREO) 2 else 1

    private val javaFormat = javax.sound.sampled.AudioFormat(
        sampleRateInHz.toFloat(),
        if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) 16 else -1,
        channelCount,
        true,
        false,
    )

    /** open 缓冲给足 provider 传进来的大小，read 才能一次拿到整块数据 */
    private val openBufferBytes = bufferSizeInBytes.coerceAtLeast(getMinBufferSize(sampleRateInHz, channelConfig, audioFormat))

    private val ioLock = Any()

    @Volatile
    private var line: TargetDataLine? = acquireLine(audioFormat)

    @Volatile
    private var recordingState = RECORDSTATE_STOPPED

    /** 构造时只查有没有输入设备，不抛异常——真正打开放到 startRecording，由 provider 的 catch 上报 */
    private fun acquireLine(audioFormat: Int): TargetDataLine? {
        if (audioFormat != AudioFormat.ENCODING_PCM_16BIT) return null
        return runCatching { AudioSystem.getTargetDataLine(javaFormat) }.getOrNull()
    }

    fun startRecording() {
        val line = this.line ?: throw IllegalStateException("没有可用的录音输入设备")
        if (recordingState == RECORDSTATE_RECORDING) return
        synchronized(ioLock) {
            try {
                if (!line.isOpen) line.open(javaFormat, openBufferBytes)
                line.start()
            } catch (e: Exception) {
                recordingState = RECORDSTATE_STOPPED
                throw IllegalStateException("无法打开麦克风: ${e.message}", e)
            }
            recordingState = RECORDSTATE_RECORDING
        }
    }

    fun read(audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
        if (offsetInBytes < 0 || sizeInBytes < 0 || offsetInBytes + sizeInBytes > audioData.size) return -1
        val line = this.line ?: return -1
        return synchronized(ioLock) {
            if (recordingState != RECORDSTATE_RECORDING) {
                0
            } else {
                try {
                    val read = line.read(audioData, offsetInBytes, sizeInBytes)
                    if (read > 0) read else 0
                } catch (e: Exception) {
                    // 并发 release() 关线会让 read 抛异常：已停止按正常退出处理，否则按读错误上报
                    if (recordingState == RECORDSTATE_RECORDING) -1 else 0
                }
            }
        }
    }

    fun stop() {
        synchronized(ioLock) {
            if (recordingState != RECORDSTATE_RECORDING) return
            recordingState = RECORDSTATE_STOPPED
            runCatching { line?.stop() }
        }
    }

    fun release() {
        synchronized(ioLock) {
            recordingState = RECORDSTATE_STOPPED
            runCatching { line?.close() }
            line = null
        }
    }

    companion object {
        const val STATE_UNINITIALIZED = 0
        const val STATE_INITIALIZED = 1
        const val RECORDSTATE_STOPPED = 1
        const val RECORDSTATE_RECORDING = 3

        /** Android 语义是「最小可用缓冲」，取 200ms 的字节数给 provider 当下限 */
        fun getMinBufferSize(sampleRateInHz: Int, channelConfig: Int, audioFormat: Int): Int {
            val channels = if (channelConfig == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
            return maxOf(1024, sampleRateInHz / 5 * 2 * channels)
        }
    }
}
