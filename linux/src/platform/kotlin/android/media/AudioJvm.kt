package android.media

import android.content.Context
import java.io.File

/**
 * 录音前的音频焦点、音效播放与媒体库扫描都是 Android 的媒体框架。
 * 桌面端不做焦点抢占（不独占麦克风），scanFile 也只是空操作。
 */
class AudioManager {
    fun requestAudioFocus(request: AudioFocusRequest): Int = AUDIOFOCUS_REQUEST_GRANTED

    fun abandonAudioFocusRequest(request: AudioFocusRequest): Int = AUDIOFOCUS_REQUEST_GRANTED

    fun requestAudioFocus(streamType: Int, durationHint: Int): Int = AUDIOFOCUS_REQUEST_GRANTED

    fun abandonAudioFocus(listener: OnAudioFocusChangeListener?): Int = AUDIOFOCUS_REQUEST_GRANTED

    fun getStreamMaxVolume(streamType: Int): Int = 15

    fun getStreamVolume(streamType: Int): Int = 15

    fun setStreamVolume(streamType: Int, index: Int, flags: Int) = Unit

    fun isMicrophoneMute(): Boolean = false

    fun setMicrophoneMute(on: Boolean) = Unit

    interface OnAudioFocusChangeListener {
        fun onAudioFocusChange(focusChange: Int)
    }

    companion object {
        const val AUDIOFOCUS_GAIN = 1
        const val AUDIOFOCUS_GAIN_TRANSIENT = 2
        const val AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE = 4
        const val AUDIOFOCUS_REQUEST_GRANTED = 1
        const val AUDIOFOCUS_REQUEST_FAILED = 0
        const val MODE_NORMAL = 0
        const val STREAM_MUSIC = 3
        const val STREAM_VOICE_CALL = 0
    }
}

class AudioAttributes private constructor(
    val usage: Int,
    val contentType: Int,
) {
    class Builder {
        private var usage = USAGE_MEDIA
        private var contentType = CONTENT_TYPE_UNKNOWN

        fun setUsage(value: Int) = apply { usage = value }

        fun setContentType(value: Int) = apply { contentType = value }

        fun build(): AudioAttributes = AudioAttributes(usage, contentType)
    }

    companion object {
        const val USAGE_MEDIA = 1
        const val USAGE_VOICE_COMMUNICATION = 2
        const val USAGE_ASSISTANCE_SONIFICATION = 13
        const val CONTENT_TYPE_UNKNOWN = 0
        const val CONTENT_TYPE_SPEECH = 1
        const val CONTENT_TYPE_MUSIC = 2
        const val CONTENT_TYPE_SONIFICATION = 4
    }
}

class AudioFocusRequest private constructor(
    val focusGain: Int,
    val attributes: AudioAttributes?,
    val acceptsDelayedApproval: Boolean,
) {
    class Builder(private val focusGain: Int) {
        private var attributes: AudioAttributes? = null
        private var delayed = false

        fun setAudioAttributes(value: AudioAttributes) = apply { attributes = value }

        fun setAcceptsDelayedFocusGain(value: Boolean) = apply { delayed = value }

        fun setOnAudioFocusChangeListener(listener: AudioManager.OnAudioFocusChangeListener) = this

        fun build(): AudioFocusRequest = AudioFocusRequest(focusGain, attributes, delayed)
    }

    companion object {
        const val AUDIOFOCUS_REQUEST_GRANTED = 1
    }
}

/** 媒体库扫描在桌面端没有意义，保留签名让调用处原样通过。 */
object MediaScannerConnection {
    fun scanFile(
        context: Context,
        paths: Array<String>,
        mimeTypes: Array<String>?,
        callback: OnScanCompletedListener? = null,
    ) = Unit

    interface OnScanCompletedListener {
        fun onScanCompleted(path: String, uri: android.net.Uri?)
    }
}

/** SoundEffectPlayer 之外，上游还直接用 MediaPlayer 播 TTS 输出；桌面端交给 javax.sound。 */
class MediaPlayer {
    interface OnCompletionListener {
        fun onCompletion(player: MediaPlayer)
    }

    private var process: javax.sound.sampled.Clip? = null
    private var listener: OnCompletionListener? = null

    // stop()/release() 主动停止同样会发出 STOP 事件，这个标志把它们与自然播完区分开；
    // 事件在音频线程异步送达，所以标志留到下一次 start 才复位
    private var stopping = false

    fun setDataSource(path: String) {
        // 替换数据源前先释放旧 Clip，重复调用会泄漏混音器资源
        process?.close()
        stopping = false
        val clip = javax.sound.sampled.AudioSystem.getClip()
        javax.sound.sampled.AudioSystem.getAudioInputStream(File(path)).use { input ->
            clip.open(input)
        }
        clip.addLineListener { event ->
            if (event.type == javax.sound.sampled.LineEvent.Type.STOP && !stopping) {
                listener?.onCompletion(this)
            }
        }
        process = clip
    }

    fun prepare() = Unit

    fun start() {
        stopping = false
        process?.start()
    }

    fun stop() {
        stopping = true
        process?.stop()
    }

    fun release() {
        stopping = true
        process?.close()
        process = null
    }

    fun setOnCompletionListener(listener: OnCompletionListener) {
        this.listener = listener
    }

    val isPlaying: Boolean get() = process?.isRunning ?: false
}
